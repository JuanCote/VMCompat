package vmcompat;
import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import static vmcompat.Counters.Key.*;

/** Ordered OpenGL compatibility with bounded CPU ownership and versioned range copies. */
public final class Buffers {
    private static final int MIRROR_LIMIT=16*1024*1024,OBJECT_LIMIT=1024*1024,JOURNAL_LIMIT=128;
    private record Change(long version,long offset,long length){}
    static final class ObjectState {
        final int name;final long generation=ContextResources.SERIAL.incrementAndGet();
        long storage,version,size=-1,lostVersion;boolean auxiliary,unknown=true,deleted,readbackCacheAllowed;
        final ArrayDeque<Change> changes=new ArrayDeque<>();
        byte[] cpu;BitSet valid;Mapping mapping;String writer="unobserved";final ArrayList<Segment> segments=new ArrayList<>();final ArrayDeque<long[]> evicted=new ArrayDeque<>();
        ObjectState(int name){this.name=name;}
    }
    private static final class Mapping {long offset,length;int flags;ByteBuffer bytes;Mapping(long o,long n,int f,ByteBuffer b){offset=o;length=n;flags=f;bytes=b;}}
    private static final class Segment {final ObjectState owner;final long offset;final byte[] bytes;final BitSet valid;Segment(ObjectState o,long at,int length){owner=o;offset=at;bytes=new byte[length];valid=new BitSet(length);}}
    private record TextureBinding(long textureGeneration,int buffer,long bufferGeneration,int format){}
    private static final class Range {ObjectState source;int texture,copy,format;long textureGeneration;long copyGeneration=ContextResources.SERIAL.incrementAndGet();long offset,size,allocated=-1,storage=-1,version=-1;}
    static final class Shared {
        final Map<Integer,ObjectState> objects=new HashMap<>();
        final Map<Integer,Range> ranges=new HashMap<>();
        final Map<Integer,Long> textureGenerations=new HashMap<>();final Map<Integer,TextureBinding> textureBindings=new HashMap<>();
        final LinkedHashMap<Object,Boolean> mirrors=new LinkedHashMap<>(16,.75f,true);long mirrorBytes;
        void clear(){objects.clear();ranges.clear();mirrors.clear();textureGenerations.clear();textureBindings.clear();mirrorBytes=0;}
    }
    private static final class Local {ByteBuffer scratch,rangeStaging,metalStaging,metalPatches;boolean feedback;final Map<Integer,Integer> feedbackBuffers=new HashMap<>();}
    private static final StateStore<Local> LOCAL=new StateStore<>(Local::new);
    private static Shared shared(){return ContextResources.group().buffers;}
    private static ObjectState object(Shared s,int name){if(name==0)throw new IllegalArgumentException("Buffer zero has no storage");return s.objects.computeIfAbsent(name,ObjectState::new);}
    private static int binding(int target){return switch(target){case 34962->34964;case 34963->34965;case 36662,36663->target;case 35882->35882;case 36671->36675;case 35051->35053;case 35052->35055;case 35345->35368;case 35982->35983;default->throw new UnsupportedOperationException("Buffer binding "+target);};}
    private static int bound(int target){return GL11.glGetInteger(binding(target));}
    public static int glGenBuffers(){int id=GL15.glGenBuffers();Shared s=shared();synchronized(s){s.objects.put(id,new ObjectState(id));}return id;}
    public static void glGenBuffers(IntBuffer ids){GL15.glGenBuffers(ids);Shared s=shared();synchronized(s){for(int i=ids.position();i<ids.limit();i++){int id=ids.get(i);s.objects.put(id,new ObjectState(id));}}}
    public static void glGenBuffers(int[] ids){GL15.glGenBuffers(ids);Shared s=shared();synchronized(s){for(int id:ids)s.objects.put(id,new ObjectState(id));}}
    private static void evicted(ObjectState o,long offset,long size){o.evicted.addLast(new long[]{offset,size});if(o.evicted.size()>8)o.evicted.removeFirst();}
    private static void discardMirror(Shared s,ObjectState o){if(o.cpu!=null){s.mirrorBytes-=o.cpu.length;s.mirrors.remove(o);o.cpu=null;o.valid=null;}}
    private static void discardSegment(Shared s,Segment chunk){s.mirrorBytes-=chunk.bytes.length;s.mirrors.remove(chunk);chunk.owner.segments.remove(chunk);}
    private static void discardAll(Shared s,ObjectState o){discardMirror(s,o);for(Segment c:new ArrayList<>(o.segments))discardSegment(s,c);o.evicted.clear();}
    private static void space(Shared s,long bytes){while(!s.mirrors.isEmpty()&&(s.mirrorBytes+bytes>MIRROR_LIMIT||s.mirrors.size()>=2048)){Object old=s.mirrors.keySet().iterator().next();if(old instanceof Segment chunk){evicted(chunk.owner,chunk.offset,chunk.bytes.length);discardSegment(s,chunk);}else{ObjectState o=(ObjectState)old;evicted(o,0,o.cpu.length);discardMirror(s,o);}Counters.add(MIRROR_EVICTIONS,1);}}
    private static void gauge(Shared s){Counters.set(COMMAND_CACHE_BYTES,s.mirrorBytes);Counters.max(COMMAND_CACHE_PEAK_BYTES,s.mirrorBytes);}
    private static boolean mirror(Shared s,ObjectState o){
        if(o.auxiliary||!Options.MIRROR||o.size<0||o.size>OBJECT_LIMIT)return false;
        if(o.cpu!=null){s.mirrors.get(o);return true;}
        space(s,o.size);o.cpu=new byte[(int)o.size];o.valid=new BitSet((int)o.size);s.mirrorBytes+=o.size;s.mirrors.put(o,true);gauge(s);return true;
    }
    private static void retain(Shared s,ObjectState o,long offset,ByteBuffer bytes){
        if(!Options.MIRROR||!Options.COMMAND_RANGES||bytes.remaining()==0)return;
        int length=bytes.remaining();boolean covered=false;
        for(Segment chunk:o.segments){long lo=Math.max(offset,chunk.offset),hi=Math.min(offset+length,chunk.offset+chunk.bytes.length);if(lo<hi){ByteBuffer part=bytes.duplicate();part.position(part.position()+(int)(lo-offset));part.get(chunk.bytes,(int)(lo-chunk.offset),(int)(hi-lo));chunk.valid.set((int)(lo-chunk.offset),(int)(hi-chunk.offset));}if(chunk.offset<=offset&&chunk.offset+chunk.bytes.length>=offset+length){covered=true;s.mirrors.get(chunk);}}
        if(covered||o.cpu!=null||length>OBJECT_LIMIT)return;
        if(o.segments.size()>=128){Segment oldest=o.segments.getFirst();evicted(o,oldest.offset,oldest.bytes.length);discardSegment(s,oldest);Counters.add(MIRROR_EVICTIONS,1);}
        space(s,length);Segment chunk=new Segment(o,offset,length);bytes.duplicate().get(chunk.bytes);chunk.valid.set(0,length);o.segments.add(chunk);s.mirrorBytes+=length;s.mirrors.put(chunk,true);gauge(s);
    }
    private static void change(ObjectState o,long offset,long length){o.version++;o.changes.addLast(new Change(o.version,offset,length));if(o.changes.size()>JOURNAL_LIMIT)o.lostVersion=o.changes.removeFirst().version;}
    private static void invalidate(ObjectState o,long offset,long length){
        if(o.cpu!=null){if(offset<0||length<0)o.valid.clear();else{int from=(int)Math.min(o.cpu.length,offset),to=(int)Math.min(o.cpu.length,offset+length);if(from<to)o.valid.clear(from,to);}}
        for(Segment chunk:o.segments){if(offset<0||length<0)chunk.valid.clear();else{long lo=Math.max(offset,chunk.offset),hi=Math.min(offset+length,chunk.offset+chunk.bytes.length);if(lo<hi)chunk.valid.clear((int)(lo-chunk.offset),(int)(hi-chunk.offset));}}
        change(o,offset,length);
    }
    private static void patch(Shared s,ObjectState o,long offset,ByteBuffer bytes){
        int n=bytes.remaining();if(offset<0||o.size<0||offset>o.size||n>o.size-offset){o.unknown=true;invalidate(o,-1,-1);return;}
        change(o,offset,n);if(mirror(s,o)){bytes.duplicate().get(o.cpu,(int)offset,n);o.valid.set((int)offset,(int)offset+n);}retain(s,o,offset,bytes);
        if(offset==0&&n==o.size){o.unknown=false;o.readbackCacheAllowed=true;}
    }
    private static void storage(int id,long size,ByteBuffer bytes){Shared s=shared();synchronized(s){ObjectState o=object(s,id);discardAll(s,o);o.writer="data";o.storage++;o.size=size;o.unknown=false;o.readbackCacheAllowed=true;o.mapping=null;o.changes.clear();o.lostVersion=0;change(o,-1,-1);if(bytes!=null)patch(s,o,0,bytes);}}
    static ByteBuffer raw(Buffer data){
        if(data instanceof ByteBuffer b)return b.duplicate().slice().order(ByteOrder.nativeOrder());
        int word=data instanceof ShortBuffer?2:data instanceof IntBuffer||data instanceof FloatBuffer?4:8;
        if(data.isDirect()){
            long address=data instanceof IntBuffer b?MemoryUtil.memAddress(b):data instanceof FloatBuffer b?MemoryUtil.memAddress(b):data instanceof ShortBuffer b?MemoryUtil.memAddress(b):data instanceof LongBuffer b?MemoryUtil.memAddress(b):MemoryUtil.memAddress((DoubleBuffer)data);
            return MemoryUtil.memByteBuffer(address,Math.multiplyExact(data.remaining(),word)).order(ByteOrder.nativeOrder());
        }
        ByteBuffer out=ByteBuffer.allocate(Math.multiplyExact(data.remaining(),word)).order(ByteOrder.nativeOrder());
        for(int i=data.position();i<data.limit();i++){if(data instanceof IntBuffer b)out.putInt(b.get(i));else if(data instanceof FloatBuffer b)out.putFloat(b.get(i));else if(data instanceof ShortBuffer b)out.putShort(b.get(i));else if(data instanceof LongBuffer b)out.putLong(b.get(i));else out.putDouble(((DoubleBuffer)data).get(i));}return out.flip();
    }
    public static void glBufferData(int target,long size,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,size,usage);}storage(id,size,null);}
    public static void glBufferData(int target,ByteBuffer bytes,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,bytes,usage);}storage(id,bytes.remaining(),raw(bytes));}
    public static void glBufferData(int target,IntBuffer bytes,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,bytes,usage);}storage(id,(long)bytes.remaining()*4,raw(bytes));}
    public static void glBufferData(int target,FloatBuffer bytes,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,bytes,usage);}storage(id,(long)bytes.remaining()*4,raw(bytes));}
    public static void glBufferData(int target,ShortBuffer bytes,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,bytes,usage);}storage(id,(long)bytes.remaining()*2,raw(bytes));}
    public static void glBufferData(int target,DoubleBuffer bytes,int usage){int id=bound(target);try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,bytes,usage);}storage(id,(long)bytes.remaining()*8,raw(bytes));}
    public static void uploadFloatArray(int target,float[] data,int usage){
        int id=bound(target);
        try(var guard=new BufferTextureGuard()){GL15.glBufferData(target,data,usage);}
        // Track the storage revision without making a redundant CPU mirror of OldPoint's cutaway table.
        storage(id,(long)data.length*Float.BYTES,null);
    }
    private static void uploaded(int target,long at,Buffer bytes){Shared s=shared();synchronized(s){ObjectState o=object(s,bound(target));o.writer="subdata";patch(s,o,at,raw(bytes));}}
    public static void glBufferSubData(int target,long at,ByteBuffer bytes){try(var guard=new BufferTextureGuard()){GL15.glBufferSubData(target,at,bytes);}uploaded(target,at,bytes);}
    public static void glBufferSubData(int target,long at,IntBuffer bytes){try(var guard=new BufferTextureGuard()){GL15.glBufferSubData(target,at,bytes);}uploaded(target,at,bytes);}
    public static void glBufferSubData(int target,long at,FloatBuffer bytes){try(var guard=new BufferTextureGuard()){GL15.glBufferSubData(target,at,bytes);}uploaded(target,at,bytes);}
    public static void glBufferSubData(int target,long at,ShortBuffer bytes){try(var guard=new BufferTextureGuard()){GL15.glBufferSubData(target,at,bytes);}uploaded(target,at,bytes);}
    public static void glBufferSubData(int target,long at,DoubleBuffer bytes){try(var guard=new BufferTextureGuard()){GL15.glBufferSubData(target,at,bytes);}uploaded(target,at,bytes);}
    public static void glCopyBufferSubData(int r,int w,long from,long to,long size){
        int source=bound(r),dest=bound(w);Shared s=shared();synchronized(s){
            ObjectState a=object(s,source),b=object(s,dest);ByteBuffer owned=owned(s,a,from,size);byte[] saved=owned==null?null:new byte[(int)size];if(saved!=null)owned.get(saved);
            try(var guard=new BufferTextureGuard()){GL31.glCopyBufferSubData(r,w,from,to,size);}
            b.writer=saved==null?"copy_GPU":"copy_CPU";if(saved==null)b.readbackCacheAllowed=false;invalidate(b,to,size);if(saved!=null)patch(s,b,to,ByteBuffer.wrap(saved));
        }
    }
    private static ByteBuffer owned(Shared s,ObjectState o,long offset,long size){
        if(o.unknown||o.mapping!=null||offset<0||size<0||size>OBJECT_LIMIT)return null;
        if(o.cpu!=null&&offset<=o.cpu.length&&size<=o.cpu.length-offset&&o.valid.nextClearBit((int)offset)>=offset+size){s.mirrors.get(o);return ByteBuffer.wrap(o.cpu,(int)offset,(int)size).slice().order(ByteOrder.nativeOrder());}
        if(!Options.COMMAND_RANGES)return null;
        for(Segment chunk:o.segments)if(offset>=chunk.offset&&offset+size<=chunk.offset+chunk.bytes.length&&chunk.valid.nextClearBit((int)(offset-chunk.offset))>=offset-chunk.offset+size){s.mirrors.get(chunk);return ByteBuffer.wrap(chunk.bytes,(int)(offset-chunk.offset),(int)size).slice().order(ByteOrder.nativeOrder());}
        byte[] combined=null;BitSet valid=null;
        for(Segment chunk:o.segments){long lo=Math.max(offset,chunk.offset),hi=Math.min(offset+size,chunk.offset+chunk.bytes.length);if(lo>=hi)continue;s.mirrors.get(chunk);if(combined==null){combined=new byte[(int)size];valid=new BitSet((int)size);}for(int at=(int)(lo-chunk.offset);at<hi-chunk.offset;at++)if(chunk.valid.get(at)){int dest=(int)(chunk.offset+at-offset);combined[dest]=chunk.bytes[at];valid.set(dest);}}
        return combined!=null&&valid.nextClearBit(0)>=size?ByteBuffer.wrap(combined).order(ByteOrder.nativeOrder()):null;
    }
    private static Counters.Key miss(ObjectState o,long offset,int size,boolean trusted){
        if(!Options.MIRROR)return COMMAND_MISS_DISABLED;if(!trusted)return COMMAND_MISS_CONTEXT;if(o.mapping!=null)return COMMAND_MISS_MAPPING;if(o.unknown||!o.readbackCacheAllowed)return COMMAND_MISS_UNKNOWN;
        for(long[] span:o.evicted)if(offset<span[0]+span[1]&&span[0]<offset+size)return COMMAND_MISS_EVICTED;
        if(size>OBJECT_LIMIT||!Options.COMMAND_RANGES&&o.size>OBJECT_LIMIT)return COMMAND_MISS_SIZE;
        return o.cpu==null&&o.segments.isEmpty()?COMMAND_MISS_NO_COPY:COMMAND_MISS_INVALID;
    }
    public static ByteBuffer commands(int target,long offset,int size){
        int id=bound(target);if(id==0)return MemoryUtil.memByteBuffer(offset,size).order(ByteOrder.nativeOrder());
        Shared s=shared();ObjectState observed;long observedVersion,observedStorage;synchronized(s){ObjectState o=object(s,id);observed=o;observedVersion=o.version;observedStorage=o.storage;if(Options.MIRROR&&ContextResources.group().trusted){ByteBuffer known=owned(s,o,offset,size);if(known!=null){s.mirrors.get(o);Counters.add(MIRROR_HITS,1);Counters.add(MIRROR_BYTES,size);ByteBuffer copy=scratch(size);copy.put(known).flip();gauge(s);return copy;}}Counters.Key why=miss(o,offset,size,ContextResources.group().trusted);Counters.add(why,1);CommandMisses.record(o.name,o.generation,o.size,offset,size,o.writer,why.name());gauge(s);}
        ByteBuffer bytes=scratch(size);
        long started=Counters.now();try{GL15.glGetBufferSubData(target,offset,bytes);}finally{Telemetry.read(Counters.ENABLED?System.nanoTime()-started:0,size);}
        if(Options.MIRROR&&Options.COMMAND_RANGES&&size<=OBJECT_LIMIT&&ContextResources.group().trusted){synchronized(s){ObjectState o=object(s,id);if(o==observed&&o.version==observedVersion&&o.storage==observedStorage&&!o.unknown&&o.readbackCacheAllowed&&o.mapping==null&&o.size>=offset+size){retain(s,o,offset,bytes);Counters.add(COMMAND_READBACK_RETAINS,1);}}}return bytes;
    }
    private static ByteBuffer scratch(int size){
        Local local=LOCAL.get();ByteBuffer bytes;
        if(Options.SCRATCH){if(local.scratch==null||local.scratch.capacity()<size){local.scratch=BufferUtils.createByteBuffer(Math.max(256,size));Counters.add(SCRATCH_ALLOCATIONS,1);Counters.add(SCRATCH_BYTES,local.scratch.capacity());}bytes=local.scratch.duplicate().clear().limit(size).slice().order(ByteOrder.nativeOrder());}
        else{bytes=BufferUtils.createByteBuffer(size).order(ByteOrder.nativeOrder());Counters.add(SCRATCH_ALLOCATIONS,1);Counters.add(SCRATCH_BYTES,size);}
        return bytes;
    }
    static void textureCreated(int texture){Shared s=shared();synchronized(s){s.textureGenerations.put(texture,ContextResources.SERIAL.incrementAndGet());s.textureBindings.remove(texture);Range old=s.ranges.remove(texture);if(old!=null)GL15.glDeleteBuffers(old.copy);}}
    /** External/raw association changes require this conservative boundary. */
    public static void textureBindingUnknown(int texture){Shared s=shared();synchronized(s){s.textureBindings.remove(texture);}}
    private static void associate(Shared s,boolean trusted,int target,int texture,long textureGen,int buffer,long generation,int format){
        Counters.add(TEX_BUFFER_REQUESTS,1);TextureBinding cached=s.textureBindings.get(texture);
        if(Options.TEX_BUFFER_BINDINGS&&trusted&&cached!=null&&cached.textureGeneration==textureGen&&cached.buffer==buffer&&cached.bufferGeneration==generation&&cached.format==format){Counters.add(TEX_BUFFER_BIND_SKIPS,1);return;}
        long started=Counters.now();try{GL31.glTexBuffer(target,format,buffer);}finally{Counters.elapsed(TEX_BUFFER_NATIVE_NS,started);Counters.add(TEX_BUFFER_BINDS,1);}
        if(!Options.TEX_BUFFER_BINDINGS){s.textureBindings.remove(texture);return;}
        boolean verified=texture!=0&&(buffer==0||GL15.glIsBuffer(buffer))&&GL11.glGetTexLevelParameteri(target,0,GL31.GL_TEXTURE_BUFFER_DATA_STORE_BINDING)==buffer&&(buffer==0||GL11.glGetTexLevelParameteri(target,0,GL11.GL_TEXTURE_INTERNAL_FORMAT)==format);if(verified)s.textureBindings.put(texture,new TextureBinding(textureGen,buffer,generation,format));else s.textureBindings.remove(texture);
    }
    public static void glTexBuffer(int target,int format,int buffer){int texture=GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);ContextResources.Group group=ContextResources.group();Shared s=group.buffers;synchronized(s){Range range=s.ranges.remove(texture);s.textureBindings.remove(texture);long generation=buffer==0?0:object(s,buffer).generation;associate(s,group.trusted,target,texture,s.textureGenerations.computeIfAbsent(texture,k->ContextResources.SERIAL.incrementAndGet()),buffer,generation,format);if(range!=null)GL15.glDeleteBuffers(range.copy);}}
    public static void glTexBufferRange(int target,int format,int source,long offset,long size){
        Counters.add(TEX_BUFFER_RANGE_CALLS,1);long started=Counters.now();try{
            if(offset<0||size<=0)throw new IllegalArgumentException("Texture buffer range");
            long entry=Counters.now();ContextResources.Group group=ContextResources.group();Shared s=group.buffers;Counters.elapsed(RANGE_ENTRY_NS,entry);synchronized(s){long stateAt=Counters.now();int texture=GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);if(texture==0)throw new IllegalStateException("No bound texture buffer");
                Range range=s.ranges.computeIfAbsent(texture,k->{Range r=new Range();r.texture=k;r.textureGeneration=s.textureGenerations.computeIfAbsent(k,t->ContextResources.SERIAL.incrementAndGet());r.copy=GL15.glGenBuffers();return r;});ObjectState o=object(s,source);
                boolean changed=range.source!=o||range.offset!=offset||range.size!=size;range.source=o;range.offset=offset;range.size=size;range.format=format;
                if(changed){range.version=-1;range.storage=-1;}
                if(!Options.RANGE_STORAGE||range.allocated!=size){long at=Counters.now();int saved=bound(GL31.GL_COPY_WRITE_BUFFER);try(var guard=new BufferTextureGuard()){GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,range.copy);GL15.glBufferData(GL31.GL_COPY_WRITE_BUFFER,size,GL15.GL_DYNAMIC_DRAW);}finally{GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,saved);Counters.elapsed(RANGE_ALLOC_NS,at);}range.allocated=size;range.version=-1;Counters.add(RANGE_ALLOCATIONS,1);Counters.add(RANGE_ALLOC_BYTES,size);}
                Counters.elapsed(RANGE_STATE_NS,stateAt);synchronize(range,group.trusted);associate(s,group.trusted,target,texture,range.textureGeneration,range.copy,range.copyGeneration,format);
            }
        }finally{Counters.elapsed(RANGE_BIND_NS,started);}
    }
    private static boolean dirty(Range r){ObjectState o=r.source;if(r.storage!=o.storage||r.version<0||r.version<o.lostVersion)return true;if(r.version==o.version)return false;for(Change c:o.changes)if(c.version>r.version&&(c.offset<0||c.offset<r.offset+r.size&&r.offset<c.offset+c.length))return true;return false;}
    private static boolean synchronize(Range range,boolean trusted){
        Counters.add(RANGE_VISITS,1);ObjectState o=range.source;if(o==null||o.deleted)return false;
        boolean unknown=o.unknown||o.mapping!=null||!trusted;
        if(Options.RANGES&&!unknown&&!dirty(range)){range.version=o.version;Counters.add(RANGE_SKIPS,1);return false;}
        Counters.add(unknown?RANGE_UNKNOWN:RANGE_DIRTY,1);
        if(o.size>=0&&(range.offset>o.size||range.size>o.size-range.offset))throw new IllegalStateException("Range exceeds current source storage");
        long updateAt=Counters.now();Shared shared=ContextResources.group().buffers;
        ByteBuffer known=trusted&&!unknown?owned(shared,o,range.offset,range.size):null;
        Counters.Key origin=!trusted?RANGE_UNTRUSTED:o.mapping!=null?RANGE_MAPPING:o.unknown?RANGE_GPU_UNKNOWN:known!=null?RANGE_CPU_AVAILABLE:RANGE_CPU_ABSENT;
        Counters.add(origin,1);
        // The shared-resource monitor is held throughout selection, staging and submission.
        // No GL readback is introduced; unavailable bytes keep the original GPU-copy path.
        boolean cpu=Options.RANGE_CPU&&known!=null;long enterBefore=Counters.value(GUARD_ENTER_NS),restoreBefore=Counters.value(GUARD_RESTORE_NS);
        int read=cpu?0:bound(GL31.GL_COPY_READ_BUFFER),write=bound(GL31.GL_COPY_WRITE_BUFFER);
        try(var guard=new BufferTextureGuard()){
            GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,range.copy);
            if(cpu){
                long stageAt=Counters.now();Local local=LOCAL.get();int bytes=(int)range.size;
                if(local.rangeStaging==null||local.rangeStaging.capacity()<bytes){local.rangeStaging=BufferUtils.createByteBuffer(Math.max(256,bytes));Counters.add(RANGE_STAGING_ALLOCATIONS,1);Counters.add(RANGE_STAGING_BYTES,local.rangeStaging.capacity());}
                ByteBuffer stage=local.rangeStaging.clear().limit(bytes);stage.put(known).flip();Counters.elapsed(RANGE_CPU_STAGING_NS,stageAt);
                long nativeAt=Counters.now();try{GL15.glBufferSubData(GL31.GL_COPY_WRITE_BUFFER,0,stage);}finally{Counters.elapsed(RANGE_CPU_UPLOAD_NS,nativeAt);}Counters.add(RANGE_CPU_UPDATES,1);
            }else{
                GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,o.name);long nativeAt=Counters.now();
                try{GL31.glCopyBufferSubData(GL31.GL_COPY_READ_BUFFER,GL31.GL_COPY_WRITE_BUFFER,range.offset,0,range.size);}finally{Counters.elapsed(RANGE_NATIVE_COPY_NS,nativeAt);}Counters.add(RANGE_GL_UPDATES,1);
            }
        }finally{Counters.add(RANGE_GUARD_ENTER_NS,Counters.value(GUARD_ENTER_NS)-enterBefore);Counters.add(RANGE_GUARD_RESTORE_NS,Counters.value(GUARD_RESTORE_NS)-restoreBefore);if(!cpu)GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,read);GL15.glBindBuffer(GL31.GL_COPY_WRITE_BUFFER,write);}
        RangeUpdates.record(o,range.offset,range.size,origin,Counters.ENABLED?System.nanoTime()-updateAt:0);
        range.storage=o.storage;range.version=o.version;Counters.add(RANGE_COPIES_ALL,1);Counters.add(RANGE_BYTES_ALL,range.size);return true;
    }
    static void synchronizeRanges(){long started=Counters.now(),bytes=0,copies=0;ContextResources.Group group=ContextResources.group();Shared s=group.buffers;synchronized(s){try{for(Range r:s.ranges.values())if(synchronize(r,group.trusted)){bytes+=r.size;copies++;}}finally{long ended=Counters.ENABLED?System.nanoTime():0;Telemetry.ranges(Counters.ENABLED?ended-started:0,bytes,copies);Breakdown.resource(started,ended);}}}
    public static void glDeleteBuffers(int id){FarShell.bufferDeleted(id);Opaque.deletedBuffer(id);Draws.bufferDeleted(id);Shared s=shared();synchronized(s){ObjectState o=s.objects.get(id);if(o!=null){for(Range r:s.ranges.values())if(r.source==o)synchronize(r,ContextResources.group().trusted);o.deleted=true;discardAll(s,o);s.objects.remove(id);s.textureBindings.entrySet().removeIf(e->e.getValue().buffer==id&&e.getValue().bufferGeneration==o.generation);}GL15.glDeleteBuffers(id);}}
    public static void glDeleteBuffers(int[] ids){for(int id:ids)glDeleteBuffers(id);}
    public static void glDeleteBuffers(IntBuffer ids){for(int i=ids.position();i<ids.limit();i++)glDeleteBuffers(ids.get(i));}
    static void deletedTexture(int texture){Shared s=shared();synchronized(s){Range r=s.ranges.remove(texture);s.textureBindings.remove(texture);s.textureGenerations.remove(texture);if(r!=null)GL15.glDeleteBuffers(r.copy);}}
    private static ByteBuffer mapped(int target,long offset,long size,int flags,ByteBuffer result){if(result==null)return null;Shared s=shared();synchronized(s){ObjectState o=object(s,bound(target));if(o.size<0)o.size=GL32.glGetBufferParameteri64(target,GL15.GL_BUFFER_SIZE);o.writer="map";o.mapping=new Mapping(offset,size,flags,result);if((flags&2)!=0)invalidate(o,offset,size);}return result;}
    public static ByteBuffer glMapBuffer(int target,int access){long size=GL32.glGetBufferParameteri64(target,GL15.GL_BUFFER_SIZE);return mapped(target,0,size,access==GL15.GL_READ_ONLY?1:access==GL15.GL_WRITE_ONLY?2:3,GL15.glMapBuffer(target,access));}
    public static ByteBuffer glMapBuffer(int target,int access,ByteBuffer old){long size=GL32.glGetBufferParameteri64(target,GL15.GL_BUFFER_SIZE);return mapped(target,0,size,access==GL15.GL_READ_ONLY?1:access==GL15.GL_WRITE_ONLY?2:3,GL15.glMapBuffer(target,access,old));}
    public static ByteBuffer glMapBuffer(int target,int access,long length,ByteBuffer old){return mapped(target,0,length,access==GL15.GL_READ_ONLY?1:access==GL15.GL_WRITE_ONLY?2:3,GL15.glMapBuffer(target,access,length,old));}
    public static ByteBuffer glMapBufferRange(int target,long offset,long length,int flags){return mapped(target,offset,length,flags,GL30.glMapBufferRange(target,offset,length,flags));}
    public static ByteBuffer glMapBufferRange(int target,long offset,long length,int flags,ByteBuffer old){return mapped(target,offset,length,flags,GL30.glMapBufferRange(target,offset,length,flags,old));}
    public static void glFlushMappedBufferRange(int target,long offset,long length){GL30.glFlushMappedBufferRange(target,offset,length);Shared s=shared();synchronized(s){ObjectState o=object(s,bound(target));o.writer="flush/unmap";Mapping m=o.mapping;if(m!=null){invalidate(o,m.offset+offset,length);if(offset>=0&&length>=0&&offset+length<=m.length&&m.length<=OBJECT_LIMIT)patch(s,o,m.offset+offset,m.bytes.duplicate().position((int)offset).limit((int)(offset+length)).slice());}else{o.unknown=true;invalidate(o,-1,-1);}}}
    public static boolean glUnmapBuffer(int target){Shared s=shared();synchronized(s){ObjectState o=object(s,bound(target));o.writer="flush/unmap";Mapping m=o.mapping;byte[] bytes=null;if(m!=null&&(m.flags&2)!=0&&(m.flags&16)==0&&m.length<=OBJECT_LIMIT){bytes=new byte[(int)m.length];m.bytes.duplicate().clear().limit(bytes.length).get(bytes);}boolean ok=GL15.glUnmapBuffer(target);o.mapping=null;if(!ok){o.unknown=true;invalidate(o,-1,-1);}else if(m!=null&&(m.flags&2)!=0){if(bytes!=null)patch(s,o,m.offset,ByteBuffer.wrap(bytes));else change(o,m.offset,m.length);}return ok;}}
    /** Unknown external producer remains conservative until a complete owned overwrite. */
    public static void unknownWrite(int buffer){gpuWrite(buffer,"external_unknown");}
    private static void gpuWrite(int buffer,String writer){if(buffer==0)return;Shared s=shared();synchronized(s){ObjectState o=object(s,buffer);o.unknown=true;o.readbackCacheAllowed=false;o.writer=writer;invalidate(o,-1,-1);}}
    static void pixelPackWrite(){gpuWrite(bound(GL21.GL_PIXEL_PACK_BUFFER),"pixel_pack");}
    public static void glBindBufferBase(int target,int index,int buffer){if(target==StorageBuffers.TARGET){StorageBuffers.bindBase(index,buffer);return;}GL30.glBindBufferBase(target,index,buffer);if(target==GL30.GL_TRANSFORM_FEEDBACK_BUFFER)LOCAL.get().feedbackBuffers.put(index,buffer);}
    public static void glBindBufferRange(int target,int index,int buffer,long offset,long size){if(target==StorageBuffers.TARGET){StorageBuffers.bindRange(index,buffer,offset,size);return;}GL30.glBindBufferRange(target,index,buffer,offset,size);if(target==GL30.GL_TRANSFORM_FEEDBACK_BUFFER)LOCAL.get().feedbackBuffers.put(index,buffer);}
    public static void glBeginTransformFeedback(int primitive){GL30.glBeginTransformFeedback(primitive);LOCAL.get().feedback=true;for(int id:LOCAL.get().feedbackBuffers.values())gpuWrite(id,"transform_feedback");}
    public static void glEndTransformFeedback(){GL30.glEndTransformFeedback();LOCAL.get().feedback=false;for(int id:LOCAL.get().feedbackBuffers.values())gpuWrite(id,"transform_feedback");}
    public static void glBindTransformFeedback(int target,int object){GL40.glBindTransformFeedback(target,object);Local l=LOCAL.get();l.feedbackBuffers.clear();int n=GL11.glGetInteger(GL30.GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS);for(int i=0;i<n;i++)l.feedbackBuffers.put(i,GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,i));}
    record MetalPatches(ByteBuffer wire,int count){}
    static boolean metalReadable(int id){Shared s=shared();synchronized(s){return object(s,id).mapping==null;}}
    /** Reuses the bounded ownership journal, never a whole MeshArena CPU mirror. */
    static MetalPatches metalPatches(int id,long previous){Shared s=shared();synchronized(s){ObjectState o=object(s,id);long old=previous&0xffffffffL;if(!ContextResources.group().trusted||o.unknown||o.mapping!=null||old<o.lostVersion)return null;
        int size=0,count=0;for(Change c:o.changes)if(c.version>old){if(c.offset<0||c.length<0||c.length>OBJECT_LIMIT||c.length>3*1024*1024-size-32)return null;ByteBuffer bytes=owned(s,o,c.offset,c.length);if(bytes==null)return null;size+=32+((int)c.length+7&~7);count++;}if(count==0)return new MetalPatches(org.lwjgl.BufferUtils.createByteBuffer(0),0);
        Local l=LOCAL.get();if(l.metalPatches==null||l.metalPatches.capacity()<size)l.metalPatches=org.lwjgl.BufferUtils.createByteBuffer(size);ByteBuffer wire=l.metalPatches.clear().limit(size).order(ByteOrder.nativeOrder());long last=previous;
        for(Change c:o.changes)if(c.version>old){ByteBuffer bytes=owned(s,o,c.offset,c.length);long next=(o.generation<<32)|(c.version&0xffffffffL);wire.putInt(id).putInt((int)c.length).putLong(c.offset).putLong(last).putLong(next).put(bytes);wire.position((wire.position()+7)&~7);last=next;}return new MetalPatches(wire.flip().slice().order(ByteOrder.nativeOrder()),count);
    }}
    static ByteBuffer metalOwned(int buffer){Shared s=shared();synchronized(s){ObjectState o=object(s,buffer);ByteBuffer b=owned(s,o,0,o.size);if(b==null)return null;Local l=LOCAL.get();if(l.metalStaging==null||l.metalStaging.capacity()<b.remaining())l.metalStaging=org.lwjgl.BufferUtils.createByteBuffer(b.remaining());ByteBuffer out=l.metalStaging.clear().limit(b.remaining());out.put(b).flip();return out.slice().order(ByteOrder.nativeOrder());}}
    static void auxiliary(int buffer){Shared s=shared();synchronized(s){ObjectState o=object(s,buffer);discardAll(s,o);o.auxiliary=true;}}
    static long textureLifetime(int texture){Shared s=shared();synchronized(s){return s.textureGenerations.getOrDefault(texture,-1L);}}
    static long[] identity(int buffer){Shared s=shared();synchronized(s){ObjectState o=object(s,buffer);return new long[]{o.generation,o.storage,o.version,o.size};}}
    public static long mirrorBytesForTest(){Shared s=shared();synchronized(s){return s.mirrorBytes;}}
}
