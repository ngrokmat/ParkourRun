package io.thadow.parkourrun.commands;

import io.thadow.parkourrun.arena.Arena;
import io.thadow.parkourrun.arena.status.ArenaStatus;
import io.thadow.parkourrun.managers.ArenaManager;
import io.thadow.parkourrun.utils.Permission;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;

public class LeaveCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }
        final Player player = (Player) sender;
        if (player.hasPermission("parkourrun.commands.leave")) {
            final Arena arena = ArenaManager.getArenaManager().getArena(player);
            if (arena == null) {
                player.sendMessage("Este comando solo se puede ejecutar en arena");
                return true;
            }
            ArenaManager.getArenaManager().removePlayer(player, arena.getArenaStatus() == ArenaStatus.ENDING);
            player.teleport(player.getWorld().getSpawnLocation());
        } else {
            Permission.deny(player, "parkourrun.commands.leave");
        }
        return false;
    }
}