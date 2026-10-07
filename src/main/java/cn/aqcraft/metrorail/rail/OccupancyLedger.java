package cn.aqcraft.metrorail.rail;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 占用账本：列车对区段（有向边）的预约与持有。
 *
 * <p>两种持有方式：{@link #occupy} 独占（同一时刻只允许一列车），
 * {@link #hold} 共享（多列车可同时持有，用于进路预留）。</p>
 */
public final class OccupancyLedger {

    private final JavaPlugin plugin;
    private final File file;
    /** 边 id -> 持有者集合。 */
    private final Map<String, Set<String>> holders = new LinkedHashMap<>();

    public OccupancyLedger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "occupancy.yml");
    }

    public void load() {
        holders.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        for (String edge : yc.getKeys(false)) {
            Set<String> set = new LinkedHashSet<>(yc.getStringList(edge));
            if (!set.isEmpty()) {
                holders.put(edge, set);
            }
        }
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<String, Set<String>> entry : holders.entrySet()) {
            yc.set(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 occupancy.yml 失败: " + ex.getMessage());
        }
    }

    /** 该边当前是否无人持有。 */
    public boolean isFree(String edgeId) {
        Set<String> set = holders.get(edgeId);
        return set == null || set.isEmpty();
    }

    /** 是否被除 holder 之外的人持有。 */
    public boolean isBlockedFor(String edgeId, String holder) {
        Set<String> set = holders.get(edgeId);
        return set != null && !set.isEmpty() && !set.contains(holder);
    }

    /** 当前持有者（可能多个）。 */
    public Set<String> holdersOf(String edgeId) {
        Set<String> set = holders.get(edgeId);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    /** 独占占用；已被别人占则返回 false。 */
    public boolean occupy(String edgeId, String holder) {
        Set<String> set = holders.computeIfAbsent(edgeId, key -> new LinkedHashSet<>());
        if (!set.isEmpty() && !set.contains(holder)) {
            return false;
        }
        set.add(holder);
        return true;
    }

    /** 共享持有，永远成功。 */
    public void hold(String edgeId, String holder) {
        holders.computeIfAbsent(edgeId, key -> new LinkedHashSet<>()).add(holder);
    }

    public void release(String edgeId, String holder) {
        Set<String> set = holders.get(edgeId);
        if (set == null) {
            return;
        }
        set.remove(holder);
        if (set.isEmpty()) {
            holders.remove(edgeId);
        }
    }

    /** 释放该持有者的全部占用。 */
    public int releaseAll(String holder) {
        int count = 0;
        List<String> empty = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : holders.entrySet()) {
            if (entry.getValue().remove(holder)) {
                count++;
            }
            if (entry.getValue().isEmpty()) {
                empty.add(entry.getKey());
            }
        }
        for (String key : empty) {
            holders.remove(key);
        }
        return count;
    }

    public Collection<String> occupiedEdges() {
        return holders.keySet();
    }

    public int size() {
        return holders.size();
    }

    public void clear() {
        holders.clear();
    }
}
