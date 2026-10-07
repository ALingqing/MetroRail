package cn.aqcraft.metrorail.rail;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 应答器（balise）登记表：牌子第 1 行是标记，第 2 行起是数据。
 *
 * <p>支持的标记：{@code [station]} 车站、{@code [property]} 属性、
 * {@code [spawn]} 出生点、{@code [limit]} 限速、{@code [stop]} 强制停车。</p>
 */
public final class BaliseRegistry {

    public static final String STATION = "station";
    public static final String PROPERTY = "property";
    public static final String SPAWN = "spawn";
    public static final String LIMIT = "limit";
    public static final String STOP = "stop";

    public static final List<String> TAGS = List.of(STATION, PROPERTY, SPAWN, LIMIT, STOP);

    /** 一个应答器。 */
    public static final class Balise {
        private final String id;
        private final String tag;
        private final List<String> lines;

        Balise(String id, String tag, List<String> lines) {
            this.id = id;
            this.tag = tag;
            this.lines = lines;
        }

        public String id() {
            return id;
        }

        /** 方块坐标 id。 */
        public String tag() {
            return tag;
        }

        public List<String> lines() {
            return lines;
        }

        public String line(int index) {
            return index >= 0 && index < lines.size() ? lines.get(index) : "";
        }

        public String name() {
            return line(0);
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Balise> balises = new LinkedHashMap<>();

    public BaliseRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balises.yml");
    }

    public void load() {
        balises.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        for (String id : yc.getKeys(false)) {
            String tag = yc.getString(id + ".tag", "");
            List<String> lines = new ArrayList<>(yc.getStringList(id + ".lines"));
            balises.put(id, new Balise(id, tag, lines));
        }
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Balise balise : balises.values()) {
            yc.set(balise.id() + ".tag", balise.tag());
            yc.set(balise.id() + ".lines", balise.lines());
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录，应答器表未保存");
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 balises.yml 失败: " + ex.getMessage());
        }
    }

    public Balise get(String id) {
        return balises.get(id);
    }

    public Collection<Balise> all() {
        return balises.values();
    }

    public Collection<Balise> byTag(String tag) {
        List<Balise> list = new ArrayList<>();
        for (Balise balise : balises.values()) {
            if (balise.tag().equalsIgnoreCase(tag)) {
                list.add(balise);
            }
        }
        return list;
    }

    public int size() {
        return balises.size();
    }

    public boolean remove(String id) {
        if (balises.remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    /** 牌子上写的标记，例如 {@code [station]} → station；不是应答器返回 null。 */
    public static String tagOf(Block block) {
        if (!(block.getState() instanceof Sign sign)) {
            return null;
        }
        String first = org.bukkit.ChatColor.stripColor(sign.getLine(0)).trim();
        if (first.length() < 3 || first.charAt(0) != '[' || first.charAt(first.length() - 1) != ']') {
            return null;
        }
        String tag = first.substring(1, first.length() - 1).trim().toLowerCase(Locale.ROOT);
        return TAGS.contains(tag) ? tag : null;
    }

    /** 登记一块牌子为应答器；不是应答器返回 null。 */
    public Balise register(Block block) {
        String tag = tagOf(block);
        if (tag == null) {
            return null;
        }
        Sign sign = (Sign) block.getState();
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < sign.getLines().length; i++) {
            String raw = org.bukkit.ChatColor.stripColor(sign.getLine(i)).trim();
            if (!raw.isEmpty()) {
                lines.add(raw);
            }
        }
        Balise balise = new Balise(RailNode.idOf(block.getLocation()), tag, lines);
        balises.put(balise.id(), balise);
        save();
        return balise;
    }
}
