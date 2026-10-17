package de.reptudn.agonessync.events;

import de.reptudn.agonessync.agones.Agones;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerJoinLeaveEvent implements Listener {

    Agones agones;
    public PlayerJoinLeaveEvent(@NotNull Agones agones) {
        this.agones = agones;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        agones.allocate();
    }

    // we don't yet need to handle player leave because we want a little buffer
    // after the last player leaves before we put it back to ready and that should
    // already work with the normal scheduler. Additionally, we could start a detached
    // scheduler to wait for a few seconds and then check how many players are on the server.
}
