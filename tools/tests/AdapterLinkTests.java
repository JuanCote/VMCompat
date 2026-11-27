package vmcompat;
import java.lang.classfile.*;import java.lang.classfile.instruction.*;import java.nio.file.*;import java.util.*;import java.util.jar.*;
/** Resolve every injected helper call against this artifact without initializing game classes. */
public final class AdapterLinkTests {
 public static void main(String[]args)throws Exception{
  Map<String,Set<String>> methods=new HashMap<>();try(JarFile own=new JarFile(Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toFile())){for(var e:Collections.list(own.entries()))if(e.getName().endsWith(".class")){var c=ClassFile.of().parse(own.getInputStream(e).readAllBytes());Set<String> signatures=new HashSet<>();for(var m:c.methods())signatures.add(m.methodName().stringValue()+m.methodType().stringValue());methods.put(c.thisClass().asInternalName(),signatures);}}
  int classes=0,calls=0;for(String arg:args)try(JarFile jar=new JarFile(arg)){for(var e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")&&WorkshopBootstrap.target(e.getName().replace('/','.'))){String name=e.getName().substring(0,e.getName().length()-6);byte[] out=Transformer.rewrite(name,jar.getInputStream(e).readAllBytes());var c=ClassFile.of().parse(out);classes++;for(var m:c.methods())if(m.code().isPresent())for(var op:m.code().get())if(op instanceof InvokeInstruction i&&i.owner().asInternalName().startsWith("vmcompat/")){if(!methods.getOrDefault(i.owner().asInternalName(),Set.of()).contains(i.name().stringValue()+i.type().stringValue()))throw new AssertionError(name+" -> missing "+i.owner().asInternalName()+"."+i.name()+i.type());calls++;}}}
  System.out.println("PASS "+classes+" class files, "+calls+" injected helper references resolve without game initialization");
 }
}
