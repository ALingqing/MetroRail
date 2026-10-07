package cn.aqcraft.metrorail;

import cn.aqcraft.metrorail.command.MrCommand;
import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.data.MileageStore;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.VehicleRegistry;
import cn.aqcraft.metrorail.hud.CabGui;
import cn.aqcraft.metrorail.hud.CabListener;
import cn.aqcraft.metrorail.hud.DriveHud;
import cn.aqcraft.metrorail.hud.HotbarController;
import cn.aqcraft.metrorail.i18n.Messages;
import cn.aqcraft.metrorail.listener.RailListener;
import cn.aqcraft.metrorail.metro.MetroHook;
import cn.aqcraft.metrorail.rail.RailServices;
import cn.aqcraft.metrorail.signs.AutoSignService;
import cn.aqcraft.metrorail.web.DispatchWebServer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * MetroRail 主类：Metro 的驾驶/列车控制附属。
 */
public final class MetroRailPlugin extends JavaPlugin {

    private RailConfig config;
    private Messages messages;
    private VehicleRegistry registry;
    private MileageStore mileageStore;
    private RailServices rail;
    private DriveManager driveManager;
    private DriveHud hud;
    private CabGui cabGui;
    private CabListener cabListener;
    private AutoSignService autoSigns;
    private DispatchWebServer web;
    private MetroHook metro;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new RailConfig();
        config.load(this);
        messages = new Messages(this);
        messages.load(config.language());
        registry = new VehicleRegistry(this);
        registry.load();
        mileageStore = new MileageStore(this);
        mileageStore.load();
        metro = new MetroHook();

        rail = new RailServices(this);
        rail.loadAll();

        driveManager = new DriveManager(this, config, registry, mileageStore, rail);
        driveManager.loadConsists();
        hud = new DriveHud(config, messages);
        cabGui = new CabGui(config, messages, driveManager);
        cabListener = new CabListener(cabGui);
        autoSigns = new AutoSignService(this, config, driveManager, rail);
        web = new DispatchWebServer(this, config, driveManager, rail);

        getServer().getPluginManager().registerEvents(cabListener, this);
        getServer().getPluginManager().registerEvents(new HotbarController(config, driveManager), this);
        getServer().getPluginManager().registerEvents(new RailListener(this, config, driveManager, rail), this);

        MrCommand executor = new MrCommand(this, config, messages, driveManager, registry, mileageStore,
                metro, rail, cabGui, autoSigns, web);
        PluginCommand command = getCommand("mr");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("plugin.yml 中缺少 mr 命令定义");
        }

        startTicker();
        web.start();

        getLogger().info("MetroRail 已启用（" + metro.describe() + "，车型 "
                + registry.ids().size() + " 种，轨道 " + rail.graph().nodeCount()
                + " 节点，道岔 " + rail.switches().size() + " 处）");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (web != null) {
            web.stop();
        }
        if (rail != null) {
            rail.saveAll();
        }
        if (driveManager != null) {
            driveManager.saveConsists();
            driveManager.releaseAll();
        }
        if (mileageStore != null) {
            mileageStore.save();
        }
        if (hud != null) {
            hud.clearAll();
        }
    }

    private void startTicker() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        int interval = config.tickInterval();
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            driveManager.tick(interval);
            autoSigns.tick();
            hud.update(driveManager);
        }, interval, interval);
    }

    /** 重新加载全部配置与数据。 */
    public void reloadAll() {
        config.load(this);
        registry.load();
        driveManager.loadConsists();
        mileageStore.load();
        rail.loadAll();
        metro.refresh();
        applyLanguage(config.language());
        web.stop();
        web.start();
        startTicker();
    }

    /** 切换语言（不写回 config.yml）。 */
    public void applyLanguage(String locale) {
        messages.load(locale);
    }

    public RailConfig railConfig() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public DriveManager driveManager() {
        return driveManager;
    }

    public DriveHud hud() {
        return hud;
    }

    public VehicleRegistry registry() {
        return registry;
    }

    public RailServices rail() {
        return rail;
    }

    public CabGui cabGui() {
        return cabGui;
    }

    public CabListener cabListener() {
        return cabListener;
    }

    public DispatchWebServer web() {
        return web;
    }
}
