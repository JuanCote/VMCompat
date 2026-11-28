package vmcompat;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
public final class Metadata {
    public static String artifactSha(){try{var source=Agent.class.getProtectionDomain().getCodeSource();/* Storm defines mod classes without a CodeSource. */Path file=source==null||source.getLocation()==null?WorkshopBootstrap.selfFromResource():Path.of(source.getLocation().toURI());return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}catch(Exception e){throw new IllegalStateException("Artifact identity unavailable",e);}}
    static void write(Path path)throws Exception{
        StringBuilder options=new StringBuilder();for(var e:new TreeMap<>(Options.describe()).entrySet()){if(!options.isEmpty())options.append(',');options.append('"').append(e.getKey()).append("\":");if(e.getValue() instanceof Boolean||e.getValue() instanceof Number)options.append(e.getValue());else options.append('"').append(e.getValue()).append('"');}
        Files.writeString(path,"{\"schema_version\":7,\"version\":\""+Agent.VERSION+"\",\"jar_sha256\":\""+artifactSha()+"\",\"backend\":\""+Options.OPAQUE+"\",\"native_sha256\":\""+Opaque.nativeSha()+"\",\"options\":{"+options+"},\"frame_breakdown\":"+Breakdown.json(Breakdown.metadata())+",\"mirror_limit_bytes\":16777216,\"mirror_object_limit_bytes\":1048576,\"timing\":\"CPU wall; nested compatibility times overlap pass timings; requested bytes are not measured physical traffic\"}\n");
    }
}
