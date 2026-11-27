package vmcompat.tests;
import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;import vmcompat.*;
public class ArrayClearTests{
 public static void main(String[]args)throws Exception{
  if(!GLFW.glfwInit())throw new AssertionError();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long w=Context.createWindow(32,32,"Actual PZ null texture array clear",0,0);Context.makeCurrent(w);Context.capabilities();
  try{
   Class<?> c=Class.forName("zombie.core.rendering.RenderList$ArrayTexture");var ctor=c.getDeclaredConstructor(int.class,int.class,int.class);ctor.setAccessible(true);Object a=ctor.newInstance(4,4,2);var copy=c.getDeclaredMethod("Copy",zombie.core.textures.Texture.class,int.class);copy.setAccessible(true);copy.invoke(a,null,1);
   var field=c.getDeclaredField("texture");field.setAccessible(true);int id=field.getInt(a);GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY,id);var pixels=org.lwjgl.BufferUtils.createByteBuffer(4*4*2*4);GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);for(int i=4*4*4;i<pixels.capacity();i++)if((pixels.get(i)&255)!=255)throw new AssertionError("Missing white placeholder texel at "+i);// A partial clear must keep other texels and the already-white layer intact.
   java.nio.ByteBuffer blue=org.lwjgl.BufferUtils.createByteBuffer(4*4*4);for(int i=0;i<16;i++)blue.putInt(0xffff0000);blue.flip();GL12.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY,0,0,0,0,4,4,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,blue);
   GL11.glColorMask(false,true,false,false);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,0,0);GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
   StartupGl.glClearTexSubImage(id,0,1,1,0,2,2,1,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{1,0,0,1});
   if(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)||!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)||!GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB))throw new AssertionError("clear flags not restored");
   var cm=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,cm);if(cm.get(0)!=0||cm.get(1)==0||cm.get(2)!=0||cm.get(3)!=0)throw new AssertionError("clear color mask not restored");
   GL11.glColorMask(true,true,true,true);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
   GL11.glGetTexImage(GL30.GL_TEXTURE_2D_ARRAY,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
   for(int y=0;y<4;y++)for(int x=0;x<4;x++){int at=(y*4+x)*4;boolean red=x>=1&&x<3&&y>=1&&y<3;if((pixels.get(at+(red?0:2))&255)!=255||(pixels.get(at+(red?2:0))&255)!=0)throw new AssertionError("clear changed unrelated texels");}
   for(int i=64;i<pixels.capacity();i++)if((pixels.get(i)&255)!=255)throw new AssertionError("clear changed unrelated layer");
   boolean refused=false;try{StartupGl.glClearTexSubImage(id,0,3,3,0,2,2,1,GL11.GL_RGBA,GL11.GL_FLOAT,new float[]{1,1,1,1});}catch(IllegalArgumentException expected){refused=true;}if(!refused)throw new AssertionError("clear bounds not checked");
   Textures.glDeleteTextures(id);
   if(GL11.glGetError()!=0)throw new AssertionError("GL error");System.out.println("PASS actual PZ ArrayTexture.Copy(null, layer), white fallback and no native crash");
  }finally{Context.destroyWindow(w);GLFW.glfwTerminate();}
 }
}
