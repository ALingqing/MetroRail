package cn.aqcraft.metrorail.command;

import cn.aqcraft.metrorail.MetroRailPlugin;
import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.data.MileageStore;
import cn.aqcraft.metrorail.drive.Consist;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.drive.Notch;
import cn.aqcraft.metrorail.drive.Reverser;
import cn.aqcraft.metrorail.drive.VehicleRegistry;
import cn.aqcraft.metrorail.drive.VehicleType;
import cn.aqcraft.metrorail.hud.CabGui;
import cn.aqcraft.metrorail.i18n.Messages;
import cn.aqcraft.metrorail.metro.MetroHook;
import cn.aqcraft.metrorail.rail.BaliseRegistry;
import cn.aqcraft.metrorail.rail.ControlMode;
import cn.aqcraft.metrorail.rail.RailEdge;
import cn.aqcraft.metrorail.rail.RailNode;
import cn.aqcraft.metrorail.rail.RailServices;
import cn.aqcraft.metrorail.rail.SwitchRegistry;
import cn.aqcraft.metrorail.rail.TrainProperties;
import cn.aqcraft.metrorail.rail.TrainState;
import cn.aqcraft.metrorail.signs.AutoSignService;
import cn.aqcraft.metrorail.web.DispatchWebServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /mr 命令。
 */
public final class MrCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "help", "drive", "release", "notch", "reverser",
            "vehicle", "train", "status", "mileage", "lang", "reload",
            "cab", "hotbar", "horn", "bell", "speedunit",
            "switch", "balise", "graph", "ma", "mode", "savedtrain", "property", "web");

    private final MetroRailPlugin plugin;
    private final RailConfig config;
    private final Messages messages;
    private final DriveManager manager;
    private final VehicleRegistry registry;
    private final MileageStore mileage;
    private final MetroHook metro;
    private final RailServices rail;
    private final CabGui cabGui;
    private final AutoSignService autoSigns;
    private final DispatchWebServer web;

    public MrCommand(MetroRailPlugin plugin, RailConfig config, Messages messages, DriveManager manager,
                     VehicleRegistry registry, MileageStore mileage, MetroHook metro,
                     RailServices rail, CabGui cabGui, AutoSignService autoSigns, DispatchWebServer web) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.manager = manager;
        this.registry = registry;
        this.mileage = mileage;
        this.metro = metro;
        this.rail = rail;
        this.cabGui = cabGui;
        this.autoSigns = autoSigns;
        this.web = web;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                help(sender);
                return true;
            case "drive":
                return drive(sender, args);
            case "release":
                return release(sender);
            case "notch":
                return notch(sender, args);
            case "reverser":
                return reverser(sender, args);
            case "vehicle":
                return vehicle(sender, args);
            case "train":
                return train(sender, args);
            case "status":
                return status(sender);
            case "mileage":
                return mileageCmd(sender, args);
            case "lang":
                return lang(sender, args);
            case "reload":
                return reload(sender);
            case "cab":
                return cab(sender);
            case "hotbar":
                return hotbar(sender, args);
            case "horn":
                return horn(sender, false);
            case "bell":
                return horn(sender, true);
            case "speedunit":
                return speedunit(sender, args);
            case "switch":
                return switchCmd(sender, args);
            case "balise":
            case "origin":
            case "end":
                return balise(sender, args);
            case "graph":
                return graph(sender, args);
            case "ma":
                return ma(sender, args);
            case "mode":
                return mode(sender, args);
            case "savedtrain":
                return savedtrain(sender, args);
            case "property":
                return property(sender, args);
            case "web":
                return web(sender);
            default:
                break;
        }
        // 级位简写：/mr p1  /mr n  /mr eb
        Notch notch = Notch.parse(sub);
        if (notch != null) {
            return notch(sender, new String[]{"notch", sub});
        }
        help(sender);
        return true;
    }

    // ------------------------------------------------------------ 子命令

    private void help(CommandSender sender) {
        sender.sendMessage(Messages.color("&b&l==== MetroRail ===="));
        for (String key : Arrays.asList("help-drive", "help-release", "help-notch", "help-reverser",
                "help-vehicle", "help-train", "help-status", "help-mileage", "help-lang", "help-reload",
                "help-cab", "help-horn", "help-switch", "help-graph", "help-ma", "help-mode",
                "help-savedtrain", "help-property", "help-web")) {
            sender.sendMessage(messages.get(key));
        }
    }

    private boolean player(CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        sender.sendMessage(messages.prefixed("player-only"));
        return false;
    }

    private boolean drive(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        if (manager.isDriving(player.getUniqueId())) {
            msg(player, "already-driving");
            return true;
        }
        String vehicleId = args.length > 1 ? args[1] : null;
        if (vehicleId != null && !registry.has(vehicleId)) {
            msg(player, "vehicle-unknown", vehicleId);
            return true;
        }
        DriveManager.Result result = manager.acquire(player, vehicleId);
        if (!result.success()) {
            msg(player, result.errorKey());
            return true;
        }
        msg(player, "drive-acquired", result.session().vehicleId());
        return true;
    }

    private boolean release(CommandSender sender) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        double distance = manager.release(player.getUniqueId());
        if (distance < 0.0) {
            msg(player, "not-driving");
            return true;
        }
        plugin.hud().clear(player);
        msg(player, "drive-released");
        msg(player, "session-ended", (long) distance);
        return true;
    }

    private boolean notch(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            msg(player, "notch-unknown", "");
            return true;
        }
        Notch notch = Notch.parse(args[1]);
        if (notch == null) {
            msg(player, "notch-unknown", args[1]);
            return true;
        }
        if (!manager.isDriving(player.getUniqueId())) {
            msg(player, "not-driving");
            return true;
        }
        manager.setNotch(player, notch);
        msg(player, "notch-set", notch.id());
        return true;
    }

    private boolean reverser(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            msg(player, "reverser-unknown", "");
            return true;
        }
        Reverser reverser = Reverser.parse(args[1]);
        if (reverser == null) {
            msg(player, "reverser-unknown", args[1]);
            return true;
        }
        if (!manager.isDriving(player.getUniqueId())) {
            msg(player, "not-driving");
            return true;
        }
        if (!manager.setReverser(player, reverser)) {
            DriveSession session = manager.byDriver(player.getUniqueId());
            msg(player, "reverser-too-fast",
                    String.format(Locale.ROOT, "%.2f", session.speedKmh()));
            return true;
        }
        msg(player, "reverser-set", reverser.id());
        return true;
    }

    private boolean vehicle(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.use")) {
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list": {
                sender.sendMessage(messages.get("vehicle-list-header", registry.ids().size()));
                for (VehicleType type : registry.all()) {
                    sender.sendMessage(messages.get("vehicle-list-line",
                            type.id(), type.displayName(),
                            String.format(Locale.ROOT, "%.2f", type.maxSpeed())));
                }
                return true;
            }
            case "info": {
                if (args.length < 3) {
                    sender.sendMessage(messages.get("vehicle-unknown", ""));
                    return true;
                }
                VehicleType type = registry.get(args[2]);
                if (type == null) {
                    sender.sendMessage(messages.get("vehicle-unknown", args[2]));
                    return true;
                }
                sender.sendMessage(messages.get("vehicle-info", type.id(), type.displayName(),
                        fmt(type.maxSpeed()), fmt(type.acceleration()), fmt(type.brake()),
                        fmt(type.emergencyBrake())));
                return true;
            }
            case "select": {
                if (!player(sender) || !permission(sender, "metrorail.drive")) {
                    return true;
                }
                Player player = (Player) sender;
                if (args.length < 3) {
                    msg(player, "vehicle-unknown", "");
                    return true;
                }
                if (!registry.has(args[2])) {
                    msg(player, "vehicle-unknown", args[2]);
                    return true;
                }
                DriveSession session = manager.byDriver(player.getUniqueId());
                if (session == null) {
                    msg(player, "not-driving");
                    return true;
                }
                session.vehicleId(registry.get(args[2]).id());
                msg(player, "vehicle-current", session.vehicleId());
                return true;
            }
            default:
                sender.sendMessage(messages.get("vehicle-list-header", registry.ids().size()));
                return true;
        }
    }

    private boolean train(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.train")) {
            return true;
        }
        Player player = (Player) sender;
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list": {
                if (manager.consistCount() == 0) {
                    msg(player, "consist-empty");
                    return true;
                }
                msg(player, "consist-list-header", manager.consistCount());
                for (Consist consist : manager.consists()) {
                    msg(player, "consist-list-line", consist.id(), consist.size(), consist.ownerName());
                }
                return true;
            }
            case "scan": {
                Minecart cart = nearest(player);
                if (cart == null) {
                    msg(player, "consist-no-cart", fmt(config.scanRadius()));
                    return true;
                }
                String id = String.format(Locale.ROOT, "c%d", manager.consistCount() + 1);
                manager.createConsist(id, player.getName());
                Consist consist = manager.consist(id);
                if (consist != null) {
                    consist.append(cart.getUniqueId());
                    manager.saveConsists();
                    msg(player, "consist-created", id, consist.size());
                }
                return true;
            }
            case "create": {
                if (args.length < 3) {
                    msg(player, "consist-not-found", "");
                    return true;
                }
                if (!manager.createConsist(args[2], player.getName())) {
                    msg(player, "consist-exists", args[2]);
                    return true;
                }
                msg(player, "consist-created", args[2], 0);
                return true;
            }
            case "append": {
                if (args.length < 3) {
                    msg(player, "consist-not-found", "");
                    return true;
                }
                Consist consist = manager.consist(args[2]);
                if (consist == null) {
                    msg(player, "consist-not-found", args[2]);
                    return true;
                }
                if (consist.size() >= manager.limit()) {
                    msg(player, "consist-limit", manager.limit());
                    return true;
                }
                Minecart cart = nearest(player);
                if (cart == null) {
                    msg(player, "consist-no-cart", fmt(config.scanRadius()));
                    return true;
                }
                if (!consist.append(cart.getUniqueId())) {
                    msg(player, "consist-info", consist.id(), consist.name(), consist.size(), consist.ownerName());
                    return true;
                }
                manager.saveConsists();
                msg(player, "consist-appended", consist.id(), consist.size());
                return true;
            }
            case "unlink": {
                if (args.length < 3) {
                    msg(player, "consist-not-found", "");
                    return true;
                }
                Consist consist = manager.consist(args[2]);
                if (consist == null) {
                    msg(player, "consist-not-found", args[2]);
                    return true;
                }
                Minecart cart = nearest(player);
                if (cart == null || !consist.remove(cart.getUniqueId())) {
                    msg(player, "consist-no-cart", fmt(config.scanRadius()));
                    return true;
                }
                manager.saveConsists();
                msg(player, "consist-unlinked", consist.id(), consist.size());
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    msg(player, "consist-not-found", "");
                    return true;
                }
                if (!manager.removeConsist(args[2])) {
                    msg(player, "consist-not-found", args[2]);
                    return true;
                }
                msg(player, "consist-removed", args[2]);
                return true;
            }
            case "info": {
                if (args.length < 3) {
                    msg(player, "consist-not-found", "");
                    return true;
                }
                Consist consist = manager.consist(args[2]);
                if (consist == null) {
                    msg(player, "consist-not-found", args[2]);
                    return true;
                }
                msg(player, "consist-info", consist.id(), consist.name(), consist.size(), consist.ownerName());
                return true;
            }
            default:
                msg(player, "consist-empty");
                return true;
        }
    }

    private boolean status(CommandSender sender) {
        if (!permission(sender, "metrorail.use")) {
            return true;
        }
        sender.sendMessage(messages.get("status-header"));
        DriveSession session = sender instanceof Player
                ? manager.byDriver(((Player) sender).getUniqueId()) : null;
        if (session != null) {
            sender.sendMessage(messages.get("status-drive", messages.raw("status-drive-yes")));
            sender.sendMessage(messages.get("status-speed", fmt(session.speed()),
                    fmt(session.speedKmh())));
            sender.sendMessage(messages.get("status-vehicle", session.vehicleId()));
            sender.sendMessage(messages.get("status-consist",
                    session.consistId() == null ? messages.raw("status-none") : session.consistId()));
            sender.sendMessage(messages.get("status-control", session.mode().id(),
                    session.state() == null ? "-" : session.state().id(),
                    fmt(session.eoaDistance()), session.ma() == null ? "-" : session.ma().reason()));
            sender.sendMessage(messages.get("status-integrity",
                    session.integrityOk() ? messages.raw("status-drive-yes") : messages.raw("status-drive-no"),
                    session.integrityReason()));
        } else {
            sender.sendMessage(messages.get("status-drive", messages.raw("status-drive-no")));
        }
        sender.sendMessage(messages.get("status-metro", metro.describe()));
        sender.sendMessage(messages.get("status-vehicles", registry.joinIds()));
        sender.sendMessage(messages.get("status-consists", manager.consistCount()));
        return true;
    }

    private boolean mileageCmd(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.use")) {
            return true;
        }
        if (args.length > 1) {
            if (!permission(sender, "metrorail.admin")) {
                return true;
            }
            java.util.UUID target = mileage.lookupByName(args[1]);
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                target = online.getUniqueId();
            }
            if (target == null) {
                sender.sendMessage(messages.get("mileage-other", args[1], 0, "0.00"));
                return true;
            }
            double blocks = mileage.total(target);
            sender.sendMessage(messages.get("mileage-other", args[1], (long) blocks, fmt(blocks / 1000.0)));
            return true;
        }
        if (!player(sender)) {
            return true;
        }
        Player player = (Player) sender;
        double blocks = mileage.total(player.getUniqueId()) + currentMileage(player);
        sender.sendMessage(messages.get("mileage-self", (long) blocks, fmt(blocks / 1000.0)));
        return true;
    }

    private double currentMileage(Player player) {
        DriveSession session = manager.byDriver(player.getUniqueId());
        return session == null ? 0.0 : session.mileage();
    }

    private boolean lang(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.admin")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("lang-list", String.join(", ", messages.available())));
            return true;
        }
        String loc = args[1];
        if (!messages.exists(loc)) {
            sender.sendMessage(messages.get("lang-unknown", loc));
            return true;
        }
        plugin.getConfig().set("language", loc);
        plugin.saveConfig();
        plugin.applyLanguage(loc);
        sender.sendMessage(messages.get("lang-changed", loc));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!permission(sender, "metrorail.admin")) {
            return true;
        }
        plugin.reloadAll();
        sender.sendMessage(messages.get("reload-done"));
        return true;
    }

    // ------------------------------------------------------------ 新子系统

    private boolean cab(CommandSender sender) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        DriveSession session = manager.byDriver(player.getUniqueId());
        if (session == null) {
            msg(player, "not-driving");
            return true;
        }
        cabGui.open(player, session);
        return true;
    }

    private boolean hotbar(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        String current = config.hotbarSlot();
        player.sendMessage(messages.get("hotbar-status", current == null ? "off" : current));
        if (args.length > 1) {
            plugin.getConfig().set("drive.hotbar-slot", args[1]);
            plugin.saveConfig();
            plugin.railConfig().load(plugin);
            player.sendMessage(messages.get("hotbar-set", args[1]));
        }
        return true;
    }

    private boolean horn(CommandSender sender, boolean bell) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        DriveSession session = manager.byDriver(player.getUniqueId());
        if (session == null) {
            msg(player, "not-driving");
            return true;
        }
        if (bell) {
            cabGui.bell(player);
        } else {
            cabGui.horn(player);
        }
        return true;
    }

    private boolean speedunit(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.use")) {
            return true;
        }
        Player player = (Player) sender;
        DriveSession session = manager.byDriver(player.getUniqueId());
        if (args.length < 2) {
            String unit = session == null ? "kph" : session.unit();
            player.sendMessage(messages.get("unit-current", unit));
            return true;
        }
        String unit = args[1].toLowerCase(Locale.ROOT);
        if (!unit.equals("kph") && !unit.equals("mph") && !unit.equals("block") && !unit.equals("block/tick")) {
            player.sendMessage(messages.get("unit-unknown", args[1]));
            return true;
        }
        if (session != null) {
            session.unit(unit);
        }
        player.sendMessage(messages.get("unit-set", unit));
        return true;
    }

    private boolean switchCmd(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.switch")) {
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list": {
                sender.sendMessage(messages.get("switch-header", rail.switches().size()));
                for (SwitchRegistry.Switch sw : rail.switches().all()) {
                    sender.sendMessage(messages.get("switch-line", sw.id(), sw.size(),
                            String.valueOf(sw.location())));
                }
                return true;
            }
            case "scan": {
                if (!player(sender)) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.get("switch-usage"));
                    return true;
                }
                Player player = (Player) sender;
                SwitchRegistry.Switch sw = rail.switches().scan(player.getLocation(), args[2], config.scanRadius());
                if (sw == null) {
                    msg(player, "switch-none");
                    return true;
                }
                msg(player, "switch-scanned", sw.id(), sw.size());
                rail.events().add("switch", player.getName() + " 登记道岔 " + sw.id());
                return true;
            }
            case "info": {
                if (args.length < 3) {
                    sender.sendMessage(messages.get("switch-usage"));
                    return true;
                }
                SwitchRegistry.Switch sw = rail.switches().get(args[2]);
                if (sw == null) {
                    sender.sendMessage(messages.get("switch-not-found", args[2]));
                    return true;
                }
                sender.sendMessage(messages.get("switch-info", sw.id(), sw.size(),
                        String.valueOf(sw.location()), String.valueOf(sw.active())));
                return true;
            }
            case "set":
            case "toggle": {
                if (args.length < 3) {
                    sender.sendMessage(messages.get("switch-usage"));
                    return true;
                }
                SwitchRegistry.Switch sw = rail.switches().get(args[2]);
                if (sw == null) {
                    sender.sendMessage(messages.get("switch-not-found", args[2]));
                    return true;
                }
                int changed = rail.switches().toggle(sw);
                sender.sendMessage(messages.get("switch-toggled", sw.id(), changed));
                rail.events().add("switch", sender.getName() + " 转换道岔 " + sw.id());
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage(messages.get("switch-usage"));
                    return true;
                }
                if (!rail.switches().remove(args[2])) {
                    sender.sendMessage(messages.get("switch-not-found", args[2]));
                    return true;
                }
                sender.sendMessage(messages.get("switch-removed", args[2]));
                return true;
            }
            default:
                sender.sendMessage(messages.get("switch-usage"));
                return true;
        }
    }

    private boolean balise(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.use")) {
            return true;
        }
        String filter = null;
        if (args[0].equalsIgnoreCase("origin")) {
            filter = BaliseRegistry.SPAWN;
        } else if (args[0].equalsIgnoreCase("end")) {
            filter = BaliseRegistry.STOP;
        } else if (args.length > 1 && !args[1].equalsIgnoreCase("list")) {
            filter = args[1].toLowerCase(Locale.ROOT);
        }
        sender.sendMessage(messages.get("balise-header", rail.balises().size()));
        for (BaliseRegistry.Balise balise : rail.balises().all()) {
            if (filter != null && !balise.tag().equalsIgnoreCase(filter)) {
                continue;
            }
            sender.sendMessage(messages.get("balise-line", balise.tag(), balise.name(), balise.id()));
        }
        return true;
    }

    private boolean graph(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.use")) {
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status":
            case "inspect": {
                sender.sendMessage(messages.get("graph-status", rail.graph().worldName(),
                        rail.graph().nodeCount(), rail.graph().edgeCount(),
                        rail.ledger().size(), rail.switches().size(), rail.balises().size()));
                return true;
            }
            case "rebuild": {
                if (!permission(sender, "metrorail.admin")) {
                    return true;
                }
                if (!player(sender)) {
                    return true;
                }
                Player player = (Player) sender;
                int count = rail.rebuild(player.getLocation(), config.maxGraphNodes());
                sender.sendMessage(messages.get("graph-rebuilt", count));
                rail.events().add("graph", player.getName() + " 重建轨道图 " + count + " 节点");
                return true;
            }
            case "export": {
                if (!permission(sender, "metrorail.admin")) {
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (RailNode node : rail.graph().nodes()) {
                    sb.append(node.id()).append(';');
                }
                sender.sendMessage(messages.get("graph-export", rail.graph().nodeCount()));
                plugin.getLogger().info("TrackGraph export: " + sb);
                return true;
            }
            default:
                sender.sendMessage(messages.get("graph-usage"));
                return true;
        }
    }

    private boolean ma(CommandSender sender, String[] args) {
        if (!player(sender) || !permission(sender, "metrorail.drive")) {
            return true;
        }
        Player player = (Player) sender;
        DriveSession session = manager.byDriver(player.getUniqueId());
        if (session == null) {
            msg(player, "not-driving");
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status": {
                msg(player, "ma-status", fmt(session.eoaDistance()),
                        session.ma() == null ? "-" : session.ma().reason(),
                        session.ma() == null ? "-" : String.valueOf(session.ma().segments()));
                return true;
            }
            case "demand": {
                int segments = args.length > 2 ? parseInt(args[2], 3) : 3;
                int reserved = manager.demand(session, segments);
                msg(player, "ma-demand", reserved);
                rail.events().add("ma", player.getName() + " 申请 " + reserved + " 段");
                return true;
            }
            case "release": {
                int released = rail.ledger().releaseAll(session.consistId() != null
                        ? session.consistId() : session.cart().toString());
                msg(player, "ma-release", released);
                return true;
            }
            case "ack": {
                manager.acknowledge(session);
                msg(player, "ma-ack");
                return true;
            }
            default:
                msg(player, "graph-usage");
                return true;
        }
    }

    private boolean mode(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ControlMode[] modes = ControlMode.values();
            StringBuilder sb = new StringBuilder();
            for (ControlMode mode : modes) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(mode.id());
            }
            sender.sendMessage(messages.get("mode-list", sb.toString()));
            return true;
        }
        ControlMode mode = ControlMode.parse(args[1]);
        if (mode == null) {
            sender.sendMessage(messages.get("mode-unknown", args[1]));
            return true;
        }
        if (mode == ControlMode.ENFORCE && !permission(sender, "metrorail.admin")) {
            return true;
        }
        if (player(sender)) {
            DriveSession session = manager.byDriver(((Player) sender).getUniqueId());
            if (session == null) {
                msg((Player) sender, "not-driving");
                return true;
            }
            manager.setMode(session, mode);
        }
        sender.sendMessage(messages.get("mode-set", mode.id()));
        rail.events().add("mode", sender.getName() + " 切换模式 " + mode.id());
        return true;
    }

    private boolean savedtrain(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.train")) {
            return true;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "list": {
                sender.sendMessage(messages.get("savedtrain-header", rail.trainProperties().templates().size()));
                for (TrainProperties.Template template : rail.trainProperties().templates().values()) {
                    sender.sendMessage(messages.get("savedtrain-line", template.id(),
                            template.carts().size(), String.valueOf(template.world())));
                }
                return true;
            }
            case "save": {
                if (!player(sender)) {
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(messages.get("savedtrain-usage"));
                    return true;
                }
                Player player = (Player) sender;
                Consist consist = manager.consist(args[2]);
                if (consist == null) {
                    msg(player, "consist-not-found", args[2]);
                    return true;
                }
                List<String> carts = new ArrayList<>();
                for (java.util.UUID id : consist.carts()) {
                    carts.add(id.toString());
                }
                TrainProperties.Template template = rail.trainProperties().saveTemplate(
                        args[3], player.getWorld().getName(), player.getLocation(), carts,
                        new java.util.LinkedHashMap<>());
                plugin.rail().saveAll();
                sender.sendMessage(messages.get("savedtrain-saved", template.id(), template.carts().size()));
                return true;
            }
            case "spawn": {
                if (!player(sender)) {
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(messages.get("savedtrain-usage"));
                    return true;
                }
                Player player = (Player) sender;
                TrainProperties.Template template = rail.trainProperties().template(args[2]);
                if (template == null) {
                    msg(player, "savedtrain-not-found", args[2]);
                    return true;
                }
                String id = String.format(Locale.ROOT, "c%d", manager.consistCount() + 1);
                manager.createConsist(id, player.getName());
                Consist consist = manager.consist(id);
                int spawned = 0;
                for (int i = 0; i < template.carts().size(); i++) {
                    org.bukkit.Location location = player.getLocation().add(0, 1, i * 2.0);
                    Minecart cart = player.getWorld().spawn(location, Minecart.class);
                    if (consist != null && consist.append(cart.getUniqueId())) {
                        spawned++;
                    }
                }
                manager.saveConsists();
                sender.sendMessage(messages.get("savedtrain-spawned", template.id(), spawned, id));
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage(messages.get("savedtrain-usage"));
                    return true;
                }
                if (!rail.trainProperties().removeTemplate(args[2])) {
                    sender.sendMessage(messages.get("savedtrain-not-found", args[2]));
                    return true;
                }
                plugin.rail().saveAll();
                sender.sendMessage(messages.get("savedtrain-removed", args[2]));
                return true;
            }
            default:
                sender.sendMessage(messages.get("savedtrain-usage"));
                return true;
        }
    }

    private boolean property(CommandSender sender, String[] args) {
        if (!permission(sender, "metrorail.train")) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get("property-usage", String.join(" ", TrainProperties.KEYS)));
            return true;
        }
        String train = args[1];
        TrainProperties.Properties properties = rail.trainProperties().find(train);
        if (args.length == 2) {
            if (properties == null || properties.values().isEmpty()) {
                sender.sendMessage(messages.get("property-none", train));
                return true;
            }
            for (java.util.Map.Entry<String, String> entry : properties.values().entrySet()) {
                sender.sendMessage(messages.get("property-line", entry.getKey(), entry.getValue()));
            }
            return true;
        }
        String key = args[2].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            String value = properties == null ? null : properties.get(key);
            sender.sendMessage(messages.get("property-get", train, key,
                    value == null ? messages.raw("status-none") : value));
            return true;
        }
        StringBuilder value = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (value.length() > 0) {
                value.append(' ');
            }
            value.append(args[i]);
        }
        rail.trainProperties().of(train).set(key, value.toString());
        plugin.rail().saveAll();
        sender.sendMessage(messages.get("property-set", train, key, value.toString()));
        return true;
    }

    private boolean web(CommandSender sender) {
        if (!permission(sender, "metrorail.admin")) {
            return true;
        }
        if (!web.running()) {
            sender.sendMessage(messages.get("web-off"));
            sender.sendMessage(messages.get("web-folder", web.webDir().getPath()));
            return true;
        }
        sender.sendMessage(messages.get("web-status", web.port(), config.webEnabled() ? "on" : "off",
                config.webToken().isBlank() ? messages.raw("web-no-token") : messages.raw("web-token")));
        sender.sendMessage(messages.get("web-folder", web.webDir().getPath()));
        return true;
    }

    // ------------------------------------------------------------ 工具

    private boolean permission(CommandSender sender, String node) {
        if (sender.hasPermission(node) || sender.hasPermission("metrorail.admin")) {
            return true;
        }
        sender.sendMessage(messages.prefixed("no-permission"));
        return false;
    }

    private void msg(Player player, String key, Object... args) {
        player.sendMessage(messages.get(key, args));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Minecart nearest(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart) {
            return (Minecart) vehicle;
        }
        Minecart best = null;
        double bestDist = 8.0;
        for (Entity entity : player.getNearbyEntities(bestDist, bestDist, bestDist)) {
            if (!(entity instanceof Minecart) || entity.isDead()) {
                continue;
            }
            double dist = entity.getLocation().distance(player.getLocation());
            if (dist <= bestDist) {
                bestDist = dist;
                best = (Minecart) entity;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ 补全

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUBS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "drive":
                    return filter(registry.ids(), args[1]);
                case "notch":
                    return filter(Arrays.asList(Notch.allIds().split(" ")), args[1]);
                case "reverser":
                    return filter(Arrays.asList(Reverser.allIds().split(" ")), args[1]);
                case "vehicle":
                    return filter(Arrays.asList("list", "info", "select"), args[1]);
                case "train":
                    return filter(Arrays.asList("list", "scan", "create", "append", "unlink", "remove", "info"), args[1]);
                case "switch":
                    return filter(Arrays.asList("list", "scan", "info", "set", "toggle", "remove"), args[1]);
                case "balise":
                case "origin":
                case "end":
                    return filter(Arrays.asList("list", "station", "property", "spawn", "limit", "stop"), args[1]);
                case "graph":
                    return filter(Arrays.asList("status", "inspect", "rebuild", "export"), args[1]);
                case "ma":
                    return filter(Arrays.asList("status", "demand", "release", "ack"), args[1]);
                case "mode":
                    return filter(Arrays.asList("shadow", "bypass", "isolate", "enforce"), args[1]);
                case "savedtrain":
                    return filter(Arrays.asList("list", "save", "spawn", "remove"), args[1]);
                case "property":
                    return filter(new ArrayList<>(rail.trainProperties().trains().keySet()), args[1]);
                case "speedunit":
                    return filter(Arrays.asList("kph", "mph", "block", "block/tick"), args[1]);
                case "hotbar":
                    return filter(Arrays.asList("off", "1", "2", "3", "4", "5", "6", "7", "8", "9"), args[1]);
                case "lang":
                    return filter(messages.available(), args[1]);
                case "mileage":
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        names.add(p.getName());
                    }
                    return filter(names, args[1]);
                default:
                    return Collections.emptyList();
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("vehicle") && args[1].equalsIgnoreCase("info")) {
                return filter(registry.ids(), args[2]);
            }
            if (sub.equals("vehicle") && args[1].equalsIgnoreCase("select")) {
                return filter(registry.ids(), args[2]);
            }
            if (sub.equals("train")) {
                List<String> ids = new ArrayList<>();
                for (Consist consist : manager.consists()) {
                    ids.add(consist.id());
                }
                return filter(ids, args[2]);
            }
            if (sub.equals("switch") && (args[1].equalsIgnoreCase("info")
                    || args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("toggle")
                    || args[1].equalsIgnoreCase("remove"))) {
                List<String> ids = new ArrayList<>();
                for (SwitchRegistry.Switch sw : rail.switches().all()) {
                    ids.add(sw.id());
                }
                return filter(ids, args[2]);
            }
            if (sub.equals("savedtrain") && (args[1].equalsIgnoreCase("spawn")
                    || args[1].equalsIgnoreCase("remove"))) {
                return filter(new ArrayList<>(rail.trainProperties().templates().keySet()), args[2]);
            }
            if (sub.equals("property")) {
                return filter(new ArrayList<>(TrainProperties.KEYS), args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("savedtrain") && args[1].equalsIgnoreCase("save")) {
                List<String> ids = new ArrayList<>();
                for (Consist consist : manager.consists()) {
                    ids.add(consist.id());
                }
                return filter(ids, args[2]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }

    private static List<String> filter(java.util.Set<String> options, String prefix) {
        return filter(new ArrayList<>(options), prefix);
    }
}
