package cn.aqcraft.metrorail.rail;

import org.bukkit.Location;

import java.util.List;

/**
 * 沿 RailGraph 计算行车许可 MA / 授权终点 EoA。
 *
 * <p>从当前节点沿前进方向逐段推进：累计距离，遇到被他人占用的区段、
 * 无匹配出边（断头）或超过探测上限则停止。返回 EoA 距离与原因。</p>
 */
public final class MovementAuthority {

    /** 一次计算的结果。 */
    public static final class Result {

        private final double eoaDistance;
        private final double limitSpeed;
        private final String reason;
        private final String endNode;
        private final int segments;

        Result(double eoaDistance, double limitSpeed, String reason, String endNode, int segments) {
            this.eoaDistance = eoaDistance;
            this.limitSpeed = limitSpeed;
            this.reason = reason;
            this.endNode = endNode;
            this.segments = segments;
        }

        /** 到授权终点还有多少格。 */
        public double eoaDistance() {
            return eoaDistance;
        }

        /** 该范围内最低限速（blocks/tick），未设限则返回原速度上限。 */
        public double limitSpeed() {
            return limitSpeed;
        }

        /** 停止原因：occupied / end-of-track / max-lookahead / no-graph。 */
        public String reason() {
            return reason;
        }

        public String endNode() {
            return endNode;
        }

        public int segments() {
            return segments;
        }

        public boolean blocked() {
            return "occupied".equals(reason);
        }
    }

    private final RailGraph graph;
    private final OccupancyLedger ledger;
    private final BaliseRegistry balises;

    public MovementAuthority(RailGraph graph, OccupancyLedger ledger, BaliseRegistry balises) {
        this.graph = graph;
        this.ledger = ledger;
        this.balises = balises;
    }

    /** 无图时的兜底结果。 */
    public static Result none(double maxSpeed) {
        return new Result(-1.0, maxSpeed, "no-graph", null, 0);
    }

    /**
     * 计算 MA。
     *
     * @param start      起始节点
     * @param hx         前进方向 x
     * @param hz         前进方向 z
     * @param holder     本列车标识（自己占的段不算阻挡）
     * @param maxSpeed   当前物理上限
     * @param lookahead  最大探测距离（格）
     */
    public Result compute(RailNode start, double hx, double hz, String holder,
                          double maxSpeed, double lookahead) {
        if (graph == null || start == null) {
            return none(maxSpeed);
        }
        double distance = 0.0;
        double limit = maxSpeed;
        RailNode node = start;
        double dirX = hx;
        double dirZ = hz;
        String reason = "max-lookahead";
        int segments = 0;

        while (distance < lookahead) {
            RailEdge edge = graph.bestEdge(node, dirX, dirZ);
            if (edge == null) {
                reason = "end-of-track";
                break;
            }
            if (holder != null && ledger != null && ledger.isBlockedFor(edge.id(), holder)) {
                reason = "occupied";
                break;
            }
            distance += edge.length();
            segments++;
            node = graph.node(edge.to());
            if (node == null) {
                reason = "end-of-track";
                break;
            }
            double flat = Math.sqrt(edge.dx() * edge.dx() + edge.dz() * edge.dz());
            if (flat > 1.0E-6) {
                dirX = edge.dx() / flat;
                dirZ = edge.dz() / flat;
            }
            if (balises != null) {
                for (BaliseRegistry.Balise balise : balises.byTag(BaliseRegistry.LIMIT)) {
                    Location location = SwitchRegistry.locationOf(balise.id());
                    if (location == null) {
                        continue;
                    }
                    if (location.getBlockX() != node.x() || location.getBlockY() != node.y()
                            || location.getBlockZ() != node.z()) {
                        continue;
                    }
                    try {
                        double v = Double.parseDouble(balise.name());
                        if (v > 0.0) {
                            limit = Math.min(limit, v);
                        }
                    } catch (NumberFormatException ignored) {
                        // 非法限速牌忽略
                    }
                }
            }
        }
        return new Result(distance, limit, reason, node.id(), segments);
    }

    /** 沿图取一段轨道坐标点（用于网页绘制）。 */
    public List<Location> trace(RailNode start, double hx, double hz, int maxSegments) {
        List<Location> points = new java.util.ArrayList<>();
        if (graph == null || start == null) {
            return points;
        }
        RailNode node = start;
        double dirX = hx;
        double dirZ = hz;
        for (int i = 0; i < maxSegments; i++) {
            Location location = node.location();
            if (location != null) {
                points.add(location);
            }
            RailEdge edge = graph.bestEdge(node, dirX, dirZ);
            if (edge == null) {
                break;
            }
            RailNode next = graph.node(edge.to());
            if (next == null) {
                break;
            }
            double flat = Math.sqrt(edge.dx() * edge.dx() + edge.dz() * edge.dz());
            if (flat > 1.0E-6) {
                dirX = edge.dx() / flat;
                dirZ = edge.dz() / flat;
            }
            node = next;
        }
        return points;
    }
}
