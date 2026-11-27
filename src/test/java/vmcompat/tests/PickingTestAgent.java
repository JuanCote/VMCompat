package vmcompat.tests;
import java.lang.classfile.*;import java.lang.classfile.instruction.*;import java.lang.constant.*;import java.lang.instrument.*;import java.security.ProtectionDomain;
/** Fixture-only fence readiness control. Never bundled in the installed mod. */
public final class PickingTestAgent {
    public static void premain(String args,Instrumentation instrumentation){instrumentation.addTransformer(new ClassFileTransformer(){
        public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
            if(!"viewpoint/render/MousePick".equals(name))return null;
            return ClassFile.of().transformClass(ClassFile.of().parse(bytes),ClassTransform.transformingMethodBodies((b,e)->{
                if(e instanceof InvokeInstruction i&&i.owner().asInternalName().equals("vmcompat/Picking")&&i.name().equalsString("poll"))b.invokestatic(ClassDesc.of("vmcompat.tests.OpaqueTests"),"testPoll",i.typeSymbol());else b.with(e);
            }));
        }
    });}
}
