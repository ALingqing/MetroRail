package cn.aqcraft.metrorail.rail;

import java.util.Locale;

/**
 * 列控模式（对应 SkyRail 的 shadow/bypass/isolate/enforce）。
 */
public enum ControlMode {

    /** 影子：只读计算并显示 MA，不施制动。 */
    SHADOW("shadow"),
    /** 旁路：完全忽略 MA 计算。 */
    BYPASS("bypass"),
    /** 隔离：不计算 MA，等同于纯手动。 */
    ISOLATE("isolate"),
    /** 强制保护：MA 触发时强制紧急制动（实验）。 */
    ENFORCE("enforce");

    private final String id;

    ControlMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ControlMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (ControlMode mode : values()) {
            if (mode.id.equals(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
