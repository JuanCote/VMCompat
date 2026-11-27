package vmcompat;
import java.nio.*;
import java.util.*;
import org.lwjgl.opengl.*;

/** Straight compatibility translation: every indirect row becomes its original GL draw. */
public final class Draws{
    private static final class Binding{int buffer,stride,advance,divisor;long offset;}
    private static final class Attribute{int binding,size=4,type=GL11.GL_FLOAT,relative;boolean normalized,integer;}
    private static final class Vao{Instanced[] prepared;final Map<Integer,Binding> bindings=new HashMap<>();final Map<Integer,Attribute> attributes=new HashMap<>();final Map<Integer,Pointer> live=new HashMap<>();final Map<Integer,Integer> divisors=new HashMap<>();}
    private static final class Pointer {int buffer,size,type,stride;long offset;boolean normalized,integer;
        Pointer(int buffer,int size,int type,int stride,long offset,boolean normalized,boolean integer){set(buffer,size,type,stride,offset,normalized,integer);}
        void set(int buffer,int size,int type,int stride,long offset,boolean normalized,boolean integer){this.buffer=buffer;this.size=size;this.type=type;this.stride=stride;this.offset=offset;this.normalized=normalized;this.integer=integer;}}

    private record Locations(long revision,int base,int draw){}
    private static final class State{int vao,defaultVao,preparedProgram;final Map<Integer,Locations> locations=new HashMap<>();final Map<Integer,Vao> vaos=new HashMap<>();}
    private static final StateStore<State> STATES=new StateStore<>(State::new);
    private static Vao vao(){State s=STATES.get();return s.vaos.computeIfAbsent(s.vao,k->new Vao());}
    static int logicalVao(){return STATES.get().vao;}public static void initialize(){glBindVertexArray(0);}
    public static int vertexArrayBinding(){State s=STATES.get();int id=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);return id==s.defaultVao?0:id;}
    public static void glBindVertexArray(int id){State s=STATES.get();s.vao=id;if(id==0&&s.defaultVao==0)s.defaultVao=GL30.glGenVertexArrays();GL30.glBindVertexArray(id==0?s.defaultVao:id);}
    public static void glBindVertexBuffer(int index,int buffer,long offset,int stride){Binding b=vao().bindings.computeIfAbsent(index,k->new Binding());b.buffer=buffer;b.offset=offset;b.stride=stride;b.advance=stride;applyBinding(index,0);}
    public static void glVertexAttribBinding(int index,int binding){Attribute a=vao().attributes.computeIfAbsent(index,k->new Attribute());a.binding=binding;apply(index,a,0);}
    public static void glVertexAttribFormat(int index,int size,int type,boolean normalized,int relative){Attribute a=vao().attributes.computeIfAbsent(index,k->new Attribute());a.size=size;a.type=type;a.normalized=normalized;a.relative=relative;a.integer=false;apply(index,a,0);}
    public static void glVertexAttribIFormat(int index,int size,int type,int relative){Attribute a=vao().attributes.computeIfAbsent(index,k->new Attribute());a.size=size;a.type=type;a.relative=relative;a.integer=true;apply(index,a,0);}
    public static void glVertexBindingDivisor(int index,int divisor){vao().bindings.computeIfAbsent(index,k->new Binding()).divisor=divisor;applyBinding(index,0);}
    private static void applyBinding(int binding,int base){Vao v=vao();v.prepared=null;for(var e:v.attributes.entrySet())if(e.getValue().binding==binding)apply(e.getKey(),e.getValue(),base);}
    private static void apply(int index,Attribute a,int base){Vao v=vao();v.prepared=null;Binding binding=v.bindings.get(a.binding);if(binding==null||binding.buffer==0)return;int saved=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);try{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,binding.buffer);pointer(v,index,a,binding,base);}finally{GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,saved);}}
    private static void pointer(Vao vao,int index,Attribute a,Binding binding,int base){
        long offset=binding.offset+a.relative+(binding.divisor==0?0:(long)base*binding.advance);
        Pointer old=vao.live.get(index);
        boolean same=old!=null&&old.buffer==binding.buffer&&old.size==a.size&&old.type==a.type&&old.stride==binding.stride&&old.offset==offset&&old.normalized==a.normalized&&old.integer==a.integer;
        if(!Options.BATCH||!same){if(a.integer)GL30.glVertexAttribIPointer(index,a.size,a.type,binding.stride,offset);else GL20.glVertexAttribPointer(index,a.size,a.type,a.normalized,binding.stride,offset);if(Options.INSTANCE_GROUPS&&old!=null)old.set(binding.buffer,a.size,a.type,binding.stride,offset,a.normalized,a.integer);else{vao.live.put(index,new Pointer(binding.buffer,a.size,a.type,binding.stride,offset,a.normalized,a.integer));Counters.add(Counters.Key.POINTER_STATE_ALLOCATIONS,1);}Counters.add(Counters.Key.POINTER_UPDATES,1);}else Counters.add(Counters.Key.POINTER_CACHE_HITS,1);
        Integer divisor=vao.divisors.get(index);if(!Options.BATCH||divisor==null||divisor!=binding.divisor){GL33.glVertexAttribDivisor(index,binding.divisor);vao.divisors.put(index,binding.divisor);Counters.add(Counters.Key.DIVISOR_UPDATES,1);}
    }
    private static void rawPointer(int index,int size,int type,boolean normalized,int stride,long offset,boolean integer){
        Vao v=vao();v.prepared=null;Attribute a=v.attributes.computeIfAbsent(index,k->new Attribute());Binding old=v.bindings.get(a.binding);int divisor=old==null?0:old.divisor;
        Binding binding=v.bindings.computeIfAbsent(index,k->new Binding());binding.buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);binding.stride=stride;binding.advance=stride==0?size*(type==GL11.GL_BYTE||type==GL11.GL_UNSIGNED_BYTE?1:type==GL11.GL_SHORT||type==GL11.GL_UNSIGNED_SHORT||type==GL30.GL_HALF_FLOAT?2:type==GL11.GL_DOUBLE?8:4):stride;binding.offset=offset;binding.divisor=divisor;
        a.binding=index;a.relative=0;a.size=size;a.type=type;a.normalized=normalized;a.integer=integer;v.live.remove(index);
    }
    public static void glVertexAttribPointer(int index,int size,int type,boolean normalized,int stride,long pointer){GL20.glVertexAttribPointer(index,size,type,normalized,stride,pointer);rawPointer(index,size,type,normalized,stride,pointer,false);}
    public static void glVertexAttribIPointer(int index,int size,int type,int stride,long pointer){GL30.glVertexAttribIPointer(index,size,type,stride,pointer);rawPointer(index,size,type,false,stride,pointer,true);}
    public static void glVertexAttribDivisor(int index,int divisor){GL33.glVertexAttribDivisor(index,divisor);Vao v=vao();v.prepared=null;Attribute a=v.attributes.get(index);if(a!=null){Binding binding=v.bindings.get(a.binding);if(binding!=null)binding.divisor=divisor;}v.divisors.remove(index);}
    private static int currentProgram(){Counters.add(Counters.Key.PROGRAM_QUERIES,1);return GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);}
    private static Locations locations(State state,int program){
        long revision=ContextResources.programRevision(program);Locations cached=state.locations.get(program);
        if(Options.LOCATIONS&&cached!=null&&cached.revision==revision){Counters.add(Counters.Key.LOCATIONS_HITS,1);return cached;}
        Counters.add(Counters.Key.LOCATIONS_MISSES,1);long started=Counters.now();
        Locations result;try{result=new Locations(revision,GL20.glGetUniformLocation(program,"vmcompatBaseInstance"),GL20.glGetUniformLocation(program,"vmcompatDrawId"));}finally{Counters.add(Counters.Key.LOCATION_CALLS,2);Counters.elapsed(Counters.Key.LOCATION_NS,started);}
        if(Options.LOCATIONS)state.locations.put(program,result);return result;
    }
    private record Instanced(int index,Attribute attribute,Binding binding){}
    private static final class InstanceBatch implements AutoCloseable {
        final State state;final Vao vertex;final int program;final Locations fixed;final Instanced[] attributes;int saved,current,lastBase=Integer.MIN_VALUE,lastDraw=Integer.MIN_VALUE;
        InstanceBatch(){long at=Counters.now();state=STATES.get();vertex=state.vaos.computeIfAbsent(state.vao,k->new Vao());program=state.preparedProgram;fixed=Options.BATCH&&Options.LOCATIONS?locations(state,program):null;
            if(Options.INSTANCE_GROUPS&&vertex.prepared!=null){attributes=vertex.prepared;Counters.add(Counters.Key.INSTANCE_GROUP_HITS,1);}
            else {ArrayList<Instanced> list=new ArrayList<>();for(var e:vertex.attributes.entrySet()){Binding binding=vertex.bindings.get(e.getValue().binding);if(binding!=null&&binding.buffer!=0&&binding.divisor!=0)list.add(new Instanced(e.getKey(),e.getValue(),binding));}
                if(Options.INSTANCE_GROUPS)list.sort(Comparator.comparingInt((Instanced x)->x.binding.buffer).thenComparingInt(x->x.attribute.binding).thenComparingInt(x->x.index));attributes=list.toArray(Instanced[]::new);if(Options.INSTANCE_GROUPS)vertex.prepared=attributes;Counters.add(Counters.Key.INSTANCE_GROUP_BUILDS,1);}
            int groups=0,lastBinding=-1;for(Instanced a:attributes)if(lastBinding!=a.attribute.binding){groups++;lastBinding=a.attribute.binding;}Counters.add(Counters.Key.INSTANCED_ATTRIBUTES,attributes.length);Counters.add(Counters.Key.INSTANCED_BINDING_GROUPS,groups);
            if(Options.BATCH&&attributes.length!=0)saved=current=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            Counters.elapsed(Counters.Key.BATCH_SETUP_NS,at);
        }
        void set(int base,int draw){
            long at=Counters.now();if(lastBase!=base)Counters.add(Counters.Key.BASE_CHANGES,1);
            int selected=Options.BATCH?program:currentProgram();Locations l=fixed!=null?fixed:locations(state,selected);
            if(l.base>=0&&(!Options.BATCH||lastBase!=base))GL20.glUniform1i(l.base,base);
            if(l.draw>=0&&(!Options.BATCH||lastDraw!=draw))GL20.glUniform1i(l.draw,draw);
            if(!Options.BATCH){for(var e:vertex.attributes.entrySet()){Binding binding=vertex.bindings.get(e.getValue().binding);if(binding!=null&&binding.divisor!=0)apply(e.getKey(),e.getValue(),base);}}
            else if(lastBase!=base)for(Instanced a:attributes){if(current!=a.binding.buffer){GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,a.binding.buffer);current=a.binding.buffer;Counters.add(Counters.Key.ARRAY_BUFFER_SWITCHES,1);}pointer(vertex,a.index,a.attribute,a.binding,base);}
            lastBase=base;lastDraw=draw;Counters.elapsed(Counters.Key.INSTANCE_STATE_NS,at);
        }
        public void close(){long at=Counters.now();try{set(0,0);}finally{if(Options.BATCH&&attributes.length!=0&&current!=saved)GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,saved);Counters.elapsed(Counters.Key.BATCH_FINISH_NS,at);}}
    }
    static boolean farLayout(int id,int buffer){Vao v=STATES.get().vaos.get(id);if(v==null)return false;int[] sizes={3,2,4,3,1,1},types={GL11.GL_FLOAT,GL11.GL_FLOAT,GL11.GL_UNSIGNED_SHORT,GL11.GL_BYTE,GL11.GL_FLOAT,GL11.GL_INT},offsets={0,12,20,28,32,36};for(int i=0;i<6;i++){Attribute a=v.attributes.get(i);if(a==null)return false;Binding b=v.bindings.get(a.binding);if(b==null||b.buffer!=buffer||b.stride!=40||b.divisor!=0||b.offset+a.relative!=offsets[i]||a.size!=sizes[i]||a.type!=types[i]||a.integer!=(i==5)||a.normalized!=(i==2||i==3))return false;}return true;}
    static void farArrays(int mode,IntBuffer first,IntBuffer count){int p=prepare();long at=Counters.now();int issued=0;for(int i=count.position();i<count.limit();i++)if(count.get(i)>0)issued++;try{GL14.glMultiDrawArrays(mode,first,count);Counters.add(Counters.Key.BATCHES,1);Counters.add(Counters.Key.NATIVE_DRAWS,1);Counters.add(Counters.Key.LOGICAL_DRAWS,issued);Counters.add(Counters.Key.ROWS_SINGLE,issued);Telemetry.indirect(count.remaining(),issued);PassTiming.nativeDraw();}finally{Counters.elapsed(Counters.Key.DRAW_SUBMIT_NS,at);PassTiming.submit(at);finish(p,mode);}}

    private static int prepare(){HangTrace.step("texture-views");Textures.synchronizeViews();HangTrace.step("buffer-ranges");Buffers.synchronizeRanges();HangTrace.step("legacy-prepare");int previous=currentProgram();int p=Legacy.prepare(previous);HangTrace.program(p);STATES.get().preparedProgram=p;HangTrace.step("storage-buffers");StorageBuffers.prepare(p);HangTrace.step("sampler-bindings");Samplers.beforeDraw(p);HangTrace.step("feedback");FeedbackSnapshots.prepare(p);HangTrace.step("prepared");return previous;}
    private static void finish(int old,int mode){HangTrace.step("idle");if(old==0)GL20.glUseProgram(0);Textures.markDraw(mode,old);}
    private static void arrayNative(int mode,int first,int count,int instances){HangTrace.draw(mode,count,instances);try{Counters.add(Counters.Key.NATIVE_DRAWS,1);PassTiming.nativeDraw();if(Options.SINGLE_INSTANCE&&instances==1)GL11.glDrawArrays(mode,first,count);else GL31.glDrawArraysInstanced(mode,first,count,instances);}finally{HangTrace.step("native-return");}}
    private static void arrays(int mode,int first,int count,int instances){long at=Counters.now();Counters.add(Counters.Key.LOGICAL_DRAWS,1);Counters.add(instances==1?Counters.Key.ROWS_SINGLE:Counters.Key.ROWS_MULTI,1);try{if(mode==GL11.GL_QUADS){for(int i=0;i+3<count;i+=4)arrayNative(GL11.GL_TRIANGLE_FAN,first+i,4,instances);}else arrayNative(mode,first,count,instances);}finally{Counters.elapsed(Counters.Key.DRAW_SUBMIT_NS,at);PassTiming.submit(at);}}
    private static int indexBytes(int type){return switch(type){case GL11.GL_UNSIGNED_BYTE->1;case GL11.GL_UNSIGNED_SHORT->2;case GL11.GL_UNSIGNED_INT->4;default->throw new IllegalArgumentException("Index type");};}
    private static void elementNative(int mode,int count,int type,long at,int instances,int base){HangTrace.draw(mode,count,instances);try{Counters.add(Counters.Key.NATIVE_DRAWS,1);PassTiming.nativeDraw();if(Options.SINGLE_INSTANCE&&instances==1)GL32.glDrawElementsBaseVertex(mode,count,type,at,base);else GL32.glDrawElementsInstancedBaseVertex(mode,count,type,at,instances,base);}finally{HangTrace.step("native-return");}}
    private static void elements(int mode,int count,int type,long at,int instances,int base){long started=Counters.now();Counters.add(Counters.Key.LOGICAL_DRAWS,1);Counters.add(instances==1?Counters.Key.ROWS_SINGLE:Counters.Key.ROWS_MULTI,1);try{if(mode==GL11.GL_QUADS){int size=indexBytes(type);for(int i=0;i+3<count;i+=4)elementNative(GL11.GL_TRIANGLE_FAN,4,type,at+(long)i*size,instances,base);}else elementNative(mode,count,type,at,instances,base);}finally{Counters.elapsed(Counters.Key.DRAW_SUBMIT_NS,started);PassTiming.submit(started);}}
    public static void glDrawArrays(int mode,int first,int count){glDrawArraysInstanced(mode,first,count,1);}
    public static void glDrawArraysInstanced(int mode,int first,int count,int instances){if(Opaque.direct())return;int p=prepare();try{arrays(mode,first,count,instances);}finally{finish(p,mode);}}
    public static void glDrawElements(int mode,int count,int type,long at){glDrawElementsInstancedBaseVertex(mode,count,type,at,1,0);}
    public static void glDrawElementsBaseVertex(int mode,int count,int type,long at,int base){glDrawElementsInstancedBaseVertex(mode,count,type,at,1,base);}
    public static void glDrawRangeElements(int mode,int start,int end,int count,int type,long at){glDrawElements(mode,count,type,at);}
    public static void glDrawElementsInstanced(int mode,int count,int type,long at,int instances){glDrawElementsInstancedBaseVertex(mode,count,type,at,instances,0);}
    public static void glDrawElementsInstancedBaseVertex(int mode,int count,int type,long at,int instances,int base){if(Opaque.direct())return;int p=prepare();try{elements(mode,count,type,at,instances,base);}finally{finish(p,mode);}}
    public static void glDrawArraysInstancedBaseInstance(int mode,int first,int count,int instances,int base){if(Opaque.direct())return;int p=prepare();try(InstanceBatch batch=new InstanceBatch()){batch.set(base,0);arrays(mode,first,count,instances);}finally{finish(p,mode);}}
    private static void multi(int mode,int type,long offset,int count,int stride,boolean indexed){HangTrace.step("indirect-commands");
        if(count<0)throw new IllegalArgumentException("Negative draw count");if(count==0)return;
        int row=indexed?20:16,step=stride==0?row:stride;if(step<row||step%4!=0)throw new IllegalArgumentException("Indirect stride");
        ByteBuffer data=Buffers.commands(GL40.GL_DRAW_INDIRECT_BUFFER,offset,Math.addExact(Math.multiplyExact(count-1,step),row));
        int issued=0;int p=prepare();if(Opaque.capture(mode,data,count,stride,indexed)){finish(p,mode);return;}Counters.add(Counters.Key.BATCHES,1);try(InstanceBatch batch=new InstanceBatch()){for(int i=0;i<count;i++){int at=i*step,n=data.getInt(at),instances=data.getInt(at+4);if(n==0||instances==0)continue;issued++;int first=data.getInt(at+8),base=data.getInt(at+(indexed?16:12));batch.set(base,i);if(indexed)elements(mode,n,type,Integer.toUnsignedLong(first)*indexBytes(type),instances,data.getInt(at+12));else arrays(mode,first,n,instances);}}
        finally{Telemetry.indirect(count,issued);finish(p,mode);}
    }
    public static void glMultiDrawArraysIndirect(int mode,long at,int count,int stride){multi(mode,0,at,count,stride,false);}
    public static void glMultiDrawElementsIndirect(int mode,int type,long at,int count,int stride){multi(mode,type,at,count,stride,true);}
    public static void glDeleteVertexArrays(int id){STATES.get().vaos.remove(id);GL30.glDeleteVertexArrays(id);}
    public static void glDeleteVertexArrays(int[] ids){for(int id:ids)glDeleteVertexArrays(id);}
    public static void glDeleteVertexArrays(IntBuffer ids){for(int i=ids.position();i<ids.limit();i++)glDeleteVertexArrays(ids.get(i));}
    public static void glEnableVertexAttribArray(int index){GL20.glEnableVertexAttribArray(index);vao().prepared=null;}
    public static void glDisableVertexAttribArray(int index){GL20.glDisableVertexAttribArray(index);vao().prepared=null;}
    public static void bufferDeleted(int id){State s=STATES.get();for(Vao v:s.vaos.values()){v.prepared=null;v.live.entrySet().removeIf(e->e.getValue().buffer==id);}}
    public static void deletedProgram(int p){STATES.get().locations.remove(p);}
    public static void divisorsUnknown(){Vao v=vao();v.prepared=null;v.live.clear();v.divisors.clear();}
}
