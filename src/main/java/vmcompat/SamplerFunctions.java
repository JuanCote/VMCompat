package vmcompat;
import java.util.*;import java.util.regex.*;
/** Native/value-index overloads, including the scoped ART_SAMPLER macro used by
 * Viewpoint's shared 2D/array sprite implementation. No global sampler rewrite.
 */
public final class SamplerFunctions {
 private record Define(int at,String name,String type){}
 private static String mask(String source){char[] chars=source.toCharArray();Matcher comments=Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*").matcher(source);while(comments.find())for(int i=comments.start();i<comments.end();i++)if(chars[i]!='\n'&&chars[i]!='\r')chars[i]=' ';return new String(chars);}
 public static String overloads(String source){String masked=mask(source);List<Define> definitions=new ArrayList<>();Set<String> names=new LinkedHashSet<>();names.add("sampler2D");Matcher directives=Pattern.compile("(?m)^[ \\t]*#[ \\t]*(define|undef)[ \\t]+(\\w+)([^\\r\\n]*)").matcher(masked);while(directives.find()){String value=directives.group(1).equals("undef")?"":directives.group(3).trim();definitions.add(new Define(directives.start(),directives.group(2),value));if(value.equals("sampler2D"))names.add(directives.group(2));}
  String types=String.join("|",names.stream().map(Pattern::quote).toList());Matcher functions=Pattern.compile("\\b\\w+\\s+\\w+\\s*\\(([^()]*\\b(?:"+types+")\\b[^()]*)\\)\\s*([;{])").matcher(masked);Map<String,String> aliases=new HashMap<>();StringBuilder out=new StringBuilder();int from=0,event=0;
  while(functions.find(from)){while(event<definitions.size()&&definitions.get(event).at<functions.start()){Define d=definitions.get(event++);aliases.put(d.name,d.type);}int end=functions.end();if(functions.group(2).equals("{")){int depth=1;while(end<masked.length()&&depth>0){char c=masked.charAt(end++);if(c=='{')depth++;else if(c=='}')depth--;}if(depth!=0)throw new IllegalArgumentException("Unclosed sampler helper");}
   out.append(source,from,end);String parameters=source.substring(functions.start(1),functions.end(1));Matcher samplers=Pattern.compile("\\b(?:"+types+")\\b").matcher(parameters);List<int[]> at=new ArrayList<>();while(samplers.find())if(samplers.group().equals("sampler2D")||aliases.getOrDefault(samplers.group(),"").equals("sampler2D"))at.add(new int[]{samplers.start(),samplers.end()});if(at.size()>3)throw new IllegalArgumentException("Sampler helper has more than three image arguments");
   for(int bits=1;bits<(1<<at.size());bits++){StringBuilder params=new StringBuilder(parameters);for(int k=at.size()-1;k>=0;k--)if((bits&(1<<k))!=0)params.replace(at.get(k)[0],at.get(k)[1],"VmImage");out.append('\n').append(source,functions.start(),functions.start(1)).append(params).append(source,functions.end(1),end).append('\n');}from=end;
  }
  return out.append(source.substring(from)).toString();
 }
 private SamplerFunctions(){}
}
