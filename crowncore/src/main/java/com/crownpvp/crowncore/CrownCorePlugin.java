package com.crownpvp.crowncore;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;

import java.util.logging.Level;

public class CrownCorePlugin extends JavaPlugin {

    private HikariDataSource mysql;
    private Connection sqlite;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDatabase();
        initSchema();
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                ensurePlayer(e.getPlayer());
            }
        }, this);
        getLogger().info("CrownCore enabled.");
    }

    @Override
    public void onDisable() {
        try {
            if (mysql != null) mysql.close();
            if (sqlite != null && !sqlite.isClosed()) sqlite.close();
        } catch (Exception ignored) {}
        getLogger().info("CrownCore disabled.");
    }

    private boolean isMySQL() {
        return getConfig().getString("db.type","sqlite").equalsIgnoreCase("mysql");
    }

    private void setupDatabase() {
        if (isMySQL()) {
            String host = getConfig().getString("db.host");
            int port = getConfig().getInt("db.port");
            String name = getConfig().getString("db.name");
            String user = getConfig().getString("db.user");
            String pass = getConfig().getString("db.password");

            HikariConfig cfg = new HikariConfig();
            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false&characterEncoding=utf8&autoReconnect=true");
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setMaximumPoolSize(10);
            cfg.setMinimumIdle(2);
            cfg.setPoolName("CrownCore-Hikari");
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            mysql = new HikariDataSource(cfg);
        } else {
            try {
                File folder = new File(getDataFolder(), "");
                if (!folder.exists()) folder.mkdirs();
                File db = new File(getDataFolder(), "data.db");
                Class.forName("org.sqlite.JDBC");
                sqlite = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "SQLite init failed", e);
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (isMySQL()) return mysql.getConnection();
        return sqlite;
    }

    private void initSchema() {
        String sqlPlayers = "CREATE TABLE IF NOT EXISTS crown_players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "coins INT NOT NULL DEFAULT 0," +
                "gems INT NOT NULL DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sqlPlayers)) {
            ps.executeUpdate();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "DB init failed", e);
        }
    }

    private void ensurePlayer(Player p) {
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO crown_players(uuid,name) VALUES(?,?)")) {
                // For MySQL, INSERT IGNORE is also valid. SQLite supports OR IGNORE.
                if (isMySQL()) {
                    try (PreparedStatement psMy = c.prepareStatement("INSERT IGNORE INTO crown_players(uuid,name) VALUES(?,?)")) {
                        psMy.setString(1, p.getUniqueId().toString());
                        psMy.setString(2, p.getName());
                        psMy.executeUpdate();
                        return;
                    }
                }
                ps.setString(1, p.getUniqueId().toString());
                ps.setString(2, p.getName());
                ps.executeUpdate();
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "ensurePlayer failed", ex);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("crown")) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        Player p = (Player) sender;
        ensurePlayer(p);
        int coins = getCoins(p);
        p.sendMessage("§b[CrownPvP] §7Ping! Tes Coins: §e" + coins + " §7(DB: " + (isMySQL() ? "MySQL" : "SQLite") + ")");
        return true;
    }

    private int getCoins(Player p) {
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT coins FROM crown_players WHERE uuid=?")) {
            ps.setString(1, p.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "getCoins failed", ex);
        }
        return 0;
    }
}
