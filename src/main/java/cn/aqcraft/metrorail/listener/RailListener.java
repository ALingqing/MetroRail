package cn.aqcraft.metrorail.listener;

import cn.aqcraft.metrorail.MetroRailPlugin;
import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.i18n.Messages;
import cn.aqcraft.metrorail.rail.BaliseRegistry;
import cn.aqcraft.metrorail.rail.RailNode;
import cn.aqcraft.metrorail.rail.RailServices;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 通用监听：应答器登记、道岔交互、掉线撤权。
 */
public final class RailListener implements Listener {

    private final MetroRailPlugin plugin;
    private final RailConfig config;
    private final DriveManager manager;
    private final RailServices rail;

    public RailListener(MetroRailPlugin plugin, RailConfig config, DriveManager manager, RailServices rail) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.rail = rail;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        String tag = BaliseRegistry.tagOf(event.getBlock());
        if (tag == null) {
            return;
        }
        BaliseRegistry.Balise balise = rail.balises().register(event.getBlock());
        if (balise != null) {
            Player player = event.getPlayer();
            player.sendMessage(Messages.color("&b&l[MetroRail] &7已登记应答器 &f[" + tag + "] &7" + balise.id()));
            rail.events().add("balise", player.getName() + " 登记 [" + tag + "] " + balise.id());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!RailGraph.isRail(event.getBlockPlaced())) {
            return;
        }
        // 铺设铁轨时懒加载：把玩家附近的新轨道并入图（若已有图）
        if (rail.graph().isEmpty()) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType().name().endsWith("SIGN")) {
            String id = RailNode.idOf(block.getLocation());
            if (rail.balises().get(id) != null) {
                rail.balises().remove(id);
                plugin.getLogger().info("应答器 " + id + " 已随牌子移除");
            }
        }
    }

    /** 手持「道岔棒」（红石火把）右键拉杆 → 转换道岔。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.REDSTONE_TORCH) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.LEVER) {
            return;
        }
        if (!event.getPlayer().hasPermission("metrorail.switch")) {
            return;
        }
        String id = RailNode.idOf(block.getLocation());
        String name = null;
        for (cn.aqcraft.metrorail.rail.SwitchRegistry.Switch sw : rail.switches().all()) {
            if (id.equals(sw.location())) {
                name = sw.id();
                break;
            }
        }
        if (name == null) {
            event.getPlayer().sendMessage(Messages.color("&c&l[MetroRail] &7该拉杆尚未登记为道岔，先 /mr switch scan <name>"));
            return;
        }
        int changed = rail.switches().toggle(rail.switches().get(name));
        event.getPlayer().sendMessage(Messages.color(
                "&b&l[MetroRail] &7道岔 &f" + name + " &7已转换（" + changed + " 段）"));
        rail.events().add("switch", event.getPlayer().getName() + " 转换道岔 " + name);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isDriving(event.getPlayer().getUniqueId())) {
            double distance = manager.release(event.getPlayer().getUniqueId());
            if (distance >= 0.0) {
                rail.events().add("drive", event.getPlayer().getName() + " 掉线撤权，里程 " + (long) distance);
            }
        }
    }

    // ---- 供外部使用的简易引用 ----
    private static final class RailGraph {
        static boolean isRail(Block block) {
            return cn.aqcraft.metrorail.rail.RailGraph.isRail(block);
        }
    }
}
