package vmcompat;

import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import java.util.regex.*;

/** Opt-in proof for the installed built-in shader contract, not arbitrary GLSL. */
public final class FloorSamplerContract {
    private static final String VERTEX_MAIN="25b030b2f28c1e45655726cc38ff030fdca135411085f269ddc60f9768d1f316";
    private static final String MESH_VERTEX="153f6156856c441319b959c0c8540e3364230b47fd15924be5a3d9d0bebb01fd";
    private static final String FRAGMENT_MAIN="e421837f585bc9bb267185051ca6cf1e5183f21453faa1374dd8976793c50a83";
    private static String body(String source,String pattern)throws Exception{
        Matcher m=Pattern.compile(pattern).matcher(source);if(!m.find())return "";int from=source.indexOf('{',m.start()),at=from+1,depth=1;while(at<source.length()&&depth>0){char c=source.charAt(at++);if(c=='{')depth++;else if(c=='}')depth--;}if(depth!=0)return "";
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.substring(from,at).replaceAll("\\s+","").getBytes(StandardCharsets.UTF_8)));
    }
    private static boolean constant(String source,String name,String value){Matcher m=Pattern.compile("(?m)^\\s*#define\\s+"+name+"\\s+([^\\r\\n]+)").matcher(source);boolean found=false;while(m.find()){if(!m.group(1).trim().equals(value))return false;found=true;}return found;}
    public static boolean supported(String vertex,String fragment){
        if(vertex==null||fragment==null)return false;
        try{
            if(Pattern.compile("\\buTexture\\b").matcher(vertex).results().count()!=0||Pattern.compile("\\buTexture\\b").matcher(fragment).results().count()!=3)return false;
            String both=vertex+"\n"+fragment;
            if(Pattern.compile("(?m)^\\s*#define\\s+(?:aFlags|vFlags|main|meshVertex|MeshVertex|sampleFloor|sampleSprite|wallSurface)\\b").matcher(both).find())return false;
            for(String s:List.of(vertex,fragment))if(!constant(s,"FLAG_BAKED_FLOOR","64")||!constant(s,"WALL_FAMILY_SHIFT","16")||!constant(s,"WALL_FAMILY_MASK","15"))return false;
            return body(vertex,"\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{").equals(VERTEX_MAIN)&&body(vertex,"\\bMeshVertex\\s+meshVertex\\s*\\([^)]*\\)\\s*\\{").equals(MESH_VERTEX)&&body(fragment,"\\bvoid\\s+main\\s*\\(\\s*\\)\\s*\\{").equals(FRAGMENT_MAIN);
        }catch(Exception unsupported){return false;}
    }
    private FloorSamplerContract(){}
}
