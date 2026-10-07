package cn.aqcraft.metrorail.rail;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 列车完整性监测（TIMS）：校验编组车厢是否齐全、间距是否异常。
 *
 * <p>SkyRail 的 TIMS 会按时效采样车厢位置，若某节丢失或间距突变则判定为
 * 「完整性丢失」并触发保护制动。这里实现同一套语义的轻量版本。</p>
 */
public final class TimsMonitor {

    /** 一节车厢的采样。 */
    public static final class Sample {
        private final UUID cart;
        private long lastSeen;
        private double lastX;
        private double lastY;
        private double lastZ;
        private boolean seen;

        Sample(UUID cart) {
            this.cart = cart;
        }

        public UUID cart() {
            return cart;
        }

        public boolean seen() {
            return seen;
        }

        public long lastSeen() {
            return lastSeen;
        }
    }

    /** 一次校验结果。 */
    public static final class Verdict {
        private final boolean intact;
        private final String reason;
        private final int missing;

        Verdict(boolean intact, String reason, int missing) {
            this.intact = intact;
            this.reason = reason;
            this.missing = missing;
        }

        public boolean intact() {
            return intact;
        }

        public String reason() {
            return reason;
        }

        public int missing() {
            return missing;
        }
    }

    private final Map<UUID, Sample> samples = new HashMap<>();

    /** 记录某节车厢本 tick 的观测位置。 */
    public void observe(UUID cart, double x, double y, double z, long tick) {
        Sample sample = samples.computeIfAbsent(cart, Sample::new);
        sample.lastSeen = tick;
        sample.lastX = x;
        sample.lastY = y;
        sample.lastZ = z;
        sample.seen = true;
    }

    /** 车厢是否在时效窗口内被观测到。 */
    public boolean fresh(UUID cart, long tick, long timeoutTicks) {
        Sample sample = samples.get(cart);
        return sample != null && sample.seen && (tick - sample.lastSeen) <= timeoutTicks;
    }

    /**
     * 校验一个编组。
     *
     * @param carts         编组车厢
     * @param tick          当前 tick
     * @param timeoutTicks  采样时效
     * @param maxSpacing    相邻车厢最大间距（格），0 表示不检查
     */
    public Verdict verify(Iterable<UUID> carts, long tick, long timeoutTicks, double maxSpacing) {
        int missing = 0;
        double previousX = Double.NaN;
        double previousY = Double.NaN;
        double previousZ = Double.NaN;
        boolean spacingBad = false;
        int total = 0;

        for (UUID cart : carts) {
            total++;
            Sample sample = samples.get(cart);
            if (sample == null || !sample.seen || (tick - sample.lastSeen) > timeoutTicks) {
                missing++;
                continue;
            }
            if (!Double.isNaN(previousX)) {
                double dx = sample.lastX - previousX;
                double dy = sample.lastY - previousY;
                double dz = sample.lastZ - previousZ;
                double spacing = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (maxSpacing > 0.0 && spacing > maxSpacing) {
                    spacingBad = true;
                }
            }
            previousX = sample.lastX;
            previousY = sample.lastY;
            previousZ = sample.lastZ;
        }

        if (total == 0) {
            return new Verdict(true, "empty", 0);
        }
        if (missing > 0) {
            return new Verdict(false, "missing", missing);
        }
        if (spacingBad) {
            return new Verdict(false, "spacing", 0);
        }
        return new Verdict(true, "ok", 0);
    }

    public void forget(UUID cart) {
        samples.remove(cart);
    }

    public void clear() {
        samples.clear();
    }
}
