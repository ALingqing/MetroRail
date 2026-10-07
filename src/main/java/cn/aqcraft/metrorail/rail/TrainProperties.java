package cn.aqcraft.metrorail.rail;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 列车属性与模板：属性字典 + savedtrain 模板。
 */
public final class TrainProperties {

    /** 一部列车的属性。 */
    public static final class Properties {
        private final String train;
        private final Map<String, String> values = new LinkedHashMap<>();

        Properties(String train) {
            this.train = train;
        }

        public String train() {
            return train;
        }

        public Map<String, String> values() {
            return values;
        }

        public String get(String key) {
            return values.get(key.toLowerCase(Locale.ROOT));
        }

        public void set(String key, String value) {
            if (value == null) {
                values.remove(key.toLowerCase(Locale.ROOT));
            } else {
                values.put(key.toLowerCase(Locale.ROOT), value);
            }
        }

        public String name() {
            return get("name");
        }

        public String displayName() {
            String display = get("displayname");
            return display != null ? display : name();
        }

        public String destination() {
            return get("destination");
        }

        public String trainNumber() {
            return get("trainnumber");
        }

        public double maxSpeed(double fallback) {
            String value = get("maxspeed");
            if (value == null) {
                return fallback;
            }
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }

    /** 一部模板。 */
    public static final class Template {
        private final String id;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final List<String> carts = new ArrayList<>();
        private String world;
        private double x;
        private double y;
        private double z;

        Template(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public Map<String, String> properties() {
            return properties;
        }

        public List<String> carts() {
            return carts;
        }

        public String world() {
            return world;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }
    }

    public static final List<String> KEYS = List.of(
            "name", "displayname", "trainnumber", "destination", "maxspeed", "spacing");

    private final Map<String, Properties> trains = new LinkedHashMap<>();
    private final Map<String, Template> templates = new LinkedHashMap<>();

    public Properties of(String train) {
        return trains.computeIfAbsent(train.toLowerCase(Locale.ROOT),
                key -> new Properties(train.toLowerCase(Locale.ROOT)));
    }

    public Properties find(String train) {
        return train == null ? null : trains.get(train.toLowerCase(Locale.ROOT));
    }

    public Map<String, Properties> trains() {
        return trains;
    }

    public Template template(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, Template> templates() {
        return templates;
    }

    /** 保存模板（来自当前编组车厢列表）。 */
    public Template saveTemplate(String id, String world, Location origin, List<String> carts,
                                 Map<String, String> properties) {
        Template template = new Template(id.toLowerCase(Locale.ROOT));
        template.world = world;
        if (origin != null) {
            template.x = origin.getX();
            template.y = origin.getY();
            template.z = origin.getZ();
        }
        template.carts.addAll(carts);
        template.properties.putAll(properties);
        templates.put(template.id(), template);
        return template;
    }

    public boolean removeTemplate(String id) {
        return templates.remove(id == null ? "" : id.toLowerCase(Locale.ROOT)) != null;
    }

    public void clearTemplates() {
        templates.clear();
    }

    public void clearProperties() {
        trains.clear();
    }
}
