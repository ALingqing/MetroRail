package cn.aqcraft.metrorail.hud;

import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.drive.Notch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * 快捷栏驾驶：司机切换快捷栏格即切换级位（1-9 → B7..P4）。
 *
 * <p>只有 config {@code drive.hotbar-slot} 非 off 时生效；该值同时表示
 * 「哪个格子作为控制台」，切换到此格时才回应数字键。</p>
 */
public final class HotbarController implements Listener {

    /** 九格 → 级位映射。 */
    private static final Notch[] MAP = {
            Notch.B7, Notch.B5, Notch.B3, Notch.B1, Notch.N,
            Notch.P1, Notch.P2, Notch.P3, Notch.P4
    };

    private final RailConfig config;
    private final DriveManager manager;

    public HotbarController(RailConfig config, DriveManager manager) {
        this.config = config;
        this.manager = manager;
    }

    private boolean enabled() {
        String value = config.hotbarSlot();
        return value != null && !value.isBlank() && !"off".equalsIgnoreCase(value);
    }

    private int trigger() {
        try {
            int slot = Integer.parseInt(config.hotbarSlot().trim());
            return Math.max(1, Math.min(9, slot)) - 1;
        } catch (NumberFormatException ex) {
            return 8;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (!enabled()) {
            return;
        }
        Player player = event.getPlayer();
        DriveSession session = manager.byDriver(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getNewSlot() != trigger()) {
            return;
        }
        int index = event.getPreviousSlot();
        if (index < 0 || index >= MAP.length) {
            return;
        }
        Notch notch = MAP[index];
        manager.setNotch(player, notch);
        player.sendMessage(cn.aqcraft.metrorail.i18n.Messages.color(
                "&b&l[MetroRail] &7级位 → &f" + notch.id()));
    }
}
