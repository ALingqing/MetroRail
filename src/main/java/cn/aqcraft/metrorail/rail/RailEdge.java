package cn.aqcraft.metrorail.rail;

/**
 * 轨道图有向边：从 from 指向 to。
 */
public final class RailEdge {

    private final String id;
    private final String from;
    private final String to;
    private final double length;
    private final double dx;
    private final double dy;
    private final double dz;

    public RailEdge(String from, String to, double length, double dx, double dy, double dz) {
        this.from = from;
        this.to = to;
        this.length = length;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.id = keyOf(from, to);
    }

    public static String keyOf(String from, String to) {
        return from + ">" + to;
    }

    public String id() {
        return id;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public double length() {
        return length;
    }

    public double dx() {
        return dx;
    }

    public double dy() {
        return dy;
    }

    public double dz() {
        return dz;
    }

    /** 水平方向单位向量与给定方向的贴合度（-1..1）。 */
    public double alignment(double hx, double hz) {
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0E-6) {
            return 0.0;
        }
        return (dx * hx + dz * hz) / flat;
    }

    @Override
    public String toString() {
        return id;
    }
}
