package vmcompat;
import java.lang.classfile.*;import java.lang.classfile.instruction.*;import java.lang.constant.*;
/** Typed, fail-closed hooks into the pinned ShellArena and mask callsites. */
public final class FarTransform {
 static final ClassFile CF=ClassFileSupport.create();static final ClassDesc F=ClassDesc.of("vmcompat.FarShell"),A=ClassDesc.of("viewpoint.render.ShellArena"),L=ClassDesc.of("viewpoint.render.ShellArena$IntList"),P=ClassDesc.of("viewpoint.render.ShellArena$Page");
 static void field(CodeBuilder b,String n,String desc){b.getstatic(A,n,ClassDesc.ofDescriptor(desc));}
 public static byte[] rewrite(String owner,byte[] bytes){if(!owner.equals("viewpoint/render/ShellArena")&&!owner.equals("viewpoint/render/ShellArena$Page")&&!owner.equals("viewpoint/render/FarPass"))return bytes;
 if(owner.startsWith("viewpoint/render/ShellArena")&&new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("glMultiDrawElementsIndirect")){if(Options.FAR_BATCH)throw new UnsupportedOperationException("Indexed ShellArena requires a separately qualified batch adapter");return bytes;}if(owner.equals("viewpoint/render/ShellArena$Page")&&!Options.FAR_BATCH)return bytes;return CF.transformClass(CF.parse(bytes),(cb,e)->{if(!(e instanceof MethodModel m)||m.code().isEmpty()){cb.with(e);return;}String name=m.methodName().stringValue();boolean issue=owner.equals("viewpoint/render/ShellArena")&&name.equals("issue");cb.transformMethod(m,MethodTransform.transformingCode(new CodeTransform(){Label start,end,handler;int calls;
 public void atStart(CodeBuilder b){if(issue){field(b,"records","Ljava/nio/FloatBuffer;");field(b,"recordCount","I");field(b,"commands","Ljava/nio/IntBuffer;");field(b,"instances","Ljava/nio/IntBuffer;");field(b,"spans","Lviewpoint/render/ShellArena$IntList;");b.getfield(L,"a",ClassDesc.ofDescriptor("[I"));field(b,"spans","Lviewpoint/render/ShellArena$IntList;");b.getfield(L,"n",ConstantDescs.CD_int);field(b,"commandsAt","J");b.invokestatic(F,"begin",MethodTypeDesc.ofDescriptor("(Ljava/nio/FloatBuffer;ILjava/nio/IntBuffer;Ljava/nio/IntBuffer;[IIJ)V"));start=b.newLabel();end=b.newLabel();handler=b.newLabel();b.labelBinding(start);}
 if(owner.equals("viewpoint/render/ShellArena")&&name.equals("letGo")){b.aload(0);b.getfield(P,"slot",ConstantDescs.CD_int);b.invokestatic(F,"remove",MethodTypeDesc.ofDescriptor("(I)V"));}
 if(owner.equals("viewpoint/render/ShellArena")&&name.equals("free")){b.iload(0);b.iload(1);field(b,"pageVertices","I");b.invokestatic(F,"free",MethodTypeDesc.ofDescriptor("(III)V"));}}
 public void accept(CodeBuilder b,CodeElement e){if(e instanceof ReturnInstruction){if(owner.equals("viewpoint/render/ShellArena$Page")&&name.equals("<init>")){b.iload(1);b.iload(2);b.iload(3);b.invokestatic(F,"page",MethodTypeDesc.ofDescriptor("(III)V"));}if(issue)b.invokestatic(F,"end",MethodTypeDesc.ofDescriptor("()V"));}
 if(e instanceof InvokeInstruction i&&i.owner().asInternalName().startsWith("org/lwjgl/opengl/")){
  String called=i.name().stringValue(),desc=i.type().stringValue();
  if(owner.equals("viewpoint/render/FarPass")&&name.equals("uploadMask")&&called.equals("glTexSubImage2D")&&desc.equals("(IIIIIIIILjava/nio/ByteBuffer;)V")){b.aload(0);b.invokestatic(ClassDesc.of("vmcompat.FarMasks"),"upload",MethodTypeDesc.ofDescriptor("(IIIIIIIILjava/nio/ByteBuffer;Ljava/lang/Object;)V"));return;}
  if(owner.equals("viewpoint/render/ShellArena")&&name.equals("upload")&&called.equals("glBufferSubData")&&desc.equals("(IJLjava/nio/ByteBuffer;)V")){b.invokestatic(F,"uploadGeometry",i.typeSymbol());return;}
  if(issue&&called.equals("glMultiDrawArraysIndirect")&&desc.equals("(IJII)V")){calls++;b.iload(0);b.iconst_5();b.idiv();field(b,"recordTexture","I");b.invokestatic(F,"draw",MethodTypeDesc.ofDescriptor("(IJIIII)V"));return;}
 }b.with(e);}
 public void atEnd(CodeBuilder b){if(!issue)return;if(calls!=1)throw new IllegalStateException("ShellArena.issue anchor changed");b.labelBinding(end);b.exceptionCatchAll(start,end,handler);b.labelBinding(handler);int ex=b.allocateLocal(TypeKind.REFERENCE);b.astore(ex);b.invokestatic(F,"end",MethodTypeDesc.ofDescriptor("()V"));b.aload(ex);b.athrow();}
 }));});}
}
