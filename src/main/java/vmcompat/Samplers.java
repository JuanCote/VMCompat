package vmcompat;

import java.nio.*;
import java.util.*;
import org.lwjgl.*;
import org.lwjgl.opengl.*;

import zombie.core.textures.Texture;

public final class Samplers {
    private static final int[] TARGETS={3553,32879,34067,35866,35882,3552};
    private static final StateStore<State> STATES=new StateStore<>(State::new);
    static final class Input { final int location,target,type;int physical,logical;Input(int l,int t,int p,int ty){location=l;target=t;physical=p;type=ty;} }
    static final class State {

        final Map<Integer,Integer> slow=new HashMap<>();
        final int[] logical=new int[256*6];
        final Map<Integer,Long> revisions=new HashMap<>();
        final int[] sampler=new int[256];
        final int[][] live=new int[80][6];
        final int[] liveSampler=new int[80];
        final Map<Integer,Input[]> programs=new HashMap<>();
        final Map<Integer,Integer> dummy=new HashMap<>();
        int active;
        int scratch=Math.min(15,GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)-1);
        void bind(int logical,int target,int texture){if(Options.ARRAYS)this.logical[logical*6+target]=texture;else slow.put(logical*8+target,texture);}
        int texture(int logical,int target){return Options.ARRAYS?this.logical[logical*6+target]:slow.getOrDefault(logical*8+target,0);}
    }
    static int activeUnit(){return state().active;}
    static int boundTexture(int unit,int target){return state().texture(unit,slot(target));}
    static void restoreInput(int program,int location,int texture){State s=state();for(Input in:s.programs.getOrDefault(program,new Input[0]))if(in.location==location){int t=slot(in.target);if(s.live[in.physical][t]!=texture){GL13.glActiveTexture(GL13.GL_TEXTURE0+in.physical);GL11.glBindTexture(in.target,texture);s.live[in.physical][t]=texture;Counters.add(Counters.Key.SAMPLER_BINDS,1);}return;}}
    static int logicalUnit(int program,int location){for(Input in:state().programs.getOrDefault(program,new Input[0]))if(in.location==location)return in.logical;return -1;}
    static State state(){return STATES.get();}
    static final class Snapshot {
        final int[][] textures=new int[256][6];final int[] samplers;final int active;
        Snapshot(State s){active=s.active;samplers=s.sampler.clone();for(int u=0;u<256;u++)for(int t=0;t<6;t++)textures[u][t]=s.texture(u,t);}
        void restore(){State s=state();for(int u=0;u<256;u++)for(int t=0;t<6;t++)s.bind(u,t,textures[u][t]);System.arraycopy(samplers,0,s.sampler,0,256);invalidate();glActiveTexture(GL13.GL_TEXTURE0+active);}
    }
    static Snapshot snapshot(){return new Snapshot(state());}
    private static int slot(int target){for(int i=0;i<TARGETS.length;i++)if(TARGETS[i]==target)return i;throw new UnsupportedOperationException("Texture target "+target);}
    public static void glActiveTexture(int unit){int logical=unit-GL13.GL_TEXTURE0;if(logical<0||logical>=256)throw new IllegalArgumentException("Logical texture unit "+logical);State s=state();s.active=logical;GL13.glActiveTexture(GL13.GL_TEXTURE0+s.scratch);for(int t=0;t<TARGETS.length;t++){int tex=s.texture(logical,t);if(s.live[s.scratch][t]!=tex){GL11.glBindTexture(TARGETS[t],tex);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.live[s.scratch][t]=tex;}}if(s.liveSampler[s.scratch]!=s.sampler[logical]){GL33.glBindSampler(s.scratch,s.sampler[logical]);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.liveSampler[s.scratch]=s.sampler[logical];}}
    public static void glBindTexture(int target,int texture){if(Legacy.record(()->glBindTexture(target,texture)))return;State s=state();int t=slot(target);GL13.glActiveTexture(GL13.GL_TEXTURE0+s.scratch);GL11.glBindTexture(target,texture);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.bind(s.active,t,texture);s.live[s.scratch][t]=texture;Textures.target(texture,target);}
    public static void glBindSampler(int logical,int sampler){State s=state();s.sampler[logical]=sampler;GL33.glBindSampler(s.scratch,sampler);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.liveSampler[s.scratch]=sampler;}
    public static void glSamplerParameteri(int sampler,int name,int value){GL33.glSamplerParameteri(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value);}
    public static void glSamplerParameterf(int sampler,int name,float value){GL33.glSamplerParameterf(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value);}
    public static void glSamplerParameteriv(int sampler,int name,IntBuffer value){GL33.glSamplerParameteriv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value.get(value.position()));}
    public static void glSamplerParameteriv(int sampler,int name,int[] value){GL33.glSamplerParameteriv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value[0]);}
    public static void glSamplerParameterfv(int sampler,int name,FloatBuffer value){GL33.glSamplerParameterfv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value.get(value.position()));}
    public static void glSamplerParameterfv(int sampler,int name,float[] value){GL33.glSamplerParameterfv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,value[0]);}
    public static void glSamplerParameterIiv(int sampler,int name,IntBuffer value){GL33.glSamplerParameterIiv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,Double.NaN);}
    public static void glSamplerParameterIiv(int sampler,int name,int[] value){GL33.glSamplerParameterIiv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,Double.NaN);}
    public static void glSamplerParameterIuiv(int sampler,int name,IntBuffer value){GL33.glSamplerParameterIuiv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,Double.NaN);}
    public static void glSamplerParameterIuiv(int sampler,int name,int[] value){GL33.glSamplerParameterIuiv(sampler,name,value);PackedSampling.samplerParameter(sampler,name,Double.NaN);}
    public static void glDeleteSamplers(int sampler){GL33.glDeleteSamplers(sampler);PackedSampling.samplerDeleted(sampler);State s=state();for(int i=0;i<s.sampler.length;i++)if(s.sampler[i]==sampler)s.sampler[i]=0;invalidate();}
    public static void glDeleteSamplers(IntBuffer samplers){for(int i=samplers.position();i<samplers.limit();i++)glDeleteSamplers(samplers.get(i));}
    public static void glDeleteSamplers(int[] samplers){for(int sampler:samplers)glDeleteSamplers(sampler);}
    public static void bindHostTexture(Object object){Texture texture=(Texture)object;texture.bind();State s=state();s.bind(s.active,0,texture.getID());}
    public static void glUniform1i(int location,int value){State s=state();int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(WordStorage.assign(program,location,value))return;if(Packing.assign(program,location,value))return;for(Input in:s.programs.getOrDefault(program,new Input[0]))if(in.location==location){if(value<0||value>=256)throw new IllegalArgumentException("Sampler unit "+value);in.logical=value;GL20.glUniform1i(location,in.physical);return;}GL20.glUniform1i(location,value);}
    public static void glUniform1iv(int location,IntBuffer values){for(int i=0;i<values.remaining();i++)glUniform1i(location+i,values.get(values.position()+i));}
    public static void glUniform1iv(int location,int[] values){for(int i=0;i<values.length;i++)glUniform1i(location+i,values[i]);}
    public static void linked(int program){
        State s=state();List<Input> inputs=new ArrayList<>();IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);
        for(int i=0;i<GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS);i++){
            String name=GL20.glGetActiveUniform(program,i,512,size,type);int target=uniformTarget(type.get(0));if(target==0||name.startsWith("vmcompatImage"))continue;
            for(int j=0;j<size.get(0);j++){String element=size.get(0)>1?name.replace("[0]","["+j+"]"):name;int location=GL20.glGetUniformLocation(program,element);if(location<0)continue;inputs.add(new Input(location,target,-1,type.get(0)));}
        }
        boolean[] used=new boolean[s.live.length];used[s.scratch]=true;used[s.scratch-1]=true;int bufferUnit=0;
        for(Input in:inputs)if(in.target==GL31.GL_TEXTURE_BUFFER){if(bufferUnit>=s.scratch-1)throw new IllegalStateException("Too many buffer samplers for safe Apple texture units");in.physical=bufferUnit;used[bufferUnit++]=true;}
        int next=0;for(Input in:inputs)if(in.physical<0){while(next<used.length&&used[next])next++;if(next>=used.length)throw new IllegalStateException("Too many combined samplers");in.physical=next;used[next++]=true;}
        s.programs.put(program,inputs.toArray(Input[]::new));s.revisions.put(program,ContextResources.programRevision(program));for(Input in:inputs)GL41.glProgramUniform1i(program,in.location,in.physical);
    }
    public static void deleted(int program){state().programs.remove(program);state().revisions.remove(program);}
    private static int uniformTarget(int type){return switch(type){case 35678,35682,36298,36306->3553;case 35679,36299,36307->32879;case 35680,36293,36300,36308->34067;case 36289,36292,36303,36311->35866;case 36290,36304,36312->35882;case 35677,36297,36305->3552;default->0;};}
    public static void beforeDraw(){beforeDraw(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));}
    static void beforeDraw(int program){
        State s=state();Input[] inputs=s.programs.get(program);if(inputs==null||!java.util.Objects.equals(s.revisions.get(program),ContextResources.programRevision(program))){linked(program);inputs=s.programs.get(program);}
        for(Input in:inputs){int t=slot(in.target),tex=s.texture(in.logical,t);if(tex==0)tex=dummy(s,in.target,in.type);int p=in.physical;if(s.live[p][t]!=tex){GL13.glActiveTexture(GL13.GL_TEXTURE0+p);GL11.glBindTexture(in.target,tex);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.live[p][t]=tex;}if(s.liveSampler[p]!=s.sampler[in.logical]){GL33.glBindSampler(p,s.sampler[in.logical]);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.liveSampler[p]=s.sampler[in.logical];}}
        Packing.beforeDraw(program);glActiveTexture(GL13.GL_TEXTURE0+s.active);
    }
    private static int dummy(State s,int target,int type){Integer cached=s.dummy.get(type);if(cached!=null)return cached;int tex=Textures.glGenTextures();GL13.glActiveTexture(GL13.GL_TEXTURE0+s.scratch);GL11.glBindTexture(target,tex);Counters.add(Counters.Key.SAMPLER_BINDS,1);s.live[s.scratch][slot(target)]=tex;
        boolean signed=type>=36297&&type<=36304,unsigned=type>=36305&&type<=36312,shadow=type==35682||type==35684||type==36292||type==36293;
        int internal=signed?GL30.GL_R32I:unsigned?GL30.GL_R32UI:shadow?GL14.GL_DEPTH_COMPONENT16:GL11.GL_RGBA8;
        int external=signed||unsigned?GL30.GL_RED_INTEGER:shadow?GL11.GL_DEPTH_COMPONENT:GL11.GL_RGBA;
        int datatype=signed?GL11.GL_INT:unsigned?GL11.GL_UNSIGNED_INT:GL11.GL_UNSIGNED_BYTE;
        if(target==35882){int b=Buffers.glGenBuffers();int old=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);GL11.glBindTexture(target,0);Counters.add(Counters.Key.SAMPLER_BINDS,1);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,b);Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,64,GL15.GL_STATIC_DRAW);GL11.glBindTexture(target,tex);Counters.add(Counters.Key.SAMPLER_BINDS,1);Buffers.glTexBuffer(target,signed?GL30.GL_R32I:unsigned?GL30.GL_R32UI:GL30.GL_RGBA32F,b);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,old);}
        else if(target==32879||target==35866)GL12.glTexImage3D(target,0,internal,1,1,1,0,external,datatype,(ByteBuffer)null);
        else if(target==34067)for(int face=0;face<6;face++)GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,0,internal,1,1,0,external,datatype,(ByteBuffer)null);
        else if(target==3552)GL11.glTexImage1D(target,0,internal,1,0,external,datatype,(ByteBuffer)null);
        else GL11.glTexImage2D(target,0,internal,1,1,0,external,datatype,(ByteBuffer)null);
        if(target!=35882){GL11.glTexParameteri(target,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(target,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);}if(shadow)GL11.glTexParameteri(target,GL14.GL_TEXTURE_COMPARE_MODE,GL30.GL_COMPARE_REF_TO_TEXTURE);s.dummy.put(type,tex);return tex;
    }
    public static void invalidate(){Counters.add(Counters.Key.SAMPLER_INVALIDATIONS,1);State s=state();for(int[] r:s.live)Arrays.fill(r,-1);Arrays.fill(s.liveSampler,-1);}
    public static void deletedTexture(int texture){State s=state();s.slow.values().removeIf(v->v==texture);for(int i=0;i<s.logical.length;i++)if(s.logical[i]==texture)s.logical[i]=0;for(int[] row:s.live)for(int i=0;i<row.length;i++)if(row[i]==texture)row[i]=-1;s.dummy.values().removeIf(v->v==texture);}
    private Samplers(){}
}
