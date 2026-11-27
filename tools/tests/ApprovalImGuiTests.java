package vmcompat.tests;
import java.lang.reflect.*;
import java.nio.*;
import imgui.*;
import imgui.gl3.ImGuiImplGl3;
import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import vmcompat.*;

/** Actual ZombieBuddy approval context, on a hidden GL window; no approval decisions or game loop. */
public final class ApprovalImGuiTests {
    static int samplerBinding(int unit){int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);return GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);}finally{GL13.glActiveTexture(active);}}
    public static void main(String[] args)throws Exception {
        boolean expectFailure=args.length>0&&args[0].equals("--expect-failure");
        boolean instanced=java.util.Arrays.asList(args).contains("--instanced-vao");
        boolean capture=java.util.Arrays.asList(args).contains("--capture");
        boolean mipSampler=java.util.Arrays.asList(args).contains("--mipmap-sampler");
        boolean poison=java.util.Arrays.asList(args).contains("--poison-state"),expectBlack=java.util.Arrays.asList(args).contains("--expect-black");
        boolean fullDialog=java.util.Arrays.asList(args).contains("--full-dialog");
        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();
        if(!GLFW.glfwInit())throw new AssertionError("GLFW");GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=Context.createWindow(fullDialog?1280:64,fullDialog?800:64,"ZombieBuddy approval shader regression",0,0);Context.makeCurrent(window);Context.capabilities();
        AutoCloseable owned=null;int inheritedSampler=0,physicalSampler=0,scratchSampler=0,hostVbo=0,hostVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        if(instanced){hostVbo=Buffers.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,hostVbo);Buffers.glBufferData(GL15.GL_ARRAY_BUFFER,4096,GL15.GL_STATIC_DRAW);Draws.glBindVertexBuffer(0,hostVbo,0,20);Draws.glVertexAttribFormat(0,2,GL11.GL_FLOAT,false,0);Draws.glVertexAttribBinding(0,0);Draws.glVertexBindingDivisor(0,1);}
        if(mipSampler){int sampler=GL33.glGenSamplers();inheritedSampler=sampler;GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL33.glSamplerParameteri(sampler,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);Samplers.glBindSampler(0,sampler);
            physicalSampler=GL33.glGenSamplers();GL33.glSamplerParameteri(physicalSampler,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
            scratchSampler=GL33.glGenSamplers();Samplers.glActiveTexture(GL13.GL_TEXTURE0+7);Samplers.glBindSampler(7,scratchSampler);GL33.glBindSampler(0,physicalSampler);}
        try {
            // Only the approval style's font-height input needs minimal fixture data;
            // no game font packs, world or loading-screen loop is initialized.
            var fontFile=Log.ROOT.resolve("approval-fixture.fnt");
            java.nio.file.Files.writeString(fontFile,"info face=Fixture size=14\ncommon lineHeight=14 base=12 scaleW=1 scaleH=1 pages=0 packed=0\nchars count=0\n");
            var font=new zombie.core.fonts.AngelCodeFont(fontFile.toString(),(zombie.core.textures.Texture)null);
            var height=zombie.core.fonts.AngelCodeFont.class.getDeclaredField("lineHeight");height.setAccessible(true);height.setInt(font,14);
            zombie.ui.TextManager.instance.enumToFont[zombie.ui.UIFont.Small.ordinal()]=font;
            Class<?> type=Class.forName("me.zed_0xff.zombie_buddy.frontend.ImguiModApprovalFrontend$OwnedImguiContext");
            var ctor=type.getDeclaredConstructor(long.class);ctor.setAccessible(true);
            try{owned=(AutoCloseable)ctor.newInstance(window);}
            catch(InvocationTargetException e){
                Throwable cause=e.getCause();
                if(expectFailure&&cause instanceof IllegalStateException&&cause.getMessage().contains("attribute")){
                    System.out.println("BASELINE REPRODUCED: actual ZombieBuddy Cocoa approval context fails at attribute shader syntax");return;
                }
                throw e;
            }
            if(expectFailure)throw new AssertionError("Baseline unexpectedly succeeded; fixture did not reproduce the incident");
            var field=type.getDeclaredField("gl3");field.setAccessible(true);var renderer=(ImGuiImplGl3)field.get(owned);
            var version=ImGuiImplGl3.class.getDeclaredField("glslVersion");version.setAccessible(true);
            if(!"#version 410 core".equals(version.get(renderer)))throw new AssertionError("Approval backend did not select its core variant");
            var reported=ImGuiImplGl3.class.getDeclaredField("glVersion");reported.setAccessible(true);int actualVersion=GL11.glGetInteger(GL30.GL_MAJOR_VERSION)*100+GL11.glGetInteger(GL30.GL_MINOR_VERSION)*10;
            if(!expectBlack&&(int)reported.get(renderer)!=actualVersion)throw new AssertionError("Backend ignores actual core context version");
            if(fullDialog){
                Class<?> dialogType=Class.forName("me.zed_0xff.zombie_buddy.frontend.ImguiApprovalDialog");
                var dc=dialogType.getDeclaredConstructor(java.util.List.class,java.util.concurrent.atomic.AtomicReference.class);dc.setAccessible(true);
                var result=new java.util.concurrent.atomic.AtomicReference<>();java.util.List<?> pending=java.util.List.of();
                if(java.util.Arrays.asList(args).contains("--pending-row")){
                    Class<?> entry=Class.forName("me.zed_0xff.zombie_buddy.JarBatchApprovalProtocol$Entry"),sig=Class.forName(entry.getName()+"$ZBSignature"),flags=Class.forName("me.zed_0xff.zombie_buddy.ModFlags");
                    Object unsigned=sig.getMethod("none").invoke(null),empty=flags.getField("EMPTY").get(null);
                    Object row=entry.getConstructors()[0].newInstance("FixturePending",null,Log.ROOT.resolve("fixture-pending.jar"),Log.ROOT.resolve("fixture-mod.info"),"0".repeat(64),new java.util.Date(),null,empty,"Pending Java mod fixture",unsigned,null);pending=java.util.List.of(row);
                }
                Object dialog=dc.newInstance(pending,result);
                var render=type.getDeclaredMethod("render",dialogType);render.setAccessible(true);
                int poisonFbo=0,poisonTexture=0,packProbe=0,readProbe=0;
                if(capture){packProbe=GL15.glGenBuffers();GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packProbe);GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER,4096,GL15.GL_STREAM_READ);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,37);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,2);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,1);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,8);readProbe=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,readProbe);}
                if(poison){
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    poisonTexture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,poisonTexture);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,8,8,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
                    poisonFbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,poisonFbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,poisonTexture,0);
                    GL11.glColorMask(false,false,false,false);GL11.glEnable(GL11.GL_STENCIL_TEST);GL11.glStencilFunc(GL11.GL_NEVER,1,255);GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,0,0);
                }
                for(int i=0;i<(capture?8:4);i++){if(mipSampler){Samplers.glBindSampler(0,inheritedSampler);Samplers.glActiveTexture(GL13.GL_TEXTURE0+7);Samplers.glBindSampler(7,scratchSampler);GL33.glBindSampler(0,physicalSampler);}render.invoke(owned,dialog);GL11.glFinish();if(poison&&GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=poisonFbo)throw new AssertionError("approval FBO not restored");}
                if(capture){
                    if(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=packProbe||GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH)!=37||GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)!=2||GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS)!=1||GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)!=8||GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=readProbe)throw new AssertionError("Startup probe changed pack/read framebuffer state");
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);GL15.glDeleteBuffers(packProbe);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,4);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(readProbe);
                }
                if(poison){
                    ByteBuffer cm=BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,cm);
                    if(cm.get(0)!=0||cm.get(1)!=0||cm.get(2)!=0||cm.get(3)!=0||!GL11.glIsEnabled(GL11.GL_STENCIL_TEST)||!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)||!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))throw new AssertionError("approval state not restored");
                    if(!expectBlack){
                        field.set(owned,null);boolean failed=false;try{render.invoke(owned,dialog);}catch(InvocationTargetException expected){failed=expected.getCause() instanceof NullPointerException;}finally{field.set(owned,renderer);}
                        if(!failed||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=poisonFbo||!GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD))throw new AssertionError("exception restoration failed");
                        render.invoke(owned,dialog);
                    }
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL11.glColorMask(true,true,true,true);GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL30.glDeleteFramebuffers(poisonFbo);GL11.glDeleteTextures(poisonTexture);
                }
                if(mipSampler&&!expectBlack){
                    if(samplerBinding(0)!=physicalSampler)throw new AssertionError("physical host sampler not restored");
                    var sm=Samplers.class.getDeclaredMethod("state");sm.setAccessible(true);Object ss=sm.invoke(null);var sf=ss.getClass().getDeclaredField("sampler");sf.setAccessible(true);
                    if(samplerBinding(15)!=scratchSampler)throw new AssertionError("scratch sampler not restored");
                    if(((int[])sf.get(ss))[0]!=inheritedSampler)throw new AssertionError("logical host sampler not restored");
                    boolean threw=false;try{renderer.renderDrawData(null);}catch(NullPointerException expected){threw=true;}
                    if(!threw||samplerBinding(0)!=physicalSampler||((int[])sf.get(ss))[0]!=inheritedSampler)throw new AssertionError("sampler exception boundary");
                }
                int[] w=new int[1],h=new int[1];GLFW.glfwGetFramebufferSize(window,w,h);ByteBuffer all=BufferUtils.createByteBuffer(w[0]*h[0]*4);GL11.glReadPixels(0,0,w[0],h[0],GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,all);
                var image=new java.awt.image.BufferedImage(w[0],h[0],java.awt.image.BufferedImage.TYPE_INT_RGB);long visible=0;
                for(int y=0;y<h[0];y++)for(int x=0;x<w[0];x++){int at=(y*w[0]+x)*4,r=all.get(at)&255,g=all.get(at+1)&255,b=all.get(at+2)&255;if(r>25||g>25||b>25)visible++;image.setRGB(x,h[0]-1-y,(r<<16)|(g<<8)|b);}
                javax.imageio.ImageIO.write(image,"png",Log.ROOT.resolve("actual-approval.png").toFile());
                System.out.println("ACTUAL APPROVAL DIALOG visible_pixels="+visible+" framebuffer="+GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)+" error="+GL11.glGetError());
                if(instanced&&!expectBlack&&(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)!=hostVao||GL20.glGetVertexAttribi(0,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR)!=1))throw new AssertionError("Host instanced VAO was changed by UI");
                if(result.get()!=null)throw new AssertionError("Fixture made an approval decision");if(expectBlack){if(visible>=10000)throw new AssertionError("Baseline did not reproduce hidden approval");System.out.println("BASELINE BLACK APPROVAL REPRODUCED under inherited GL state");}else if(visible<10000)throw new AssertionError("Actual approval dialog is black");
                if(capture){
                    for(int i=0;i<3;i++)Telemetry.swap(window);
                    var flush=Log.class.getDeclaredMethod("flush");flush.setAccessible(true);flush.invoke(null);
                    String state=java.nio.file.Files.readString(Log.ROOT.resolve("startup-ui-state.json"));
                    if(!state.contains("\"ui_program_linked\":1")||!state.contains("\"probe_errors\":[]")||!state.contains("\"errors_after_ui_before_probe\":[]")||!state.contains("\"number\":3")||!java.nio.file.Files.isRegularFile(Log.ROOT.resolve("startup-ui-back.png"))||!java.nio.file.Files.isRegularFile(Log.ROOT.resolve("startup-ui-texture.png")))throw new AssertionError("Incomplete startup capture");
                    if(GL11.glGetError()!=0)throw new AssertionError("Startup probe left a GL error");
                    System.out.println("STARTUP UI CAPTURE PASS: back, font texture, three presentation samples, background writer");
                }
                return;
            }
            ImGui.getIO().setIniFilename(null);ImGui.getIO().setDisplaySize(64,64);ImGui.getIO().setDeltaTime(1f/60);
            GL11.glViewport(0,0,64,64);GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            ImGui.newFrame();ImGui.getForegroundDrawList().addRectFilled(0,0,64,64,0xff0000ff);ImGui.render();renderer.renderDrawData(ImGui.getDrawData());
            ByteBuffer pixel=BufferUtils.createByteBuffer(4);GL11.glReadPixels(32,32,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
            if((pixel.get(0)&255)<250||(pixel.get(1)&255)>3||GL11.glGetError()!=0)throw new AssertionError("Approval context pixel mismatch/GL error");
            System.out.println("APPROVAL PIXEL PASS: actual ZombieBuddy OwnedImguiContext, core GLSL branch, no approval or policy bypass");
        } finally {
            if(owned!=null)owned.close();else if(ImGui.getCurrentContext().isValidPtr())ImGui.destroyContext();
            if(hostVbo!=0){GL33.glVertexAttribDivisor(0,0);Buffers.glDeleteBuffers(hostVbo);}
            if(inheritedSampler!=0)Samplers.glDeleteSamplers(new int[]{inheritedSampler,physicalSampler,scratchSampler});
            Context.destroyWindow(window);GLFW.glfwTerminate();callback.free();
        }
    }
}
