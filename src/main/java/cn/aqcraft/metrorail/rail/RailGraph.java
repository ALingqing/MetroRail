package cn.aqcraft.metrorail.rail;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 有向轨道图。节点=铁轨方块，边=相邻铁轨连接。
 */
public final class RailGraph {

    /** 水平四方向 + 上下层（坡道）。 */
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}
    };

    private final Map<String, RailNode> nodes = new HashMap<>();
    private final Map<String, RailEdge> edges = new HashMap<>();
    private String originId;
    private String worldName = "-";

    public static boolean isRail(Material material) {
        return material == Material.RAIL
                || material == Material.POWERED_RAIL
                || material == Material.DETECTOR_RAIL
                || material == Material.ACTIVATOR_RAIL;
    }

    public static boolean isRail(Block block) {
        return block != null && isRail(block.getType());
    }

    /** 从起点洪泛建图。返回新增节点数。 */
    public int scan(Location origin, int maxNodes) {
        clear();
        if (origin == null || origin.getWorld() == null) {
            return 0;
        }
        World world = origin.getWorld();
        worldName = world.getName();
        Block start = origin.getBlock();
        if (!isRail(start)) {
            return 0;
        }

        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(RailNode.idOf(origin));

        while (!queue.isEmpty() && nodes.size() < maxNodes) {
            Block block = queue.poll();
            RailNode node = new RailNode(RailNode.idOf(block.getLocation()), worldName,
                    block.getX(), block.getY(), block.getZ());
            nodes.put(node.id(), node);
            if (originId == null) {
                originId = node.id();
            }
            for (int[] offset : NEIGHBOURS) {
                Block next = block.getRelative(offset[0], offset[1], offset[2]);
                if (!isRail(next)) {
                    continue;
                }
                String nextId = RailNode.idOf(next.getLocation());
                boolean isNew = visited.add(nextId);
                RailNode target = nodes.computeIfAbsent(nextId, id ->
                        new RailNode(id, worldName, next.getX(), next.getY(), next.getZ()));
                link(node, target);
                if (isNew) {
                    queue.add(next);
                }
            }
        }
        return nodes.size();
    }

    private void link(RailNode from, RailNode to) {
        if (from.id().equals(to.id())) {
            return;
        }
        String key = RailEdge.keyOf(from.id(), to.id());
        if (edges.containsKey(key)) {
            return;
        }
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        RailEdge edge = new RailEdge(from.id(), to.id(), length, dx, dy, dz);
        edges.put(key, edge);
        from.outgoingRaw().add(to.id());
        to.incomingRaw().add(from.id());
    }

    public void clear() {
        nodes.clear();
        edges.clear();
        originId = null;
        worldName = "-";
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public String worldName() {
        return worldName;
    }

    public String originId() {
        return originId;
    }

    public void originId(String id) {
        this.originId = id;
    }

    public void worldName(String name) {
        this.worldName = name;
    }

    public RailNode node(String id) {
        return id == null ? null : nodes.get(id);
    }

    public RailEdge edge(String from, String to) {
        return edges.get(RailEdge.keyOf(from, to));
    }

    public Collection<RailNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Collection<RailEdge> edges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public Map<String, RailNode> nodeMap() {
        return nodes;
    }

    public Map<String, RailEdge> edgeMap() {
        return edges;
    }

    /** 直接放置一个已建好的节点（读盘用）。 */
    public RailNode putNode(RailNode node) {
        return nodes.put(node.id(), node);
    }

    /** 直接放置一条边并维护邻接表（读盘用）。 */
    public void putEdge(RailEdge edge) {
        edges.put(edge.id(), edge);
        RailNode from = nodes.get(edge.from());
        RailNode to = nodes.get(edge.to());
        if (from != null && !from.outgoingRaw().contains(edge.to())) {
            from.outgoingRaw().add(edge.to());
        }
        if (to != null && !to.incomingRaw().contains(edge.from())) {
            to.incomingRaw().add(edge.from());
        }
    }

    /** 距给定位置最近的节点（受半径限制），没有则返回 null。 */
    public RailNode nearestNode(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        RailNode best = null;
        double bestDist = radius * radius;
        for (RailNode node : nodes.values()) {
            if (!node.worldName().equals(world)) {
                continue;
            }
            double dx = node.x() + 0.5 - location.getX();
            double dy = node.y() + 0.5 - location.getY();
            double dz = node.z() + 0.5 - location.getZ();
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist <= bestDist) {
                bestDist = dist;
                best = node;
            }
        }
        return best;
    }

    /** 沿给定水平方向最贴合的出边。 */
    public RailEdge bestEdge(RailNode node, double hx, double hz) {
        if (node == null) {
            return null;
        }
        RailEdge best = null;
        double bestAlign = 0.5;
        for (String to : node.outgoing()) {
            RailEdge edge = edges.get(RailEdge.keyOf(node.id(), to));
            if (edge == null) {
                continue;
            }
            double align = edge.alignment(hx, hz);
            if (align > bestAlign) {
                bestAlign = align;
                best = edge;
            }
        }
        return best;
    }

    /** 出边列表副本。 */
    public List<RailEdge> outEdges(RailNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        List<RailEdge> list = new ArrayList<>();
        for (String to : node.outgoing()) {
            RailEdge edge = edges.get(RailEdge.keyOf(node.id(), to));
            if (edge != null) {
                list.add(edge);
            }
        }
        return list;
    }
}
