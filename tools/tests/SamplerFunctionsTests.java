package vmcompat;
/** Focused regression for the helper signatures that forced full packing. */
public final class SamplerFunctionsTests {
 static void check(boolean v,String text){if(!v)throw new AssertionError(text);}
 public static void main(String[] a){String original="// sampler2D comment\n#define ART_SAMPLER sampler2D\nvec4 sampleA(ART_SAMPLER tex, vec2 uv){return texture(tex,uv);}\n#undef ART_SAMPLER\n#define ART_SAMPLER sampler2DArray\nvec4 sampleB(ART_SAMPLER tex, vec3 uv){return texture(tex,uv);}\n#undef ART_SAMPLER\nvec4 pair(sampler2D a,sampler2D b, vec2 uv){return texture(a,uv)+texture(b,uv);}\n";String out=SamplerFunctions.overloads(original);check(out.contains("sampleA(VmImage tex"),"2D macro overload");check(out.contains("sampleA(ART_SAMPLER tex"),"native helper retained");check(!out.contains("sampleB(VmImage tex"),"array helper stays native");check(out.contains("pair(VmImage a,sampler2D b")&&out.contains("pair(sampler2D a,VmImage b")&&out.contains("pair(VmImage a,VmImage b"),"mixed argument overloads");check(out.startsWith("// sampler2D comment"),"comments preserved");System.out.println("PASS scoped aliases/native/value helpers");}
}
