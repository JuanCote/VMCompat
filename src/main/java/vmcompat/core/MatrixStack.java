package vmcompat.core;

import java.util.*;

public final class MatrixStack {
    private final ArrayDeque<float[]> stack = new ArrayDeque<>();
    private float[] current = identity();
    public static float[] identity() { float[] m=new float[16]; m[0]=m[5]=m[10]=m[15]=1; return m; }
    public float[] value() { return current; }
    public void loadIdentity() { current=identity(); }
    public void load(float[] matrix) { if(matrix.length!=16)throw new IllegalArgumentException(); current=matrix.clone(); }
    public void push() { if(stack.size()>=64)throw new IllegalStateException("Matrix stack overflow"); stack.push(current.clone()); }
    public void pop() { if(stack.isEmpty())throw new IllegalStateException("Matrix stack underflow"); current=stack.pop(); }
    public void multiply(float[] b) { current=multiply(current,b); }
    public static float[] multiply(float[] a,float[] b) {
        float[] c=new float[16];
        for(int col=0;col<4;col++)for(int row=0;row<4;row++)for(int k=0;k<4;k++) c[col*4+row]+=a[k*4+row]*b[col*4+k];
        return c;
    }
    public void translate(float x,float y,float z) { float[] m=identity();m[12]=x;m[13]=y;m[14]=z;multiply(m); }
    public void scale(float x,float y,float z) { float[] m=identity();m[0]=x;m[5]=y;m[10]=z;multiply(m); }
    public void rotate(float degrees,float x,float y,float z) {
        double len=Math.sqrt(x*x+y*y+z*z);if(len==0)return;x/=len;y/=len;z/=len;
        float c=(float)Math.cos(Math.toRadians(degrees)),s=(float)Math.sin(Math.toRadians(degrees)),t=1-c;
        float[] m=identity();
        m[0]=t*x*x+c;m[4]=t*x*y-s*z;m[8]=t*x*z+s*y;
        m[1]=t*x*y+s*z;m[5]=t*y*y+c;m[9]=t*y*z-s*x;
        m[2]=t*x*z-s*y;m[6]=t*y*z+s*x;m[10]=t*z*z+c;multiply(m);
    }
    public void ortho(double l,double r,double b,double t,double n,double f) {
        float[] m=identity();m[0]=(float)(2/(r-l));m[5]=(float)(2/(t-b));m[10]=(float)(-2/(f-n));
        m[12]=(float)(-(r+l)/(r-l));m[13]=(float)(-(t+b)/(t-b));m[14]=(float)(-(f+n)/(f-n));multiply(m);
    }
}
