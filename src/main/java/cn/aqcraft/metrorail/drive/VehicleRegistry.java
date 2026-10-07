package cn.aqcraft.metrorail.drive;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 车型注册表：读取 plugins/MetroRail/vehicles/*.yml。
 * 首次运行会从 jar 内置释放 default.yml / express.yml。
 */
public final class VehicleRegistry {

    private final JavaPlugin plugin;
    private final Map<String, VehicleType> vehicles = new LinkedHashMap<>();

    public VehicleRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        vehicles.clear();
        File dir = new File(plugin.getDataFolder(), "vehicles");
        if (!dir.isDirectory()) {
            plugin.saveResource("vehicles/default.yml", false);
            plugin.saveResource("vehicles/express.yml", false);
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        List<File> ordered = new ArrayList<>();
        if (files != null) {
            ordered.addAll(Arrays.asList(files));
            ordered.sort(Comparator.comparing(File::getName));
        }
        for (File file : ordered) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            vehicles.put(id, new VehicleType(
                    id,
                    yc.getString("display-name", id),
                    clampMin(yc.getDouble("max-speed", 0.40), 0.01),
                    clampMin(yc.getDouble("acceleration", 0.020), 0.001),
                    clampMin(yc.getDouble("brake", 0.030), 0.001),
                    clampMin(yc.getDouble("emergency-brake", 0.100), 0.001),
                    clampMin(yc.getDouble("friction", 0.010), 0.0)));
        }
        if (vehicles.isEmpty()) {
            vehicles.put("default", new VehicleType("default", "default", 0.40, 0.020, 0.030, 0.100, 0.010));
        }
    }

    private static double clampMin(double value, double min) {
        return Math.max(min, value);
    }

    public boolean has(String id) {
        return id != null && vehicles.containsKey(id.toLowerCase(java.util.Locale.ROOT));
    }

    public VehicleType get(String id) {
        if (id == null) {
            return null;
        }
        return vehicles.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public VehicleType fallback() {
        return vehicles.values().iterator().next();
    }

    public Collection<VehicleType> all() {
        return vehicles.values();
    }

    public Set<String> ids() {
        return vehicles.keySet();
    }

    public String joinIds() {
        return String.join(", ", vehicles.keySet());
    }
}
