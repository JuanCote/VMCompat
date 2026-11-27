package vmcompat.tests;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import vmcompat.Transformer;

public final class BytecodeAudit {
    public static void main(String[] args)throws Exception{
        ClassFile cf=ClassFile.of();int checked=0,changed=0;Set<String> unsupported=new TreeSet<>();
        try(JarFile jar=new JarFile(args[2])){for(var e:Collections.list(jar.entries()))if(e.getName().endsWith(".class")&&!e.getName().startsWith("vmcompat/"))throw new AssertionError("Unexpected bundled class: "+e.getName());}
        for(int file=0;file<2;file++)try(JarFile jar=new JarFile(args[file])){
            for(var e:Collections.list(jar.entries())){
                String name=e.getName();if(!name.endsWith(".class")||!(name.startsWith("zombie/")||name.startsWith("viewpoint/")||name.startsWith("org/lwjglx/")||name.startsWith("imgui/gl3/")))continue;
                byte[] original=jar.getInputStream(e).readAllBytes();byte[] rewritten=Transformer.rewrite(name.substring(0,name.length()-6),original);
                checked++;if(!Arrays.equals(original,rewritten)){changed++;var errors=cf.verify(rewritten);if(!errors.isEmpty())throw new AssertionError(name+": "+errors.getFirst());}
                for(var method:cf.parse(rewritten).methods())if(method.code().isPresent())for(var ins:method.code().get())if(ins instanceof InvokeInstruction invoke){String owner=invoke.owner().asInternalName(),called=invoke.name().stringValue();if((name.startsWith("viewpoint/")&&owner.matches("org/lwjgl/opengl/GL4[2-6]C?"))||(owner.startsWith("org/lwjgl/opengl/")&&(called.matches("gl(?:Multi)?Draw(?:Arrays|Elements|Range).*")||called.matches("(?:n?gl)(?:Named)?(?:Buffer(?:Data|SubData|Storage)|CopyBufferSubData|MapBuffer(?:Range)?|UnmapBuffer|FlushMappedBufferRange|BeginTransformFeedback|EndTransformFeedback|ReadPixels|GetTexImage)"))))unsupported.add(name+": "+owner+"."+called+invoke.type().stringValue());}
            }
        }
        System.out.println("Bytecode checked: "+checked+" classes; transformed: "+changed);
        if(!unsupported.isEmpty())throw new AssertionError("Unadapted modern calls: "+unsupported);
        System.out.println("No remaining direct draw dispatches or Viewpoint OpenGL 4.2+ call sites; artifact contains only own vmcompat classes.");
    }
}
