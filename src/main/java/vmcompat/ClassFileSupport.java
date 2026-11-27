package vmcompat;
import java.lang.classfile.*;

/** Never define a dependency while generating stack maps inside a transformer.
 * The JVM suppresses recursive transform callbacks for such definitions.
 */
public final class ClassFileSupport {
    private static final ThreadLocal<ClassLoader> RESOURCES=new ThreadLocal<>();
    public static ClassFile create(){
        var resolver=ClassHierarchyResolver.ofResourceParsing(type->{
            String d=type.descriptorString();if(!d.startsWith("L"))return null;
            String resource=d.substring(1,d.length()-1)+".class";
            ClassLoader loader=RESOURCES.get();if(loader==null)loader=ClassFileSupport.class.getClassLoader();
            return loader.getResourceAsStream(resource);
        });
        return ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver));
    }
    public static Scope resources(ClassLoader loader){return new Scope(loader);}
    public static final class Scope implements AutoCloseable {
        private final ClassLoader previous=RESOURCES.get();
        private Scope(ClassLoader loader){if(loader==null)RESOURCES.remove();else RESOURCES.set(loader);}
        public void close(){if(previous==null)RESOURCES.remove();else RESOURCES.set(previous);}
    }
    private ClassFileSupport(){}
}
