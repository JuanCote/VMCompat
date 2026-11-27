package vmcompat;
import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
/** Qualified expanded shaders, not a structural/textual guess about an unknown pack. */
public final class FarShaders {
 static final Properties HASHES=new Properties();static {try(var in=FarShaders.class.getResourceAsStream("/vmcompat/far/hashes.properties")){if(in!=null)HASHES.load(in);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean patch(Map<Integer,String> sources,Map<Integer,Boolean> vertices){if(!Options.FAR_BATCH||sources.size()!=2)return false;Integer v=null;boolean fragment=false;for(var e:sources.entrySet())if(vertices.getOrDefault(e.getKey(),false)){if(hash(e.getValue()).equals(HASHES.getProperty("vertex")))v=e.getKey();}else if(Set.of(HASHES.getProperty("band",""),HASHES.getProperty("far","")).contains(hash(e.getValue())))fragment=true;
  if(v==null||!fragment)return false;String text=sources.get(v);if(!text.contains("layout(location = 6) in int aDraw;")||!text.contains("void main() {"))return false;
  sources.put(v,text.replace("layout(location = 6) in int aDraw;","layout(location = 6) in int vmDrawSource;\nuniform int vmFarBatch;\nuniform int vmOwnerBase;").replace("void main() {","void main() {\nint aDraw=vmDrawSource;\nif(vmFarBatch!=0){vec4 vmMeta=texelFetch(uShellRecords,vmOwnerBase+vmDrawSource);aDraw=int(vmMeta.x)|(int(vmMeta.y)<<24);}\n"));return true;
 }
 record Qualification(long revision,boolean supported,int mode,int base,int sampler){}
 static final StateStore<Map<Integer,Qualification>> PROGRAMS=new StateStore<>(HashMap::new);
 static Qualification qualification(int p){var map=PROGRAMS.get();long rev=ContextResources.programRevision(p);Qualification q=map.get(p);if(q!=null&&q.revision==rev)return q;boolean supported=uncached(p);q=new Qualification(rev,supported,supported?org.lwjgl.opengl.GL20.glGetUniformLocation(p,"vmFarBatch"):-1,supported?org.lwjgl.opengl.GL20.glGetUniformLocation(p,"vmOwnerBase"):-1,supported?org.lwjgl.opengl.GL20.glGetUniformLocation(p,"uShellRecords"):-1);if(map.size()>=256)map.clear();map.put(p,q);return q;}
 static boolean qualified(int p){return qualification(p).supported;}
 static boolean uncached(int program){String v=Shaders.vertexSource(program),f=Shaders.fragmentSource(program);return Shaders.triangleOutput(program)&&v!=null&&v.contains("uniform int vmFarBatch;")&&f!=null&&(hash(f).equals(HASHES.getProperty("band"))||hash(f).equals(HASHES.getProperty("far")));}
}
