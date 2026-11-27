package vmcompat;

import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** Server state used by PZ model/atlas render scopes. Respects the requested attribute groups. */
final class AttributeState {
    private static final int ENABLE=0x2000,COLOR=0x4000,DEPTH=0x100,STENCIL=0x400,POLYGON=8,VIEWPORT=0x800,SCISSOR=0x80000,TEXTURE=0x40000,CURRENT=1,LINE=4,TRANSFORM=0x1000;
    private static final int[] CAPS={GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL11.GL_SCISSOR_TEST,GL11.GL_CULL_FACE,GL11.GL_DITHER,GL11.GL_POLYGON_OFFSET_FILL,GL11.GL_POLYGON_OFFSET_LINE,GL11.GL_LINE_SMOOTH,GL13.GL_MULTISAMPLE,GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,GL13.GL_SAMPLE_COVERAGE};
    private static final int[] GROUPS={COLOR,DEPTH,STENCIL,SCISSOR,POLYGON,COLOR,POLYGON,POLYGON,LINE,0x20000000,0x20000000,0x20000000};
    private final int mask;
    private final boolean[] enabled=new boolean[CAPS.length];
    private final int[] blend=new int[6],stencil=new int[14],viewport=new int[4],scissor=new int[4];
    private final boolean[] colourMask=new boolean[4];
    private final float[] clearColour=new float[4],colour=new float[4];
    private final double[] depthRange=new double[2];
    private int depthFunc,clearStencil,cullFace,frontFace,polygonMode,matrixMode;
    private double clearDepth;private boolean depthMask,alpha,textured;private int alphaFunc,textureEnv;private float alphaRef,lineWidth,offsetFactor,offsetUnits,u,v;
    private Samplers.Snapshot textures;
    private boolean[] textureEnabled;
    private boolean has(int bits){return (mask&bits)!=0;}
    AttributeState(int mask,Legacy.State s){
        this.mask=mask;for(int i=0;i<CAPS.length;i++)if(has(ENABLE|GROUPS[i]))enabled[i]=GL11.glIsEnabled(CAPS[i]);
        if(has(ENABLE|COLOR)){alpha=s.alpha;alphaFunc=s.alphaFunc;alphaRef=s.alphaRef;}
        if(has(ENABLE|TEXTURE)){textured=s.textured;textureEnabled=s.textureEnabled.clone();}
        if(has(CURRENT)){colour[0]=s.r;colour[1]=s.g;colour[2]=s.b;colour[3]=s.a;u=s.u;v=s.v;}
        if(has(TRANSFORM))matrixMode=s.matrixMode;
        if(has(COLOR)){
            int[] names={GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_BLEND_EQUATION_ALPHA};for(int i=0;i<6;i++)blend[i]=GL11.glGetInteger(names[i]);
            ByteBuffer values=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,values);for(int i=0;i<4;i++)colourMask[i]=values.get(i)!=0;
            FloatBuffer floats=BufferUtils.createFloatBuffer(4);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,floats);floats.get(clearColour);
        }
        if(has(DEPTH)){depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);DoubleBuffer b=BufferUtils.createDoubleBuffer(1);GL11.glGetDoublev(GL11.GL_DEPTH_CLEAR_VALUE,b);clearDepth=b.get(0);}
        if(has(STENCIL)){int[] names={GL11.GL_STENCIL_FUNC,GL11.GL_STENCIL_REF,GL11.GL_STENCIL_VALUE_MASK,GL11.GL_STENCIL_WRITEMASK,GL11.GL_STENCIL_FAIL,GL11.GL_STENCIL_PASS_DEPTH_FAIL,GL11.GL_STENCIL_PASS_DEPTH_PASS,GL20.GL_STENCIL_BACK_FUNC,GL20.GL_STENCIL_BACK_REF,GL20.GL_STENCIL_BACK_VALUE_MASK,GL20.GL_STENCIL_BACK_WRITEMASK,GL20.GL_STENCIL_BACK_FAIL,GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL,GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS};for(int i=0;i<names.length;i++)stencil[i]=GL11.glGetInteger(names[i]);clearStencil=GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);}
        if(has(VIEWPORT)){GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);DoubleBuffer b=BufferUtils.createDoubleBuffer(2);GL11.glGetDoublev(GL11.GL_DEPTH_RANGE,b);b.get(depthRange);}
        if(has(SCISSOR))GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);
        if(has(POLYGON)){cullFace=GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);frontFace=GL11.glGetInteger(GL11.GL_FRONT_FACE);int[] modes=new int[2];GL11.glGetIntegerv(GL11.GL_POLYGON_MODE,modes);polygonMode=modes[0];offsetFactor=floating(GL11.GL_POLYGON_OFFSET_FACTOR);offsetUnits=floating(GL11.GL_POLYGON_OFFSET_UNITS);}
        if(has(LINE))lineWidth=floating(GL11.GL_LINE_WIDTH);
        if(has(TEXTURE)){textures=Samplers.snapshot();textureEnv=s.textureEnv;}
    }
    private static float floating(int name){FloatBuffer b=BufferUtils.createFloatBuffer(1);GL11.glGetFloatv(name,b);return b.get(0);}
    void restore(Legacy.State s){
        for(int i=0;i<CAPS.length;i++)if(has(ENABLE|GROUPS[i])){if(enabled[i])GL11.glEnable(CAPS[i]);else GL11.glDisable(CAPS[i]);}
        if(has(ENABLE|COLOR)){s.alpha=alpha;s.alphaFunc=alphaFunc;s.alphaRef=alphaRef;}
        if(has(ENABLE|TEXTURE)){s.textured=textured;System.arraycopy(textureEnabled,0,s.textureEnabled,0,textureEnabled.length);}
        if(has(CURRENT)){s.r=colour[0];s.g=colour[1];s.b=colour[2];s.a=colour[3];s.u=u;s.v=v;}
        if(has(TRANSFORM))s.matrixMode=matrixMode;
        if(has(COLOR)){GL14.glBlendFuncSeparate(blend[0],blend[1],blend[2],blend[3]);GL20.glBlendEquationSeparate(blend[4],blend[5]);GL11.glColorMask(colourMask[0],colourMask[1],colourMask[2],colourMask[3]);GL11.glClearColor(clearColour[0],clearColour[1],clearColour[2],clearColour[3]);}
        if(has(DEPTH)){GL11.glDepthFunc(depthFunc);GL11.glDepthMask(depthMask);GL11.glClearDepth(clearDepth);}
        if(has(STENCIL)){for(int side=0;side<2;side++){int at=side*7,face=side==0?GL11.GL_FRONT:GL11.GL_BACK;GL20.glStencilFuncSeparate(face,stencil[at],stencil[at+1],stencil[at+2]);GL20.glStencilMaskSeparate(face,stencil[at+3]);GL20.glStencilOpSeparate(face,stencil[at+4],stencil[at+5],stencil[at+6]);}GL11.glClearStencil(clearStencil);}
        if(has(VIEWPORT)){GL11.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);GL11.glDepthRange(depthRange[0],depthRange[1]);}
        if(has(SCISSOR))GL11.glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
        if(has(POLYGON)){GL11.glCullFace(cullFace);GL11.glFrontFace(frontFace);GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK,polygonMode);GL11.glPolygonOffset(offsetFactor,offsetUnits);}
        if(has(LINE))GL11.glLineWidth(lineWidth);
        if(textures!=null){textures.restore();s.textureEnv=textureEnv;}
    }
}
