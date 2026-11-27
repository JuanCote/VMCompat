package vmcompat;
import org.lwjgl.*;import org.lwjgl.glfw.*;import org.lwjgl.opengl.*;import java.nio.*;
public final class FeedbackTests {
 public static void main(String[]args){if(!GLFW.glfwInit())throw new AssertionError();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,0);long w=Context.createWindow(16,16,"Exact texture feedback fixture",0,0);Context.makeCurrent(w);Context.capabilities();
  int texture=Textures.glCreateTextures(GL11.GL_TEXTURE_2D);Textures.glTextureStorage2D(texture,1,GL30.GL_R32F,16,16);Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);Textures.glTextureParameteri(texture,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);FloatBuffer pixels=BufferUtils.createFloatBuffer(256);for(int i=0;i<256;i++)pixels.put(i,.25f);Textures.bound(texture,()->Textures.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,16,16,GL11.GL_RED,GL11.GL_FLOAT,pixels));
  int f=GL30.glGenFramebuffers();Gl41.glNamedFramebufferTextureLayer(f,GL30.GL_COLOR_ATTACHMENT0,texture,0,0);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,f);GL11.glViewport(0,0,16,16);int p=StorageBuffersTests.program("#version 330\nuniform sampler2D image;out vec4 c;void main(){c=vec4(texelFetch(image,ivec2(gl_FragCoord.xy),0).r+.25,0,0,1);}");Shaders.glUseProgram(p);Samplers.glUniform1i(GL20.glGetUniformLocation(p,"image"),8);Samplers.glActiveTexture(GL13.GL_TEXTURE0+8);Samplers.glBindTexture(GL11.GL_TEXTURE_2D,texture);
  for(float expected:new float[]{.5f,.75f,1f}){ModernGl.glTextureBarrier();float actual=StorageBuffersTests.pixel();StorageBuffersTests.check(Math.abs(actual-expected)<1e-6,"exact feedback "+actual+" vs "+expected);StorageBuffersTests.clean("feedback snapshot");}
  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);Shaders.glDeleteProgram(p);Textures.glDeleteTextures(texture);Gl41.glDeleteFramebuffers(f);Context.destroyWindow(w);GLFW.glfwTerminate();System.out.println("PASS repeated read/write feedback uses exact GPU snapshots, no waits/readback in adapter");
 }
}
