package cn.aqcraft.metrorail.hud;

import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.i18n.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.UUID;

/**
 * 驾驶室 GUI 事件：点击按钮下发指令，同时避免玩家拿走按钮。
 */
public final class CabListener implements Listener {

    private final CabGui gui;

    public CabListener(CabGui gui) {
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CabGui.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        DriveSession session = holder.session();
        if (session == null || !session.driver().equals(player.getUniqueId())) {
            return;
        }
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        gui.click(player, session, event.getSlot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CabGui.Holder) {
            event.setCancelled(true);
        }
    }

    /** 会话结束时关闭对应驾驶室。 */
    public void closeFor(UUID driver) {
        Player player = org.bukkit.Bukkit.getPlayer(driver);
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof CabGui.Holder) {
            player.closeInventory();
            player.sendMessage(Messages.color("&7驾驶室已关闭"));
        }
    }
}
