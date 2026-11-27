package vmcompat;

import org.lwjgl.opengl.*;

/** Apple GLEngine can dereference stale TBO bind points while a buffer store changes. */
final class BufferTextureGuard implements AutoCloseable {
    private final int[] textures=new int[16];
    private int active;
    BufferTextureGuard(){
        long started=Counters.now();
        Samplers.State state=Samplers.state();boolean changed=false;
        for(int unit=0;unit<=state.scratch;unit++){
            if(state.live[unit][4]==0)continue;
            if(!changed){active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);changed=true;}
            GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);int texture=GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);textures[unit]=texture;
            if(texture!=0)GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,0);
        }
        if(changed)GL13.glActiveTexture(active);
        Counters.elapsed(Counters.Key.GUARD_ENTER_NS,started);
    }
    @Override public void close(){
        long started=Counters.now();
        for(int unit=0;unit<textures.length;unit++)if(textures[unit]!=0){GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER,textures[unit]);}
        if(active!=0)GL13.glActiveTexture(active);
        Counters.elapsed(Counters.Key.GUARD_RESTORE_NS,started);
    }
}
