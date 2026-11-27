package vmcompat;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import vmcompat.core.MatrixStack;

/** OpenGL compatibility operations implemented against a real 4.1 core context. */
public final class Legacy {
    private static final StateStore<State> STATES=new StateStore<>(State::new);
    static final class State {
        final MatrixStack model=new MatrixStack(), projection=new MatrixStack(), texture=new MatrixStack();
        final ArrayDeque<AttributeState> attributes=new ArrayDeque<>();
        final ArrayDeque<ClientState> clients=new ArrayDeque<>();
        int matrixMode=5888, vao, vbo, program, primitive;
        float r=1,g=1,b=1,a=1,u,v,alphaRef;
        boolean alpha, textured;
        final boolean[] textureEnabled=new boolean[256];
        int alphaFunc=516, textureEnv=8448;
        FloatBuffer vertices=BufferUtils.createFloatBuffer(9*4096);
        int list, nextList=1;
        boolean replay;
        final Map<Integer,List<Runnable>> lists=new HashMap<>();
        final Map<Integer,int[]> uniforms=new HashMap<>();
        MatrixStack matrix(){return matrixMode==5889?projection:matrixMode==5890?texture:model;}
    }
    private static State state(){return STATES.get();}
    public static void initialize(){State s=state();if(s.vao==0){s.vao=GL30.glGenVertexArrays();s.vbo=Buffers.glGenBuffers();GL30.glBindVertexArray(s.vao);}}
    static boolean record(Runnable call){State s=state();if(s.list!=0&&!s.replay){s.lists.get(s.list).add(call);return true;}return false;}
    public static void glMatrixMode(int mode){if(record(()->glMatrixMode(mode)))return;if(mode!=5888&&mode!=5889&&mode!=5890)throw new IllegalArgumentException("Matrix mode "+mode);state().matrixMode=mode;}
    public static void glLoadIdentity(){if(!record(Legacy::glLoadIdentity))state().matrix().loadIdentity();}
    public static void glPushMatrix(){if(!record(Legacy::glPushMatrix))state().matrix().push();}
    public static void glPopMatrix(){if(!record(Legacy::glPopMatrix))state().matrix().pop();}
    public static void glLoadMatrixf(FloatBuffer b){float[] a=new float[16];b.get(b.position(),a);if(!record(()->state().matrix().load(a)))state().matrix().load(a);}
    public static void glMultMatrixf(FloatBuffer b){float[] a=new float[16];b.get(b.position(),a);if(!record(()->state().matrix().multiply(a)))state().matrix().multiply(a);}
    public static void glTranslatef(float x,float y,float z){if(!record(()->glTranslatef(x,y,z)))state().matrix().translate(x,y,z);}
    public static void glTranslated(double x,double y,double z){glTranslatef((float)x,(float)y,(float)z);}
    public static void glScalef(float x,float y,float z){if(!record(()->glScalef(x,y,z)))state().matrix().scale(x,y,z);}
    public static void glScaled(double x,double y,double z){glScalef((float)x,(float)y,(float)z);}
    public static void glRotatef(float angle,float x,float y,float z){if(!record(()->glRotatef(angle,x,y,z)))state().matrix().rotate(angle,x,y,z);}
    public static void glRotated(double angle,double x,double y,double z){glRotatef((float)angle,(float)x,(float)y,(float)z);}
    public static void glOrtho(double l,double r,double b,double t,double n,double f){if(!record(()->glOrtho(l,r,b,t,n,f)))state().matrix().ortho(l,r,b,t,n,f);}
    public static void glColor3f(float r,float g,float b){glColor4f(r,g,b,1);}
    public static void glColor4f(float r,float g,float b,float a){if(record(()->glColor4f(r,g,b,a)))return;State s=state();s.r=r;s.g=g;s.b=b;s.a=a;}
    public static void glColor4ub(byte r,byte g,byte b,byte a){glColor4f((r&255)/255f,(g&255)/255f,(b&255)/255f,(a&255)/255f);}
    public static void glNormal3f(float x,float y,float z){} // Fixed-function lighting is replaced by the game's shaders.
    public static void glTexCoord2f(float u,float v){if(record(()->glTexCoord2f(u,v)))return;state().u=u;state().v=v;}
    public static void glAlphaFunc(int func,float reference){if(record(()->glAlphaFunc(func,reference)))return;state().alphaFunc=func;state().alphaRef=reference;}
    public static void glEnable(int cap){if(record(()->glEnable(cap)))return;State s=state();if(cap==3008)s.alpha=true;else if(cap==3553){int unit=Samplers.state().active;s.textureEnabled[unit]=true;if(unit==0)s.textured=true;}else if(!removedCapability(cap))GL11.glEnable(cap);}
    public static void glDisable(int cap){if(record(()->glDisable(cap)))return;State s=state();if(cap==3008)s.alpha=false;else if(cap==3553){int unit=Samplers.state().active;s.textureEnabled[unit]=false;if(unit==0)s.textured=false;}else if(!removedCapability(cap))GL11.glDisable(cap);}
    public static boolean glIsEnabled(int cap){if(cap==3008)return state().alpha;if(cap==3553)return state().textureEnabled[Samplers.state().active];if(removedCapability(cap))return false;return GL11.glIsEnabled(cap);}
    private static boolean removedCapability(int cap){return cap==2896||cap==2903||cap==2977||cap==2912||cap==32826||cap>=16384&&cap<16392;}
    public static void glTexEnvi(int target,int pname,int value){if(record(()->glTexEnvi(target,pname,value)))return;if(target==8960&&pname==8704)state().textureEnv=value;else throw new UnsupportedOperationException("Legacy texture environment: "+target+"/"+pname);}
    public static void glLightf(int light,int parameter,float value){}
    public static void glLightfv(int light,int parameter,FloatBuffer value){}
    public static void glMaterialfv(int face,int parameter,FloatBuffer value){}
    public static void glColorMaterial(int face,int mode){}
    public static int glGetInteger(int name){if(name==GL13.GL_ACTIVE_TEXTURE)return GL13.GL_TEXTURE0+Samplers.state().active;if(name==GL30.GL_VERTEX_ARRAY_BINDING)return Draws.vertexArrayBinding();if(name==2976)return state().matrixMode;if(name==37279)return 4;return GL11.glGetInteger(name);}
    public static String glGetString(int name){if(name!=GL11.GL_EXTENSIONS)return GL11.glGetString(name);StringJoiner extensions=new StringJoiner(" ");for(int i=0;i<GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);i++)extensions.add(GL30.glGetStringi(GL11.GL_EXTENSIONS,i));return extensions.toString();}
    public static void glGetIntegerv(int name,int[] out){if(name==2976||name==GL13.GL_ACTIVE_TEXTURE||name==GL30.GL_VERTEX_ARRAY_BINDING)out[0]=glGetInteger(name);else GL11.glGetIntegerv(name,out);}
    public static void glGetIntegerv(int name,IntBuffer out){if(name==2976||name==GL13.GL_ACTIVE_TEXTURE||name==GL30.GL_VERTEX_ARRAY_BINDING)out.put(out.position(),glGetInteger(name));else GL11.glGetIntegerv(name,out);}
    public static void glGetFloatv(int name,FloatBuffer out){float[] a=name==2982?state().model.value():name==2983?state().projection.value():name==2984?state().texture.value():null;if(a==null)GL11.glGetFloatv(name,out);else out.put(out.position(),a);}
    public static void glBegin(int mode){if(record(()->glBegin(mode)))return;State s=state();s.primitive=mode;s.vertices.clear();}
    public static void glVertex3f(float x,float y,float z){if(record(()->glVertex3f(x,y,z)))return;State s=state();if(s.vertices.remaining()<9){FloatBuffer bigger=BufferUtils.createFloatBuffer(s.vertices.capacity()*2);s.vertices.flip();bigger.put(s.vertices);s.vertices=bigger;}s.vertices.put(x).put(y).put(z).put(s.u).put(s.v).put(s.r).put(s.g).put(s.b).put(s.a);}
    public static void glVertex2f(float x,float y){glVertex3f(x,y,0);}
    public static void glVertex2d(double x,double y){glVertex3f((float)x,(float)y,0);}
    public static void glVertex2i(int x,int y){glVertex3f(x,y,0);}
    public static void glEnd(){
        if(record(Legacy::glEnd))return;State s=state();s.vertices.flip();int count=s.vertices.remaining()/9;if(count==0)return;
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        FloatBuffer data=s.vertices;int mode=s.primitive;
        if(mode==7){FloatBuffer triangles=BufferUtils.createFloatBuffer((count/4)*6*9);for(int q=0;q+3<count;q+=4)for(int k:new int[]{0,1,2,0,2,3})for(int j=0;j<9;j++)triangles.put(data.get((q+k)*9+j));triangles.flip();data=triangles;count=data.remaining()/9;mode=4;}
        GL30.glBindVertexArray(s.vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,s.vbo);Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW);
        GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,36,0L);GL20.glVertexAttribPointer(1,4,GL11.GL_FLOAT,false,36,20L);GL20.glVertexAttribPointer(2,2,GL11.GL_FLOAT,false,36,12L);
        GL20.glEnableVertexAttribArray(0);GL20.glEnableVertexAttribArray(1);GL20.glEnableVertexAttribArray(2);
        prepare();Samplers.beforeDraw();GL11.glDrawArrays(mode,0,count);
        GL20.glUseProgram(oldProgram);GL30.glBindVertexArray(oldVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);
    }
    public static void glEnableClientState(int cap){if(cap==32884)GL20.glEnableVertexAttribArray(0);else if(cap==32886)GL20.glEnableVertexAttribArray(1);else if(cap==32888)GL20.glEnableVertexAttribArray(2);}
    public static void glDisableClientState(int cap){if(cap==32884)GL20.glDisableVertexAttribArray(0);else if(cap==32886)GL20.glDisableVertexAttribArray(1);else if(cap==32888)GL20.glDisableVertexAttribArray(2);}
    public static void glVertexPointer(int size,int type,int stride,long pointer){GL20.glVertexAttribPointer(0,size,type,false,stride,pointer);}
    public static void glColorPointer(int size,int type,int stride,long pointer){GL20.glVertexAttribPointer(1,size,type,true,stride,pointer);}
    public static void glTexCoordPointer(int size,int type,int stride,long pointer){GL20.glVertexAttribPointer(2,size,type,false,stride,pointer);}
    public static void glPushClientAttrib(int mask){state().clients.push(new ClientState(mask));}
    public static void glPopClientAttrib(){if(state().clients.isEmpty())throw new IllegalStateException("Client attribute stack underflow");state().clients.pop().restore();}
    public static void glPushAttrib(int mask){State s=state();s.attributes.push(new AttributeState(mask,s));}
    public static void glPopAttrib(){State s=state();if(s.attributes.isEmpty())throw new IllegalStateException("Attribute stack underflow");s.attributes.pop().restore(s);}
    public static int glGenLists(int count){State s=state();int first=s.nextList;s.nextList+=count;return first;}
    public static void glNewList(int id,int mode){State s=state();s.list=id;s.lists.put(id,new ArrayList<>());}
    public static void glEndList(){state().list=0;}
    public static void glCallList(int id){State s=state();List<Runnable> calls=s.lists.get(id);if(calls==null)return;s.replay=true;try{for(Runnable call:calls)call.run();}finally{s.replay=false;}}
    public static void glDeleteLists(int first,int count){for(int i=first;i<first+count;i++)state().lists.remove(i);}
    public static void prepare(){prepare(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));}
    static int prepare(int program){
        State s=state();
        if(program==0){if(s.program==0)s.program=makeProgram();program=s.program;GL20.glUseProgram(program);}
        if(program!=s.program&&!Shaders.legacy(program))return program;
        int attributes=program==s.program?3:Shaders.legacyAttributes(program);
        if((attributes&1)!=0)GL20.glVertexAttrib4f(1,s.r,s.g,s.b,s.a);
        if((attributes&2)!=0)GL20.glVertexAttrib2f(2,s.u,s.v);
        final int selected=program;int[] locations=s.uniforms.computeIfAbsent(program,key->{String[] names={"vmcompatMvp","vmcompatModel","vmcompatProjection","vmcompatTint","vmcompatUseTexture","vmcompatAlpha"};int[] out=new int[names.length];for(int i=0;i<out.length;i++)out[i]=GL20.glGetUniformLocation(selected,names[i]);return out;});
        if(locations[0]>=0)GL20.glUniformMatrix4fv(locations[0],false,MatrixStack.multiply(s.projection.value(),s.model.value()));
        if(locations[1]>=0)GL20.glUniformMatrix4fv(locations[1],false,s.model.value());
        if(locations[2]>=0)GL20.glUniformMatrix4fv(locations[2],false,s.projection.value());
        if(locations[3]>=0)GL20.glUniform4f(locations[3],s.r,s.g,s.b,s.a);
        if(locations[4]>=0)GL20.glUniform1i(locations[4],s.textured?1:0);
        if(locations[5]>=0)GL20.glUniform2f(locations[5],s.alpha?s.alphaFunc:519,s.alphaRef);
        return program;
    }
    public static void deletedProgram(int program){state().uniforms.remove(program);}
    private static int makeProgram(){
        String vertex="#version 410 core\nlayout(location=0) in vec3 vmcompatPosition;layout(location=1) in vec4 vmcompatColor;layout(location=2) in vec2 vmcompatUV;uniform mat4 vmcompatMvp;out vec4 colour;out vec2 uv;void main(){gl_Position=vmcompatMvp*vec4(vmcompatPosition,1);colour=vmcompatColor;uv=vmcompatUV;}";
        String fragment="#version 410 core\nuniform sampler2D image;uniform bool vmcompatUseTexture;uniform vec2 vmcompatAlpha;in vec4 colour;in vec2 uv;out vec4 result;void main(){result=colour*(vmcompatUseTexture?texture(image,uv):vec4(1));if(vmcompatAlpha.x==516.0&&result.a<=vmcompatAlpha.y)discard;}";
        int vs=Shaders.compileRaw(GL20.GL_VERTEX_SHADER,vertex),fs=Shaders.compileRaw(GL20.GL_FRAGMENT_SHADER,fragment),p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p));return p;
    }
    private Legacy(){}
}
