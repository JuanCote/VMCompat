package vmcompat;
import java.util.regex.*;
/** Keep the pinned 0.1.7 selector code consistent with the existing CPU batch
 * capacities (AtlasGroups=3/4, ModelBatches=6). This splits work, not visibility.
 */
public final class NativeSamplers {
 static final boolean ENABLED=Boolean.parseBoolean(Options.value("nativeBatchSamplers","true"));
 static boolean protectedSource(String source){return source.contains("lib/atlas_pages.glsl has a case for each of 8 slots")||source.contains("lib/model_textures.glsl has a case for each of 16 slots")||source.contains("uWindscreenMeasure");}
 public static int atlasSlots(boolean fourth){return fourth?4:3;}
 public static String rewrite(String source){if(!ENABLED)return source;
  if(source.contains("lib/atlas_pages.glsl has a case for each of 8 slots")){
   int slots=Pattern.compile("(?m)^\\s*#define\\s+PIPELINE_SHADOWS\\s+1\\b").matcher(source).find()?3:4;source=shadow(source,slots);
   source=selector(source,"atlasSprite","sampleSprite","uv,rect,flags","uAtlases",8,slots,true);
   source=source.replaceAll("(?m)(#define\\s+ATLAS_SLOTS\\s+)8\\b","$1"+slots).replaceAll("(#if\\s+ATLAS_SLOTS\\s*!=\\s*)8\\b","$1"+slots);
  }
  if(source.contains("lib/model_textures.glsl has a case for each of 16 slots")){
   source=selector(source,"modelTexel","textureGrad","uv,dx,dy","uTextures",16,6,true);
   source=selector(source,"modelArt","modelArtFrom","uv,dx,dy,posDx,posDy,upright,mode","uTextures",16,6,false);
   source=source.replaceAll("(?m)(#define\\s+MODEL_TEXTURES\\s+)16\\b","$16").replaceAll("(#if\\s+MODEL_TEXTURES\\s*!=\\s*)16\\b","$16");
  }return source;
 }
 private static String shadow(String source,int slots){Matcher m=Pattern.compile("(?s)void\\s+atlasCrownShadow\\s*\\([^)]*\\)\\s*\\{(.*?)\\n\\}").matcher(source);if(!m.find())return source;StringBuilder old=new StringBuilder("switch(slot){");for(int i=0;i<7;i++)old.append("case").append(i).append(":crownShadow(uAtlases[").append(i).append("],uv,rect);break;");old.append("default:crownShadow(uAtlases[7],uv,rect);break;}");if(!m.group(1).replaceAll("\\s+","").equals(old.toString()))throw new IllegalStateException("Changed atlas crown selector");StringBuilder generated=new StringBuilder("switch(slot){");for(int i=0;i<slots-1;i++)generated.append("case ").append(i).append(":crownShadow(uAtlases[").append(i).append("],uv,rect);break;");String body=generated.append("default:crownShadow(uAtlases[").append(slots-1).append("],uv,rect);break;}").toString();return source.substring(0,m.start(1))+body+source.substring(m.end(1));}
 private static String body(String called,String args,String array,int n){StringBuilder b=new StringBuilder("switch(slot){");for(int i=0;i<n-1;i++)b.append("case ").append(i).append(":return ").append(called).append('(').append(array).append('[').append(i).append("],").append(args).append(");");return b.append("default:return ").append(called).append('(').append(array).append('[').append(n-1).append("],").append(args).append(");}").toString();}
 private static String selector(String s,String function,String called,String args,String array,int from,int to,boolean required){Matcher m=Pattern.compile("\\bvec4\\s+"+function+"\\s*\\(\\s*int\\s+slot\\b[^)]*\\)\\s*\\{").matcher(s);if(!m.find()){if(required)throw new IllegalStateException("Missing pinned native sampler selector "+function);return s;}int start=m.end(),at=start,depth=1;while(at<s.length()&&depth>0){char c=s.charAt(at++);if(c=='{')depth++;else if(c=='}')depth--;}if(depth!=0||!s.substring(start,at-1).replaceAll("\\s+","").equals(body(called,args,array,from).replaceAll("\\s+","")))throw new IllegalStateException("Changed pinned native sampler selector "+function);return s.substring(0,start)+body(called,args,array,to)+s.substring(at-1);}
 private NativeSamplers(){}
}
