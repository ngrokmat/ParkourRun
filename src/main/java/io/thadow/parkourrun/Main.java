package io.thadow.parkourrun;

import io.thadow.parkourrun.api.PAPIExpansion;
import io.thadow.parkourrun.api.ParkourRunAPI;
import io.thadow.parkourrun.api.server.VersionHandler;
import io.thadow.parkourrun.arena.Arena;
import io.thadow.parkourrun.listeners.ArenaEventsListener;
import io.thadow.parkourrun.listeners.ArenaListener;
import io.thadow.parkourrun.arena.status.ArenaStatus;
import io.thadow.parkourrun.listeners.PlayerListener;
import io.thadow.parkourrun.managers.ArenaManager;
import io.thadow.parkourrun.commands.LeaveCommand;
import io.thadow.parkourrun.commands.ParkourRunCommand;
import io.thadow.parkourrun.managers.PlayerDataManager;
import io.thadow.parkourrun.managers.ScoreboardManager;
import io.thadow.parkourrun.managers.SignsManager;
import io.thadow.parkourrun.socket.DataSenderSocket;
import io.thadow.parkourrun.socket.DataSenderTask;
import io.thadow.parkourrun.utils.Utils;
import io.thadow.parkourrun.utils.configurations.MainConfiguration;
import io.thadow.parkourrun.utils.configurations.MessagesConfiguration;
import io.thadow.parkourrun.utils.configurations.ScoreboardsConfiguration;
import io.thadow.parkourrun.utils.configurations.SignsConfiguration;
import io.thadow.parkourrun.utils.storage.ActionCooldown;
import io.thadow.parkourrun.utils.storage.Storage;
import io.thadow.parkourrun.utils.storage.StorageType;
import io.thadow.parkourrun.utils.storage.type.mysql.MySQLConntection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Arrays;

@SuppressWarnings("all")
public class Main extends JavaPlugin {
    private static Main instance;
    public static VersionHandler VERSION_HANDLER;
    private static boolean mysql = false;
    private static boolean debug = false;
    private static boolean lobby = false;
    private static boolean versionSupported = false;
    private static boolean bungeecord = false;
    private static boolean lobbyServer = false;
    private static final String version = Bukkit.getServer().getClass().getName().split("\\.")[3];

    @Override
    public void onLoad() {
        super.onLoad();
        Class supp;

        try {
            supp = Class.forName("io.thadow.parkourrun.server." + version);
        } catch (ClassNotFoundException e) {
            Bukkit.getConsoleSender().sendMessage(Utils.colorize("&cUnsupported minecraft version: " + version));
            versionSupported = false;
            return;
        }
        try {
            Bukkit.getConsoleSender().sendMessage(Utils.colorize("&aTrying to load NMS suport for: " + version));
            VERSION_HANDLER = (VersionHandler) supp.getConstructor(Class.forName("org.bukkit.plugin.Plugin"), String.class).newInstance(this, version);
            versionSupported = true;
            Bukkit.getConsoleSender().sendMessage(Utils.colorize("&aNMS support sucessfully loaded for: " + version));
        } catch (InstantiationException | NoSuchMethodException | ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            Bukkit.getConsoleSender().sendMessage("&cUnable to load NMS support for: " + version);
            versionSupported = false;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        if (!versionSupported) {
            Bukkit.getConsoleSender().sendMessage(Utils.colorize("&cYour server version is unsupported: " + version));
            Bukkit.getConsoleSender().sendMessage(Utils.colorize("&cDisabling..."));
            Bukkit.getPluginManager().disablePlugin(this);
        }
        MainConfiguration.init();
        MessagesConfiguration.init();
        SignsConfiguration.init();
        ScoreboardsConfiguration.init();
        setDebug(getConfiguration().getBoolean("Configuration.Debug"));
        if (getConfiguration().contains("Configuration.Lobby.Location.World")) {
            lobby = true;
        }
        if (getConfiguration().getBoolean("Configuration.BungeeCord.Enabled")) {
            bungeecord = true;
        }
        if (getConfiguration().getBoolean("Configuration.BungeeCord.Is Lobby Server")) {
            lobbyServer = true;
        }
        getCommand("parkourrun").setExecutor(new ParkourRunCommand());
        getCommand("leave").setExecutor(new LeaveCommand());
        registerListeners(new ArenaListener(), new PlayerListener(), new ArenaEventsListener());
        ActionCooldown.createCooldown("cantWinMessage", 5);
        if (getConfiguration().getString("Configuration.StorageType").equals("TRANSFORM")) {
            String from = getConfiguration().getString("Configuration.Transform.From");
            String to = getConfiguration().getString("Configuration.Transform.To");
            Bukkit.getConsoleSender().sendMessage("&aTransforming data!");
            PlayerDataManager.getPlayerDataManager().transformData(from, to);
            return;
        }
        if (getConfiguration().getString("Configuration.StorageType").equals("MySQL")) {
            Storage.getStorage().setupStorage(StorageType.MySQL);
        } else {
            if (getConfiguration().getString("Configuration.StorageType").equals("LOCAL")) {
                Storage.getStorage().setupStorage(StorageType.LOCAL);
            }
        }
        PlayerDataManager.getPlayerDataManager().loadPlayers();
        ScoreboardManager.getScoreboardManager().startScoreboards();
        if (isBungeecord()) {
            if (!isLobbyServer()) {
                new ParkourRunAPI();
                new PAPIExpansion(this).register();
            }
        } else {
            new ParkourRunAPI();
            new PAPIExpansion(this).register();
        }
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        if (isBungeecord()) {
            if (isLobbyServer()) {
                if (Storage.getStorage().getStorageType() != StorageType.MySQL) {
                    Bukkit.getConsoleSender().sendMessage(Utils.colorize("&cYou can't use BungeeMode with out using MySQL Storage!"));
                    Bukkit.getPluginManager().disablePlugin(this);
                }
                return;
            } else {
                if (Storage.getStorage().getStorageType() != StorageType.MySQL) {
                    Bukkit.getConsoleSender().sendMessage(Utils.colorize("&cYou can't use BungeeMode with out using MySQL Storage!"));
                    Bukkit.getPluginManager().disablePlugin(this);
                }
                DataSenderSocket.lobbies.addAll(getConfiguration().getStringList("Configuration.BungeeCord.Sockets"));
                ArenaManager.getArenaManager().loadArenas();
                DataSenderTask.start();
            }
            return;
        } else {
            new SignsManager();
            ArenaManager.getArenaManager().loadArenas();
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        for (Arena arena : ArenaManager.getArenaManager().getArenas()) {
            String removeMessage = Utils.getRemoveMessage();
            DataSenderSocket.sendMessage(removeMessage);
            if (arena.getArenaStatus() == ArenaStatus.PLAYING) {
                arena.finalizeArena(true);
            } else if (arena.getArenaStatus() == ArenaStatus.ENDING) {
                arena.restoreWaitingZone();
                for (Player players : arena.getPlayers()) {
                    players.teleport(players.getWorld().getSpawnLocation());
                }
                arena.getPlayers().clear();
                arena.setArenaStatus(ArenaStatus.DISABLED);
            }
        }
        if (mysql) {
            try {
                MySQLConntection.getConnection().close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        PlayerDataManager.getPlayerDataManager().savePlayers();
        if (isBungeecord() && !isLobbyServer()) {
            DataSenderSocket.disable();
        }
    }

    public static Main getInstance() {
        return instance;
    }

    public void registerListeners(Listener... listeners) {
        Arrays.stream(listeners).forEach(listener -> getInstance().getServer().getPluginManager().registerEvents(listener, getInstance()));
    }

    public FileConfiguration getConfiguration() {
        return MainConfiguration.mainConfiguration.getConfiguration();
    }

    public static FileConfiguration getMessagesConfiguration() {
        return MessagesConfiguration.messagesConfiguration.getConfiguration();
    }

    public static FileConfiguration getSignsConfiguration() {
        return SignsConfiguration.signsConfiguration.getConfiguration();
    }

    public static FileConfiguration getScoreboardsConfiguration() {
        return ScoreboardsConfiguration.scoreboardsConfiguration.getConfiguration();
    }

    public static boolean isMySQLEnabled() {
        return mysql;
    }

    public static void setMysql(boolean mysql) {
        Main.mysql = mysql;
    }

    public static boolean isDebugEnabled() {
        return debug;
    }

    public static void setDebug(boolean debug) {
        Main.debug = debug;
    }

    public static boolean isLobbyPresent() {
        return lobby;
    }

    public static void setIsLobbyPresent(boolean isLobbyPresent) {
        lobby = isLobbyPresent;
    }

    public static boolean isBungeecord() {
        return bungeecord;
    }

    public static boolean isLobbyServer() {
        return lobbyServer;
    }
}
