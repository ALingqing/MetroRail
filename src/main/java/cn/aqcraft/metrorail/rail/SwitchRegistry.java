package cn.aqcraft.metrorail.rail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 道岔登记表：一个开关控制一组铁轨的朝向状态。
 */
public final class SwitchRegistry {

    /** 单个道岔。 */
    public static final class Switch {
        private final String id;
        private final List<String> positions = new ArrayList<>();
        private String location;
        private String active;

        Switch(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** 位置名 -> 方块坐标 id。 */
        public List<String> positions() {
            return Collections.unmodifiableList(positions);
        }

        public String location() {
            return location;
        }

        public void location(String location) {
            this.location = location;
        }

        public String active() {
            return active;
        }

        public int size() {
            return positions.size();
        }

        void rawPosition(String value) {
            positions.add(value);
        }

        void rawActive(String value) {
            this.active = value;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Switch> switches = new LinkedHashMap<>();

    public SwitchRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "switches.yml");
    }

    public void load() {
        switches.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        for (String id : yc.getKeys(false)) {
            Switch sw = new Switch(id.toLowerCase(Locale.ROOT));
            sw.location(yc.getString(id + ".location"));
            for (String pos : yc.getStringList(id + ".positions")) {
                sw.rawPosition(pos);
            }
            sw.rawActive(yc.getString(id + ".active"));
            switches.put(sw.id(), sw);
        }
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Switch sw : switches.values()) {
            yc.set(sw.id() + ".location", sw.location());
            yc.set(sw.id() + ".positions", sw.positions());
            yc.set(sw.id() + ".active", sw.active());
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录，道岔表未保存");
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 switches.yml 失败: " + ex.getMessage());
        }
    }

    public Switch get(String id) {
        return id == null ? null : switches.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Switch> all() {
        return switches.values();
    }

    public int size() {
        return switches.size();
    }

    public boolean remove(String id) {
        if (switches.remove(id == null ? "" : id.toLowerCase(Locale.ROOT)) == null) {
            return false;
        }
        save();
        return true;
    }

    /** 以玩家位置为核心扫描道岔：登记铁轨拐角为位置，开关方块为控制点。 */
    public Switch scan(Location center, String id, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        Switch sw = new Switch(id.toLowerCase(Locale.ROOT));
        int r = (int) Math.ceil(radius);
        int count = 0;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (block.getType() == Material.LEVER) {
                        sw.location(RailNode.idOf(block.getLocation()));
                        continue;
                    }
                    if (!RailGraph.isRail(block)) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data instanceof Rail && count < 16) {
                        sw.rawPosition(RailNode.idOf(block.getLocation()));
                        count++;
                    }
                }
            }
        }
        if (sw.size() == 0) {
            return null;
        }
        switches.put(sw.id(), sw);
        save();
        return sw;
    }

    /**
     * 切换道岔：所有登记铁轨按当前形状的顺时针/逆时针方向翻转。
     * 返回实际改动的方块数。
     */
    public int toggle(Switch sw) {
        if (sw == null) {
            return 0;
        }
        int changed = 0;
        for (String id : sw.positions()) {
            Location location = locationOf(id);
            if (location == null) {
                continue;
            }
            Block block = location.getBlock();
            BlockData data = block.getBlockData();
            if (!(data instanceof Rail)) {
                continue;
            }
            Rail rail = (Rail) data;
            Rail.Shape shape = rail.getShape();
            Rail.Shape next = switch (shape) {
                case NORTH_EAST -> Rail.Shape.NORTH_WEST;
                case NORTH_WEST -> Rail.Shape.NORTH_EAST;
                case SOUTH_EAST -> Rail.Shape.SOUTH_WEST;
                case SOUTH_WEST -> Rail.Shape.SOUTH_EAST;
                default -> shape;
            };
            if (next != shape) {
                rail.setShape(next);
                block.setBlockData(rail, false);
                changed++;
            }
        }
        return changed;
    }

    public static Location locationOf(String id) {
        if (id == null) {
            return null;
        }
        String[] parts = id.split(":");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
