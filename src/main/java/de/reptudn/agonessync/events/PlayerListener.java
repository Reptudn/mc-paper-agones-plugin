package de.reptudn.agonessync.events;

import de.reptudn.agonessync.Main;
import de.reptudn.agonessync.agones.Agones;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final Main plugin;
    private final Agones agones;
    private final int maxPlayers;
    private boolean markedFull = false;

    public PlayerListener(Main plugin, int maxPlayers) {
        this.plugin = plugin;
        this.agones = plugin.getAgones();
        this.maxPlayers = maxPlayers;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        update(Bukkit.getOnlinePlayers().size());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // player is still counted during the quit event
        update(Bukkit.getOnlinePlayers().size() - 1);
    }

    private synchronized void update(int players) {
        // Counter approach: always report the number
        agones.setCounterCount("players", players);

        // "Full" approach: Ready <-> Allocated
        if (!markedFull && players >= maxPlayers) {
            markedFull = true;
            agones.allocate();
        } else if (markedFull && players < maxPlayers) {
            markedFull = false;
            agones.ready();
        }
    }
}