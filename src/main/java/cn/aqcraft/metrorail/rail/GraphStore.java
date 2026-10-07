package cn.aqcraft.metrorail.rail;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * RailGraph 持久化，存 plugins/MetroRail/graph.yml。
 */
public final class GraphStore {

    private final JavaPlugin plugin;
    private final File file;

    public GraphStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graph.yml");
    }

    public void save(RailGraph graph) {
        YamlConfiguration yc = new YamlConfiguration();
        yc.set("origin", graph.originId());
        yc.set("world", graph.worldName());
        List<String> nodes = new ArrayList<>();
        for (RailNode node : graph.nodes()) {
            nodes.add(node.id());
        }
        yc.set("nodes", nodes);
        List<String> edges = new ArrayList<>();
        for (RailEdge edge : graph.edges()) {
            edges.add(edge.from() + "|" + edge.to() + "|" + String.format(java.util.Locale.ROOT, "%.3f", edge.length()));
        }
        yc.set("edges", edges);
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建数据目录，轨道图未保存");
                return;
            }
            yc.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("保存 graph.yml 失败: " + ex.getMessage());
        }
    }

    /** 读盘重建图；失败返回空图。 */
    public void load(RailGraph graph) {
        graph.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
        graph.originId(yc.getString("origin"));
        String world = yc.getString("world", "-");
        graph.worldName(world);

        for (String id : yc.getStringList("nodes")) {
            String[] parts = id.split(":");
            if (parts.length < 4) {
                continue;
            }
            try {
                graph.putNode(new RailNode(id, parts[0],
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {
                // 跳过损坏节点
            }
        }
        for (String raw : yc.getStringList("edges")) {
            String[] parts = raw.split("\\|");
            if (parts.length != 3) {
                continue;
            }
            RailNode from = graph.node(parts[0]);
            RailNode to = graph.node(parts[1]);
            if (from == null || to == null) {
                continue;
            }
            double length;
            try {
                length = Double.parseDouble(parts[2]);
            } catch (NumberFormatException ignored) {
                length = 1.0;
            }
            graph.putEdge(new RailEdge(parts[0], parts[1], length,
                    to.x() - from.x(), to.y() - from.y(), to.z() - from.z()));
        }
    }

    /** 供其他模块写入任意键（占位，保持单文件风格）。 */
    public ConfigurationSection section(String name) {
        if (!file.isFile()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(file).getConfigurationSection(name);
    }
}
