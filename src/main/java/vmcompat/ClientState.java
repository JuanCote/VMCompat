package vmcompat;

import org.lwjgl.opengl.*;

/** Vertex-array and pixel-transfer state for model/atlas push/pop scopes. */
final class ClientState {
    private record Attribute(int buffer,int size,int type,int stride,boolean normalized,boolean integer,boolean enabled,int divisor,long pointer){}
    private static final int[] PIXEL={GL11.GL_PACK_ALIGNMENT,GL11.GL_UNPACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_UNPACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_UNPACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_UNPACK_SKIP_PIXELS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_UNPACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL12.GL_UNPACK_SKIP_IMAGES};
    private Attribute[] attributes;private int vao,array,element,pack,unpack;private int[] pixel;
    ClientState(int mask){
        if((mask&2)!=0){
            vao=Draws.vertexArrayBinding();array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);element=GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);attributes=new Attribute[GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)];
            for(int i=0;i<attributes.length;i++)attributes[i]=new Attribute(get(i,GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING),get(i,GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE),get(i,GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE),get(i,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE),get(i,GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED)!=0,get(i,GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER)!=0,get(i,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)!=0,get(i,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR),GL20.glGetVertexAttribPointer(i,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER));
        }
        if((mask&1)!=0){pixel=new int[PIXEL.length];for(int i=0;i<pixel.length;i++)pixel[i]=GL11.glGetInteger(PIXEL[i]);pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);}
    }
    private static int get(int attribute,int name){return GL20.glGetVertexAttribi(attribute,name);}
    void restore(){
        if(attributes!=null){
            Draws.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER,element);
            for(int i=0;i<attributes.length;i++){Attribute a=attributes[i];if(a.buffer!=0){GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,a.buffer);if(a.integer)GL30.glVertexAttribIPointer(i,a.size,a.type,a.stride,a.pointer);else GL20.glVertexAttribPointer(i,a.size,a.type,a.normalized,a.stride,a.pointer);}GL33.glVertexAttribDivisor(i,a.divisor);if(a.enabled)GL20.glEnableVertexAttribArray(i);else GL20.glDisableVertexAttribArray(i);}
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);Draws.divisorsUnknown();
        }
        if(pixel!=null){for(int i=0;i<pixel.length;i++)GL11.glPixelStorei(PIXEL[i],pixel[i]);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);}
    }
}
