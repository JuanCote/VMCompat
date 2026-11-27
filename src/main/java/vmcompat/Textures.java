package vmcompat;

import java.nio.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.*;


public final class Textures {
    public record Image(int target,int format,int width,int height,int depth,int levels){}
    private static final Map<Integer,Integer> TARGETS=new ConcurrentHashMap<>();
    private static final Map<Integer,Image> IMAGES=new ConcurrentHashMap<>();
    private static final Map<Integer,View> VIEWS=new HashMap<>();
    private static final Map<Integer,Integer> DEPTH_ATTACHMENTS=new HashMap<>();
    private static final Map<Integer,Map<Integer,Integer>> ATTACHMENTS=new HashMap<>();
    private static final Map<Integer,Long> VERSIONS=new HashMap<>();
    private static long serial;
    private static final StateStore<int[]> COPY_FBOS=new StateStore<>(()->new int[2]);
    public static void pixelsChanged(int id){if(id!=0)VERSIONS.put(id,++serial);}
    public static void pixelsChanged(int id,int level,int layer,int x,int y,int w,int h){pixelsChanged(id);}
    public static void pixelsChanged(int id,int level,int layer){pixelsChanged(id);}
    private static void fullPixelsChanged(int id){pixelsChanged(id);}
    private static void pixelChangeVersion(int id){pixelsChanged(id);}
    public static Image description(int id){return IMAGES.get(id);}
    public static long pixelVersion(int id){return version(id);}
    public static long version(int id){return VERSIONS.getOrDefault(id,0L);}
    public static void changed(int id){Opaque.textureParameters(id);pixelsChanged(id);}
    public static void glGenerateMipmap(int target){GL30.glGenerateMipmap(target);pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    public static void glGenerateTextureMipmap(int texture){bound(texture,()->glGenerateMipmap(target(texture)));}
    public static void attachedLayer(int fbo,int attachment,int texture,int level,int layer){attached(fbo,attachment,texture);}
    private static final class View {int original,level,layer,width,height,format;boolean dirty;}
    public static void target(int id,int target){if(id!=0&&TARGETS.putIfAbsent(id,target)==null)pixelsChanged(id);}
    public static int target(int id){Integer t=TARGETS.get(id);if(t==null)throw new IllegalStateException("Texture target is unknown for "+id);return t;}
    private static int binding(int target){return switch(target){case 3553->32873;case 32879->32874;case 34067->34068;case 35866->35869;case 35882->35884;case 3552->32872;default->throw new IllegalArgumentException("Texture target "+target);};}
    public static void bound(int id,Runnable action){int target=target(id),old=GL11.glGetInteger(binding(target));GL11.glBindTexture(target,id);try{action.run();}finally{GL11.glBindTexture(target,old);}}
    public static int glGenTextures(){int id=GL11.glGenTextures();Buffers.textureCreated(id);return id;}
    public static void glGenTextures(IntBuffer ids){GL11.glGenTextures(ids);for(int i=ids.position();i<ids.limit();i++)Buffers.textureCreated(ids.get(i));}
    public static void glGenTextures(int[] ids){GL11.glGenTextures(ids);for(int id:ids)Buffers.textureCreated(id);}
    public static int glCreateTextures(int target){int id=glGenTextures();target(id,target);int old=GL11.glGetInteger(binding(target));GL11.glBindTexture(target,id);GL11.glBindTexture(target,old);return id;}
    private static int format(int internal){
        if(internal==33189||internal==33190||internal==33191||internal==36012)return GL11.GL_DEPTH_COMPONENT;
        return integer(internal)?GL30.GL_RGBA_INTEGER:GL11.GL_RGBA;
    }
    private static boolean integer(int internal){return switch(internal){
        case GL30.GL_R8I,GL30.GL_R8UI,GL30.GL_R16I,GL30.GL_R16UI,GL30.GL_R32I,GL30.GL_R32UI,
             GL30.GL_RG8I,GL30.GL_RG8UI,GL30.GL_RG16I,GL30.GL_RG16UI,GL30.GL_RG32I,GL30.GL_RG32UI,
             GL30.GL_RGB8I,GL30.GL_RGB8UI,GL30.GL_RGB16I,GL30.GL_RGB16UI,GL30.GL_RGB32I,GL30.GL_RGB32UI,
             GL30.GL_RGBA8I,GL30.GL_RGBA8UI,GL30.GL_RGBA16I,GL30.GL_RGBA16UI,GL30.GL_RGBA32I,GL30.GL_RGBA32UI -> true;
        default -> false;
    };}
    private static int dataType(int internal){return integer(internal)?GL11.GL_INT:GL11.GL_FLOAT;}
    public static void glTexStorage1D(int target,int levels,int internal,int width){for(int level=0;level<levels;level++)GL11.glTexImage1D(target,level,internal,Math.max(1,width>>level),0,format(internal),dataType(internal),(ByteBuffer)null);GL11.glTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL,Math.min(levels-1,GL11.glGetTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL)));IMAGES.put(GL11.glGetInteger(binding(target)),new Image(target,internal,width,1,1,levels));pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    public static void glTexStorage2D(int target,int levels,int internal,int width,int height){
        for(int level=0;level<levels;level++){
            if(target==GL13.GL_TEXTURE_CUBE_MAP)for(int face=0;face<6;face++)GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+face,level,internal,Math.max(1,width>>level),Math.max(1,height>>level),0,format(internal),dataType(internal),(ByteBuffer)null);
            else GL11.glTexImage2D(target,level,internal,Math.max(1,width>>level),Math.max(1,height>>level),0,format(internal),dataType(internal),(ByteBuffer)null);
        }GL11.glTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL,Math.min(levels-1,GL11.glGetTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL)));IMAGES.put(GL11.glGetInteger(binding(target)),new Image(target,internal,width,height,1,levels));pixelsChanged(GL11.glGetInteger(binding(target)));
    }
    public static void glTexStorage3D(int target,int levels,int internal,int width,int height,int depth){for(int level=0;level<levels;level++)GL12.glTexImage3D(target,level,internal,Math.max(1,width>>level),Math.max(1,height>>level),target==GL12.GL_TEXTURE_3D?Math.max(1,depth>>level):depth,0,format(internal),dataType(internal),(ByteBuffer)null);GL11.glTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL,Math.min(levels-1,GL11.glGetTexParameteri(target,GL12.GL_TEXTURE_MAX_LEVEL)));IMAGES.put(GL11.glGetInteger(binding(target)),new Image(target,internal,width,height,depth,levels));pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    public static void glTextureStorage2D(int id,int levels,int internal,int w,int h){bound(id,()->glTexStorage2D(target(id),levels,internal,w,h));}
    public static void glTextureStorage3D(int id,int levels,int internal,int w,int h,int d){bound(id,()->glTexStorage3D(target(id),levels,internal,w,h,d));}
    public static void glTextureParameteri(int id,int name,int value){bound(id,()->GL11.glTexParameteri(target(id),name,value));PackedSampling.textureParameter(id,target(id),name,value);}
    public static void glTexParameteri(int target,int name,int value){GL11.glTexParameteri(target,name,value);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,value);}
    public static void glTexParameterf(int target,int name,float value){GL11.glTexParameterf(target,name,value);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,value);}
    public static void glTexParameteriv(int target,int name,IntBuffer values){GL11.glTexParameteriv(target,name,values);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,values.get(values.position()));}
    public static void glTexParameteriv(int target,int name,int[] values){GL11.glTexParameteriv(target,name,values);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,values[0]);}
    public static void glTexParameterfv(int target,int name,FloatBuffer values){GL11.glTexParameterfv(target,name,values);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,values.get(values.position()));}
    public static void glTexParameterfv(int target,int name,float[] values){GL11.glTexParameterfv(target,name,values);PackedSampling.textureParameter(GL11.glGetInteger(binding(target)),target,name,values[0]);}
    public static void glTextureParameterf(int id,int name,float value){bound(id,()->GL11.glTexParameterf(target(id),name,value));PackedSampling.textureParameter(id,target(id),name,value);}
    public static void glTextureParameteriv(int id,int name,IntBuffer values){bound(id,()->GL11.glTexParameteriv(target(id),name,values));PackedSampling.textureParameter(id,target(id),name,values.get(values.position()));}
    public static void glTextureParameteriv(int id,int name,int[] values){bound(id,()->GL11.glTexParameteriv(target(id),name,values));PackedSampling.textureParameter(id,target(id),name,values[0]);}
    public static void glTextureParameterfv(int id,int name,FloatBuffer values){bound(id,()->GL11.glTexParameterfv(target(id),name,values));PackedSampling.textureParameter(id,target(id),name,values.get(values.position()));}
    public static void glTextureParameterfv(int id,int name,float[] values){bound(id,()->GL11.glTexParameterfv(target(id),name,values));PackedSampling.textureParameter(id,target(id),name,values[0]);}
    public static void glTextureSubImage2D(int id,int level,int x,int y,int w,int h,int format,int type,ByteBuffer data){long previous=pixelVersion(id);bound(id,()->GL11.glTexSubImage2D(target(id),level,x,y,w,h,format,type,data));pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTextureSubImage2D(int id,int level,int x,int y,int w,int h,int format,int type,IntBuffer data){long previous=pixelVersion(id);bound(id,()->GL11.glTexSubImage2D(target(id),level,x,y,w,h,format,type,data));pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTextureSubImage2D(int id,int level,int x,int y,int w,int h,int format,int type,FloatBuffer data){long previous=pixelVersion(id);bound(id,()->GL11.glTexSubImage2D(target(id),level,x,y,w,h,format,type,data));pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTextureSubImage3D(int id,int level,int x,int y,int z,int w,int h,int d,int format,int type,ByteBuffer data){bound(id,()->GL12.glTexSubImage3D(target(id),level,x,y,z,w,h,d,format,type,data));pixelsChanged(id);}
    public static void glTextureSubImage3D(int id,int level,int x,int y,int z,int w,int h,int d,int format,int type,IntBuffer data){bound(id,()->GL12.glTexSubImage3D(target(id),level,x,y,z,w,h,d,format,type,data));pixelsChanged(id);}
    public static void glCompressedTexImage2D(int target,int level,int internal,int w,int h,int border,ByteBuffer data){GL13.glCompressedTexImage2D(target,level,internal,w,h,border,data);imageChanged(target,level,internal,w,h,1);}
    public static void glCompressedTexSubImage2D(int target,int level,int x,int y,int w,int h,int format,ByteBuffer data){GL13.glCompressedTexSubImage2D(target,level,x,y,w,h,format,data);pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    public static void glTexImage2D(int target,int level,int internal,int w,int h,int border,int format,int type,ByteBuffer data){GL11.glTexImage2D(target,level,internal,w,h,border,format,type,data);int texture=imageChanged(target,level,internal,w,h,1);}
    public static void glTexImage2D(int target,int level,int internal,int w,int h,int border,int format,int type,IntBuffer data){GL11.glTexImage2D(target,level,internal,w,h,border,format,type,data);int texture=imageChanged(target,level,internal,w,h,1);}
    public static void glTexImage2D(int target,int level,int internal,int w,int h,int border,int format,int type,long data){GL11.glTexImage2D(target,level,internal,w,h,border,format,type,data);imageChanged(target,level,internal,w,h,1);}
    public static void glTexSubImage2D(int target,int level,int x,int y,int w,int h,int format,int type,ByteBuffer data){int id=GL11.glGetInteger(binding(target));long previous=pixelVersion(id);GL11.glTexSubImage2D(target,level,x,y,w,h,format,type,data);pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTexSubImage2D(int target,int level,int x,int y,int w,int h,int format,int type,FloatBuffer data){int id=GL11.glGetInteger(binding(target));long previous=pixelVersion(id);GL11.glTexSubImage2D(target,level,x,y,w,h,format,type,data);pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTexSubImage2D(int target,int level,int x,int y,int w,int h,int format,int type,ShortBuffer data){int id=GL11.glGetInteger(binding(target));long previous=pixelVersion(id);GL11.glTexSubImage2D(target,level,x,y,w,h,format,type,data);pixelsChanged(id,level,0,x,y,w,h);}
    public static void glTexImage3D(int target,int level,int internal,int w,int h,int d,int border,int format,int type,ByteBuffer data){GL12.glTexImage3D(target,level,internal,w,h,d,border,format,type,data);imageChanged(target,level,internal,w,h,d);}
    public static void glTexImage3D(int target,int level,int internal,int w,int h,int d,int border,int format,int type,IntBuffer data){GL12.glTexImage3D(target,level,internal,w,h,d,border,format,type,data);imageChanged(target,level,internal,w,h,d);}
    public static void glTexImage3D(int target,int level,int internal,int w,int h,int d,int border,int format,int type,long data){GL12.glTexImage3D(target,level,internal,w,h,d,border,format,type,data);imageChanged(target,level,internal,w,h,d);}
    public static void glTexSubImage3D(int target,int level,int x,int y,int z,int w,int h,int d,int format,int type,ByteBuffer data){GL12.glTexSubImage3D(target,level,x,y,z,w,h,d,format,type,data);pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    public static void glTexSubImage3D(int target,int level,int x,int y,int z,int w,int h,int d,int format,int type,IntBuffer data){GL12.glTexSubImage3D(target,level,x,y,z,w,h,d,format,type,data);pixelsChanged(GL11.glGetInteger(binding(target>=34069&&target<=34074?34067:target)));}
    private static int imageChanged(int target,int level,int internal,int w,int h,int d){int canonical=target>=34069&&target<=34074?34067:target,id=GL11.glGetInteger(binding(canonical));target(id,canonical);Image old=IMAGES.get(id);if(level==0)IMAGES.put(id,new Image(canonical,internal,w,h,d,old==null?1:old.levels));else if(old!=null)IMAGES.put(id,new Image(old.target,old.format,old.width,old.height,old.depth,Math.max(old.levels,level+1)));fullPixelsChanged(id);return id;}
    public static int glGetTextureLevelParameteri(int id,int level,int name){int[] result=new int[1];bound(id,()->result[0]=GL11.glGetTexLevelParameteri(target(id),level,name));return result[0];}
    public static void glGetTextureImage(int id,int level,int format,int type,int size,long pointer){bound(id,()->GL11.nglGetTexImage(target(id),level,format,type,pointer));}
    private static void attach(int framebufferTarget,int attachment,int texture,int target,int level,int layer){if(target==3553)GL30.glFramebufferTexture2D(framebufferTarget,attachment,target,texture,level);else if(target==34067)GL30.glFramebufferTexture2D(framebufferTarget,attachment,GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X+layer,texture,level);else GL30.glFramebufferTextureLayer(framebufferTarget,attachment,texture,level,layer);}
    public static void glCopyImageSubData(int source,int sourceTarget,int sourceLevel,int sx,int sy,int sz,int destination,int destinationTarget,int destinationLevel,int dx,int dy,int dz,int width,int height,int depth){
        if(CompressedCopies.copy(source,sourceTarget,sourceLevel,sx,sy,sz,destination,destinationTarget,destinationLevel,dx,dy,dz,width,height,depth))return;
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read,draw;int[] cache=COPY_FBOS.get();if(cache[0]==0){cache[0]=GL30.glGenFramebuffers();cache[1]=GL30.glGenFramebuffers();}read=cache[0];draw=cache[1];
        int internal=IMAGES.containsKey(source)?IMAGES.get(source).format:queryInternal(source,sourceTarget,sourceLevel);
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_SCISSOR_TEST);boolean isDepth=format(internal)==GL11.GL_DEPTH_COMPONENT;int attachment=isDepth?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0;
        boolean complete=true;
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL11.glReadBuffer(isDepth?GL11.GL_NONE:GL30.GL_COLOR_ATTACHMENT0);GL11.glDrawBuffer(isDepth?GL11.GL_NONE:GL30.GL_COLOR_ATTACHMENT0);
            for(int layer=0;layer<depth;layer++){attach(GL30.GL_READ_FRAMEBUFFER,attachment,source,sourceTarget,sourceLevel,sz+layer);attach(GL30.GL_DRAW_FRAMEBUFFER,attachment,destination,destinationTarget,destinationLevel,dz+layer);
                if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE||GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE){complete=false;break;}
                GL30.glBlitFramebuffer(sx,sy,sx+width,sy+height,dx,dy,dx+width,dy+height,isDepth?GL11.GL_DEPTH_BUFFER_BIT:GL11.GL_COLOR_BUFFER_BIT,GL11.GL_NEAREST);}
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL32.glFramebufferTexture(GL30.GL_READ_FRAMEBUFFER,attachment,0,0);GL32.glFramebufferTexture(GL30.GL_DRAW_FRAMEBUFFER,attachment,0,0);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);}
        
        
        if(!complete&&!IntegerImageCopy.copy(source,sourceTarget,sourceLevel,sx,sy,sz,destination,destinationTarget,destinationLevel,dx,dy,dz,width,height,depth))throw new IllegalStateException("Cannot attach copy image framebuffer: format=0x"+Integer.toHexString(internal)+" targets="+sourceTarget+" -> "+destinationTarget);
        for(int z=0;z<depth;z++)pixelsChanged(destination,destinationLevel,dz+z,dx,dy,width,height);
        // Completed copies only become visible to caches after the operation has succeeded.
    }
    private static int queryInternal(int id,int target,int level){target(id,target);return glGetTextureLevelParameteri(id,level,GL11.GL_TEXTURE_INTERNAL_FORMAT);}
    public static void glTextureView(int id,int target,int original,int internal,int minLevel,int levels,int minLayer,int layers){
        Image image=IMAGES.get(original);if(image==null)throw new IllegalStateException("Texture view source has no known storage");target(id,target);int w=Math.max(1,image.width>>minLevel),h=Math.max(1,image.height>>minLevel);
        if(target==3553)glTextureStorage2D(id,levels,internal,w,h);else glTextureStorage3D(id,levels,internal,w,h,layers);
        View v=new View();v.original=original;v.level=minLevel;v.layer=minLayer;v.width=w;v.height=h;v.format=internal;VIEWS.put(id,v);
        for(int mip=0;mip<levels;mip++)glCopyImageSubData(original,image.target,minLevel+mip,0,0,minLayer,id,target,mip,0,0,0,Math.max(1,w>>mip),Math.max(1,h>>mip),layers);
    }
    public static java.util.Set<Integer> feedbackAttachments(int fbo){return new java.util.HashSet<>(ATTACHMENTS.getOrDefault(fbo,java.util.Map.of()).values());}public static void attached(int fbo,int attachment,int texture){Map<Integer,Integer> attachments=ATTACHMENTS.computeIfAbsent(fbo,k->new HashMap<>());if(texture==0)attachments.remove(attachment);else attachments.put(attachment,texture);}
    static boolean opaqueTarget2D(int fbo){Map<Integer,Integer> a=ATTACHMENTS.getOrDefault(fbo,Map.of());for(int i=0;i<3;i++){Image image=IMAGES.get(a.get(GL30.GL_COLOR_ATTACHMENT0+i));if(image==null||image.target()!=GL11.GL_TEXTURE_2D)return false;}Image z=IMAGES.get(DEPTH_ATTACHMENTS.get(fbo));return z!=null&&z.target()==GL11.GL_TEXTURE_2D;}
    public static void attachedDepth(int fbo,int texture){DEPTH_ATTACHMENTS.put(fbo,texture);}
    public static void deletedFramebuffer(int fbo){ATTACHMENTS.remove(fbo);DEPTH_ATTACHMENTS.remove(fbo);}
    public static void markDraw(){Opaque.drawn();markWrites();}
    public static void markDraw(int mode,int program){if(!Opaque.recording())Opaque.drawn();markWrites();}
    public static void markClear(int mask){Opaque.cleared(mask);markWrites();}
    private static void markWrites(){if(!Options.SAMPLERS)Samplers.invalidate();int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);for(int texture:ATTACHMENTS.getOrDefault(fbo,Map.of()).values())pixelsChanged(texture);View view=VIEWS.get(DEPTH_ATTACHMENTS.getOrDefault(fbo,0));if(view!=null)view.dirty=true;}
    public static void glDeleteTextures(int texture){Packing.textureDeleted(texture);PackedSampling.textureDeleted(texture);Opaque.deletedTexture(texture);Buffers.deletedTexture(texture);Samplers.deletedTexture(texture);TARGETS.remove(texture);IMAGES.remove(texture);VIEWS.remove(texture);VERSIONS.remove(texture);GL11.glDeleteTextures(texture);}
    public static void glDeleteTextures(int[] textures){for(int texture:textures)glDeleteTextures(texture);}
    public static void glDeleteTextures(IntBuffer textures){for(int i=textures.position();i<textures.limit();i++)glDeleteTextures(textures.get(i));}
    public static void synchronizeViews(){for(var entry:VIEWS.entrySet()){View v=entry.getValue();if(!v.dirty)continue;Image dest=IMAGES.get(v.original);glCopyImageSubData(entry.getKey(),target(entry.getKey()),0,0,0,0,v.original,dest.target,v.level,0,0,v.layer,v.width,v.height,1);v.dirty=false;}}
    public static void glClearTexImage(int texture,int level,int format,int type,ByteBuffer data){
        Image image=IMAGES.get(texture);if(image==null)throw new IllegalStateException("Clear texture without storage");
        int old=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),fbo=GL30.glGenFramebuffers();boolean depth=Textures.format(image.format)==GL11.GL_DEPTH_COMPONENT;
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer mask=org.lwjgl.BufferUtils.createByteBuffer(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
        int channels=(format==GL11.GL_RGBA||format==GL30.GL_RGBA_INTEGER)?4:format==GL11.GL_RGB?3:format==GL30.GL_RG?2:1;
        float[] values=new float[4];int[] words=new int[4];if(data!=null)for(int i=0;i<channels;i++){
            int at=data.position();if(type==GL11.GL_FLOAT)values[i]=data.order(ByteOrder.nativeOrder()).getFloat(at+i*4);
            else if(type==GL11.GL_UNSIGNED_BYTE)values[i]=(data.get(at+i)&255)/255f;
            else if(type==GL11.GL_INT||type==GL11.GL_UNSIGNED_INT)words[i]=data.order(ByteOrder.nativeOrder()).getInt(at+i*4);
            else throw new UnsupportedOperationException("Texture clear type "+type);
        }
        try{GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(true);GL11.glColorMask(true,true,true,true);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glDrawBuffer(depth?GL11.GL_NONE:GL30.GL_COLOR_ATTACHMENT0);
            int layers=image.target==GL12.GL_TEXTURE_3D?Math.max(1,image.depth>>level):image.depth;
            for(int z=0;z<layers;z++){
                attach(GL30.GL_DRAW_FRAMEBUFFER,depth?GL30.GL_DEPTH_ATTACHMENT:GL30.GL_COLOR_ATTACHMENT0,texture,image.target,level,z);
                if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete texture clear framebuffer");
                if(depth)GL30.glClearBufferfv(GL11.GL_DEPTH,0,values);else if(integer(image.format)){if(type==GL11.GL_UNSIGNED_INT)GL30.glClearBufferuiv(GL11.GL_COLOR,0,words);else GL30.glClearBufferiv(GL11.GL_COLOR,0,words);}else GL30.glClearBufferfv(GL11.GL_COLOR,0,values);
            }
        }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,old);GL30.glDeleteFramebuffers(fbo);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glDepthMask(depthMask);GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);}
        pixelsChanged(texture);View v=VIEWS.get(texture);if(v!=null)v.dirty=true;
    }

    public static void glCompressedTexImage2D(int t,int l,int f,int w,int h,int border,int size,long data){GL13.glCompressedTexImage2D(t,l,f,w,h,border,size,data);imageChanged(t,l,f,w,h,1);}
    public static void glCompressedTexImage3D(int t,int l,int f,int w,int h,int d,int border,ByteBuffer data){GL13.glCompressedTexImage3D(t,l,f,w,h,d,border,data);imageChanged(t,l,f,w,h,d);}
    public static void glCompressedTexImage3D(int t,int l,int f,int w,int h,int d,int border,int size,long data){GL13.glCompressedTexImage3D(t,l,f,w,h,d,border,size,data);imageChanged(t,l,f,w,h,d);}
    public static void glCompressedTexSubImage2D(int t,int l,int x,int y,int w,int h,int f,int size,long data){GL13.glCompressedTexSubImage2D(t,l,x,y,w,h,f,size,data);pixelsChanged(GL11.glGetInteger(binding(t>=34069&&t<=34074?34067:t)));}
    public static void glCompressedTexSubImage3D(int t,int l,int x,int y,int z,int w,int h,int d,int f,ByteBuffer data){GL13.glCompressedTexSubImage3D(t,l,x,y,z,w,h,d,f,data);pixelsChanged(GL11.glGetInteger(binding(t)));}
    public static void glCompressedTexSubImage3D(int t,int l,int x,int y,int z,int w,int h,int d,int f,int size,long data){GL13.glCompressedTexSubImage3D(t,l,x,y,z,w,h,d,f,size,data);pixelsChanged(GL11.glGetInteger(binding(t)));}
    public static void nglCompressedTexSubImage2D(int t,int l,int x,int y,int w,int h,int f,int size,long data){glCompressedTexSubImage2D(t,l,x,y,w,h,f,size,data);}
    public static void nglCompressedTexSubImage3D(int t,int l,int x,int y,int z,int w,int h,int d,int f,int size,long data){glCompressedTexSubImage3D(t,l,x,y,z,w,h,d,f,size,data);}
    public static void nglCompressedTexImage2D(int t,int l,int f,int w,int h,int border,int size,long data){glCompressedTexImage2D(t,l,f,w,h,border,size,data);}
    public static void nglCompressedTexImage3D(int t,int l,int f,int w,int h,int d,int border,int size,long data){glCompressedTexImage3D(t,l,f,w,h,d,border,size,data);}
    private Textures(){}
}
