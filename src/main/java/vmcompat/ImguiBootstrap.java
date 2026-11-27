package vmcompat;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;

/** Existing core shaders plus an explicit state boundary around Buddy's loading UI. */
public final class ImguiBootstrap {
    public static final boolean NATIVE=Boolean.parseBoolean(Options.value("nativeImGui","true"));
    public static final String OWNED="me/zed_0xff/zombie_buddy/frontend/ImguiModApprovalFrontend$OwnedImguiContext";
    public static byte[] rewrite(String name,byte[] bytes) {
        boolean backend=name.equals("imgui/gl3/ImGuiImplGl3"),owned=name.equals(OWNED),view=name.equals("viewpoint/platform/ImGuiFrame");
        if(!backend&&!owned&&!view)return bytes;
        ClassFile cf=ClassFileSupport.create();
        return cf.transformClass(cf.parse(bytes),(builder,element)->{
            if(!(element instanceof MethodModel m)||m.code().isEmpty()){builder.with(element);return;}
            if(backend&&m.methodName().equalsString("readGlVersion")&&m.methodType().equalsString("()V")){
                // The game's bundled backend hardcodes 210 on Cocoa. Our context is core 4.1;
                // that stale value disables its private VAO and base-vertex support.
                builder.withMethodBody(m.methodName().stringValue(),m.methodTypeSymbol(),m.flags().flagsMask(),b->{
                    b.aload(0);b.loadConstant(33307);b.invokestatic(ClassDesc.of("org.lwjgl.opengl.GL11"),"glGetInteger",MethodTypeDesc.of(ConstantDescs.CD_int,ConstantDescs.CD_int));b.loadConstant(100);b.imul();
                    b.loadConstant(33308);b.invokestatic(ClassDesc.of("org.lwjgl.opengl.GL11"),"glGetInteger",MethodTypeDesc.of(ConstantDescs.CD_int,ConstantDescs.CD_int));b.loadConstant(10);b.imul();b.iadd();
                    b.putfield(ClassDesc.of("imgui.gl3.ImGuiImplGl3"),"glVersion",ConstantDescs.CD_int);b.return_();
                });return;
            }
            boolean init=backend&&m.methodName().equalsString("init")&&m.methodType().equalsString("(Ljava/lang/String;)V");
            boolean ui=backend&&m.methodName().equalsString("renderDrawData");
            boolean guarded=(owned&&m.methodName().equalsString("render"))||ui;
            ClassDesc state=ClassDesc.of(ui?"vmcompat.ImGuiSamplerGuard":"vmcompat.ApprovalDrawState");
            builder.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){
                int token=-1;Label start,end,handler;
                public void atStart(CodeBuilder b){
                    if(init){b.loadConstant("#version 410 core");b.astore(1);}
                    if(guarded){token=b.allocateLocal(TypeKind.REFERENCE);if(ui){b.aload(0);b.aload(1);b.invokestatic(state,"begin",MethodTypeDesc.of(state,ConstantDescs.CD_Object,ClassDesc.of("imgui.ImDrawData")));}else b.invokestatic(state,"begin",MethodTypeDesc.of(state));b.astore(token);start=b.newLabel();end=b.newLabel();handler=b.newLabel();b.labelBinding(start);}
                }
                void close(CodeBuilder b){b.aload(token);b.invokevirtual(state,"close",MethodTypeDesc.of(ConstantDescs.CD_void));}
                public void accept(CodeBuilder b,CodeElement e){
                    if((owned||view)&&e instanceof ConstantInstruction c&&c.constantValue() instanceof String s&&s.startsWith("#version ")){b.loadConstant("#version 410 core");return;}
                    if(guarded&&e instanceof ReturnInstruction)close(b);
                    b.with(e);
                }
                public void atEnd(CodeBuilder b){if(!guarded)return;b.labelBinding(end);b.exceptionCatchAll(start,end,handler);b.labelBinding(handler);int error=b.allocateLocal(TypeKind.REFERENCE);b.astore(error);close(b);b.aload(error);b.athrow();}
            }));
        });
    }
}
