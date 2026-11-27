package vmcompat;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.lwjgl.opengl.*;

public final class Shaders {
    private static final Map<Integer,Boolean> GEOMETRY=new HashMap<>();
    public static boolean triangleOutput(int program){return Boolean.FALSE.equals(GEOMETRY.get(program));}
    private static final Map<Integer,String> SOURCES=new HashMap<>();
    private static final Map<Integer,String> FRAGMENTS=new HashMap<>();
    private static final Map<Integer,String> VERTEX_SOURCES=new HashMap<>();
    public static String fragmentSource(int program){return FRAGMENTS.get(program);}
    public static String vertexSource(int program){return VERTEX_SOURCES.get(program);}
    private static final Map<Integer,List<Integer>> ATTACHED=new HashMap<>();
    private static final Set<Integer> LEGACY_PROGRAMS=new HashSet<>();
    private static final Map<Integer,Integer> LEGACY_ATTRIBUTES=new HashMap<>();
    private static final Set<Integer> LEGACY_SHADERS=new HashSet<>();
    private static final Map<Integer,Boolean> VERTICES=new HashMap<>();
    private static final Set<Integer> VP_SHADERS=new HashSet<>();
    public static boolean legacy(int program){return LEGACY_PROGRAMS.contains(program);}
    public static int legacyAttributes(int program){return LEGACY_ATTRIBUTES.getOrDefault(program,0);}
    public static void gameSource(int shader,CharSequence source){source(shader,source.toString(),true,false);}
    public static void vpSource(int shader,CharSequence source){source(shader,source.toString(),false,true);}
    public static void coreSource(int shader,CharSequence source){source(shader,source.toString(),false,false);}
    private static void source(int shader,String text,boolean game,boolean vp){
        int kind=GL20.glGetShaderi(shader,GL20.GL_SHADER_TYPE);
        VERTICES.put(shader,kind==GL20.GL_VERTEX_SHADER);LEGACY_SHADERS.remove(shader);VP_SHADERS.remove(shader);if(vp)VP_SHADERS.add(shader);
        String converted=convert(text,kind==GL20.GL_VERTEX_SHADER,game);
        if(game&&converted.contains("vmcompat"))LEGACY_SHADERS.add(shader);
        SOURCES.put(shader,converted);GL20.glShaderSource(shader,converted);
    }
    public static String convert(String input,boolean vertex,boolean game){
        String source=NativeSamplers.rewrite(WordStorage.convert(StorageBuffers.convert(input))).replace("\uFEFF","");
        source="#version 410 core\n"+source.replaceAll("(?m)^\\s*#version\\s+\\d+[^\\n]*", "");
        source=source.replaceAll("(?m)^\\s*#extension\\s+GL_ARB_(?:shading_language_420pack|shader_draw_parameters|explicit_uniform_location)\\s*:[^\\n]*", "");
        source=source.replaceAll("layout\\s*\\(\\s*binding\\s*=\\s*\\d+\\s*\\)", "");
        source=source.replaceAll(",\\s*binding\\s*=\\s*\\d+", "");
        source=source.replaceAll("binding\\s*=\\s*\\d+\\s*,", "");
        // GLSL 4.1 reserves `sample`; older shader sources use it as a parameter name.
        if(Pattern.compile("\\b(?:int|uint|float|[iub]?vec[234])\\s+sample\\b").matcher(source).find())source=source.replaceAll("\\bsample\\b","vmcompatSampleIndex");
        source=source.replaceAll("(?m)^(\\s*)#if\\s+REFLECTED_CLOUDS\\s*$", "$1#ifndef REFLECTED_CLOUDS\n#define REFLECTED_CLOUDS 0\n#endif\n#if REFLECTED_CLOUDS");
        if(!source.contains("lib/model_textures.glsl has a case for each of 16 slots"))source=source.replaceAll("(?m)(#define\\s+MODEL_TEXTURES\\s+)16\\b", "$16");
        if(!source.contains("lib/atlas_pages.glsl has a case for each of 8 slots"))source=source.replaceAll("(?m)(#define\\s+ATLAS_SLOTS\\s+)8\\b", "$14");
        source=source.replaceAll("\\bgl_BaseInstance(?:ARB)?\\b", "vmcompatBaseInstance").replaceAll("\\bgl_DrawID(?:ARB)?\\b", "vmcompatDrawId");
        String declarations="";
        if(source.contains("vmcompatBaseInstance")&&!source.contains("uniform int vmcompatBaseInstance;"))declarations+="uniform int vmcompatBaseInstance;\n";
        if(source.contains("vmcompatDrawId")&&!source.contains("uniform int vmcompatDrawId;"))declarations+="uniform int vmcompatDrawId;\n";
        if(game){
            source=source.replaceAll("\\battribute\\b","in").replaceAll("\\bvarying\\b",vertex?"out":"in");
            if(source.contains("texture2D")){for(String prefix:new String[]{"","i","u"}){String vector=prefix.isEmpty()?"vec4":prefix+"vec4";declarations+=vector+" vmcompatTexture2D("+prefix+"sampler2D s,vec2 p){return texture(s,p);}\n";declarations+=vector+" vmcompatTexture2D("+prefix+"sampler2D s,vec2 p,float b){return "+(vertex?"textureLod":"texture")+"(s,p,b);}\n";}source=source.replaceAll("\\btexture2D\\b","vmcompatTexture2D");}
            if(source.contains("texture2DLod")){declarations+="vec4 vmcompatTexture2DLod(sampler2D s,vec2 p,float l){return textureLod(s,p,l);}\n";source=source.replaceAll("\\btexture2DLod\\b","vmcompatTexture2DLod");}
            if(source.contains("textureCube")){declarations+="vec4 vmcompatTextureCube(samplerCube s,vec3 p){return texture(s,p);}\n";source=source.replaceAll("\\btextureCube\\b","vmcompatTextureCube");}
            if(source.contains("gl_TexCoord")){declarations+=(vertex?"out":"in")+" vec4 vmcompatTexCoord[8];\n";source=source.replace("gl_TexCoord","vmcompatTexCoord");}
            if(source.contains("gl_FrontColor")){declarations+="out vec4 vmcompatFrontColor;\n";source=source.replace("gl_FrontColor","vmcompatFrontColor");}
            if(!vertex&&source.contains("gl_Color")){declarations+="in vec4 vmcompatFrontColor;\n";source=source.replace("gl_Color","vmcompatFrontColor");}
            if(vertex){
                if(Pattern.compile("\\bgl_Vertex\\b|\\bftransform\\s*\\(").matcher(source).find()){declarations+="layout(location=0) in vec3 vmcompatPosition;\n";source=source.replaceAll("\\bgl_Vertex\\b","vec4(vmcompatPosition,1.0)").replaceAll("\\bftransform\\s*\\(\\s*\\)","(vmcompatMvp * vec4(vmcompatPosition,1.0))");}
                if(source.contains("gl_Color")){declarations+="layout(location=1) in vec4 vmcompatColor;\n";source=source.replace("gl_Color","vmcompatColor");}
                if(source.contains("gl_MultiTexCoord0")){declarations+="layout(location=2) in vec2 vmcompatUV;\n";source=source.replace("gl_MultiTexCoord0","vec4(vmcompatUV,0.0,1.0)");}
                if(source.contains("gl_MultiTexCoord1")){declarations+="layout(location=3) in vec2 vmcompatUV2;\n";source=source.replace("gl_MultiTexCoord1","vec4(vmcompatUV2,0.0,1.0)");}
                source=source.replace("gl_ModelViewProjectionMatrix","vmcompatMvp").replace("gl_ModelViewMatrix","vmcompatModel").replace("gl_ProjectionMatrix","vmcompatProjection");
                if(source.contains("vmcompatMvp"))declarations+="uniform mat4 vmcompatMvp;\n";
                if(source.contains("vmcompatModel"))declarations+="uniform mat4 vmcompatModel;\n";
                if(source.contains("vmcompatProjection"))declarations+="uniform mat4 vmcompatProjection;\n";
            }else if(source.contains("gl_FragColor")){
                declarations+="out vec4 vmcompatFragment;uniform vec2 vmcompatAlpha;\n";
                source=source.replace("gl_FragColor","vmcompatFragment").replaceFirst("void\\s+main\\s*\\(","void vmcompatMain(");
                source+="\nvoid main(){vmcompatMain();float a=vmcompatFragment.a;int f=int(vmcompatAlpha.x);float r=vmcompatAlpha.y;bool pass=f==519||(f==513&&a<r)||(f==514&&a==r)||(f==515&&a<=r)||(f==516&&a>r)||(f==517&&a!=r)||(f==518&&a>=r);if(!pass)discard;}\n";
            }
        }
        return insertDeclarations(source,declarations);
    }
    static String insertDeclarations(String source,String declarations){int version=source.indexOf("#version"),newline=source.indexOf('\n',version);Matcher extensions=Pattern.compile("(?m)^\\s*#extension[^\\n]*\\n").matcher(source);while(extensions.find())newline=Math.max(newline,extensions.end()-1);if(newline<0)return source+"\n"+declarations;return source.substring(0,newline+1)+declarations+source.substring(newline+1);}
    public static void glAttachShader(int program,int shader){GEOMETRY.put(program,GEOMETRY.getOrDefault(program,false)||GL20.glGetShaderi(shader,GL20.GL_SHADER_TYPE)==GL32.GL_GEOMETRY_SHADER);ATTACHED.computeIfAbsent(program,k->new ArrayList<>()).add(shader);GL20.glAttachShader(program,shader);}
    public static void glCompileShader(int shader){
        GL20.glCompileShader(shader);
        if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){
            String source=SOURCES.getOrDefault(shader,"source unavailable"),error=GL20.glGetShaderInfoLog(shader);
            String key=Integer.toUnsignedString(source.hashCode(),16);
            Path path=Log.ROOT.resolve("shader-failures").resolve(key+".glsl");
            try{if(Boolean.parseBoolean(Options.value("shaderErrorDumps","false"))){Files.createDirectories(path.getParent());Files.writeString(path,source);Files.writeString(path.resolveSibling(key+".log"),error);}}catch(Exception e){Log.failure("save shader diagnostics",e);}
            Log.info("Shader compilation failed ["+key+"]: "+error+"; source: "+path);
        }
    }
    public static int glCreateProgram(){int p=GL20.glCreateProgram();ContextResources.programChanged(p);return p;}
    public static void glLinkProgram(int program){
        ContextResources.programChanged(program);Draws.deletedProgram(program);Samplers.deleted(program);Packing.deleted(program);Samplers.invalidate();
        Map<Integer,String> sources=new LinkedHashMap<>();for(int shader:ATTACHED.getOrDefault(program,List.of()))if(SOURCES.containsKey(shader))sources.put(shader,SOURCES.get(shader));
        bridgeLegacyOutputs(sources);
        if(FarShaders.patch(sources,VERTICES))recompile(sources);
        boolean packed=false;
        
        GL20.glLinkProgram(program);
        if(!packed&&GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0&&Packing.canLower(sources)){
            String error=GL20.glGetProgramInfoLog(program).toLowerCase(Locale.ROOT);
            if(error.contains("sampler")||error.contains("texture")){boolean retry=false;try{recompile(Packing.lower(program,sources,VERTICES));GL20.glLinkProgram(program);retry=GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0;if(retry)Log.info("Partial packing link retry: "+GL20.glGetProgramInfoLog(program));}catch(IllegalStateException partialFailure){Log.info("Partial packing compile retry: "+partialFailure.getMessage());retry=true;}if(retry){recompile(Packing.lowerAll(program,sources,VERTICES));GL20.glLinkProgram(program);}}
        }
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)sources=LightingSamplerFallback.retry(program,sources,VERTICES,Shaders::recompile);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0){Log.info("Shader link failed: "+GL20.glGetProgramInfoLog(program));return;}
        
        String fragment=sources.entrySet().stream().filter(e->!VERTICES.getOrDefault(e.getKey(),true)).map(Map.Entry::getValue).reduce("",(a,b)->a+b);
        if(!fragment.isEmpty())FRAGMENTS.put(program,fragment);
        String vertex=sources.entrySet().stream().filter(e->VERTICES.getOrDefault(e.getKey(),false)).map(Map.Entry::getValue).reduce("",(a,b)->a+b);if(!vertex.isEmpty())VERTEX_SOURCES.put(program,vertex);
        if(ATTACHED.getOrDefault(program,List.of()).stream().anyMatch(LEGACY_SHADERS::contains))LEGACY_PROGRAMS.add(program);
        int attributes=0;for(var entry:sources.entrySet())if(VERTICES.getOrDefault(entry.getKey(),false)){if(entry.getValue().contains("in vec4 vmcompatColor;"))attributes|=1;if(entry.getValue().contains("in vec2 vmcompatUV;"))attributes|=2;}LEGACY_ATTRIBUTES.put(program,attributes);
        Legacy.deletedProgram(program);Draws.deletedProgram(program);Samplers.linked(program);StorageBuffers.linked(program);Packing.linked(program);
    }
    public static void glUseProgram(int program){GL20.glUseProgram(program);}
    public static int glGetUniformLocation(int program,CharSequence name){int cell=WordStorage.location(program,name.toString());if(cell>=0)return cell;int virtual=Packing.location(program,name.toString());return virtual>=0?virtual:GL20.glGetUniformLocation(program,name);}
    public static void glDeleteProgram(int program){StorageBuffers.deleted(program);ContextResources.programChanged(program);GEOMETRY.remove(program);VERTEX_SOURCES.remove(program);FRAGMENTS.remove(program);ATTACHED.remove(program);LEGACY_PROGRAMS.remove(program);LEGACY_ATTRIBUTES.remove(program);Samplers.deleted(program);Draws.deletedProgram(program);Legacy.deletedProgram(program);Packing.deleted(program);GL20.glDeleteProgram(program);}
    private static void bridgeLegacyOutputs(Map<Integer,String> sources){
        String fragments=sources.entrySet().stream().filter(e->!VERTICES.getOrDefault(e.getKey(),false)).map(Map.Entry::getValue).reduce("",(a,b)->a+"\n"+b);
        for(var entry:sources.entrySet()){
            if(!LEGACY_SHADERS.contains(entry.getKey())||!VERTICES.getOrDefault(entry.getKey(),false))continue;
            String source=entry.getValue(),declarations="",assignments="";
            if(source.contains("out vec4 vmcompatTexCoord[")&&Pattern.compile("\\bin\\s+vec2\\s+vUV1\\s*;").matcher(fragments).find()&&!Pattern.compile("\\bout\\s+vec2\\s+vUV1\\s*;").matcher(source).find()){
                declarations+="out vec2 vUV1;\n";assignments+="vUV1=vmcompatTexCoord[0].xy;";
            }
            if(source.contains("out vec4 vmcompatFrontColor;")&&Pattern.compile("\\bin\\s+vec4\\s+vColor\\s*;").matcher(fragments).find()&&!Pattern.compile("\\bout\\s+vec4\\s+vColor\\s*;").matcher(source).find()){
                declarations+="out vec4 vColor;\n";assignments+="vColor=vmcompatFrontColor;";
            }
            if(assignments.isEmpty())continue;
            source=source.replaceFirst("void\\s+main\\s*\\(","void vmcompatVertexMain(");
            source=insertDeclarations(source,declarations)+"\nvoid main(){vmcompatVertexMain();"+assignments+"}\n";
            GL20.glShaderSource(entry.getKey(),source);glCompileShader(entry.getKey());entry.setValue(source);
        }
    }
    private static void recompile(Map<Integer,String> sources){for(var entry:sources.entrySet()){GL20.glShaderSource(entry.getKey(),entry.getValue());GL20.glCompileShader(entry.getKey());if(GL20.glGetShaderi(entry.getKey(),GL20.GL_COMPILE_STATUS)==0)throw new IllegalStateException("Packed shader compilation: "+GL20.glGetShaderInfoLog(entry.getKey()));}}
    public static int compileRaw(int kind,String source){int s=GL20.glCreateShader(kind);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(error+"\n"+source);}return s;}
    private Shaders(){}
}
