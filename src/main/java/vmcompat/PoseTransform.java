package vmcompat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

/** Select Viewpoint's CPU palettes on the Workshop build's OpenGL 4.1 backend. */
final class PoseTransform {
    static byte[] rewrite(String owner, byte[] bytes) {
        if (!owner.equals("viewpoint/models/ModelCapture") && !owner.equals("viewpoint/models/CharacterRate")
                && !owner.equals("viewpoint/render/BakedPoseGpu") && !owner.equals("viewpoint/render/LivePoseGpu")) {
            return bytes;
        }
        ClassFile cf = ClassFileSupport.create();
        return cf.transformClass(cf.parse(bytes), (builder, element) -> {
            if (!(element instanceof MethodModel method) || method.code().isEmpty()) {
                builder.with(element);
                return;
            }
            String signature = owner + "." + method.methodName().stringValue() + method.methodType().stringValue();
            switch (signature) {
                case "viewpoint/models/ModelCapture.live(Lviewpoint/core/Frame;Lzombie/core/skinnedmodel/model/ModelSlotRenderData;Lzombie/characters/IsoGameCharacter;ZLviewpoint/models/ModelCapture$Pose;)I" ->
                    // Return the normal CPU fallback sentinel before the live path builds GPU skin tables.
                    builder.withMethodBody("live", method.methodTypeSymbol(), method.flags().flagsMask(), code -> code.iconst_m1().ireturn());
                case "viewpoint/models/CharacterRate.baked(Lviewpoint/models/CharacterRate$Entry;Lzombie/characters/IsoGameCharacter;FZ)Z" ->
                    builder.withMethodBody("baked", method.methodTypeSymbol(), method.flags().flagsMask(), code -> code.iconst_0().ireturn());
                case "viewpoint/render/BakedPoseGpu.receive()V" ->
                    // ModelPass invokes receive even when there are no GPU poses.
                    builder.withMethodBody("receive", method.methodTypeSymbol(), method.flags().flagsMask(), code -> code.return_());
                case "viewpoint/render/BakedPoseGpu.storageAlignment()I" ->
                    // CPU palette/lookup streams also call this helper. Their vec4 rows need 16-byte alignment.
                    builder.withMethodBody("storageAlignment", method.methodTypeSymbol(), method.flags().flagsMask(), code -> code.loadConstant(16).ireturn());
                case "viewpoint/render/BakedPoseGpu.upload(II[F)V", "viewpoint/render/LivePoseGpu.record(I[FI)V" ->
                    // Direct callers outside the normal model path must not accumulate unconsumed uploads.
                    builder.withMethodBody(method.methodName().stringValue(), method.methodTypeSymbol(), method.flags().flagsMask(), code -> {
                        ClassDesc exception = ClassDesc.of("java.lang.UnsupportedOperationException");
                        code.new_(exception).dup().loadConstant("Viewpoint GPU poses require compute shaders and SSBO writes; VMCompat uses CPU poses.");
                        code.invokespecial(exception, "<init>", MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V")).athrow();
                    });
                default -> builder.with(element);
            }
        });
    }

    private PoseTransform() {}
}
