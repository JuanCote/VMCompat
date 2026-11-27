package vmcompat.storm;

import io.pzstorm.storm.event.core.StormEventDispatcher;
import io.pzstorm.storm.event.core.SubscribeEvent;
import io.pzstorm.storm.event.lua.OnMainMenuEnterEvent;
import io.pzstorm.storm.mod.ZomboidMod;
import vmcompat.WorkshopBootstrap;

/**
 * Storm loader boundary. Storm instantiates this class while it registers its java mods, before it starts the game,
 * which is the earliest point a Storm mod runs. ZombieBuddy never loads this class.
 */
public final class StormMod implements ZomboidMod {
    public StormMod() { WorkshopBootstrap.startStorm(); }

    @Override public void registerEventHandlers() { StormEventDispatcher.registerEventHandler(StormMod.class); }

    /** Same moment the Workshop Lua asks for the class-loading audit under ZombieBuddy. */
    @SubscribeEvent public static void onMainMenuEnter(OnMainMenuEnterEvent event) { WorkshopBootstrap.auditLoading(); }
}
