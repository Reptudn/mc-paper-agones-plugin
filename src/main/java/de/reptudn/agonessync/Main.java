package de.reptudn.agonessync;

import de.reptudn.agonessync.agones.Agones;
import de.reptudn.agonessync.events.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private Agones agones;

    @Override
    public void onEnable() {
        agones = Agones.fromEnv();

        // health ping every 2s, async
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> agones.health(), 40L, 40L);

        // first tick = worlds/plugins loaded
        Bukkit.getScheduler().runTask(this, () ->
                agones.ready().thenAccept(ok -> getLogger().info("Agones ready: " + ok)));

        int maxPlayers = Bukkit.getMaxPlayers();
        getServer().getPluginManager().registerEvents(new PlayerListener(this, maxPlayers), this);
        agones.setCounterCapacity("players", maxPlayers);
    }

    @Override
    public void onDisable() {
        agones.shutdown().join();
    }

    public Agones getAgones() {
        return agones;
    }
}
