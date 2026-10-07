package cn.aqcraft.metrorail.rail;

import java.util.Locale;

/**
 * 列车运行状态（对应 SkyRail 的 SB/FS/SH/SR/TR/PT）。
 */
public enum TrainState {

    /** 常用制动 Service Brake。 */
    SERVICE_BRAKE("SB", "state-sb"),
    /** 全常用制动 Full Service。 */
    FULL_SERVICE("FS", "state-fs"),
    /** 调车 Shunt。 */
    SHUNT("SH", "state-sh"),
    /** 信号恢复 Signal Reset。 */
    SIGNAL_RESET("SR", "state-sr"),
    /** 紧急制动 Trip。 */
    TRIP("TR", "state-tr"),
    /** 防护 Protection（强制保护通道）。 */
    PROTECTION("PT", "state-pt");

    private final String id;
    private final String key;

    TrainState(String id, String key) {
        this.id = id;
        this.key = key;
    }

    public String id() {
        return id;
    }

    public String key() {
        return key;
    }

    public static TrainState parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (TrainState state : values()) {
            if (state.id.equals(value) || state.name().equals(value)) {
                return state;
            }
        }
        return null;
    }
}
