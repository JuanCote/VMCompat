package vmcompat;
import org.lwjgl.opengl.*;

/** The bundled GL3 backend does not clear sampler-object bindings for its font atlas.
 * A game's mipmapped sampler makes that level-zero-only atlas incomplete.
 * Preserve both compatibility ownership and actual GL bindings, which need not agree.
 */
public final class ImGuiSamplerGuard {
    private final Samplers.State state;
    private final int logical,physical,scratchBinding;
    private boolean closed;
    private StartupUiProbe.Capture capture;
    private static boolean reported;
    private static boolean drawReported;
    private ImGuiSamplerGuard(){
        state=Samplers.state();logical=state.sampler[0];
        physical=binding(0);
        scratchBinding=binding(state.scratch);
        if(!reported){reported=true;Log.info("ImGui sampler boundary: logical0="+logical+", physical0="+physical+", scratch="+scratchBinding+", logicalMin="+(logical==0?0:GL33.glGetSamplerParameteri(logical,GL11.GL_TEXTURE_MIN_FILTER))+", physicalMin="+(physical==0?0:GL33.glGetSamplerParameteri(physical,GL11.GL_TEXTURE_MIN_FILTER)));}
        state.sampler[0]=0;
        GL33.glBindSampler(0,0);GL33.glBindSampler(state.scratch,0);
        state.liveSampler[0]=0;state.liveSampler[state.scratch]=0;
    }
    private static int binding(int unit){
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);return GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}
        finally{GL13.glActiveTexture(active);}
    }
    public static ImGuiSamplerGuard begin(){return new ImGuiSamplerGuard();}
    public static ImGuiSamplerGuard begin(Object renderer,imgui.ImDrawData data){
        ImGuiSamplerGuard guard=begin();
        try{
            if(!drawReported&&data!=null&&data.getCmdListsCount()>0){
                drawReported=true;
                int texture=data.getCmdListCmdBufferSize(0)>0?data.getCmdListCmdBufferTextureId(0,0):0;
                Log.info("ImGui first draw: lists="+data.getCmdListsCount()+", vertices="+data.getTotalVtxCount()+", indices="+data.getTotalIdxCount()+", display="+data.getDisplaySizeX()+"x"+data.getDisplaySizeY()+", scale="+data.getFramebufferScaleX()+"x"+data.getFramebufferScaleY()+", firstTexture="+texture+", fbo="+GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            }
            guard.capture=StartupUiProbe.prepare(renderer,data);
            return guard;
        }catch(RuntimeException|Error error){guard.close();throw error;}
    }
    public void close(){
        if(closed)return;closed=true;
        if(capture!=null)StartupUiProbe.afterDraw(capture);
        state.sampler[0]=logical;
        GL33.glBindSampler(0,physical);GL33.glBindSampler(state.scratch,scratchBinding);
        state.liveSampler[0]=physical;state.liveSampler[state.scratch]=scratchBinding;
        if(ImguiBootstrap.NATIVE)Samplers.invalidate();
    }
}
