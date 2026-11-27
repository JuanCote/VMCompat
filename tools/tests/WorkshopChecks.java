package vmcompat;
import java.nio.file.*;import java.util.*;import java.util.jar.*;

/** Small packaging/bootstrap checks, not a second renderer or a substitute for the approval UI. */
public final class WorkshopChecks {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String kind=args[0];
        switch(kind){
        case "defaults":
            check(Options.PICK_EVERY==3&&Options.PICKING.equals("depthStaging"),"release picking defaults");
            check(Options.OPAQUE.equals("opengl")&&!Options.PICK_EARLY&&!Options.FAR_BATCH,"experiments disabled");
            check(Options.FAR_MASK&&Options.RANGES&&Options.BATCH&&Options.MIRROR&&Options.ARRAYS,"accepted GL options");
            check(Options.TELEMETRY.equals("hud")&&!Options.FRAME_BREAKDOWN&&!Options.PICK_DETAIL,"no default detailed recording");
            break;
        case "overrides":check(Options.PICK_EVERY==1&&Options.TELEMETRY.equals("off"),"JVM overrides file");break;
        case "diag":check(Options.TELEMETRY.equals("full")&&Options.FRAME_BREAKDOWN&&Options.PICK_DETAIL,"explicit Lua diagnostic settings");break;
        case "unit":
            Properties p=new Properties();check(WorkshopBootstrap.conflict(List.of("-javaagent:ZombieBuddy.jar"),p)==null,"preserve Buddy");
            check(WorkshopBootstrap.conflict(List.of("-javaagent:/old/VMCompat.jar"),p)!=null,"legacy VMCompat");
            check(WorkshopBootstrap.conflict(List.of("-javaagent:/old/VMPerf.jar"),p)!=null,"legacy VMPerf");
            p.setProperty("vpmac.active","true");check(WorkshopBootstrap.conflict(List.of(),p)!=null,"VPMac conflict");
            Path file=Path.of(args[1]);Files.writeString(file,"VERSION = 1,\nmods\n{\n mod = VMCompat,\n mod = Viewpoint,\n mod = ZombieBuddy,\n}\n");
            check(WorkshopBootstrap.selected(file).equals(Set.of("VMCompat","Viewpoint","ZombieBuddy")),"selection parsing");
            boolean refused=false;try{WorkshopBootstrap.version("42.22","42.21","PZ");}catch(IllegalStateException e){refused=e.getMessage().contains("42.21");}check(refused,"exact version refusal");
            check(WorkshopBootstrap.expectedViewpoint("3c4a7c5f149d784758c6fb5f8fa85f3a21d572e38bc081400e89035f8120d287").equals("0.1.6.2"),"current PZ/Viewpoint pair allowed for the private test");
            refused=false;try{WorkshopBootstrap.expectedViewpoint("unknown");}catch(IllegalStateException e){refused=true;}check(refused,"unknown binary still requires compatibility checking");
            WorkshopBootstrap.validateViewpoint("0.1.6.2","0.1.7","8d0d5a73227d3ba0724cab076f621435ebaed8ed2c73bfaf7d32a90b0a249e1f");
            WorkshopBootstrap.validateViewpoint("0.1.6.2","0.1.6.2","legacy");
            for(String[] bad:List.of(new String[]{"0.1.6.1","0.1.7","8d0d5a73227d3ba0724cab076f621435ebaed8ed2c73bfaf7d32a90b0a249e1f"},new String[]{"0.1.6.2","0.1.7","modified"},new String[]{"0.1.6.2","0.1.8","future"})) {
                boolean rejected=false;try{WorkshopBootstrap.validateViewpoint(bad[0],bad[1],bad[2]);}catch(IllegalStateException e){rejected=true;}check(rejected,"unqualified dependency rejected");
            }
            check(WorkshopBootstrap.namedTarget(zombie.ZomboidFileSystem.class),"named host remains a target");
            check(!WorkshopBootstrap.namedTarget(String[].class)&&!WorkshopBootstrap.namedTarget(int.class),"non-classfile VM types excluded");
            break;
        case "lua":
            for(int i=1;i<args.length;i++)try(var in=Files.newInputStream(Path.of(args[i]))){se.krka.kahlua.luaj.compiler.LuaCompiler.loadis(in,args[i],null);}
            break;
        case "hud":
            Telemetry.start();Telemetry.frame(1_000_000_000,0,100);Telemetry.frame(1_010_000_000,0,100);Thread.sleep(1100);Telemetry.stopForTest();
            check(Files.exists(Log.ROOT.resolve("performance-hud.txt")),"HUD continues");
            check(Files.exists(Log.ROOT.resolve("error-report.txt")),"error report");
            try(var files=Files.list(Log.ROOT)){check(files.noneMatch(x->x.toString().endsWith(".csv")),"no default CSV");}
            break;
        case "retention":
            Files.createDirectories(Log.ROOT);Path old=Log.ROOT.resolve("frames-old.csv"),foreign=Log.ROOT.resolve("keep-user.txt");
            Files.writeString(old,"test");Files.setLastModifiedTime(old,java.nio.file.attribute.FileTime.fromMillis(0));Files.writeString(foreign,"preserve");
            ReleaseLogs.trim();check(!Files.exists(old)&&Files.exists(foreign),"own-file retention scope");
            break;
        default:throw new AssertionError(kind);
        }
        System.out.println("PASS "+kind);
    }
}
