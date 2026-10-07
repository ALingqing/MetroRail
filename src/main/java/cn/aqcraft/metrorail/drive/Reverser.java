package cn.aqcraft.metrorail.drive;

/**
 * 换向器档位。
 */
public enum Reverser {
    FORWARD(1, "forward"),
    NEUTRAL(0, "neutral"),
    BACKWARD(-1, "backward");

    private final int sign;
    private final String id;

    Reverser(int sign, String id) {
        this.sign = sign;
        this.id = id;
    }

    /** 行驶方向符号：+1 车头方向，-1 反向，0 空档。 */
    public int sign() {
        return sign;
    }

    public String id() {
        return id;
    }

    public Reverser next() {
        switch (this) {
            case FORWARD:
                return NEUTRAL;
            case NEUTRAL:
                return BACKWARD;
            default:
                return FORWARD;
        }
    }

    public static Reverser parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        switch (s) {
            case "forward":
            case "f":
            case "up":
            case "+":
                return FORWARD;
            case "neutral":
            case "n":
            case "0":
                return NEUTRAL;
            case "backward":
            case "back":
            case "b":
            case "reverse":
            case "r":
            case "-":
                return BACKWARD;
            default:
                return null;
        }
    }

    public static String allIds() {
        return "forward neutral backward";
    }
}
