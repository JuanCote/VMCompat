package workshoptests;
import java.nio.file.*;
import se.krka.kahlua.j2se.J2SEPlatform;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.*;

/** Actual PZ ISModalDialog constructor + actual mod Lua; no game/UI main loop. */
public final class DialogChecks {
    static void run(KahluaThread thread,KahluaTable env,Path path)throws Exception {
        try(var in=Files.newInputStream(path)){thread.call(LuaCompiler.loadis(in,path.getFileName().toString(),env),new Object[0]);}
    }
    public static void main(String[] args)throws Exception {
        Path game=Path.of(args[0]),media=Path.of(args[1]),fixture=Path.of(args[2]);int count=0;
        for(String language:new String[]{"EN","RU"})for(int[] size:new int[][]{{640,480},{1280,800},{1920,1080},{3024,1964}})
            for(int scale:new int[]{1,2})for(String state:new String[]{"INACTIVE","NOT_STARTED","INCOMPATIBLE","FAILED","ACTIVE"}) {
                var platform=J2SEPlatform.getInstance();var env=platform.newEnvironment();var thread=new KahluaThread(platform,env);
                env.rawset("TEST_WIDTH",(double)size[0]);env.rawset("TEST_HEIGHT",(double)size[1]);env.rawset("TEST_SCALE",(double)scale);env.rawset("TEST_STATE",state);env.rawset("TEST_LANGUAGE",language);
                run(thread,env,fixture.resolve("dialog-environment.lua"));
                run(thread,env,game.resolve("media/lua/client/ISUI/ISModalDialog.lua"));
                run(thread,env,media.resolve("lua/shared/Translate/"+language+"/UI_"+language+".txt"));
                run(thread,env,media.resolve("lua/client/VMCompatWorkshop.lua"));
                run(thread,env,fixture.resolve("dialog-assertions.lua"));count++;
            }
        System.out.println("PASS "+count+" dialog scenarios: real PZ autosizing, centered final bounds, EN/RU, UI scales, startup/error/active and diagnostics.");
    }
}
