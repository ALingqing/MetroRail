package cn.aqcraft.metrorail.rail;

import org.bukkit.Location;

import java.util.Locale;

/**
 * 列车沿图定位：最近节点 + 在边上的偏移。
 */
public final class TrainLocator {

    /** 一次定位结果。 */
    public static final class Fix {
        private final String nodeId;
        private final String edgeId;
        private final double offset;
        private final double nodeDistance;
        private final double progress;

        Fix(String nodeId, String edgeId, double offset, double nodeDistance, double progress) {
            this.nodeId = nodeId;
            this.edgeId = edgeId;
            this.offset = offset;
            this.nodeDistance = nodeDistance;
            this.progress = progress;
        }

        public String nodeId() {
            return nodeId;
        }

        public String edgeId() {
            return edgeId;
        }

        /** 距起始节点的格数。 */
        public double offset() {
            return offset;
        }

        /** 列车到最近图节点的直线距离。 */
        public double nodeDistance() {
            return nodeDistance;
        }

        /** 在该边上的推进比例 0..1。 */
        public double progress() {
            return progress;
        }

        public String describe() {
            return String.format(Locale.ROOT, "%s +%.2f", nodeId == null ? "-" : nodeId, offset);
        }
    }

    private final RailGraph graph;

    public TrainLocator(RailGraph graph) {
        this.graph = graph;
    }

    /**
     * 定位。
     *
     * @param location 列车位置
     * @param hx       前进方向 x
     * @param hz       前进方向 z
     * @param radius   最近节点搜索半径
     */
    public Fix locate(Location location, double hx, double hz, double radius) {
        if (graph == null || location == null) {
            return null;
        }
        RailNode node = graph.nearestNode(location, radius);
        if (node == null) {
            return null;
        }
        double nodeDistance = 0.0;
        Location nodeLocation = node.location();
        if (nodeLocation != null) {
            nodeDistance = nodeLocation.distance(location);
        }
        RailEdge edge = graph.bestEdge(node, hx, hz);
        if (edge == null) {
            return new Fix(node.id(), null, 0.0, nodeDistance, 0.0);
        }
        RailNode to = graph.node(edge.to());
        if (to == null || nodeLocation == null) {
            return new Fix(node.id(), edge.id(), 0.0, nodeDistance, 0.0);
        }
        double dx = to.x() - node.x();
        double dz = to.z() - node.z();
        double len2 = dx * dx + dz * dz;
        double offset = 0.0;
        double progress = 0.0;
        if (len2 > 1.0E-6) {
            double px = location.getX() - (node.x() + 0.5);
            double pz = location.getZ() - (node.z() + 0.5);
            progress = Math.max(0.0, Math.min(1.0, (px * dx + pz * dz) / len2));
            offset = progress * edge.length();
        }
        return new Fix(node.id(), edge.id(), offset, nodeDistance, progress);
    }
}
