package de.reptudn.agonessync;

import de.reptudn.agonessync.agones.Agones;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class Main extends JavaPlugin {

    private Agones agones;
    private ScheduledExecutorService scheduler;

    @Override
    public void onEnable() {
        agones = Agones.fromEnv(getLogger());

        // Own thread, independent of the Bukkit tick loop, so pings start immediately
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(agones::health, 0, 2, java.util.concurrent.TimeUnit.SECONDS);

        // Runs on the first tick after the server finished loading
        Bukkit.getScheduler().runTask(this, () -> agones.ready());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.shutdownNow();
    }

}
