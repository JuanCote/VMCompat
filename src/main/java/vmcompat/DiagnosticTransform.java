package vmcompat;
import java.lang.classfile.*;import java.lang.classfile.instruction.*;import java.lang.constant.*;import java.util.*;
/** Runs after compatibility rewriting; observes whole methods with catch-all cleanup. */
public final class DiagnosticTransform {
    static final ClassFile CF=ClassFileSupport.create();static final ClassDesc B=ClassDesc.of("vmcompat.Breakdown");
    static final Map<String,Breakdown.Scope> METHODS=new LinkedHashMap<>();
    static {
        add("viewpoint/Hooks",Breakdown.Scope.VP_HOOK_START,"gameRenderStarted");add("viewpoint/Hooks",Breakdown.Scope.VP_HOOK_END,"gameRenderEnded");
        add("viewpoint/platform/FrameTimes",Breakdown.Scope.VP_FRAME_START,"renderStarted");add("viewpoint/platform/FrameTimes",Breakdown.Scope.VP_FRAME_END,"renderEnded");
        add("viewpoint/platform/VideoMemory",Breakdown.Scope.VP_VIDEO_MEMORY,"sample");add("viewpoint/platform/MemoryCensus",Breakdown.Scope.VP_MEMORY_CENSUS,"renderFrame");add("viewpoint/platform/Onboarding",Breakdown.Scope.VP_ONBOARDING,"renderFrame");
        add("zombie/GameWindow",Breakdown.Scope.MAIN_FRAME,"frameStep");add("zombie/GameWindow",Breakdown.Scope.MAIN_LOGIC,"logic");add("zombie/GameWindow",Breakdown.Scope.MAIN_PREP,"renderInternal");add("imgui/gl3/ImGuiImplGl3",Breakdown.Scope.PZ_UI,"renderDrawData");
        add("zombie/core/SpriteRenderer",Breakdown.Scope.PUBLISH,"pushFrameDown");add("zombie/core/SpriteRenderer",Breakdown.Scope.ACQUIRE,"acquireStateForRendering");
        add("zombie/core/SpriteRenderer",Breakdown.Scope.READY_REGION,"waitForReadyState");add("zombie/core/SpriteRenderer",Breakdown.Scope.SLOT_REGION,"waitForReadySlotToOpen");
        add("zombie/core/SpriteRenderer",Breakdown.Scope.PZ_RENDER,"postRender");add("zombie/core/SpriteRenderer",Breakdown.Scope.PZ_WORLD,"buildStateDrawBuffer");add("zombie/core/SpriteRenderer",Breakdown.Scope.PZ_UI,"buildStateUIDrawBuffer");add("zombie/core/SpriteRenderer",Breakdown.Scope.PZ_BUILD,"buildDrawBuffer");
        add("zombie/core/opengl/RenderThread",Breakdown.Scope.RENDER_STEP,"lockStepRenderStep");add("zombie/core/opengl/RenderThread",Breakdown.Scope.INVOKE_QUEUE,"flushInvokeQueue");add("zombie/core/opengl/RenderThread",Breakdown.Scope.CONTROLLERS,"checkControllers");add("zombie/ui/FPSGraph",Breakdown.Scope.FPS_GRAPH,"addRender");
        add("org/lwjglx/opengl/Display",Breakdown.Scope.DISPLAY_UPDATE,"update","swapBuffers");add("org/lwjglx/opengl/Display",Breakdown.Scope.EVENT_PUMP,"processMessages");add("org/lwjglx/opengl/Sync",Breakdown.Scope.LIMITER,"sync");
        add("viewpoint/SceneDrawer",Breakdown.Scope.VP_PREP,"prepare");add("viewpoint/render/WorldRenderer",Breakdown.Scope.VP_PREP,"prepare","begin");
        add("viewpoint/render/WorldRenderer",Breakdown.Scope.VP_WORLD,"drawWorld");add("viewpoint/render/WorldRenderer",Breakdown.Scope.VP_TRANSPARENT,"drawTranslucent");add("viewpoint/render/WorldRenderer",Breakdown.Scope.VP_WEATHER,"drawWeather");add("viewpoint/render/WorldRenderer",Breakdown.Scope.VP_FINISH,"finish");
        add("viewpoint/render/SurfacePass",Breakdown.Scope.VP_GBUFFER,"gbuffer");add("viewpoint/render/ModelPass",Breakdown.Scope.VP_MODELS,"gbuffer");add("viewpoint/render/FarPass",Breakdown.Scope.VP_FAR,"gbuffer","draw");add("viewpoint/render/MousePick",Breakdown.Scope.VP_PICK,"read");
        add("viewpoint/render/Meshes",Breakdown.Scope.VP_PREP,"prepare");add("viewpoint/render/MeshArena",Breakdown.Scope.RESOURCE,"upload","uploadRecords","uploadLight");add("viewpoint/render/MeshArena",Breakdown.Scope.RESOURCE_GROW,"grow");add("zombie/core/textures/TextureID",Breakdown.Scope.RESOURCE,"createTexture","setData","getData");
    }
    static void add(String owner,Breakdown.Scope s,String...names){for(String n:names)METHODS.put(owner+"."+n,s);}
    static void objectCall(CodeBuilder b,String method){b.aload(0);b.invokestatic(B,method,MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));}
    public static byte[] rewrite(String owner,byte[] bytes){return rewrite(owner,bytes,Map.of());}
    /** Extra map is used by synthetic bytecode tests, not by production class loading. */
    public static byte[] rewrite(String owner,byte[] bytes,Map<String,Breakdown.Scope> extra){
        boolean relevant=METHODS.keySet().stream().anyMatch(k->k.startsWith(owner+"."))||Set.of("viewpoint/FP","viewpoint/SceneDrawer","zombie/core/sprite/SpriteRenderState","zombie/core/sprite/GenericSpriteRenderState","zombie/core/opengl/RenderContextQueueItem","zombie/core/opengl/RenderThread").contains(owner)||!extra.isEmpty();if(!relevant)return bytes;
        return CF.transformClass(CF.parse(bytes),(cb,e)->{if(!(e instanceof MethodModel m)||m.code().isEmpty()){cb.with(e);return;}String name=m.methodName().stringValue();Breakdown.Scope scope=extra.getOrDefault(name,METHODS.get(owner+"."+name));boolean drawer=owner.equals("viewpoint/SceneDrawer")&&name.equals("render");
            cb.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){int token=-1,error=-1;Label start,end,handler;
                public void atStart(CodeBuilder b){
                    if(owner.equals("zombie/core/sprite/SpriteRenderState")&&name.equals("prePopulating"))objectCall(b,"populate");
                    if(owner.equals("zombie/core/sprite/SpriteRenderState")&&name.equals("onReady"))objectCall(b,"publish");
                    if(owner.equals("zombie/core/sprite/GenericSpriteRenderState")&&name.equals("onRenderAcquired"))objectCall(b,"consume");
                    if(scope!=null||drawer){token=b.allocateLocal(TypeKind.LONG);if(drawer){b.aload(0);b.aload(0);b.getfield(ClassDesc.of("viewpoint.SceneDrawer"),"frame",ClassDesc.of("viewpoint.core.Frame"));b.invokestatic(B,"drawerBegin",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Lviewpoint/core/Frame;)J"));}else{b.loadConstant(scope.ordinal());b.invokestatic(B,"open",MethodTypeDesc.ofDescriptor("(I)J"));}b.lstore(token);start=b.newLabel();end=b.newLabel();handler=b.newLabel();b.labelBinding(start);}
                }
                public void accept(CodeBuilder b,CodeElement e){
                    if(e instanceof ReturnInstruction){
                        if(owner.equals("viewpoint/SceneDrawer")&&name.equals("<init>")){b.aload(0);b.aload(1);b.invokestatic(B,"drawerRegistered",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Lviewpoint/core/Frame;)V"));}
                        
                        if(owner.equals("zombie/core/sprite/SpriteRenderState")&&name.equals("onRendered"))objectCall(b,"rendered");
                        if(token>=0){b.lload(token);b.invokestatic(B,"close",MethodTypeDesc.ofDescriptor("(J)V"));}
                    }
                    if(e instanceof InvokeInstruction i){String target=i.owner().asInternalName(),called=i.name().stringValue(),desc=i.type().stringValue();
                        if(owner.equals("viewpoint/FP")&&name.equals("renderWorld")&&target.equals("zombie/core/SpriteRenderer")&&called.equals("drawGeneric")){b.dup();b.invokestatic(B,"drawerQueued",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));}
                        if(owner.equals("zombie/core/opengl/RenderThread")&&name.equals("lockStepRenderStep")){
                            String helper=called.equals("acquireStateForRendering")?"stepAcquire":called.equals("postRender")?"stepPost":target.equals("org/lwjglx/opengl/Display")&&called.equals("update")?"stepDisplay":called.equals("profile")?"stepProfile":called.equals("close")?"stepProfileClose":null;
                            if(helper!=null){String hd=desc;if(!helper.equals("stepDisplay"))hd="(L"+target+";"+desc.substring(1);b.invokestatic(B,helper,MethodTypeDesc.ofDescriptor(hd));return;}
                        }
                        if(target.equals("java/lang/Object")&&called.equals("wait")&&Set.of("zombie/core/SpriteRenderer","zombie/core/opengl/RenderContextQueueItem").contains(owner)&&Set.of("()V","(J)V","(JI)V").contains(desc)){b.invokestatic(B,"waitObject",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;"+desc.substring(1)));return;}
                        if(target.equals("java/lang/Thread")&&Set.of("org/lwjglx/opengl/Sync","zombie/core/opengl/RenderThread","zombie/GameWindow").contains(owner)){
                            if(called.equals("sleep")&&desc.equals("(J)V")){b.invokestatic(B,"sleep",i.typeSymbol());return;}
                            if(called.equals("yield")&&desc.equals("()V")){b.invokestatic(B,"yieldThread",i.typeSymbol());return;}
                        }
                    }
                    b.with(e);
                }
                public void atEnd(CodeBuilder b){if(token<0)return;b.labelBinding(end);b.exceptionCatchAll(start,end,handler);b.labelBinding(handler);error=b.allocateLocal(TypeKind.REFERENCE);b.astore(error);b.lload(token);b.invokestatic(B,"exceptional",MethodTypeDesc.ofDescriptor("(J)V"));b.aload(error);b.athrow();}
            }));
        });
    }
}
