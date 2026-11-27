package vmcompat;
import org.lwjgl.opengl.*;
/** State and source identity around 0.1.7's built-in depth-to-R32F copy.
 * Only entered after original eligibility + our frequency gate; no GPU waits.
 */
public final class NativePickBoundary {
 static final int[] TARGETS={3553,32879,34067,35866,35882,3552},QUERIES={32873,32874,34068,35869,35884,32872};
 static final class State {
  boolean active,blend;long staged;int texture,x,y,read,draw,program,vao,physical,logical,pack;final int[] viewport=new int[4],units=new int[2],samplers=new int[2];final int[][] physicalTextures=new int[2][6],logicalTextures=new int[2][6];
 }
 static final StateStore<State> STATES=new StateStore<>(State::new);
 public static boolean allow(){if(!Picking.allowRequest())return false;State s=STATES.get();s.active=true;s.blend=GL11.glIsEnabled(GL11.GL_BLEND);s.texture=0;s.read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);s.draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);s.program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);s.vao=Draws.logicalVao();s.physical=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);s.pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);GL11.glGetIntegerv(GL11.GL_VIEWPORT,s.viewport);Samplers.State state=Samplers.state();s.logical=state.active;s.units[0]=0;s.units[1]=state.scratch;
  for(int i=0;i<2;i++){int logical=i==0?s.logical:12;for(int t=0;t<6;t++)s.logicalTextures[i][t]=state.texture(logical,t);GL13.glActiveTexture(GL13.GL_TEXTURE0+s.units[i]);for(int t=0;t<6;t++)s.physicalTextures[i][t]=GL11.glGetInteger(QUERIES[t]);s.samplers[i]=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}GL13.glActiveTexture(s.physical);return true;
 }
 public static void source(int texture,int x,int y){State s=STATES.get();s.texture=texture;s.x=x;s.y=y;s.staged++;}
 public static void end(){State s=STATES.get();if(!s.active)return;s.active=false;Samplers.State state=Samplers.state();for(int i=0;i<2;i++){int logical=i==0?s.logical:12;for(int t=0;t<6;t++)state.bind(logical,t,s.logicalTextures[i][t]);}state.active=s.logical;
  GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,s.read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,s.draw);GL20.glUseProgram(s.program);Draws.glBindVertexArray(s.vao);if(s.blend)GL11.glEnable(GL11.GL_BLEND);else GL11.glDisable(GL11.GL_BLEND);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,s.pack);GL11.glViewport(s.viewport[0],s.viewport[1],s.viewport[2],s.viewport[3]);
  for(int i=0;i<2;i++){GL13.glActiveTexture(GL13.GL_TEXTURE0+s.units[i]);for(int t=0;t<6;t++)GL11.glBindTexture(TARGETS[t],s.physicalTextures[i][t]);GL33.glBindSampler(s.units[i],s.samplers[i]);}GL13.glActiveTexture(s.physical);Samplers.invalidate();
 }
 static boolean staged(){State s=STATES.get();return s.active&&s.texture!=0;}
 private NativePickBoundary(){}
}
