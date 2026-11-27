package vmcompat;
import org.lwjgl.opengl.*;
import org.lwjgl.BufferUtils;

/** PZ's white placeholder array-layer clear, implemented without OpenGL 4.4. */
public final class StartupGl {
    public static void glClearTexSubImage(int texture,int level,int x,int y,int z,int width,int height,int depth,int format,int type,float[] value){
        int target=Textures.target(texture);
        if(target!=GL30.GL_TEXTURE_2D_ARRAY||format!=GL11.GL_RGBA||type!=GL11.GL_FLOAT||value==null||value.length<4)
            throw new UnsupportedOperationException("Unqualified texture sub-clear: target="+target+", format="+format+", type="+type);
        if(width<0||height<0||depth<0||x<0||y<0||z<0||level<0)throw new IllegalArgumentException("Invalid texture clear region");
        if(width==0||height==0||depth==0)return;
        Textures.bound(texture,()->{
            int w=GL11.glGetTexLevelParameteri(target,level,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(target,level,GL11.GL_TEXTURE_HEIGHT),d=GL11.glGetTexLevelParameteri(target,level,GL12.GL_TEXTURE_DEPTH);
            if((long)x+width>w||(long)y+height>h||(long)z+depth>d)throw new IllegalArgumentException("Texture clear exceeds allocated storage");
        });
        int old=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),fbo=GL30.glGenFramebuffers();
        var box=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
        var mask=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
        int[] flags={GL11.GL_SCISSOR_TEST,GL30.GL_FRAMEBUFFER_SRGB,GL11.GL_DITHER,GL30.GL_RASTERIZER_DISCARD};boolean[] enabled=new boolean[flags.length];for(int i=0;i<flags.length;i++)enabled[i]=GL11.glIsEnabled(flags[i]);
        try{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x,y,width,height);GL11.glColorMask(true,true,true,true);
            for(int i=1;i<flags.length;i++)GL11.glDisable(flags[i]);
            for(int layer=z;layer<Math.addExact(z,depth);layer++){
                GL30.glFramebufferTextureLayer(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,texture,level,layer);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete texture sub-clear framebuffer");
                GL30.glClearBufferfv(GL11.GL_COLOR,0,value);
            }
            Textures.pixelsChanged(texture);
        }finally{
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,old);GL30.glDeleteFramebuffers(fbo);
            GL11.glScissor(box.get(0),box.get(1),box.get(2),box.get(3));GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            for(int i=0;i<flags.length;i++)if(enabled[i])GL11.glEnable(flags[i]);else GL11.glDisable(flags[i]);
        }
    }
    private StartupGl(){}
}
