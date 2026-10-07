package cn.aqcraft.metrorail.rail;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 列车属性与模板持久化，存 trains-rail.yml。
 */
public final class TrainStore {

    private final JavaPlugin plugin;
    private final File file;

    public TrainStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rail-trains.yml");
    }

    public void save(TrainProperties properties) {
        YamlConfiguration yc = new YamlConfiguration();
        for (Map.Entry<String, TrainProperties.Properties> entry : properties.trains().entrySet()) {
            for (Map.Entry<String, String> value : entry.getValue().values().entrySet()) {
                yc.set("trains." + entry.getKey() + "." + value.getKey(), value.getValue());
            }
        }
        for (Map.Entry<String, TrainProperties.Template> entry : properties.templates().entrySet()) {
            TrainProperties.Template template = entry.getValue();
            String base = "templates." + entry.getKey();
            yc.set(base + ".world", template.world());
            yc.set(base + ".x", template.x());
            yc.set(base + ".y", template.y());
            yc.set(base + ".z", template.z());
            yc.set(base + ".carts", template.carts());
            for (Map.Entry<String, String> value : template.properties().entrySet()) {
                yc.set(base + ".properties." + value.getKey(), value.getValue());
            }
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 rail-trains.yml 失败: " + ex.getMessage());
        }
    }

    public void load(TrainProperties properties) {
        properties.clearTemplates();
        properties.clearProperties();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection trains = yc.getConfigurationSection("trains");
        if (trains != null) {
            for (String key : trains.getKeys(false)) {
                TrainProperties.Properties prop = properties.of(key);
                ConfigurationSection section = trains.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                for (String field : section.getKeys(false)) {
                    String value = section.getString(field);
                    if (value != null) {
                        prop.set(field, value);
                    }
                }
            }
        }

        ConfigurationSection templates = yc.getConfigurationSection("templates");
        if (templates != null) {
            for (String key : templates.getKeys(false)) {
                ConfigurationSection section = templates.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                List<String> carts = new ArrayList<>(section.getStringList("carts"));
                TrainProperties.Template template = properties.saveTemplate(
                        key, section.getString("world", "-"), null, carts, new java.util.LinkedHashMap<>());
                ConfigurationSection propSection = section.getConfigurationSection("properties");
                if (propSection != null) {
                    for (String field : propSection.getKeys(false)) {
                        String value = propSection.getString(field);
                        if (value != null) {
                            template.properties().put(field, value);
                        }
                    }
                }
            }
        }
    }
}
