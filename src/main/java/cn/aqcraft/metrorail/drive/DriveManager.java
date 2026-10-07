package cn.aqcraft.metrorail.drive;

import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.data.MileageStore;
import cn.aqcraft.metrorail.rail.ControlMode;
import cn.aqcraft.metrorail.rail.MovementAuthority;
import cn.aqcraft.metrorail.rail.RailNode;
import cn.aqcraft.metrorail.rail.RailServices;
import cn.aqcraft.metrorail.rail.TimsMonitor;
import cn.aqcraft.metrorail.rail.TrainLocator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 驾驶总控：持有会话、按 tick 施加牵引/制动、维护编组与里程。
 */
public final class DriveManager {

    /** 1 blocks/tick == 72 km/h。 */
    public static final double KMH_PER_BLOCK_PER_TICK = 72.0;

    /** 取车结果。 */
    public static final class Result {
        private final DriveSession session;
        private final String errorKey;

        private Result(DriveSession session, String errorKey) {
            this.session = session;
            this.errorKey = errorKey;
        }

        static Result ok(DriveSession session) {
            return new Result(session, null);
        }

        static Result fail(String key) {
            return new Result(null, key);
        }

        public boolean success() {
            return session != null;
        }

        public DriveSession session() {
            return session;
        }

        public String errorKey() {
            return errorKey;
        }
    }

    private final JavaPlugin plugin;
    private final RailConfig config;
    private final VehicleRegistry registry;
    private final MileageStore mileage;
    private final RailServices rail;

    private final Map<UUID, DriveSession> byDriver = new LinkedHashMap<>();
    private final Map<UUID, DriveSession> byCart = new HashMap<>();
    private final Map<String, Consist> consists = new LinkedHashMap<>();
    private final File consistFile;
    private long tickCounter;

    public DriveManager(JavaPlugin plugin, RailConfig config, VehicleRegistry registry,
                        MileageStore mileage, RailServices rail) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.mileage = mileage;
        this.rail = rail;
        this.consistFile = new File(plugin.getDataFolder(), "trains.yml");
    }

    public RailServices rail() {
        return rail;
    }

    // ---------------------------------------------------------------- 会话

    public DriveSession byDriver(UUID driver) {
        return byDriver.get(driver);
    }

    public DriveSession byCart(UUID cart) {
        return byCart.get(cart);
    }

    public Collection<DriveSession> sessions() {
        return byDriver.values();
    }

    public int sessionCount() {
        return byDriver.size();
    }

    public boolean isDriving(UUID driver) {
        return byDriver.containsKey(driver);
    }

    /** 取得（或抢占）最近一辆矿车的驾驶权。 */
    public Result acquire(Player player, String vehicleId) {
        if (byDriver.containsKey(player.getUniqueId())) {
            return Result.fail("already-driving");
        }
        Minecart cart = findCart(player);
        if (cart == null) {
            return Result.fail("no-cart-nearby");
        }
        if (byCart.containsKey(cart.getUniqueId())) {
            return Result.fail("cart-busy");
        }
        String vid = vehicleId != null && registry.has(vehicleId) ? vehicleId
                : (registry.has(config.defaultVehicle()) ? config.defaultVehicle() : null);
        VehicleType type = vid != null ? registry.get(vid) : registry.fallback();
        if (type == null) {
            return Result.fail("no-cart-nearby");
        }
        DriveSession session = new DriveSession(player.getUniqueId(), player.getName(),
                cart.getUniqueId(), type.id());
        session.reverser(Reverser.NEUTRAL);
        session.notch(Notch.N);
        byDriver.put(session.driver(), session);
        byCart.put(session.cart(), session);
        return Result.ok(session);
    }

    /** 结束会话，返回里程（block）。 */
    public double release(UUID driver) {
        DriveSession session = byDriver.remove(driver);
        if (session == null) {
            return -1.0;
        }
        byCart.remove(session.cart());
        releaseOccupancy(session);
        if (session.mileage() > 0.0) {
            mileage.add(session.driver(), session.driverName(), session.mileage());
        }
        return session.mileage();
    }

    public void releaseAll() {
        for (UUID id : new ArrayList<>(byDriver.keySet())) {
            release(id);
        }
    }

    public void setNotch(Player player, Notch notch) {
        DriveSession session = byDriver.get(player.getUniqueId());
        if (session != null) {
            session.notch(notch);
        }
    }

    /** 换向器：速度超过阈值时拒绝。返回 false 表示速度过高。 */
    public boolean setReverser(Player player, Reverser reverser) {
        DriveSession session = byDriver.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (session.speed() > config.reverserMaxSpeed()) {
            return false;
        }
        session.reverser(reverser);
        if (reverser == Reverser.NEUTRAL) {
            session.speed(Math.min(session.speed(), config.reverserMaxSpeed()));
        }
        return true;
    }

    /** 设置列控模式。 */
    public boolean setMode(DriveSession session, ControlMode mode) {
        if (session == null || mode == null) {
            return false;
        }
        session.mode(mode);
        session.maAcknowledged(mode != ControlMode.ENFORCE);
        return true;
    }

    /** 设置状态机状态。 */
    public boolean setState(DriveSession session, cn.aqcraft.metrorail.rail.TrainState state) {
        if (session == null || state == null) {
            return false;
        }
        session.state(state);
        return true;
    }

    /** 申请行车许可：沿前进方向预约区段（共享持有）。返回预约段数。 */
    public int demand(DriveSession session, int segments) {
        if (session == null || rail == null || segments <= 0) {
            return 0;
        }
        String holder = holderOf(session);
        RailNode node = session.lastKnown() != null
                ? rail.graph().nearestNode(session.lastKnown(), config.scanRadius() + 4.0) : null;
        if (node == null) {
            return 0;
        }
        double hx = session.headingValid() ? session.headingX() * session.reverser().sign() : 0.0;
        double hz = session.headingValid() ? session.headingZ() * session.reverser().sign() : 0.0;
        int reserved = 0;
        RailNode current = node;
        double dirX = hx;
        double dirZ = hz;
        for (int i = 0; i < segments; i++) {
            cn.aqcraft.metrorail.rail.RailEdge edge = rail.graph().bestEdge(current, dirX, dirZ);
            if (edge == null) {
                break;
            }
            if (rail.ledger().isBlockedFor(edge.id(), holder)) {
                break;
            }
            rail.ledger().hold(edge.id(), holder);
            reserved++;
            current = rail.graph().node(edge.to());
            if (current == null) {
                break;
            }
            double flat = Math.sqrt(edge.dx() * edge.dx() + edge.dz() * edge.dz());
            if (flat > 1.0E-6) {
                dirX = edge.dx() / flat;
                dirZ = edge.dz() / flat;
            }
        }
        return reserved;
    }

    /** 确认当前 MA（清除未确认标记）。 */
    public void acknowledge(DriveSession session) {
        if (session != null) {
            session.maAcknowledged(true);
        }
    }

    /** 寻找玩家乘坐的矿车，否则找附近最近的一辆。 */
    private Minecart findCart(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Minecart) {
            return (Minecart) vehicle;
        }
        Minecart best = null;
        double bestDist = config.scanRadius();
        for (Entity entity : player.getNearbyEntities(bestDist, bestDist, bestDist)) {
            if (!(entity instanceof Minecart) || entity.isDead()) {
                continue;
            }
            if (byCart.containsKey(entity.getUniqueId())) {
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

    // ---------------------------------------------------------------- tick

    /** 按 tick 施加动力；interval 为调度间隔（tick）。 */
    public void tick(int interval) {
        tickCounter += Math.max(1, interval);
        if (byDriver.isEmpty()) {
            return;
        }
        for (DriveSession session : new ArrayList<>(byDriver.values())) {
            Player player = Bukkit.getPlayer(session.driver());
            Minecart cart = cartOf(session);
            if (player == null || cart == null) {
                release(session.driver());
                continue;
            }
            if (config.releaseOnDismount() && !cart.equals(player.getVehicle())) {
                release(session.driver());
                continue;
            }
            step(session, player, cart, interval);
        }
    }

    private Minecart cartOf(DriveSession session) {
        Entity entity = Bukkit.getEntity(session.cart());
        if (entity instanceof Minecart && !entity.isDead()) {
            return (Minecart) entity;
        }
        return null;
    }

    private void step(DriveSession session, Player player, Minecart cart, int interval) {
        VehicleType type = registry.get(session.vehicleId());
        if (type == null) {
            type = registry.fallback();
        }
        // 朝向：优先用当前速度，静止时用玩家朝向
        Vector velocity = cart.getVelocity();
        double hx = velocity.getX();
        double hz = velocity.getZ();
        if (Math.sqrt(hx * hx + hz * hz) > 1.0E-4) {
            session.heading(hx, hz);
        } else if (!session.headingValid()) {
            double yaw = Math.toRadians(player.getLocation().getYaw());
            session.heading(-Math.sin(yaw), Math.cos(yaw));
        }

        double target;
        double rate;
        switch (session.notch().kind()) {
            case POWER:
                target = type.maxSpeed() * session.notch().powerRatio();
                rate = type.acceleration();
                break;
            case BRAKE:
                target = 0.0;
                rate = type.brake() * Math.max(1.0, session.notch().brakeRatio() * 7.0);
                break;
            case EMERGENCY:
                target = 0.0;
                rate = type.emergencyBrake();
                break;
            case NEUTRAL:
            default:
                target = 0.0;
                rate = type.friction();
                break;
        }

        double speed = session.speed();
        if (speed < target) {
            speed = Math.min(target, speed + rate);
        } else if (speed > target) {
            speed = Math.max(target, speed - rate);
        }
        if (session.reverser() == Reverser.NEUTRAL) {
            speed = 0.0;
        }

        // ---- 列控：MA / 限速 / TIMS / 强制保护 ----
        session.lastKnown(cart.getLocation());
        applyControl(session, cart, type, speed);

        // applyControl 可能把速度压到安全值，取较小者写回
        speed = Math.min(speed, session.speed());
        session.speed(speed);

        if (speed > 1.0E-4 && session.headingValid()) {
            double sign = session.reverser().sign();
            if (sign != 0) {
                cart.setVelocity(new Vector(
                        session.headingX() * speed * sign,
                        velocity.getY(),
                        session.headingZ() * speed * sign));
            }
        }
        session.addMileage(speed * interval);
    }

    /**
     * 计算并应用列控：MA、限速、完整性、强制保护制动。
     * 会把 session 的速度压回安全值，但不直接改写速度字段（由调用方写回）。
     */
    private void applyControl(DriveSession session, Minecart cart, VehicleType type, double speed) {
        if (rail == null) {
            return;
        }
        if (session.mode() == ControlMode.BYPASS || session.mode() == ControlMode.ISOLATE) {
            session.ma(null);
        } else if (session.headingValid()) {
            RailNode node = rail.graph().nearestNode(cart.getLocation(), config.scanRadius() + 4.0);
            double hx = session.headingX() * session.reverser().sign();
            double hz = session.headingZ() * session.reverser().sign();
            MovementAuthority.Result ma = rail.ma().compute(node, hx, hz, holderOf(session),
                    type.maxSpeed(), config.maLookahead());
            session.ma(ma);
        }

        // MA 限速：接近 EoA 时按制动距离压速
        MovementAuthority.Result ma = session.ma();
        if (ma != null && ma.eoaDistance() >= 0.0) {
            double allowed = Math.sqrt(Math.max(0.0, 2.0 * type.emergencyBrake() * ma.eoaDistance()));
            if (allowed < speed) {
                session.speed(allowed);
            }
        }

        // TIMS 完整性
        TimsMonitor.Verdict verdict = rail.tims().verify(
                session.consistId() != null ? cartsOf(session) : List.of(session.cart()),
                tickCounter, config.timsTimeout(), config.timsMaxSpacing());
        session.integrity(verdict.intact(), verdict.reason());
        for (UUID cartId : session.consistId() != null ? cartsOf(session) : List.of(session.cart())) {
            Entity entity = Bukkit.getEntity(cartId);
            if (entity != null) {
                rail.tims().observe(cartId, entity.getLocation().getX(), entity.getLocation().getY(),
                        entity.getLocation().getZ(), tickCounter);
            }
        }

        // 强制保护：模式为 enforce 且 MA 被占用或完整性丢失时紧急制动
        if (session.mode() == ControlMode.ENFORCE) {
            boolean stop = (ma != null && ma.blocked()) || !session.integrityOk();
            if (stop) {
                session.state(cn.aqcraft.metrorail.rail.TrainState.PROTECTION);
                session.speed(0.0);
                session.maAcknowledged(false);
            }
        }
    }

    private String holderOf(DriveSession session) {
        return session.consistId() != null ? session.consistId() : session.cart().toString();
    }

    private java.util.List<UUID> cartsOf(DriveSession session) {
        Consist consist = consist(session.consistId());
        return consist == null ? List.of(session.cart()) : consist.carts();
    }

    /** 释放一次会话占用的所有区段。 */
    private void releaseOccupancy(DriveSession session) {
        if (rail != null) {
            rail.ledger().releaseAll(holderOf(session));
        }
    }

    // ---------------------------------------------------------------- 编组

    public Consist consist(String id) {
        return id == null ? null : consists.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Consist> consists() {
        return consists.values();
    }

    public int consistCount() {
        return consists.size();
    }

    public boolean createConsist(String id, String owner) {
        String key = id.toLowerCase(Locale.ROOT);
        if (consists.containsKey(key)) {
            return false;
        }
        consists.put(key, new Consist(key, id, owner));
        saveConsists();
        return true;
    }

    public boolean removeConsist(String id) {
        Consist removed = consists.remove(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        saveConsists();
        return true;
    }

    public int limit() {
        return config.maxConsistSize();
    }

    public void loadConsists() {
        consists.clear();
        if (!consistFile.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(consistFile);
        for (String key : yc.getKeys(false)) {
            Consist consist = new Consist(key.toLowerCase(Locale.ROOT),
                    yc.getString(key + ".name", key),
                    yc.getString(key + ".owner", "-"));
            List<String> carts = yc.getStringList(key + ".carts");
            for (String raw : carts) {
                try {
                    consist.raw().add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                    // 跳过损坏条目
                }
            }
            consists.put(key.toLowerCase(Locale.ROOT), consist);
        }
    }

    public void saveConsists() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Consist consist : consists.values()) {
            String base = consist.id();
            yc.set(base + ".name", consist.name());
            yc.set(base + ".owner", consist.ownerName());
            List<String> carts = new ArrayList<>();
            for (UUID id : consist.carts()) {
                carts.add(id.toString());
            }
            yc.set(base + ".carts", carts);
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录，编组未保存");
                return;
            }
            yc.save(consistFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 trains.yml 失败: " + ex.getMessage());
        }
    }
}
