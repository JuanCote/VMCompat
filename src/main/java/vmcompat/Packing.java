package vmcompat;

import java.nio.*;
import java.util.*;
import java.util.regex.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;

/** Overflow path for float sampler2D resources. Metadata and mip texels stay in one buffer texture. */
public final class Packing {
    private static final Pattern DECL=Pattern.compile("\\buniform\\s+sampler2D\\s+(\\w+)\\s*(?:\\[\\s*(\\w+)\\s*\\])?\\s*;");
    private static final int VIRTUAL=1_000_000;
    private static final Map<ContextResources.Group,Map<Integer,Long>> PACKED_GROUPS=new WeakHashMap<>();
    private static final StateStore<Map<Integer,Plan>> CONTEXT_PLANS=new StateStore<>(HashMap::new);
    // Program names may repeat in independent contexts. Keep the existing Map
    // view for inspection, but never replace/delete another context's storage.
    private static final Map<Integer,Plan> PLANS=new AbstractMap<>(){
        public Set<Entry<Integer,Plan>> entrySet(){return CONTEXT_PLANS.get().entrySet();}
        public Plan get(Object key){return CONTEXT_PLANS.get().get(key);}
        public Plan put(Integer key,Plan value){return CONTEXT_PLANS.get().put(key,value);}
        public Plan remove(Object key){return CONTEXT_PLANS.get().remove(key);}
    };
    static final class Slot {String name;int unit;boolean active=true;Slot(String n){name=n;}}
    static final class Plan {
        final List<Slot> slots=new ArrayList<>();final Map<String,Integer> indices=new LinkedHashMap<>();
        boolean lighting;int buffer,texture,readFramebuffer,imageLocation=-2,boundBuffer;long fingerprint=Long.MIN_VALUE,lastBytes,capacity,cursor,reportedCapacity,revision;IntBuffer metadata;FloatBuffer depthScratch;UploadSlot[] staging;UploadSlot depthStage;int nextStage;final ContextResources.Identity context=ContextResources.current();final Map<Key,Resident> images=new LinkedHashMap<>(16,.75f,true);long cacheLimit;final TreeMap<Long,Long> free=new TreeMap<>();
        PackedTables tables;
        int tableOffset, tableLocation = -2, selectedOffset = -1;
        int reservedTableBytes(){return slots.size()*256+16+(tables==null?0:tables.extraBytes());}
    }
    public static boolean canLower(Map<Integer,String> sources){
        // FXAA's explicit mip offsets have no VmImage overload. Keep the native sampler path.
        return sources.values().stream().noneMatch(s->s.contains("textureLodOffset"))&&sources.values().stream().anyMatch(s->DECL.matcher(s).find());
    }
    private static final Set<String> LIGHT_INPUTS = Set.of(
        "uRelief", "uReliefWater", "uSkyView", "uCloudShadow", "uLampFields",
        "uBounce", "uBounceOpen", "uWaterGrid", "uBedColours", "uWeatherMap",
        "uNoiseTex", "uCloudCurl", "uSnowFresh", "uSnowGrid",
        "uCloudSheets", "uMultipleScattering", "uTransmittance", "uWeatherNoise", "uOcclusion");
    static boolean lightingSource(String source) {
        return source.contains("uniform sampler2D uAlbedo;")
            && source.contains("uniform sampler2D uNormal;")
            && source.contains("uniform sampler2D uEngineLight;")
            && source.contains("vec3 lightShare(");
    }
    public static Map<Integer,String> lower(int program,Map<Integer,String> sources,Map<Integer,Boolean> vertices){
        return lower(program,sources,vertices,Boolean.parseBoolean(Options.value("packingArrayOnly","true")));
    }
    public static Map<Integer,String> lowerAll(int program,Map<Integer,String> sources,Map<Integer,Boolean> vertices){
        return lower(program,sources,vertices,false);
    }
    private static Map<Integer,String> lower(int program,Map<Integer,String> sources,Map<Integer,Boolean> vertices,boolean preferArrays){
        boolean lighting=NativeSamplers.ENABLED&&sources.values().stream().anyMatch(Packing::lightingSource);
        boolean protectedSource=NativeSamplers.ENABLED&&sources.values().stream().anyMatch(NativeSamplers::protectedSource);
        if(!preferArrays&&protectedSource){
            throw new UnsupportedOperationException("No native sampler recipe for this shader; refusing full world atlas packing");
        }
        Plan plan=new Plan();
        plan.lighting=lighting;
        Set<String> selected=new HashSet<>();
        if(lighting){
            selected.addAll(LIGHT_INPUTS);
            // Keep the reflection depth pyramid native unless the first recipe still exceeds the sampler limit.
            if(!preferArrays)selected.add("uReflectionDepth");
        }else if(preferArrays){
            for(String source:sources.values()){
                Matcher d=DECL.matcher(source);
                while(d.find()){
                    String name=d.group(1);
                    if(d.group(2)!=null&&arrayCount(source,d.group(2))>=4
                        &&!(NativeSamplers.ENABLED&&(name.equals("uAtlases")||name.equals("uTextures"))))selected.add(name);
                    if(Set.of("uTreeAtlas","uWeatherNoise","uWeatherMap","uSnowGrid","uTop","uSide","uShellMask").contains(name)
                        ||NativeSamplers.ENABLED&&Set.of("uRustMap","uZones","uLamps","uDamage1Overlay","uDamage1Shell","uDamage2Overlay","uDamage2Shell","uNoiseTex").contains(name))selected.add(name);
                }
            }
        }
        boolean partial=!selected.isEmpty();
        if(!partial&&protectedSource)throw new UnsupportedOperationException("No bounded native sampler recipe; refusing implicit full atlas packing");
        for(String source:sources.values()){
            Matcher declarations=DECL.matcher(source);while(declarations.find()){
                String name=declarations.group(1);if(partial&&!selected.contains(name))continue;int count=arrayCount(source,declarations.group(2));
                if(!plan.indices.containsKey(name)){
                    int first=plan.slots.size();plan.indices.put(name,first);
                    for(int i=0;i<count;i++){String element=count==1?name:name+"["+i+"]";plan.indices.put(element,plan.slots.size());plan.slots.add(new Slot(element));}
                    plan.indices.put(name+"[0]",first);
                }
            }
        }
        if(plan.slots.size()>128)throw new IllegalStateException("More than 128 float image samplers");
        plan.tables=PackedTables.create(plan.slots.size());
        Map<Integer,String> out=new HashMap<>();
        for(var entry:sources.entrySet()){
            String source=entry.getValue();Matcher declarations=DECL.matcher(source);StringBuffer replaced=new StringBuffer();
            while(declarations.find()){
                String name=declarations.group(1);if(partial&&!selected.contains(name)){declarations.appendReplacement(replaced,Matcher.quoteReplacement(declarations.group()));continue;}int count=arrayCount(source,declarations.group(2)),index=plan.indices.get(name);
                String declaration;
                if(lighting){
                    if(declarations.group(2)!=null)throw new IllegalStateException("Unexpected lighting sampler array: "+name);
                    declaration="uniform int vmLightSlot_"+name+";\n#define "+name+" VmImage(vmLightSlot_"+name+")\n";
                }else if(declarations.group(2)==null)declaration="const VmImage "+name+"=VmImage("+index+");";
                else {StringJoiner slots=new StringJoiner(",");for(int i=0;i<count;i++)slots.add("VmImage("+(index+i)+")");declaration="const VmImage "+name+"["+count+"]=VmImage["+count+"]("+slots+");";}
                declarations.appendReplacement(replaced,Matcher.quoteReplacement(declaration));
            }declarations.appendTail(replaced);
            source=partial?SamplerFunctions.overloads(replaced.toString()):replaced.toString().replaceAll("\\bsampler2D\\b","VmImage");
            source=ModelPacking.collapse(source,plan.indices.getOrDefault("uTextures",-1));source=ModelPacking.collapseAtlas(source,plan.indices.getOrDefault("uAtlases",-1));source=Shaders.insertDeclarations(source,helpers(vertices.get(entry.getKey()),plan.tables!=null));out.put(entry.getKey(),source);
        }
        plan.revision=ContextResources.programRevision(program);PACKED_GROUPS.computeIfAbsent(ContextResources.group(),k->new HashMap<>()).put(program,plan.revision);PLANS.put(program,plan);Log.info("Packing program "+program+": "+plan.slots.size()+" float images in one buffer sampler; selectedArrays="+partial);return out;
    }
    private static int arrayCount(String source,String expression){if(expression==null)return 1;if(expression.matches("[0-9]+"))return Integer.parseInt(expression);Matcher m=Pattern.compile("(?m)^\\s*#define\\s+"+Pattern.quote(expression)+"\\s+(\\d+)\\b").matcher(source);if(m.find())return Integer.parseInt(m.group(1));throw new IllegalArgumentException("Cannot resolve sampler array size "+expression);}
    static void linked(int program){
        Plan plan=PLANS.get(program);
        if(plan==null||!plan.lighting)return;
        for(int i=0;i<plan.slots.size();i++){
            Slot slot=plan.slots.get(i);
            int location=GL20.glGetUniformLocation(program,"vmLightSlot_"+slot.name);
            slot.active=location>=0;
            if(slot.active)GL41.glProgramUniform1i(program,location,i);
        }
    }
    public static int location(int program,String name){Plan p=PLANS.get(program);Integer index=p==null?null:p.indices.get(name);return index==null||!p.slots.get(index).active?-1:VIRTUAL+index;}
    public static boolean assign(int program,int location,int unit){Plan p=PLANS.get(program);if(p==null||location<VIRTUAL||location>=VIRTUAL+p.slots.size())return false;if(unit<0||unit>=256)throw new IllegalArgumentException("Texture unit "+unit);Slot slot=p.slots.get(location-VIRTUAL);if(slot.unit!=unit){slot.unit=unit;p.fingerprint=Long.MIN_VALUE;}return true;}
    public static void deleted(int program){Plan p=PLANS.remove(program);if(p==null)return;
        if(p.texture!=0)Textures.glDeleteTextures(p.texture);long bytes=0;if(p.buffer!=0){Buffers.glDeleteBuffers(p.buffer);bytes+=p.capacity;}
        if(p.staging!=null)for(UploadSlot slot:p.staging)bytes+=deleteStage(slot);bytes+=deleteStage(p.depthStage);
        if(bytes>0){long fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(fence==0)throw stop("Packing delete fence creation failed");RETIRED.get().add(new Retired(fence,bytes));}
        if(p.readFramebuffer!=0)GL30.glDeleteFramebuffers(p.readFramebuffer);
    }
    public static void beforeDraw(int program){
        Plan plan=PLANS.get(program);if(plan==null){Long revision=PACKED_GROUPS.getOrDefault(ContextResources.group(),Map.of()).get(program);if(revision!=null&&revision==ContextResources.programRevision(program))throw stop("Packed shader execution in a second shared context is unqualified; rendering stopped");return;}if(plan.revision!=ContextResources.programRevision(program))throw stop("Packed shader relinked outside its owning context; restart required");if(failure!=null)throw new IllegalStateException(failure);if(!plan.context.equals(ContextResources.current()))throw stop("Packing program belongs to another context");Samplers.State bindings=Samplers.state();
        ContextResources.Group group=plan.context.group();
        PackedSampling sampling=PackedSampling.current(group);
        PackedTables tables=plan.tables;
        if(tables!=null&&sampling!=null&&tables.capture(plan.slots,bindings,sampling)){
            int hit=tables.find();
            if(hit>=0){
                int offset=tables.offset(hit);
                if(plan.tableOffset!=offset)Counters.add(Counters.Key.PACK_TABLE_REUSES,1);
                plan.tableOffset=offset;
            }else{
                Counters.add(Counters.Key.PACK_TABLE_MISSES,1);
                upload(plan,bindings,sampling,true);
            }
            plan.fingerprint=Long.MIN_VALUE;
        }else{
            long fingerprint=fingerprint(plan,bindings,sampling);
            if(plan.tableOffset!=0||plan.fingerprint!=fingerprint||!group.trusted||group.contexts!=1){
                upload(plan,bindings,sampling,false);
                if(sampling!=null)fingerprint=fingerprint(plan,bindings,sampling);
            }
            plan.fingerprint=fingerprint;
        }
        if(tables!=null&&plan.tableOffset!=plan.selectedOffset){
            if(plan.tableLocation==-2)plan.tableLocation=GL20.glGetUniformLocation(program,"vmcompatTableBase");
            GL41.glProgramUniform1i(program,plan.tableLocation,plan.tableOffset/16);
            plan.selectedOffset=plan.tableOffset;
        }
        int physical=bindings.scratch-1;GL13.glActiveTexture(GL13.GL_TEXTURE0+physical);if(bindings.live[physical][4]!=plan.texture){GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,plan.texture);}if(bindings.liveSampler[physical]!=0){GL33.glBindSampler(physical,0);}if(plan.imageLocation==-2){plan.imageLocation=GL20.glGetUniformLocation(program,"vmcompatImagePixels");GL20.glUniform1i(plan.imageLocation,physical);}
        bindings.live[physical][4]=plan.texture;bindings.liveSampler[physical]=0;GL13.glActiveTexture(GL13.GL_TEXTURE0+bindings.scratch);
    }
    private static long fingerprint(Plan plan,Samplers.State bindings,PackedSampling sampling){
        long result=17;
        for(Slot slot:plan.slots){
            int texture=slot.active?bindings.texture(slot.unit,0):0;
            int sampler=bindings.sampler[slot.unit];
            result=result*31+texture;
            result=result*31+Textures.version(texture);
            result=result*31+sampler;
            if(sampling!=null&&texture!=0)result=result*31+sampling.revision(texture,sampler);
        }
        return result;
    }
    private record Level(int texture,int mip,int width,int height,boolean depth,boolean bytes,boolean red16,boolean redFloat,long offset){}
    private record Key(int texture,long generation){}
    private static final class Resident {
        final Key key; final long base,bytes; final List<Level> levels;long version=Long.MIN_VALUE;
        Resident(Key k,long b,long n,List<Level> l){key=k;base=b;bytes=n;levels=l;}
    }
    private static final int SCRATCH_BYTES=4*1024*1024;
    static final long BUDGET=384L*1024*1024;
    static final long CACHE_TARGET=64L*1024*1024, CACHE_MAX=128L*1024*1024;
    private static long reserved,peak,allocations,copiedBytes,copiedImages,reusedImages,evictions,rebuilds;
    private static volatile String failure;
    private static IllegalStateException stop(String reason){failure=reason;Log.info(reason);return new IllegalStateException(reason);}
    private record Retired(long fence,long bytes){}
    private static final StateStore<List<Retired>> RETIRED=new StateStore<>(ArrayList::new);
    public static String failure(){return failure;}
    static synchronized long[] memoryStats(){return new long[]{reserved,peak,allocations,copiedBytes,copiedImages,reusedImages,evictions,rebuilds};}
    private static void reap(){var retired=RETIRED.get();for(var i=retired.iterator();i.hasNext();){var r=i.next();int status=GL32.glClientWaitSync(r.fence,0,0);if(status==GL32.GL_ALREADY_SIGNALED||status==GL32.GL_CONDITION_SATISFIED){GL32.glDeleteSync(r.fence);synchronized(Packing.class){reserved-=r.bytes;}i.remove();}else if(status==GL32.GL_WAIT_FAILED)throw new IllegalStateException("Packing retirement fence failed");}}
    static void releaseCpu(long bytes){release(bytes);}
    static void reserveAux(long bytes){reap();reserve(bytes);}
    static void retireAux(long bytes){long fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(fence==0)throw stop("Native storage retirement fence failed");RETIRED.get().add(new Retired(fence,bytes));}
    private static synchronized void reserve(long bytes){if(bytes<0||bytes>BUDGET-reserved)throw stop("VMCompat packing memory limit: need "+bytes+", reserved "+reserved+", limit "+BUDGET+" bytes; stopped before allocation");reserved+=bytes;peak=Math.max(peak,reserved);}
    private static synchronized void release(long bytes){reserved-=bytes;}
    private static long aligned(long n){return Math.addExact(n,15)&~15L;}
    private static final class Fragmented extends RuntimeException {}
    private static long allocate(Plan plan,long bytes,Set<Key> pinned){
        while(true){for(var i=plan.free.entrySet().iterator();i.hasNext();){var e=i.next();if(e.getValue()<bytes)continue;long base=e.getKey(),left=e.getValue()-bytes;i.remove();if(left>0)plan.free.put(base+bytes,left);return base;}
            if(bytes<=plan.cacheLimit-plan.cursor){long base=plan.cursor;plan.cursor+=bytes;return base;}
            boolean evicted=false;for(var i=plan.images.entrySet().iterator();i.hasNext();){var e=i.next();if(pinned.contains(e.getKey()))continue;Resident old=e.getValue();i.remove();free(plan,old);evictions++;evicted=true;break;}if(!evicted)throw new Fragmented();
        }
    }
    private static void free(Plan plan,Resident image){if(plan.tables!=null)plan.tables.invalidate();long base=image.base,size=image.bytes;var before=plan.free.lowerEntry(base);if(before!=null&&before.getKey()+before.getValue()==base){base=before.getKey();size+=before.getValue();plan.free.remove(before.getKey());}var after=plan.free.ceilingEntry(base);if(after!=null&&base+size==after.getKey()){size+=after.getValue();plan.free.remove(after.getKey());}if(base+size==plan.cursor)plan.cursor=base;else plan.free.put(base,size);}
    static void textureDeleted(int id){for(Plan plan:PLANS.values()){var it=plan.images.entrySet().iterator();while(it.hasNext()){var e=it.next();if(e.getKey().texture==id){free(plan,e.getValue());it.remove();plan.fingerprint=Long.MIN_VALUE;}}}}
    static void closeCurrent(){var identity=ContextResources.current();for(int program:new ArrayList<>(PLANS.keySet()))if(PLANS.get(program).context.equals(identity))deleted(program);for(Retired r:RETIRED.get()){GL32.glDeleteSync(r.fence);release(r.bytes);}RETIRED.get().clear();}
    private static void capacity(Plan plan,long required){
        if(required<=plan.capacity)return;reap();long max=Math.min(plan.cacheLimit,Math.multiplyExact((long)GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE),16));
        long capacity=required>16L*1024*1024?max:Math.min(max,Math.max(required,Math.max(1024*1024L,plan.capacity*2)));
        if(required>capacity)throw stop("Packed image storage requires "+required+" bytes, limit "+max);
        reserve(capacity);int next=0;boolean allocated=false;
        try(var guard=new BufferTextureGuard()){
            next=Buffers.glGenBuffers();GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,next);Buffers.glBufferData(GL31.GL_COPY_WRITE_BUFFER,capacity,GL15.GL_DYNAMIC_DRAW);if(GL32.glGetBufferParameteri64(GL31.GL_COPY_WRITE_BUFFER,GL15.GL_BUFFER_SIZE)!=capacity)throw stop("Packing buffer allocation failed before upload");
            if(plan.buffer!=0){GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,plan.buffer);GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,0,0,plan.capacity);long fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(fence==0)throw new IllegalStateException("Packing growth fence creation failed");RETIRED.get().add(new Retired(fence,plan.capacity));Buffers.glDeleteBuffers(plan.buffer);}
            plan.buffer=next;plan.capacity=capacity;allocations++;allocated=true;
        }finally{if(!allocated){if(next!=0)Buffers.glDeleteBuffers(next);release(capacity);}}
    }
    // CPU uploads touch only small, fence-protected staging buffers. The large
    // resident TBO receives ordered GPU copies, never host subdata/renaming.
    private static final class UploadSlot {int buffer;long capacity,fence;}
    private static UploadSlot createStage(long bytes){reserve(bytes);UploadSlot s=new UploadSlot();try{s.buffer=Buffers.glGenBuffers();s.capacity=bytes;GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,s.buffer);Buffers.glBufferData(GL31.GL_COPY_READ_BUFFER,bytes,GL15.GL_DYNAMIC_DRAW);if(GL32.glGetBufferParameteri64(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE)!=bytes)throw stop("Packing staging allocation failed");allocations++;return s;}catch(Throwable e){if(s.buffer!=0)Buffers.glDeleteBuffers(s.buffer);release(bytes);throw e;}}
    private static boolean ready(UploadSlot s){if(s.fence==0)return true;int status=GL32.glClientWaitSync(s.fence,0,0);if(status==GL32.GL_ALREADY_SIGNALED||status==GL32.GL_CONDITION_SATISFIED){GL32.glDeleteSync(s.fence);s.fence=0;return true;}if(status==GL32.GL_WAIT_FAILED)throw stop("Packing upload fence failed");return false;}
    private static UploadSlot metadataStage(Plan p,int bytes){if(p.staging==null){p.staging=new UploadSlot[64];for(int i=0;i<p.staging.length;i++)p.staging[i]=createStage(bytes);}for(int i=0;i<p.staging.length;i++){UploadSlot s=p.staging[p.nextStage++&63];if(ready(s))return s;}GL11.glFlush();throw stop("Packing upload queue limit (64 pending metadata copies); rendering stopped without allocating or waiting");}
    private static void staged(Plan p,IntBuffer data,int offset){int size=data.remaining()*4;UploadSlot s=metadataStage(p,size);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,s.buffer);Buffers.glBufferSubData(GL31.GL_COPY_READ_BUFFER,0,data);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,p.buffer);GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,0,offset,size);s.fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(s.fence==0)throw stop("Packing upload fence creation failed");}
    private static void stagedDepth(Plan p,long offset,FloatBuffer data){int size=data.remaining()*4;if(p.depthStage==null)p.depthStage=createStage(SCRATCH_BYTES);UploadSlot s=p.depthStage;if(!ready(s))throw stop("Packing depth staging is still busy after the depth read");GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,s.buffer);Buffers.glBufferSubData(GL31.GL_COPY_READ_BUFFER,0,data);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,p.buffer);GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,0,offset,size);s.fence=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);if(s.fence==0)throw stop("Packing depth fence creation failed");}
    private static long deleteStage(UploadSlot s){if(s==null)return 0;if(s.fence!=0)GL32.glDeleteSync(s.fence);Buffers.glDeleteBuffers(s.buffer);return s.capacity;}
    private static boolean byteExact(int format){if(format==GL11.GL_RGBA8||format==GL11.GL_RGB8||format==GL30.GL_R8||format==GL30.GL_RG8)return true;
        // Legacy PZ uploads may keep an unsized internal format. Qualify its
        // real component representation instead of assuming RGBA8 or widening it.
        if(format!=GL11.GL_RGBA&&format!=GL11.GL_RGB&&format!=GL11.GL_RED&&format!=GL30.GL_RG)return false;
        if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL13.GL_TEXTURE_COMPRESSED)!=0)return false;
        if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL30.GL_TEXTURE_RED_TYPE)!=GL30.GL_UNSIGNED_NORMALIZED)return false;
        for(int channel:new int[]{GL11.GL_TEXTURE_RED_SIZE,GL11.GL_TEXTURE_GREEN_SIZE,GL11.GL_TEXTURE_BLUE_SIZE,GL11.GL_TEXTURE_ALPHA_SIZE})if(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,channel)>8)return false;return true;}
    static final boolean BREAKDOWN=Counters.ENABLED&&Boolean.parseBoolean(Options.value("packingBreakdown","false"));
    private static long split(Counters.Key phase,long previous){
        if(!BREAKDOWN)return 0;
        long now=System.nanoTime();
        Counters.add(phase,now-previous);
        return now;
    }
    private static void upload(Plan plan,Samplers.State bindings,PackedSampling sampling,boolean cacheTable){long started=Counters.now();try{uploadStreamed(plan,bindings,sampling,cacheTable,started);Counters.add(Counters.Key.PACK_UPLOADS,1);Counters.add(Counters.Key.PACK_BYTES,plan.lastBytes);}finally{Counters.elapsed(Counters.Key.PACK_NS,started);}}
    private static void uploadStreamed(Plan plan,Samplers.State bindings,PackedSampling sampling,boolean cacheTable,long phaseStart){
        reap();
        if(!plan.context.equals(ContextResources.current()))throw new IllegalStateException("Packing program used outside its owning GL context");
        int count=plan.slots.size();if(plan.cursor==0)plan.cursor=plan.reservedTableBytes();
        int oldBuffer=GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER),oldCopyRead=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER),oldCopyWrite=GL11.glGetInteger(GL31.GL_COPY_WRITE_BUFFER),oldPack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] names={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SKIP_ROWS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST},saved=new int[names.length];
        for(int i=0;i<names.length;i++){saved[i]=GL11.glGetInteger(names[i]);GL11.glPixelStorei(names[i],i==0?1:0);}
        List<int[]> headers=new ArrayList<>();Map<Resident,Long> dirty=new LinkedHashMap<>();long actualBytes=count*256L+16;
        try(var guard=new BufferTextureGuard()){
            phaseStart=split(Counters.Key.PACK_SETUP_NS,phaseStart);
            // Describe the complete current working set before evicting anything.
            Map<Key,List<Level>> layouts=new LinkedHashMap<>();Map<Key,Long> versions=new HashMap<>();Map<Key,Long> sizes=new HashMap<>();long active=count*256L+16;
            for(Slot slot:plan.slots){if(!slot.active)continue;int texture=bindings.texture(slot.unit,0);if(texture==0)continue;Key key=new Key(texture,Buffers.textureLifetime(texture));if(layouts.containsKey(key))continue;Resident old=plan.images.get(key);long version=Textures.version(texture);versions.put(key,version);
                List<Level> levels;long size;if(old!=null&&old.version==version&&version!=0&&ContextResources.group().trusted&&ContextResources.group().contexts==1){levels=old.levels;size=old.bytes;}else{
                    GL13.glActiveTexture(GL13.GL_TEXTURE0+bindings.scratch);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);bindings.live[bindings.scratch][0]=texture;levels=new ArrayList<>();size=0;
                    for(int mip=0;mip<15;mip++){int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,mip,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,mip,GL11.GL_TEXTURE_HEIGHT);if(width==0||height==0)break;int internal=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,mip,GL11.GL_TEXTURE_INTERNAL_FORMAT);boolean depth=internal==33189||internal==33190||internal==33191||internal==36012||internal==GL30.GL_DEPTH24_STENCIL8||internal==GL30.GL_DEPTH32F_STENCIL8,compact=byteExact(internal);boolean red16=plan.lighting&&internal==GL30.GL_R16;
                        boolean redFloat=plan.lighting&&(internal==GL30.GL_R16F||internal==GL30.GL_R32F);
                        levels.add(new Level(texture,mip,width,height,depth,compact,red16,redFloat,size));
                        size=aligned(Math.addExact(size,Math.multiplyExact((long)width*height,red16?2:compact||redFloat?4:16)));if(width==1&&height==1)break;}
                    if(levels.isEmpty())throw new IllegalStateException("Packed image has no storage: "+texture);
                }layouts.put(key,List.copyOf(levels));sizes.put(key,size);active=Math.addExact(active,size);
            }
            if(active>CACHE_MAX)throw stop("Packing active working set "+active+" exceeds per-program limit "+CACHE_MAX+"; no quality reduction or allocation attempted");
            long extra=plan.tables==null?0:plan.tables.extraBytes();
            plan.cacheLimit=Math.max(CACHE_TARGET,active+extra);Set<Key> pinned=layouts.keySet();
            // Allocation is CPU bookkeeping only. If pinned regions fragment the
            // arena, rebuild this bounded layout before enqueueing any GPU writes.
            for(int attempt=0;;attempt++){try{for(var e:layouts.entrySet()){Key key=e.getKey();Resident image=plan.images.get(key);long size=sizes.get(key);if(image==null||image.bytes!=size||!image.levels.equals(e.getValue())){if(image!=null){plan.images.remove(key);free(plan,image);}long base=allocate(plan,size,pinned);plan.images.put(key,new Resident(key,base,size,e.getValue()));}}break;}catch(Fragmented fragmented){if(attempt!=0)throw stop("Packing working set cannot fit bounded arena");plan.images.clear();plan.free.clear();if(plan.tables!=null)plan.tables.invalidate();plan.cursor=plan.reservedTableBytes();rebuilds++;}}
            phaseStart=split(Counters.Key.PACK_LAYOUT_NS,phaseStart);
            int table=cacheTable?plan.tables.replace():-1;
            int offset=table>=0?plan.tables.offset(table):0;
            for(Slot slot:plan.slots){int texture=slot.active?bindings.texture(slot.unit,0):0;int[] meta=new int[64];headers.add(meta);if(texture==0){long word=count*64L+offset/4;meta[0]=(int)word&65535;meta[1]=(int)(word>>>16);meta[2]=meta[3]=1;meta[63]=1<<16;continue;}
                Key key=new Key(texture,Buffers.textureLifetime(texture));Resident image=plan.images.get(key);long version=versions.get(key);if(image.version!=version||version==0||!ContextResources.group().trusted||ContextResources.group().contexts!=1)dirty.put(image,version);else reusedImages++;
                GL13.glActiveTexture(GL13.GL_TEXTURE0+bindings.scratch);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);bindings.live[bindings.scratch][0]=texture;
                int sampler=bindings.sampler[slot.unit];int min=parameter(sampler,GL11.GL_TEXTURE_MIN_FILTER),mag=parameter(sampler,GL11.GL_TEXTURE_MAG_FILTER),ws=parameter(sampler,GL11.GL_TEXTURE_WRAP_S),wt=parameter(sampler,GL11.GL_TEXTURE_WRAP_T);
                if(sampling!=null)sampling.observe(texture,sampler,min,mag,ws,wt);
                int flags=(mag==GL11.GL_LINEAR?1:0)|((min==GL11.GL_LINEAR||min==GL11.GL_LINEAR_MIPMAP_NEAREST||min==GL11.GL_LINEAR_MIPMAP_LINEAR)?2:0)|((min==GL11.GL_NEAREST_MIPMAP_LINEAR||min==GL11.GL_LINEAR_MIPMAP_LINEAR)?4:0)|((min>=GL11.GL_NEAREST_MIPMAP_NEAREST&&min<=GL11.GL_LINEAR_MIPMAP_LINEAR)?8:0)|(wrap(ws)<<4)|(wrap(wt)<<6);
                for(Level level:image.levels){long words=(image.base+level.offset)/4;int at=level.mip*4;meta[at]=(int)words&65535;meta[at+1]=(int)(words>>>16);meta[at+2]=level.width|(level.bytes?1<<30:0)|(level.red16?1<<29:0)|(level.redFloat?1<<28:0);meta[at+3]=level.height;}
                meta[63]=flags|(image.levels.size()<<16);
            }
            phaseStart=split(Counters.Key.PACK_HEADER_NS,phaseStart);
            capacity(plan,plan.cursor);HangTrace.table(plan.capacity);
            phaseStart=split(Counters.Key.PACK_CAPACITY_NS,phaseStart);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER,plan.buffer);if(plan.metadata==null)plan.metadata=BufferUtils.createIntBuffer(count*64+4);IntBuffer metadata=plan.metadata;metadata.clear();for(int[] header:headers)metadata.put(header);metadata.put(0).put(0).put(0).put(Float.floatToRawIntBits(1));metadata.flip();staged(plan,metadata,offset);
            phaseStart=split(Counters.Key.PACK_STAGE_NS,phaseStart);
            for(var entry:dirty.entrySet()){Resident image=entry.getKey();for(Level source:image.levels){Level level=new Level(source.texture,source.mip,source.width,source.height,source.depth,source.bytes,source.red16,source.redFloat,image.base+source.offset);GL13.glActiveTexture(GL13.GL_TEXTURE0+bindings.scratch);GL11.glBindTexture(GL11.GL_TEXTURE_2D,level.texture);bindings.live[bindings.scratch][0]=level.texture;HangTrace.image(level.texture,level.width,level.height,level.depth);
                    if(level.depth)copyDepth(plan,level);else{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,plan.buffer);GL11.nglGetTexImage(GL11.GL_TEXTURE_2D,level.mip,level.red16||level.redFloat?GL11.GL_RED:GL11.GL_RGBA,level.red16?GL11.GL_UNSIGNED_SHORT:level.bytes?GL11.GL_UNSIGNED_BYTE:GL11.GL_FLOAT,level.offset);Buffers.pixelPackWrite();}
                }image.version=entry.getValue();actualBytes+=image.bytes;copiedBytes+=image.bytes;copiedImages++;}
            phaseStart=split(Counters.Key.PACK_IMAGE_NS,phaseStart);
            if(BREAKDOWN){Counters.add(Counters.Key.PACK_HEADER_BYTES,count*256L+16);Counters.add(Counters.Key.PACK_IMAGE_BYTES,actualBytes-(count*256L+16));}
            if(plan.texture==0)plan.texture=Textures.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+bindings.scratch);GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,plan.texture);if(plan.boundBuffer!=plan.buffer){Buffers.glTexBuffer(GL31.GL_TEXTURE_BUFFER,GL30.GL_RGBA32UI,plan.buffer);plan.boundBuffer=plan.buffer;}bindings.live[bindings.scratch][4]=plan.texture;
            // A row is reusable only after the header and any dirty images have
            // been queued in the same GL context as the consuming draws.
            if(table>=0&&plan.tables.capture(plan.slots,bindings,sampling))plan.tables.publish(table);
            plan.tableOffset=offset;
            plan.lastBytes=actualBytes;
            if(plan.reportedCapacity!=plan.capacity){plan.reportedCapacity=plan.capacity;Log.info("Packing resident storage: capacity="+plan.capacity+" total_reserved="+memoryStats()[0]+" budget="+BUDGET+" resident_images="+plan.images.size()+" upload_bytes="+actualBytes);}
        }finally{GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,oldPack);GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER,oldBuffer);GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,oldCopyRead);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,oldCopyWrite);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);for(int i=0;i<names.length;i++)GL11.glPixelStorei(names[i],saved[i]);}
        split(Counters.Key.PACK_RESTORE_NS,phaseStart);
    }
    private static void copyDepth(Plan plan,Level level){
        if(plan.depthScratch==null)plan.depthScratch=BufferUtils.createFloatBuffer(SCRATCH_BYTES/4);
        if(plan.readFramebuffer==0){plan.readFramebuffer=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,plan.readFramebuffer);GL11.glReadBuffer(GL11.GL_NONE);GL11.glDrawBuffer(GL11.GL_NONE);}
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,plan.readFramebuffer);GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,level.texture,level.mip);
        if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Packed depth framebuffer incomplete: texture="+level.texture+" mip="+level.mip);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);int rows=Math.max(1,SCRATCH_BYTES/16/level.width);
        for(int y=0;y<level.height;y+=rows){int height=Math.min(rows,level.height-y),n=Math.multiplyExact(level.width,height);FloatBuffer data=plan.depthScratch;data.clear().limit(n);GL11.glReadPixels(0,y,level.width,height,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,data);data.limit(n*4);
            for(int i=n-1;i>=0;i--){float depth=data.get(i);data.put(i*4,depth).put(i*4+1,0).put(i*4+2,0).put(i*4+3,1);}
            stagedDepth(plan,level.offset+(long)y*level.width*16,data);
        }
    }
    private static int parameter(int sampler,int name){return sampler==0?GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,name):GL33.glGetSamplerParameteri(sampler,name);}
    private static int wrap(int mode){return mode==GL11.GL_REPEAT?1:mode==GL14.GL_MIRRORED_REPEAT?2:0;}
    private static String helpers(boolean vertex,boolean tables){
        return (tables?"uniform int vmcompatTableBase;\n":"#define vmcompatTableBase 0\n")+"""
            uniform usamplerBuffer vmcompatImagePixels;
            struct VmImage { int index; };
            ivec4 vmInfo(VmImage image,int level){int base=vmcompatTableBase+image.index*16;ivec4 m=ivec4(texelFetch(vmcompatImagePixels,base+level));return ivec4(m.x+(m.y<<16),m.z&0xfffffff,m.w,int(texelFetch(vmcompatImagePixels,base+15).w)|(m.z&0x70000000));}
            int vmWrap(int p,int size,int mode){if(mode==1)return (p%size+size)%size;if(mode==2){int a=(p%(size*2)+size*2)%(size*2);return a<size?a:size*2-1-a;}return clamp(p,0,size-1);}
            ivec2 textureSize(VmImage image,int level){int last=max(0,((vmInfo(image,0).w>>16)&255)-1);return vmInfo(image,clamp(level,0,last)).yz;}
            vec4 texelFetch(VmImage image,ivec2 p,int level){ivec4 info=vmInfo(image,clamp(level,0,max(0,((vmInfo(image,0).w>>16)&255)-1)));int flags=vmInfo(image,0).w;int x=vmWrap(p.x,info.y,(flags>>4)&3),y=vmWrap(p.y,info.z,(flags>>6)&3);int pixel=y*info.y+x;if((info.w&0x10000000)!=0){int word=info.x+pixel;return vec4(uintBitsToFloat(texelFetch(vmcompatImagePixels,word>>2)[word&3]),0.0,0.0,1.0);}if((info.w&0x20000000)!=0){int word=info.x+(pixel>>1);uint red=texelFetch(vmcompatImagePixels,word>>2)[word&3];return vec4(float((red>>uint((pixel&1)*16))&65535u)/65535.0,0.0,0.0,1.0);}if((info.w&0x40000000)!=0){int word=info.x+pixel;uint rgba=texelFetch(vmcompatImagePixels,word>>2)[word&3];return vec4(rgba&255u,(rgba>>8)&255u,(rgba>>16)&255u,rgba>>24)/255.0;}return uintBitsToFloat(texelFetch(vmcompatImagePixels,(info.x>>2)+pixel));}
            vec4 vmLevel(VmImage image,vec2 uv,int level,bool linear){vec2 p=uv*vec2(textureSize(image,level))-0.5;if(!linear)return texelFetch(image,ivec2(floor(p+0.5)),level);ivec2 a=ivec2(floor(p));vec2 f=fract(p);return mix(mix(texelFetch(image,a,level),texelFetch(image,a+ivec2(1,0),level),f.x),mix(texelFetch(image,a+ivec2(0,1),level),texelFetch(image,a+ivec2(1,1),level),f.x),f.y);}
            vec4 textureLod(VmImage image,vec2 uv,float lod){int flags=vmInfo(image,0).w;float l=(flags&8)==0?0.0:clamp(lod,0.0,float(max(0,((flags>>16)&255)-1)));bool linear=lod<=0.0?(flags&1)!=0:(flags&2)!=0;if((flags&4)!=0)return mix(vmLevel(image,uv,int(floor(l)),linear),vmLevel(image,uv,int(ceil(l)),linear),fract(l));return vmLevel(image,uv,int(floor(l+0.5)),linear);}
            vec4 textureGrad(VmImage image,vec2 uv,vec2 dx,vec2 dy){vec2 size=vec2(textureSize(image,0));return textureLod(image,uv,log2(max(max(length(dx*size),length(dy*size)),0.00001)));}
            vec4 textureProj(VmImage image,vec3 uv){return textureLod(image,uv.xy/uv.z,0.0);}
            vec4 textureProj(VmImage image,vec4 uv){return textureLod(image,uv.xy/uv.w,0.0);}
            vec4 textureOffset(VmImage image,vec2 uv,ivec2 offset){return textureLod(image,uv+vec2(offset)/vec2(textureSize(image,0)),0.0);}
            """+(vertex?"vec4 texture(VmImage image,vec2 uv){return textureLod(image,uv,0.0);}\n":"vec4 texture(VmImage image,vec2 uv){return textureGrad(image,uv,dFdx(uv),dFdy(uv));}\nvec4 texture(VmImage image,vec2 uv,float bias){vec2 size=vec2(textureSize(image,0));return textureLod(image,uv,log2(max(max(length(dFdx(uv)*size),length(dFdy(uv)*size)),0.00001))+bias);}\n");
    }
    private Packing(){}
}
