package vmcompat;
import java.nio.*;import java.nio.file.*;import java.util.*;import java.awt.image.BufferedImage;
import org.lwjgl.BufferUtils;import org.lwjgl.glfw.GLFW;import org.lwjgl.opengl.*;

/** Private startup-only evidence: fifth owned approval draw, then three presents.
 * Readbacks are explicitly bounded and happen once, before game-world loading.
 * PNG conversion, JSON formatting and disk I/O run on the existing background writer.
 */
public final class StartupUiProbe {
    static final boolean ACTIVE=Boolean.parseBoolean(Options.value("startupUiCapture",Boolean.getBoolean("vmcompat.testMode")?"false":"true"));
    private static int draws;private static boolean captured;private static volatile Capture pending;
    public static final class Capture {
        final long window;final int width,height,texture;final Map<String,Object> meta=new LinkedHashMap<>();
        final List<Map<String,Object>> presents=new ArrayList<>();int swaps;
        Capture(imgui.ImDrawData data){window=GLFW.glfwGetCurrentContext();width=Math.max(1,(int)(data.getDisplaySizeX()*data.getFramebufferScaleX()));height=Math.max(1,(int)(data.getDisplaySizeY()*data.getFramebufferScaleY()));texture=data.getCmdListCmdBufferSize(0)>0?data.getCmdListCmdBufferTextureId(0,0):0;meta.put("pid",ProcessHandle.current().pid());meta.put("capture_epoch_ms",System.currentTimeMillis());meta.put("version",Agent.VERSION);meta.put("native_imgui",ImguiBootstrap.NATIVE);meta.put("window",window);meta.put("thread",Thread.currentThread().getName());meta.put("width",width);meta.put("height",height);meta.put("vertices",data.getTotalVtxCount());meta.put("indices",data.getTotalIdxCount());meta.put("first_texture",texture);}
    }
    public static Capture prepare(Object renderer,imgui.ImDrawData data){
        if(!ACTIVE||captured||data==null||data.getCmdListsCount()==0)return null;
        boolean owned=StackWalker.getInstance().walk(s->s.anyMatch(f->f.getClassName().equals(ImguiBootstrap.OWNED.replace('/','.'))&&f.getMethodName().equals("render")));
        if(!owned||++draws<5)return null;captured=true;Capture c=new Capture(data);
        c.meta.put("schema_version",1);
        Map<String,Object> fields=new LinkedHashMap<>();
        for(String name:List.of("glVersion","glslVersion","gShaderHandle","gAttribLocationTex","gAttribLocationProjMtx","gVboHandle","gElementsHandle","gFontTexture","gVertexArrayObjectHandle"))try{var f=renderer.getClass().getDeclaredField(name);f.setAccessible(true);fields.put(name,f.get(renderer));}catch(ReflectiveOperationException e){fields.put(name,"unavailable");}
        c.meta.put("backend_fields",fields);return c;
    }
    private record Pixels(int width,int height,byte[] rgba){}
    private static List<Integer> errors(){List<Integer> out=new ArrayList<>();for(int i=0;i<8;i++){int e=GL11.glGetError();if(e==0)break;out.add(e);}return out;}
    private static List<Integer> ints(int name,int count){IntBuffer b=BufferUtils.createIntBuffer(count);GL11.glGetIntegerv(name,b);List<Integer> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(b.get(i));return out;}
    public static void afterDraw(Capture c){
        try{
            c.meta.put("errors_after_ui_before_probe",errors());
            c.meta.put("draw_fbo",GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));c.meta.put("read_fbo",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
            c.meta.put("viewport",ints(GL11.GL_VIEWPORT,4));c.meta.put("scissor",ints(GL11.GL_SCISSOR_BOX,4));
            Map<String,Object> enabled=new LinkedHashMap<>();int[] flags={GL11.GL_BLEND,GL11.GL_CULL_FACE,GL11.GL_DEPTH_TEST,GL11.GL_STENCIL_TEST,GL30.GL_RASTERIZER_DISCARD,GL11.GL_SCISSOR_TEST,GL13.GL_SAMPLE_COVERAGE,GL13.GL_SAMPLE_ALPHA_TO_COVERAGE,GL32.GL_SAMPLE_MASK,GL30.GL_FRAMEBUFFER_SRGB};
            for(int flag:flags)enabled.put(Integer.toString(flag),GL11.glIsEnabled(flag));for(int i=0;i<Math.min(8,GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES));i++)enabled.put("clip_distance_"+i,GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0+i));c.meta.put("enabled",enabled);
            c.meta.put("sample_mask0",GL30.glGetIntegeri(GL32.GL_SAMPLE_MASK_VALUE,0));c.meta.put("samples",GL11.glGetInteger(GL13.GL_SAMPLES));
            c.meta.put("sample_coverage",GL11.glGetFloat(GL13.GL_SAMPLE_COVERAGE_VALUE));c.meta.put("sample_coverage_invert",GL11.glGetBoolean(GL13.GL_SAMPLE_COVERAGE_INVERT));
            @SuppressWarnings("unchecked") Map<String,Object> fields=(Map<String,Object>)c.meta.get("backend_fields");
            int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);c.meta.put("host_vao_after_ui",vao);if(vao!=0){List<Integer> divisors=new ArrayList<>();for(int i=0;i<8;i++)divisors.add(GL20.glGetVertexAttribi(i,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR));c.meta.put("host_divisors_after_ui",divisors);}
            if(fields.get("gShaderHandle") instanceof Integer program&&program!=0){
                c.meta.put("ui_program_linked",GL20.glGetProgrami(program,GL20.GL_LINK_STATUS));
                if(fields.get("gAttribLocationTex") instanceof Integer location&&location>=0)c.meta.put("ui_texture_unit",GL20.glGetUniformi(program,location));
                if(fields.get("gAttribLocationProjMtx") instanceof Integer location&&location>=0){FloatBuffer b=BufferUtils.createFloatBuffer(16);GL20.glGetUniformfv(program,location,b);List<Float> values=new ArrayList<>();for(int i=0;i<16;i++)values.add(b.get(i));c.meta.put("ui_projection",values);}
            }
            c.meta.put("unpack_pbo",GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING));c.meta.put("unpack_row_length",GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH));c.meta.put("unpack_skip_rows",GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS));c.meta.put("unpack_skip_pixels",GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS));
            Pixels back=read(c,false);Pixels texture=texture(c);c.meta.put("probe_errors",errors());pending=c;
            save(c,Map.of("startup-ui-back.png",back),texture);
            Log.info("Startup UI capture queued: fifth approval draw; three presentation samples pending");
        }catch(Throwable error){Log.failure("Startup UI capture failed",error);}
    }
    public static void afterSwap(long window){
        Capture c=pending;if(c==null)return;
        try{
            int n=++c.swaps;if(n>=3)pending=null;
            var point=new LinkedHashMap<String,Object>();point.put("number",n);point.put("swap_window",window);point.put("current_context",GLFW.glfwGetCurrentContext());point.put("callers",StackWalker.getInstance().walk(s->s.limit(9).map(f->f.getClassName()+"."+f.getMethodName()).toList()));
            if(c.window!=window){point.put("mismatched_window",true);c.presents.add(point);save(c,Map.of(),null);return;}
            point.put("errors_before",errors());
            Pixels front=read(c,true);point.put("errors_after",errors());point.put("center_roi",true);point.put("width",front.width);point.put("height",front.height);c.presents.add(point);
            save(c,Map.of("startup-ui-front-"+n+".png",front),null);
        }catch(Throwable error){pending=null;Log.failure("Startup UI presentation capture failed",error);}
    }
    private static final int[] PACK={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
    private static final class Pack implements AutoCloseable{
        final int pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);final int[] values=new int[PACK.length];
        Pack(){for(int i=0;i<PACK.length;i++)values[i]=GL11.glGetInteger(PACK[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int p:PACK)GL11.glPixelStorei(p,p==GL11.GL_PACK_ALIGNMENT?1:0);}
        public void close(){for(int i=0;i<PACK.length;i++)GL11.glPixelStorei(PACK[i],values[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);}
    }
    private static Pixels read(Capture c,boolean front){
        int w=Math.min(c.width,front?512:2048),h=Math.min(c.height,front?512:2048),x=front?Math.max(0,(c.width-w)/2):0,y=front?Math.max(0,(c.height-h)/2):0;
        int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),target=front?0:GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target);int oldRead=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try(Pack pack=new Pack()){
            GL11.glReadBuffer(target==0?(front?GL11.GL_FRONT:GL11.GL_BACK):GL30.GL_COLOR_ATTACHMENT0);ByteBuffer b=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(x,y,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] rgba=new byte[b.capacity()];b.get(rgba);return new Pixels(w,h,rgba);
        }finally{GL11.glReadBuffer(oldRead);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);}
    }
    private static Pixels texture(Capture c){
        if(c.texture==0||!GL11.glIsTexture(c.texture)){c.meta.put("texture_valid",false);return null;}
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);GL13.glActiveTexture(GL13.GL_TEXTURE0);int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try(Pack pack=new Pack()){
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,c.texture);int w=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),h=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            c.meta.put("texture_width",w);c.meta.put("texture_height",h);c.meta.put("texture_level1_width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH));c.meta.put("texture_min_filter",GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER));
            if(w<=0||h<=0||(long)w*h*4>4*1024*1024){c.meta.put("texture_read_skipped",true);return null;}
            ByteBuffer b=BufferUtils.createByteBuffer(w*h*4);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] rgba=new byte[b.capacity()];b.get(rgba);return new Pixels(w,h,rgba);
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,old);GL13.glActiveTexture(active);}
    }
    private static void save(Capture c,Map<String,Pixels> images,Pixels texture){
        Map<String,Object> meta=new LinkedHashMap<>(c.meta);meta.put("presentations",List.copyOf(c.presents));
        if(!Log.background(()->{
            try{Files.createDirectories(Log.ROOT);for(var e:images.entrySet())write(e.getKey(),e.getValue());if(texture!=null)write("startup-ui-texture.png",texture);Files.writeString(Log.ROOT.resolve("startup-ui-state.json"),Breakdown.json(meta)+"\n");}
            catch(Exception error){Log.failure("Save startup UI evidence",error);}
        }))Log.info("Startup UI evidence queue full; capture dropped");
    }
    private static void write(String name,Pixels p)throws Exception{
        BufferedImage image=new BufferedImage(p.width,p.height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++){int at=(y*p.width+x)*4;image.setRGB(x,p.height-1-y,((p.rgba[at+3]&255)<<24)|((p.rgba[at]&255)<<16)|((p.rgba[at+1]&255)<<8)|(p.rgba[at+2]&255));}javax.imageio.ImageIO.write(image,"png",Log.ROOT.resolve(name).toFile());
    }
}
