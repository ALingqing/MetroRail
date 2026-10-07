package cn.aqcraft.metrorail.i18n;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 语言文件层。key 缺失时回退到 zh_CN，再回退到 key 本身。
 */
public final class Messages {

    private static final String FALLBACK = "zh_CN";

    private final JavaPlugin plugin;
    private final Map<String, String> cache = new HashMap<>();
    private String locale = FALLBACK;
    private String prefix = "&b&l[MetroRail]&r";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String locale) {
        this.locale = locale == null || locale.isBlank() ? FALLBACK : locale;
        copyBundled(this.locale);
        copyBundled(FALLBACK);
        copyBundled("en_US");

        cache.clear();
        cache.putAll(readFile(new File(plugin.getDataFolder(), "lang/" + this.locale + ".yml")));
        if (cache.isEmpty()) {
            cache.putAll(readResource(this.locale));
        }
        if (cache.isEmpty()) {
            this.locale = FALLBACK;
            cache.putAll(readResource(FALLBACK));
        }
        // 缺失键回退
        for (Map.Entry<String, String> e : readResource(FALLBACK).entrySet()) {
            cache.putIfAbsent(e.getKey(), e.getValue());
        }
        prefix = cache.getOrDefault("prefix", prefix);
    }

    private void copyBundled(String loc) {
        File file = new File(plugin.getDataFolder(), "lang/" + loc + ".yml");
        if (!file.isFile() && plugin.getResource("lang/" + loc + ".yml") != null) {
            plugin.saveResource("lang/" + loc + ".yml", false);
        }
    }

    private Map<String, String> readFile(File file) {
        Map<String, String> map = new HashMap<>();
        if (file != null && file.isFile()) {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            for (String key : yc.getKeys(false)) {
                String value = yc.getString(key);
                if (value != null) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private Map<String, String> readResource(String loc) {
        Map<String, String> map = new HashMap<>();
        InputStream in = plugin.getResource("lang/" + loc + ".yml");
        if (in == null) {
            return map;
        }
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        for (String key : yc.getKeys(false)) {
            String value = yc.getString(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }

    public String raw(String key) {
        return cache.getOrDefault(key, key);
    }

    public String get(String key, Object... args) {
        String value = raw(key);
        if (args != null && args.length > 0) {
            try {
                value = String.format(value, args);
            } catch (RuntimeException ignored) {
                // 占位符不匹配时保留原文
            }
        }
        return color(value);
    }

    public String prefixed(String key, Object... args) {
        return color(prefix) + " " + get(key, args);
    }

    public static String color(String value) {
        return value == null ? "" : ChatColor.translateAlternateColorCodes('&', value);
    }

    public String locale() {
        return locale;
    }

    /** 可用语言 id（jar 内置 + 数据目录），排序去重。 */
    public Set<String> available() {
        Set<String> ids = new TreeSet<>();
        File dir = new File(plugin.getDataFolder(), "lang");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                ids.add(f.getName().substring(0, f.getName().length() - 4));
            }
        }
        ids.addAll(new LinkedHashSet<>(Arrays.asList("zh_CN", "en_US")));
        return ids;
    }

    public boolean exists(String loc) {
        File file = new File(plugin.getDataFolder(), "lang/" + loc + ".yml");
        return file.isFile() || plugin.getResource("lang/" + loc + ".yml") != null;
    }
}
