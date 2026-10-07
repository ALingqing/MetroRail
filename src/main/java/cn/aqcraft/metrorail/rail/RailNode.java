package cn.aqcraft.metrorail.rail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 轨道图节点：一个铁轨方块。
 */
public final class RailNode {

    private final String id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;

    private final List<String> outgoing = new ArrayList<>();
    private final List<String> incoming = new ArrayList<>();

    public RailNode(String id, String world, int x, int y, int z) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static String idOf(Location location) {
        return idOf(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static String idOf(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public String id() {
        return id;
    }

    public String worldName() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public List<String> outgoing() {
        return Collections.unmodifiableList(outgoing);
    }

    public List<String> incoming() {
        return Collections.unmodifiableList(incoming);
    }

    public List<String> outgoingRaw() {
        return outgoing;
    }

    public List<String> incomingRaw() {
        return incoming;
    }

    /** 节点位置；世界未加载时返回 null。 */
    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x + 0.5, y, z + 0.5);
    }

    @Override
    public String toString() {
        return id;
    }
}
