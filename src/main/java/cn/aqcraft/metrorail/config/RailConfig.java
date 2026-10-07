package cn.aqcraft.metrorail.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * config.yml 的强类型视图。
 */
public final class RailConfig {

    private String language;
    private String defaultVehicle;
    private int tickInterval;
    private boolean releaseOnDismount;
    private double reverserMaxSpeed;
    private int maxConsistSize;
    private double scanRadius;
    private boolean scoreboard;
    private boolean bossbar;
    private int maxGraphNodes;
    private double maLookahead;
    private double overspeedWarn;
    private int timsTimeout;
    private double timsMaxSpacing;
    private boolean autoSigns;
    private boolean webEnabled;
    private int webPort;
    private String webToken;
    private boolean webLoopbackOnly;
    private String hotbarSlot;

    public void load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        language = c.getString("language", "zh_CN");
        defaultVehicle = c.getString("default-vehicle", "default");
        tickInterval = Math.max(1, c.getInt("drive.tick-interval", 1));
        releaseOnDismount = c.getBoolean("drive.release-on-dismount", true);
        reverserMaxSpeed = c.getDouble("drive.reverser-max-speed", 0.05);
        maxConsistSize = Math.max(1, c.getInt("drive.max-consist-size", 20));
        scanRadius = Math.max(1.0, c.getDouble("drive.scan-radius", 8.0));
        scoreboard = c.getBoolean("display.scoreboard", true);
        bossbar = c.getBoolean("display.bossbar", true);
        maxGraphNodes = Math.max(64, c.getInt("rail.max-graph-nodes", 20000));
        maLookahead = Math.max(16.0, c.getDouble("rail.ma-lookahead", 256.0));
        overspeedWarn = Math.max(1.05, c.getDouble("rail.overspeed-warn", 1.10));
        timsTimeout = Math.max(5, c.getInt("rail.tims-timeout-ticks", 40));
        timsMaxSpacing = Math.max(0.0, c.getDouble("rail.tims-max-spacing", 8.0));
        autoSigns = c.getBoolean("signs.auto-enable", true);
        webEnabled = c.getBoolean("web.enabled", false);
        webPort = Math.max(1, Math.min(65535, c.getInt("web.port", 25690)));
        webToken = c.getString("web.token", "");
        webLoopbackOnly = c.getBoolean("web.loopback-only", true);
        hotbarSlot = c.getString("drive.hotbar-slot", "off");
    }

    public String language() {
        return language;
    }

    public String defaultVehicle() {
        return defaultVehicle;
    }

    public int tickInterval() {
        return tickInterval;
    }

    public boolean releaseOnDismount() {
        return releaseOnDismount;
    }

    public double reverserMaxSpeed() {
        return reverserMaxSpeed;
    }

    public int maxConsistSize() {
        return maxConsistSize;
    }

    public double scanRadius() {
        return scanRadius;
    }

    public boolean scoreboard() {
        return scoreboard;
    }

    public boolean bossbar() {
        return bossbar;
    }

    public int maxGraphNodes() {
        return maxGraphNodes;
    }

    public double maLookahead() {
        return maLookahead;
    }

    public double overspeedWarn() {
        return overspeedWarn;
    }

    public int timsTimeout() {
        return timsTimeout;
    }

    public double timsMaxSpacing() {
        return timsMaxSpacing;
    }

    public boolean autoSigns() {
        return autoSigns;
    }

    public boolean webEnabled() {
        return webEnabled;
    }

    public int webPort() {
        return webPort;
    }

    public String webToken() {
        return webToken;
    }

    public boolean webLoopbackOnly() {
        return webLoopbackOnly;
    }

    public String hotbarSlot() {
        return hotbarSlot;
    }
}
