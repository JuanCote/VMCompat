package vmcompat;
import java.lang.instrument.*;
public class TransformCoverageTests {
 public static void main(String[] args)throws Exception{
  var f=Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");f.setAccessible(true);Instrumentation inst=(Instrumentation)f.get(null);
  ClassFileTransformer nested=new ClassFileTransformer(){public byte[] transform(ClassLoader l,String n,Class<?> c,java.security.ProtectionDomain p,byte[] b){if("workshoptests/RecursiveTrigger".equals(n))try{Class.forName("zombie.compatfixture.RecursiveChild",false,l);}catch(Exception e){throw new RuntimeException(e);}return null;}};
  inst.addTransformer(nested,true);
  try{Class.forName("workshoptests.RecursiveTrigger");}finally{inst.removeTransformer(nested);}
  Class<?> child=Class.forName("zombie.compatfixture.RecursiveChild",false,ClassLoader.getSystemClassLoader());
  if(StartupTransforms.applied(child))throw new AssertionError("Fixture did not reproduce recursive callback suppression");
  int repaired=StartupTransforms.repair(inst);
  if(!StartupTransforms.applied(child)||repaired<1)throw new AssertionError("Uninstrumented target not repaired");
  child.getMethod("barrier").invoke(null); // no context needed: actual compatibility no-op, native entry would abort.
  if(StartupTransforms.repair(inst)!=0)throw new AssertionError("Repeated coverage repair is not idempotent");
  System.out.println("PASS real JVM recursive callback gap repaired; formerly unsafe entry uses actual adapter");
 }
}
