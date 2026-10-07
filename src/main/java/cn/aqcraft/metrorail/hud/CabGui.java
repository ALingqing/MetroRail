package cn.aqcraft.metrorail.hud;

import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.drive.Notch;
import cn.aqcraft.metrorail.drive.Reverser;
import cn.aqcraft.metrorail.i18n.Messages;
import cn.aqcraft.metrorail.rail.ControlMode;
import cn.aqcraft.metrorail.rail.TrainState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 驾驶室 GUI：级位 / 换向 / 鸣笛 / 列控模式的按钮台。
 */
public final class CabGui {

    /** 标记该 Inventory 属于驾驶室，便于事件处理。 */
    public static final class Holder implements InventoryHolder {
        private final DriveSession session;
        private Inventory inventory;

        Holder(DriveSession session) {
            this.session = session;
        }

        public DriveSession session() {
            return session;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** 按钮所在槽位。 */
    public static final int SLOT_P4 = 0;
    public static final int SLOT_P3 = 1;
    public static final int SLOT_P2 = 2;
    public static final int SLOT_P1 = 3;
    public static final int SLOT_N = 4;
    public static final int SLOT_B1 = 5;
    public static final int SLOT_B3 = 6;
    public static final int SLOT_B7 = 7;
    public static final int SLOT_EB = 8;
    public static final int SLOT_REV_FWD = 10;
    public static final int SLOT_REV_NEUTRAL = 11;
    public static final int SLOT_REV_BACK = 12;
    public static final int SLOT_HORN = 14;
    public static final int SLOT_BELL = 15;
    public static final int SLOT_MODE = 16;

    private final RailConfig config;
    private final Messages messages;
    private final DriveManager manager;

    public CabGui(RailConfig config, Messages messages, DriveManager manager) {
        this.config = config;
        this.messages = messages;
        this.manager = manager;
    }

    /** 打开驾驶室。 */
    public void open(Player player, DriveSession session) {
        Holder holder = new Holder(session);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Messages.color(messages.raw("cab-title")));
        holder.inventory = inventory;

        inventory.setItem(SLOT_P4, notchItem(Material.RED_CONCRETE, Notch.P4));
        inventory.setItem(SLOT_P3, notchItem(Material.ORANGE_CONCRETE, Notch.P3));
        inventory.setItem(SLOT_P2, notchItem(Material.YELLOW_CONCRETE, Notch.P2));
        inventory.setItem(SLOT_P1, notchItem(Material.LIME_CONCRETE, Notch.P1));
        inventory.setItem(SLOT_N, notchItem(Material.GRAY_CONCRETE, Notch.N));
        inventory.setItem(SLOT_B1, notchItem(Material.LIGHT_BLUE_CONCRETE, Notch.B1));
        inventory.setItem(SLOT_B3, notchItem(Material.BLUE_CONCRETE, Notch.B3));
        inventory.setItem(SLOT_B7, notchItem(Material.PURPLE_CONCRETE, Notch.B7));
        inventory.setItem(SLOT_EB, notchItem(Material.REDSTONE_BLOCK, Notch.EB));

        inventory.setItem(SLOT_REV_FWD, simple(Material.ARROW,
                messages.raw("cab-rev-forward"), null));
        inventory.setItem(SLOT_REV_NEUTRAL, simple(Material.BARRIER,
                messages.raw("cab-rev-neutral"), null));
        inventory.setItem(SLOT_REV_BACK, simple(Material.SPECTRAL_ARROW,
                messages.raw("cab-rev-backward"), null));

        inventory.setItem(SLOT_HORN, simple(Material.FIREWORK_ROCKET,
                messages.raw("cab-horn"), null));
        inventory.setItem(SLOT_BELL, simple(Material.BELL,
                messages.raw("cab-bell"), null));
        inventory.setItem(SLOT_MODE, simple(Material.COMPARATOR,
                messages.get("cab-mode", session.mode().id()),
                List.of(messages.raw("cab-mode-hint"))));

        player.openInventory(inventory);
    }

    private ItemStack notchItem(Material material, Notch notch) {
        return simple(material, messages.get("cab-notch",
                notch.id(), String.format(Locale.ROOT, "%.0f%%", notch.powerRatio() * 100.0)), null);
    }

    private static ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.color(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    colored.add(Messages.color(line));
                }
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 处理点击。返回 true 表示已消费（取消默认行为）。
     */
    public boolean click(Player player, DriveSession session, int slot) {
        Notch notch = notchAt(slot);
        if (notch != null) {
            manager.setNotch(player, notch);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_LEVER_CLICK, 0.6f, 1.2f);
            player.closeInventory();
            return true;
        }
        switch (slot) {
            case SLOT_REV_FWD:
                controlledReverser(player, session, Reverser.FORWARD);
                return true;
            case SLOT_REV_NEUTRAL:
                controlledReverser(player, session, Reverser.NEUTRAL);
                return true;
            case SLOT_REV_BACK:
                controlledReverser(player, session, Reverser.BACKWARD);
                return true;
            case SLOT_HORN:
                horn(player);
                return true;
            case SLOT_BELL:
                bell(player);
                return true;
            case SLOT_MODE:
                cycleMode(player, session);
                open(player, session);
                return true;
            default:
                return true;
        }
    }

    private void controlledReverser(Player player, DriveSession session, Reverser reverser) {
        if (!manager.setReverser(player, reverser)) {
            player.sendMessage(messages.prefixed("reverser-too-fast",
                    String.format(Locale.ROOT, "%.2f", session.speedKmh())));
        } else {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_LEVER_CLICK, 0.6f, 0.8f);
        }
    }

    private void cycleMode(Player player, DriveSession session) {
        ControlMode[] modes = {ControlMode.SHADOW, ControlMode.BYPASS, ControlMode.ISOLATE, ControlMode.ENFORCE};
        int index = session.mode().ordinal();
        ControlMode next = modes[(index + 1) % modes.length];
        manager.setMode(session, next);
        player.sendMessage(messages.prefixed("mode-set", next.id()));
    }

    public void horn(Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_MINECART_RIDING, 1.4f, 0.6f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.5f);
    }

    public void bell(Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.6f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.9f);
    }

    private static Notch notchAt(int slot) {
        switch (slot) {
            case SLOT_P4:
                return Notch.P4;
            case SLOT_P3:
                return Notch.P3;
            case SLOT_P2:
                return Notch.P2;
            case SLOT_P1:
                return Notch.P1;
            case SLOT_N:
                return Notch.N;
            case SLOT_B1:
                return Notch.B1;
            case SLOT_B3:
                return Notch.B3;
            case SLOT_B7:
                return Notch.B7;
            case SLOT_EB:
                return Notch.EB;
            default:
                return null;
        }
    }

    /** 供状态机使用：设置状态的友好名称。 */
    public String stateName(TrainState state) {
        return state == null ? "-" : state.id();
    }
}
