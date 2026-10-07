package cn.aqcraft.metrorail.metro;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Metro 对接层。
 * <p>Metro 的公开 API 是 Kotlin 对象 org.cubexmc.metro.api.MetroAPI（静态 getInstance()）。
 * 这里用反射访问，避免把 Metro 的 jar 打进插件、也避免编译期强耦合；
 * Metro 不存在或 API 变动时只降级，不影响驾驶功能。</p>
 */
public final class MetroHook {

    private static final String API_CLASS = "org.cubexmc.metro.api.MetroAPI";

    private final Plugin metroPlugin;
    private final Class<?> apiClass;
    private final Method getInstance;
    private final Method getLines;
    private final Method getStops;

    private Object api;

    public MetroHook() {
        this.metroPlugin = Bukkit.getPluginManager().getPlugin("Metro");
        Class<?> clazz = null;
        Method instance = null;
        Method lines = null;
        Method stops = null;
        if (metroPlugin != null) {
            try {
                clazz = Class.forName(API_CLASS);
                instance = clazz.getMethod("getInstance");
                lines = tryMethod(clazz, "getLines");
                stops = tryMethod(clazz, "getStops");
            } catch (Throwable ignored) {
                clazz = null;
            }
        }
        this.apiClass = clazz;
        this.getInstance = instance;
        this.getLines = lines;
        this.getStops = stops;
        refresh();
    }

    private static Method tryMethod(Class<?> clazz, String name) {
        try {
            return clazz.getMethod(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 重新取一次 API 单例（Metro 可能在 MetroRail 之后才初始化完成）。 */
    public void refresh() {
        api = null;
        if (getInstance == null) {
            return;
        }
        try {
            Object value = getInstance.invoke(null);
            api = value;
        } catch (Throwable ignored) {
            api = null;
        }
    }

    public boolean available() {
        return apiClass != null && getInstance != null && api != null;
    }

    public boolean pluginPresent() {
        return metroPlugin != null;
    }

    public String pluginVersion() {
        return metroPlugin == null ? "-" : metroPlugin.getDescription().getVersion();
    }

    /** 线路数量，取不到返回 -1。 */
    public int lineCount() {
        return countOf(getLines);
    }

    /** 车站数量，取不到返回 -1。 */
    public int stopCount() {
        return countOf(getStops);
    }

    private int countOf(Method method) {
        if (api == null || method == null) {
            return -1;
        }
        try {
            Object result = method.invoke(api);
            if (result instanceof java.util.Map) {
                return ((java.util.Map<?, ?>) result).size();
            }
            if (result instanceof java.util.Collection) {
                return ((java.util.Collection<?>) result).size();
            }
        } catch (Throwable ignored) {
            // 忽略，返回未知
        }
        return -1;
    }

    /** 线路 id 列表，取不到返回空表。 */
    @SuppressWarnings("unchecked")
    public List<String> lineIds() {
        if (api == null || getLines == null) {
            return Collections.emptyList();
        }
        try {
            Object result = getLines.invoke(api);
            if (result instanceof java.util.Map) {
                return new java.util.ArrayList<>(((java.util.Map<String, ?>) result).keySet());
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return Collections.emptyList();
    }

    public String describe() {
        if (!pluginPresent()) {
            return "Metro 未安装";
        }
        if (!available()) {
            return "Metro " + pluginVersion() + "（API 尚未就绪）";
        }
        return "Metro " + pluginVersion() + "（API 可用，线路 " + lineCount() + "，车站 " + stopCount() + "）";
    }
}
