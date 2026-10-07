package cn.aqcraft.metrorail.drive;

/**
 * 牵引/制动级位。level 为正表示牵引，负表示制动。
 */
public enum Notch {
    P4(4, Kind.POWER),
    P3(3, Kind.POWER),
    P2(2, Kind.POWER),
    P1(1, Kind.POWER),
    N(0, Kind.NEUTRAL),
    B1(-1, Kind.BRAKE),
    B2(-2, Kind.BRAKE),
    B3(-3, Kind.BRAKE),
    B4(-4, Kind.BRAKE),
    B5(-5, Kind.BRAKE),
    B6(-6, Kind.BRAKE),
    B7(-7, Kind.BRAKE),
    EB(-8, Kind.EMERGENCY);

    public enum Kind {POWER, NEUTRAL, BRAKE, EMERGENCY}

    private final int level;
    private final Kind kind;

    Notch(int level, Kind kind) {
        this.level = level;
        this.kind = kind;
    }

    public int level() {
        return level;
    }

    public Kind kind() {
        return kind;
    }

    /** 牵引档位比例 0..1（P4 = 1.0）。 */
    public double powerRatio() {
        return kind == Kind.POWER ? level / 4.0 : 0.0;
    }

    /** 制动档位比例 0..1（B7 = 1.0）。 */
    public double brakeRatio() {
        return kind == Kind.BRAKE ? -level / 7.0 : 0.0;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 解析 p1..p4 / n / b1..b7 / eb，失败返回 null。 */
    public static Notch parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        switch (s) {
            case "n":
            case "neutral":
            case "0":
                return N;
            case "eb":
            case "emergency":
                return EB;
            default:
                break;
        }
        for (Notch notch : values()) {
            if (notch.id().equals(s)) {
                return notch;
            }
        }
        return null;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (Notch notch : values()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(notch.id());
        }
        return sb.toString();
    }
}
