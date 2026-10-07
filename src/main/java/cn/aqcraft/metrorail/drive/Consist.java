package cn.aqcraft.metrorail.drive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 编组：一串按顺序排列的矿车 id。
 */
public final class Consist {

    private final String id;
    private String name;
    private String ownerName;
    private final List<UUID> carts = new ArrayList<>();

    public Consist(String id, String name, String ownerName) {
        this.id = id;
        this.name = name;
        this.ownerName = ownerName;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String ownerName() {
        return ownerName;
    }

    public void ownerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<UUID> carts() {
        return Collections.unmodifiableList(carts);
    }

    public int size() {
        return carts.size();
    }

    public boolean contains(UUID cart) {
        return carts.contains(cart);
    }

    public UUID head() {
        return carts.isEmpty() ? null : carts.get(0);
    }

    public boolean append(UUID cart) {
        if (cart == null || carts.contains(cart)) {
            return false;
        }
        carts.add(cart);
        return true;
    }

    public boolean remove(UUID cart) {
        return carts.remove(cart);
    }

    /** 直接暴露内部列表，仅供持久化读写。 */
    public List<UUID> raw() {
        return carts;
    }
}
