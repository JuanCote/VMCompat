package vmcompat;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.lang.instrument.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.*;

/** Compatibility operations plus swap intervals and existing Viewpoint CPU profile snapshots. */
public final class Transformer implements ClassFileTransformer{
    private static final ClassFile CF=ClassFileSupport.create();
    private static final Map<String,ClassDesc> CALLS=new HashMap<>();
    static{
        for(Class<?> owner:new Class<?>[]{ModernGl.class,StorageBuffers.class,StartupGl.class,GpuWrites.class,Legacy.class,Gl41.class,Textures.class,Buffers.class,Draws.class,Samplers.class,Shaders.class})
            for(var m:owner.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())&&(m.getName().startsWith("gl")||m.getName().startsWith("ngl")))
                CALLS.put(m.getName()+MethodType.methodType(m.getReturnType(),m.getParameterTypes()).descriptorString(),ClassDesc.of(owner.getName()));
    }
    public static boolean handles(String name,String descriptor){return CALLS.containsKey(name+descriptor);}
    public byte[] transform(ClassLoader loader,String name,Class<?> redef,ProtectionDomain domain,byte[] bytes){
        if(name==null||!(OldPointCompat.TARGET.equals(name)||name.startsWith("zombie/")||name.startsWith("viewpoint/")||name.startsWith("org/lwjglx/")||name.startsWith("imgui/gl3/")||ImguiBootstrap.OWNED.equals(name)))return null;
        try(ClassFileSupport.Scope scope=ClassFileSupport.resources(loader)){byte[] result=rewrite(name,bytes);StartupTransforms.applied(loader,name);if(java.util.Set.of("imgui/gl3/ImGuiImplGl3",ImguiBootstrap.OWNED,"viewpoint/platform/ImGuiFrame","zombie/core/rendering/RenderList$ArrayTexture").contains(name))Log.info("Startup compatibility transformed: "+name+" / redefine="+(redef!=null));return result;}catch(Throwable error){Log.failure("Cannot transform "+name,error);return new byte[]{0};}
    }
    public static byte[] rewrite(String name,byte[] bytes){if(OldPointCompat.TARGET.equals(name))return OldPointCompat.rewrite(bytes);
        bytes=ImguiBootstrap.rewrite(name,bytes);if(name.startsWith("imgui/gl3/")&&ImguiBootstrap.NATIVE)return DiagnosticTransform.rewrite(name,bytes);bytes=PoseTransform.rewrite(name,FarTransform.rewrite(name,bytes));
        boolean nativePick=name.equals("viewpoint/render/MousePick")&&CF.parse(bytes).methods().stream().anyMatch(m->m.methodName().equalsString("copied"));boolean vp=name.startsWith("viewpoint/");
        var parsed=CF.parse(bytes);
        // Discover producers from actual calls, rather than a hard-coded partial list of class names.
        boolean timestampProducer=parsed.methods().stream().flatMap(m->m.code().stream()).flatMap(c->c.elementStream()).anyMatch(e->e instanceof InvokeInstruction i&&i.owner().asInternalName().startsWith("org/lwjgl/opengl/")&&i.name().stringValue().startsWith("glQueryCounter"));
        byte[] result=CF.transformClass(CF.parse(bytes),(cb,element)->{
            if(!(element instanceof MethodModel methodModel)){cb.with(element);return;}
            if(name.equals("viewpoint/platform/FrameTimes")&&Set.of("renderStarted","renderEnded").contains(methodModel.methodName().stringValue())){
                String method=methodModel.methodName().stringValue();cb.withMethodBody(method,methodModel.methodTypeSymbol(),methodModel.flags().flagsMask(),b->{b.getstatic(ClassDesc.of("viewpoint.platform.FrameTimes"),"count",ConstantDescs.CD_long);if(method.equals("renderStarted"))b.getstatic(ClassDesc.of("viewpoint.platform.FrameTimes"),"gpuMs",ClassDesc.ofDescriptor("[F"));b.invokestatic(ClassDesc.of("vmcompat.FrameTimesGpu"),method.equals("renderStarted")?"begin":"end",MethodTypeDesc.ofDescriptor(method.equals("renderStarted")?"(J[F)Ljava/lang/Boolean;":"(J)Ljava/lang/Boolean;"));b.putstatic(ClassDesc.of("viewpoint.platform.FrameTimes"),"gpuTimed",ClassDesc.of("java.lang.Boolean"));b.return_();});return;
            }
            String methodName=methodModel.methodName().stringValue();
            String scope=switch(name+"."+methodName){case "viewpoint/render/SurfacePass.gbuffer"->"GB_SURFACES_NS";case "viewpoint/render/ModelPass.gbuffer"->"GB_MODELS_NS";case "viewpoint/render/FarPass.gbuffer"->"GB_FAR_BAND_NS";case "viewpoint/render/SurfacePass.translucent"->"GLASS_SURFACES_NS";case "viewpoint/render/ModelPass.glass"->"GLASS_MODELS_NS";case "viewpoint/render/FlamePass.draw"->"GLASS_FLAMES_NS";case "viewpoint/render/TranslucentPass.draw"->"GLASS_TOTAL_NS";case "viewpoint/render/FarPass.draw"->"FAR_DRAW_NS";case "viewpoint/render/MousePick.read"->"PICK_ENQUEUE_NS";case "viewpoint/render/MousePick.collect"->"PICK_COLLECT_NS";default->null;};
            cb.transformMethod(methodModel,MethodTransform.transformingCode(new CodeTransform(){
            int scopeLocal=-1,scopeChild=-1,previousPass=-1;boolean pickingGate=false;
            @Override public void atStart(CodeBuilder b){if(nativePick&&methodName.equals("copied")){b.iload(0);b.iload(1);b.iload(2);b.invokestatic(ClassDesc.of("vmcompat.NativePickBoundary"),"source",MethodTypeDesc.ofDescriptor("(III)V"));}
                if(name.equals("viewpoint/render/TranslucentPass")&&methodName.equals("draw"))b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"resetResolve",MethodTypeDesc.ofDescriptor("()V"));
                if(name.equals("viewpoint/render/MousePick")&&methodName.equals("read")){b.aload(0);b.iload(1);b.aload(2);b.invokestatic(ClassDesc.of("vmcompat.Picking"),"begin",MethodTypeDesc.ofDescriptor("(Lviewpoint/render/FrameContext;ILorg/joml/Matrix4f;)V"));}
                if(false&&name.equals("viewpoint/render/WorldRenderer")&&Set.of("drawWorld","drawTranslucent","drawWeather","finish").contains(methodName)&&methodModel.methodType().stringValue().endsWith(")V")){var go=b.newLabel();b.invokestatic(ClassDesc.of("vmcompat.Opaque"),"failed",MethodTypeDesc.ofDescriptor("()Z"));b.ifeq(go);b.invokestatic(ClassDesc.of("vmcompat.Opaque"),"restoreOutput",MethodTypeDesc.ofDescriptor("()V"));b.return_();b.labelBinding(go);if(methodName.equals("drawWorld"))b.invokestatic(ClassDesc.of("vmcompat.Opaque"),"worldBegin",MethodTypeDesc.ofDescriptor("()V"));}
                if(scope!=null){int tag=switch(scope){case "GB_SURFACES_NS"->1;case "GB_MODELS_NS"->2;case "GB_FAR_BAND_NS"->3;case "GLASS_SURFACES_NS"->4;case "GLASS_MODELS_NS"->5;case "GLASS_FLAMES_NS"->6;case "FAR_DRAW_NS"->8;default->0;};if(tag!=0){previousPass=b.allocateLocal(TypeKind.INT);b.loadConstant(tag);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"scope",MethodTypeDesc.ofDescriptor("(I)I"));b.istore(previousPass);}scopeLocal=b.allocateLocal(TypeKind.LONG);b.invokestatic(ClassDesc.of("vmcompat.Counters"),"now",MethodTypeDesc.ofDescriptor("()J"));b.lstore(scopeLocal);if(scope.equals("PICK_ENQUEUE_NS")){scopeChild=b.allocateLocal(TypeKind.LONG);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"collected",MethodTypeDesc.ofDescriptor("()J"));b.lstore(scopeChild);}}
                if(name.equals("viewpoint/platform/Profile")&&methodName.equals("endFrame")&&methodModel.methodType().stringValue().equals("()V")){
                    b.getstatic(ClassDesc.of("viewpoint.platform.Profile"),"frameCpuMs",ClassDesc.ofDescriptor("[D"));
                    b.getstatic(ClassDesc.of("viewpoint.platform.Profile"),"draws",ConstantDescs.CD_int);
                    b.getstatic(ClassDesc.of("viewpoint.platform.Profile"),"meshDraws",ConstantDescs.CD_int);
                    b.invokestatic(ClassDesc.of("vmcompat.Telemetry"),"world",MethodTypeDesc.ofDescriptor("([DII)V"));
                }
            }
            @Override public void atEnd(CodeBuilder b){if(name.equals("viewpoint/render/MousePick")&&methodName.equals("read")&&!pickingGate)throw new IllegalStateException("MousePick.read eligibility anchor changed; refusing an unguarded picking path");}
            @Override public void accept(CodeBuilder b,CodeElement e){
            if(name.equals("viewpoint/platform/FrameTimes")&&methodName.equals("prepare")&&e instanceof FieldInstruction f&&f.opcode()==Opcode.PUTSTATIC&&f.name().equalsString("gpuTimed")){b.invokestatic(ClassDesc.of("vmcompat.TimestampQueries"),"allowed",MethodTypeDesc.ofDescriptor("(Ljava/lang/Boolean;)Ljava/lang/Boolean;"));}
            // The first FrameContext.width load follows original collect, Ask and free-fence checks.
            // Its object reference is already on the stack; remove it only on the early-return path.
            if(!pickingGate&&name.equals("viewpoint/render/MousePick")&&methodName.equals("read")&&e instanceof FieldInstruction field&&field.opcode()==Opcode.GETFIELD&&field.owner().asInternalName().equals("viewpoint/render/FrameContext")&&field.name().equalsString("width")){
                pickingGate=true;var allowed=b.newLabel();b.invokestatic(ClassDesc.of(nativePick?"vmcompat.NativePickBoundary":"vmcompat.Picking"),nativePick?"allow":"allowRequest",MethodTypeDesc.ofDescriptor("()Z"));b.ifne(allowed);b.pop();
                b.invokestatic(ClassDesc.of("vmcompat.Picking"),"end",MethodTypeDesc.ofDescriptor("()V"));b.lload(scopeLocal);b.lload(scopeChild);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"finishRead",MethodTypeDesc.ofDescriptor("(JJ)V"));b.return_();b.labelBinding(allowed);
            }
            if(e instanceof InvokeInstruction call&&name.equals("viewpoint/render/TranslucentPass")&&methodName.equals("draw")&&call.owner().asInternalName().equals("viewpoint/render/FlamePass")&&call.name().equalsString("draw")){b.with(e);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"beginResolve",MethodTypeDesc.ofDescriptor("()V"));return;}
            if(e instanceof ReturnInstruction&&name.equals("viewpoint/render/TranslucentPass")&&methodName.equals("draw"))b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"endResolve",MethodTypeDesc.ofDescriptor("()V"));
            if(e instanceof ReturnInstruction&&name.equals("viewpoint/render/WorldRenderer")&&methodName.equals("drawWorld"))b.invokestatic(ClassDesc.of("vmcompat.Opaque"),"worldEnd",MethodTypeDesc.ofDescriptor("()V"));
            if(e instanceof ReturnInstruction && name.equals("viewpoint/render/MousePick")&&methodName.equals("read"))b.invokestatic(ClassDesc.of("vmcompat.Picking"),"end",MethodTypeDesc.ofDescriptor("()V"));
            if(e instanceof ReturnInstruction&&previousPass>=0){b.iload(previousPass);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"scope",MethodTypeDesc.ofDescriptor("(I)I"));b.pop();}
            if(e instanceof ReturnInstruction && scope!=null){if(scopeChild>=0){b.lload(scopeLocal);b.lload(scopeChild);b.invokestatic(ClassDesc.of("vmcompat.PassTiming"),"finishRead",MethodTypeDesc.ofDescriptor("(JJ)V"));}else{b.getstatic(ClassDesc.of("vmcompat.Counters$Key"),scope,ClassDesc.of("vmcompat.Counters$Key"));b.lload(scopeLocal);b.invokestatic(ClassDesc.of("vmcompat.Counters"),"elapsed",MethodTypeDesc.ofDescriptor("(Lvmcompat/Counters$Key;J)V"));}}
            if(e instanceof ReturnInstruction && name.equals("zombie/core/opengl/ShaderUnit") && methodName.equals("<clinit>")){
                b.iconst_1();b.putstatic(ClassDesc.of("zombie.core.opengl.ShaderUnit"),"combineShaderSources",ConstantDescs.CD_boolean);
            }
            if(e instanceof InvokeInstruction i && i.opcode()==Opcode.INVOKESTATIC){
                String owner=i.owner().asInternalName(),method=i.name().stringValue(),desc=i.type().stringValue();
                if(name.equals("viewpoint/render/SurfacePass")&&methodName.equals("gbuffer")&&owner.equals("viewpoint/render/Meshes")&&method.equals("drawPlanned")){b.invokestatic(ClassDesc.of("vmcompat.Opaque"),"planned",i.typeSymbol());return;}
                if(owner.startsWith("org/lwjgl/opengl/")){
                    if(name.equals("viewpoint/render/MousePick")){
                        String wrapper=switch(method+desc){case "glBindFramebuffer(II)V"->"bindFramebuffer";case "glBindBuffer(II)V"->"bindBuffer";case "glReadPixels(IIIIIIJ)V"->"readPixels";case "glFenceSync(II)J"->"fence";case "glClientWaitSync(JIJ)I"->"poll";case "glGetBufferSubData(IJLjava/nio/FloatBuffer;)V"->"collect";case "glBufferData(IJI)V"->"allocate";default->null;};if(wrapper!=null){b.invokestatic(ClassDesc.of("vmcompat.Picking"),wrapper,i.typeSymbol());return;}
                    }
                    if(method.equals("glShaderSource")&&desc.equals("(ILjava/lang/CharSequence;)V")){
                        b.invokestatic(ClassDesc.of("vmcompat.Shaders"),vp?"vpSource":name.startsWith("imgui/")?"coreSource":"gameSource",i.typeSymbol());return;
                    }
                    if(owner.equals("org/lwjgl/opengl/GL")&&method.equals("createCapabilities")){b.invokestatic(ClassDesc.of("vmcompat.Context"),"capabilities",i.typeSymbol());return;}
                    if(timestampProducer&&TimestampQueries.method(method,desc)!=null){b.invokestatic(ClassDesc.of("vmcompat.TimestampQueries"),TimestampQueries.method(method,desc),i.typeSymbol());return;}
                    if(owner.equals("org/lwjgl/opengl/ARBVertexBufferObject"))method=method.replaceFirst("ARB$","");
                    ClassDesc target=CALLS.get(method+desc);
                    if(target!=null){b.invokestatic(target,method,i.typeSymbol());return;}
                    if(owner.equals("org/lwjgl/opengl/ARBVertexBufferObject")){b.invokestatic(ClassDesc.of("org.lwjgl.opengl.GL15"),method,i.typeSymbol());return;}
                }
                if(owner.equals("org/lwjgl/glfw/GLFW")&&method.equals("glfwMakeContextCurrent")){b.invokestatic(ClassDesc.of("vmcompat.Context"),"makeCurrent",i.typeSymbol());return;}
                if(owner.equals("org/lwjgl/glfw/GLFW")&&method.equals("glfwDestroyWindow")){b.invokestatic(ClassDesc.of("vmcompat.Context"),"destroyWindow",i.typeSymbol());return;}
                if(owner.equals("org/lwjgl/glfw/GLFW")&&method.equals("glfwSwapBuffers")){b.invokestatic(ClassDesc.of("vmcompat.Telemetry"),"swap",i.typeSymbol());return;}
                if(owner.equals("org/lwjgl/glfw/GLFW")&&method.equals("glfwCreateWindow")){b.invokestatic(ClassDesc.of("vmcompat.Context"),"createWindow",i.typeSymbol());return;}
            }
            if(e instanceof InvokeInstruction i&&vp&&i.owner().asInternalName().equals("zombie/core/textures/Texture")&&i.name().stringValue().equals("bind")&&i.type().stringValue().equals("()V")){
                b.invokestatic(ClassDesc.of("vmcompat.Samplers"),"bindHostTexture",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));return;
            }
            if(name.equals("viewpoint/platform/FrameTimes")&&methodName.equals("swapped")&&e instanceof ConstantInstruction fc&&fc.opcode()==Opcode.FCONST_0){b.loadConstant(-1f);return;}
            if(e instanceof ConstantInstruction c&&c.constantValue() instanceof Integer n){
                // Keep sampler arrays within Apple's per-stage limits. Geometry is split across more draws.
                if(name.equals("viewpoint/render/AtlasGroups")&&(methodName.equals("samplers")||methodName.equals("size"))&&n==8){b.aload(0).loadConstant("uAtlases[3]").invokevirtual(ClassDesc.of("viewpoint.platform.GlProgram"),"has",MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z")).invokestatic(ClassDesc.of("vmcompat.NativeSamplers"),"atlasSlots",MethodTypeDesc.ofDescriptor("(Z)I"));return;}
                if(name.equals("viewpoint/render/ModelBatches")&&methodName.equals("slot")&&n==16){b.loadConstant(6);return;}
            }
            b.with(e);
            }}));
        });
        return NativePickTransform.protect(name,DiagnosticTransform.rewrite(name,result));
    }
}
