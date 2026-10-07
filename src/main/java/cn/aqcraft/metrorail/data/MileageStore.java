package cn.aqcraft.metrorail.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 里程账本，存 plugins/MetroRail/mileage.yml。
 */
public final class MileageStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Double> totals = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();

    public MileageStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mileage.yml");
    }

    public void load() {
        totals.clear();
        names.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        for (String key : yc.getKeys(false)) {
            totals.put(key, yc.getDouble(key + ".blocks", 0.0));
            String name = yc.getString(key + ".name");
            if (name != null) {
                names.put(key, name);
            }
        }
    }

    public void save() {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            yc.set(e.getKey() + ".blocks", e.getValue());
            String name = names.get(e.getKey());
            if (name != null) {
                yc.set(e.getKey() + ".name", name);
            }
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录，里程未保存");
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 mileage.yml 失败: " + ex.getMessage());
        }
    }

    public double total(UUID player) {
        return totals.getOrDefault(player.toString(), 0.0);
    }

    public String name(UUID player) {
        return names.get(player.toString());
    }

    public void add(UUID player, String playerName, double blocks) {
        if (player == null || blocks <= 0.0) {
            return;
        }
        String key = player.toString();
        totals.merge(key, blocks, Double::sum);
        if (playerName != null) {
            names.put(key, playerName);
        }
    }

    public void clear() {
        totals.clear();
        names.clear();
        save();
    }

    public int size() {
        return totals.size();
    }

    /** 把玩家名换成 UUID 键（用于 /mr mileage <玩家> 查询）。 */
    public UUID lookupByName(String playerName) {
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (e.getValue() != null
                    && e.getValue().toLowerCase(Locale.ROOT).equals(playerName.toLowerCase(Locale.ROOT))) {
                try {
                    return UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
