package vmcompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL20;

/** Retry the deferred light pass after the bounded recipes hit the fragment sampler limit. */
final class LightingSamplerFallback {
    static final boolean ENABLED = Boolean.parseBoolean(Options.value("lightingSamplerFallback", "true"));
    private static final List<List<String>> STEPS = List.of(
        List.of("PIPELINE_REFLECTION_CLOUDS", "PIPELINE_FAR_SHADOWS", "PIPELINE_WATER_FFT"),
        List.of("PIPELINE_CONTACT_SHADOWS", "PIPELINE_REFLECTIONS"),
        List.of("PIPELINE_LAMP_SHADOWS", "PIPELINE_FLASHLIGHT_SHADOW")
    );

    static Map<Integer,String> retry(int program, Map<Integer,String> original,
                                    Map<Integer,Boolean> vertices,
                                    java.util.function.Consumer<Map<Integer,String>> compile) {
        if (!ENABLED || !NativeSamplers.ENABLED || original.values().stream().noneMatch(Packing::lightingSource))
            return original;

        Map<Integer,String> adjusted = new LinkedHashMap<>(original);
        List<String> disabled = new ArrayList<>();
        for (List<String> step : STEPS) {
            if (!GL20.glGetProgramInfoLog(program).contains("active fragment shader samplers")) break;
            int previous = disabled.size();
            for (String feature : step) {
                Pattern define = Pattern.compile("(?m)^(#define[ \\t]+" + feature + "[ \\t]+)[1-9][0-9]*\\b");
                boolean changed = false;
                for (var shader : adjusted.entrySet()) {
                    Matcher match = define.matcher(shader.getValue());
                    if (match.find()) {
                        shader.setValue(match.replaceAll("$1 0"));
                        changed = true;
                    }
                }
                if (changed) disabled.add(feature);
            }
            if (disabled.size() == previous) continue;
            compile.accept(Packing.lowerAll(program, adjusted, vertices));
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0) {
                Log.info("Lighting sampler fallback for program " + program + ": disabled " + String.join(", ", disabled));
                return adjusted;
            }
        }
        return original;
    }

    private LightingSamplerFallback() {}
}
