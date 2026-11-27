package vmcompat;
import java.lang.classfile.*;import java.lang.constant.*;
/** Ensure the request boundary closes even if Viewpoint's copy/read throws. */
public final class NativePickTransform {
 public static byte[] protect(String name,byte[] bytes){if(!name.equals("viewpoint/render/MousePick"))return bytes;ClassFile cf=ClassFileSupport.create();var model=cf.parse(bytes);if(model.methods().stream().noneMatch(m->m.methodName().equalsString("copied")))return bytes;
  return cf.transformClass(model,(cb,e)->{if(!(e instanceof MethodModel m)||!m.methodName().equalsString("read")||m.code().isEmpty()){cb.with(e);return;}cb.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){Label start,end,handler;public void atStart(CodeBuilder b){start=b.newLabel();end=b.newLabel();handler=b.newLabel();b.labelBinding(start);}public void accept(CodeBuilder b,CodeElement e){b.with(e);}public void atEnd(CodeBuilder b){b.labelBinding(end);b.exceptionCatchAll(start,end,handler);b.labelBinding(handler);int error=b.allocateLocal(TypeKind.REFERENCE);b.astore(error);b.invokestatic(ClassDesc.of("vmcompat.NativePickBoundary"),"end",MethodTypeDesc.ofDescriptor("()V"));b.aload(error);b.athrow();}}));});
 }
 private NativePickTransform(){}
}
