package de.reptudn.agonessync.utils;

public class ServerUtils {

    public static int GetPlayerCount() {
        return org.bukkit.Bukkit.getOnlinePlayers().size();
    }

    public static boolean IsServerFull() {
        return GetPlayerCount() >= org.bukkit.Bukkit.getMaxPlayers();
    }

    public static boolean IsServerEmpty() {
        return GetPlayerCount() == 0;
    }

}
