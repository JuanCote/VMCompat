package vmcompat;
import java.util.regex.*;
/** Only rewrite the exact side-effect-free 16-case model texture selectors after
 * their samplers have become VmImage values. Native sampler shaders stay intact.
 */
public final class ModelPacking {
 static final boolean ENABLED=Boolean.parseBoolean(Options.value("modelSamplerSwitchCollapse","true"));
 public static String collapse(String source,int first){if(!ENABLED||first<0||!source.contains("const VmImage uTextures[16]"))return source;
  source=replace(source,"modelArt","modelArtFrom","uv,dx,dy,posDx,posDy,upright,mode",first,"uTextures",16);
  return replace(source,"modelTexel","textureGrad","uv,dx,dy",first,"uTextures",16);
 }
 public static String collapseAtlas(String source,int first){if(!ENABLED||first<0||!source.contains("const VmImage uAtlases[8]"))return source;return replace(source,"atlasSprite","sampleSprite","uv,rect,flags",first,"uAtlases",8);}
 private static String replace(String source,String function,String called,String arguments,int first,String array,int count){
  Matcher signature=Pattern.compile("\\bvec4\\s+"+function+"\\s*\\([^)]*\\)\\s*\\{").matcher(source);if(!signature.find())return source;int start=signature.end(),at=start,depth=1;while(at<source.length()&&depth>0){char c=source.charAt(at++);if(c=='{')depth++;else if(c=='}')depth--;}
  if(depth!=0)return source;StringBuilder expected=new StringBuilder("switch(slot){");for(int i=0;i<count-1;i++)expected.append("case").append(i).append(":return").append(called).append("(").append(array).append("[").append(i).append("],").append(arguments).append(");");expected.append("default:return").append(called).append("(").append(array).append("[").append(count-1).append("],").append(arguments).append(");}");
  if(!source.substring(start,at-1).replaceAll("\\s+","").equals(expected.toString()))return source;
  String body="return "+called+"(VmImage("+first+" + (slot >= 0 && slot < "+(count-1)+" ? slot : "+(count-1)+")),"+arguments+");";
  return source.substring(0,start)+body+source.substring(at-1);
 }
 private ModelPacking(){}
}
