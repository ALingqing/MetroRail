package cn.aqcraft.metrorail.signs;

import cn.aqcraft.metrorail.MetroRailPlugin;
import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.drive.Notch;
import cn.aqcraft.metrorail.drive.VehicleType;
import cn.aqcraft.metrorail.rail.BaliseRegistry;
import cn.aqcraft.metrorail.rail.RailServices;
import cn.aqcraft.metrorail.rail.SwitchRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * 自动牌子服务：把 {@code [station] / [property] / [spawn] / [limit] / [stop]}
 * 牌子转成运行行为。
 */
public final class AutoSignService {

    private final MetroRailPlugin plugin;
    private final RailConfig config;
    private final DriveManager manager;
    private final RailServices rail;

    public AutoSignService(MetroRailPlugin plugin, RailConfig config, DriveManager manager, RailServices rail) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.rail = rail;
    }

    /** 每 tick 对每个驾驶会话处理牌子交互。 */
    public void tick() {
        if (!config.autoSigns()) {
            return;
        }
        for (DriveSession session : manager.sessions()) {
            Location location = session.lastKnown();
            if (location == null) {
                continue;
            }
            for (BaliseRegistry.Balise balise : rail.balises().all()) {
                Location target = SwitchRegistry.locationOf(balise.id());
                if (target == null || !target.getWorld().equals(location.getWorld())) {
                    continue;
                }
                double distance = target.distance(location);
                if (distance > 64.0) {
                    continue;
                }
                switch (balise.tag()) {
                    case BaliseRegistry.STATION:
                        stationStop(session, balise, distance);
                        break;
                    case BaliseRegistry.STOP:
                        forceStop(session, balise, distance);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** 站停：按制动距离递减限速，靠站后停车。 */
    private void stationStop(DriveSession session, BaliseRegistry.Balise balise, double distance) {
        if (session.state() == cn.aqcraft.metrorail.rail.TrainState.PROTECTION) {
            return;
        }
        VehicleType type = plugin.registry().get(session.vehicleId());
        if (type == null) {
            type = plugin.registry().fallback();
        }
        if (type == null) {
            return;
        }
        String[] parts = balise.name().split("\\|");
        double stopDistance = parts.length > 1 ? parse(parts[1], 0.0) : 0.0;
        double remaining = Math.max(0.0, distance - stopDistance);
        if (remaining <= 1.0) {
            // 到站：停车保持，切 SB
            session.speed(0.0);
            session.notch(Notch.N);
            session.state(cn.aqcraft.metrorail.rail.TrainState.SERVICE_BRAKE);
            rail.ledger().hold("station@" + balise.name(), holder(session));
            return;
        }
        double allowed = Math.sqrt(Math.max(0.0, 2.0 * Math.max(0.01, type.brake()) * remaining));
        if (allowed < session.speed()) {
            session.speed(allowed);
            session.state(cn.aqcraft.metrorail.rail.TrainState.SERVICE_BRAKE);
        }
    }

    /** 强制停车牌：进入 8 格内立即停稳。 */
    private void forceStop(DriveSession session, BaliseRegistry.Balise balise, double distance) {
        if (distance > 8.0) {
            return;
        }
        session.speed(0.0);
        session.notch(Notch.N);
        session.state(cn.aqcraft.metrorail.rail.TrainState.FULL_SERVICE);
        Player player = plugin.getServer().getPlayer(session.driver());
        if (player != null && distance < 2.0) {
            player.sendMessage(cn.aqcraft.metrorail.i18n.Messages.color(
                    "&c&l[MetroRail] &7已按 &f[stop] &7牌停车"));
        }
    }

    private static double parse(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String holder(DriveSession session) {
        return session.consistId() != null ? session.consistId() : session.cart().toString();
    }

    /** 处理 spawn/destroy 牌：生成或清除受控矿车。 */
    public int handleSpawn(BaliseRegistry.Balise balise, boolean spawn) {
        Location location = SwitchRegistry.locationOf(balise.id());
        if (location == null) {
            return 0;
        }
        if (spawn) {
            location.getWorld().spawn(location.add(0, 1, 0), org.bukkit.entity.Minecart.class);
            return 1;
        }
        int removed = 0;
        for (org.bukkit.entity.Entity entity : location.getWorld()
                .getNearbyEntities(location, 2.0, 2.0, 2.0)) {
            if (entity instanceof org.bukkit.entity.Minecart) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /** {@code [property V_target 0.3]} 牌：读目标速度。 */
    public double propertyValue(BaliseRegistry.Balise balise, String key, double fallback) {
        for (String line : balise.lines()) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase(key)) {
                return parse(parts[1], fallback);
            }
        }
        return fallback;
    }

    public String describe() {
        return String.format(Locale.ROOT, "signs=%d balises=%d",
                config.autoSigns() ? 1 : 0, rail.balises().size());
    }
}
