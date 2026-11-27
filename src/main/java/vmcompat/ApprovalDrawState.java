package vmcompat;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

/** State boundary for ZombieBuddy's owned loading dialog only, never the game UI. */
public final class ApprovalDrawState {
    private final int framebuffer,defaultDraw;
    private final java.nio.ByteBuffer mask=BufferUtils.createByteBuffer(4);
    private final java.nio.FloatBuffer clear=BufferUtils.createFloatBuffer(4);
    private final int[] flags={GL11.GL_SCISSOR_TEST,GL11.GL_STENCIL_TEST,GL30.GL_RASTERIZER_DISCARD,GL11.GL_COLOR_LOGIC_OP};
    private final boolean[] enabled=new boolean[flags.length];
    private final ImGuiSamplerGuard samplers;
    private boolean closed;
    private static boolean reported;
    private ApprovalDrawState(){
        if(GLFW.glfwGetCurrentContext()==0)throw new IllegalStateException("ZombieBuddy approval render has no current OpenGL context");
        framebuffer=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        for(int i=0;i<flags.length;i++)enabled[i]=GL11.glIsEnabled(flags[i]);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);defaultDraw=GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        GL11.glDrawBuffer(GL11.GL_BACK);GL11.glColorMask(true,true,true,true);for(int flag:flags)GL11.glDisable(flag);
        samplers=ImGuiSamplerGuard.begin();
        if(!reported){reported=true;Log.info("Embedded ZombieBuddy approval: inherited FBO="+framebuffer+", stencil="+enabled[1]+", rasterizerDiscard="+enabled[2]+", colorMask="+mask.get(0)+mask.get(1)+mask.get(2)+mask.get(3));}
    }
    public static ApprovalDrawState begin(){return new ApprovalDrawState();}
    public void close(){
        if(closed)return;closed=true;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL11.glDrawBuffer(defaultDraw);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);
        GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);GL11.glClearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
        for(int i=0;i<flags.length;i++)if(enabled[i])GL11.glEnable(flags[i]);else GL11.glDisable(flags[i]);
        samplers.close();
        Samplers.invalidate();
    }
}
