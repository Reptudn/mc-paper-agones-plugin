package de.reptudn.agonessync;

import de.reptudn.agonessync.agones.Agones;
import de.reptudn.agonessync.events.PlayerJoinLeaveEvent;
import de.reptudn.agonessync.utils.ServerUtils;
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

        Bukkit.getPluginManager().registerEvents(new PlayerJoinLeaveEvent(agones), this);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            agones.health();

            if (!ServerUtils.IsServerEmpty()) {
                agones.allocate();
            } else {
                agones.ready();
            }

        }, 0, 2, java.util.concurrent.TimeUnit.SECONDS);

        Bukkit.getScheduler().runTask(this, () -> agones.ready());
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.shutdownNow();
        if (agones != null) agones.shutdown();
    }
}
