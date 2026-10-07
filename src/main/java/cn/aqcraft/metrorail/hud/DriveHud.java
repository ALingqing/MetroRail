package cn.aqcraft.metrorail.hud;

import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 驾驶 HMI：计分板 + BossBar。
 */
public final class DriveHud {

    private static final String OBJECTIVE = "metrorail";

    private final RailConfig config;
    private final Messages messages;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public DriveHud(RailConfig config, Messages messages) {
        this.config = config;
        this.messages = messages;
    }

    public void update(DriveManager manager) {
        for (DriveSession session : manager.sessions()) {
            Player player = Bukkit.getPlayer(session.driver());
            if (player == null) {
                continue;
            }
            if (config.scoreboard()) {
                updateBoard(player, session);
            } else {
                clearBoard(player);
            }
            if (config.bossbar()) {
                updateBar(player, session);
            } else {
                clearBar(player);
            }
        }
    }

    private void updateBoard(Player player, DriveSession session) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = scoreboards.get(player.getUniqueId());
        if (board == null) {
            board = manager.getNewScoreboard();
            scoreboards.put(player.getUniqueId(), board);
        }
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, "dummy",
                    Messages.color(messages.raw("hud-title")));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }
        int line = 15;
        set(objective, messages.get("hud-vehicle", session.vehicleId()), line--);
        set(objective, messages.get("hud-notch", session.notch().id()), line--);
        set(objective, messages.get("hud-reverser", session.reverser().id()), line--);
        set(objective, messages.get("hud-speed", speedText(session)), line--);
        set(objective, messages.get("hud-mode", session.mode().id(),
                session.state() == null ? "-" : session.state().id()), line--);
        set(objective, messages.get("hud-ma", maText(session)), line--);
        set(objective, messages.get("hud-integrity",
                session.integrityOk() ? messages.raw("hud-ok") : messages.raw("hud-bad")), line--);
        set(objective, messages.get("hud-meter", (long) session.mileage()), line--);
        if (session.consistId() != null) {
            set(objective, messages.get("hud-consist", session.consistId()), line);
        }
        player.setScoreboard(board);
    }

    /** 按单位偏好格式化速度。 */
    private String speedText(DriveSession session) {
        String unit = session.unit();
        double value;
        String suffix;
        switch (unit == null ? "kph" : unit) {
            case "mph":
                value = session.speed() * 72.0 * 0.621371;
                suffix = "mph";
                break;
            case "block":
            case "block/tick":
                value = session.speed();
                suffix = "block/tick";
                break;
            default:
                value = session.speedKmh();
                suffix = "km/h";
                break;
        }
        return String.format(Locale.ROOT, "%.2f %s", value, suffix);
    }

    private String maText(DriveSession session) {
        double eoa = session.eoaDistance();
        if (eoa < 0.0) {
            return messages.raw("hud-ma-none");
        }
        String reason = session.ma() == null ? "-" : session.ma().reason();
        return String.format(Locale.ROOT, "%.0fm (%s)", eoa, reason);
    }

    private static void set(Objective objective, String text, int score) {
        objective.getScore(unique(text, score)).setScore(score);
    }

    /** 计分板条目唯一化（同分同行会互相覆盖）。 */
    private static String unique(String text, int score) {
        return text + " ".repeat(Math.max(0, 9 - score));
    }

    private void updateBar(Player player, DriveSession session) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar(barTitle(session), BarColor.BLUE, BarStyle.SEGMENTED_10);
            bar.addPlayer(player);
            bars.put(player.getUniqueId(), bar);
        }
        bar.setTitle(barTitle(session));
        double max = 0.60;
        bar.setProgress(Math.max(0.0, Math.min(1.0, session.speed() / max)));
    }

    private String barTitle(DriveSession session) {
        return messages.get("hud-bar-title",
                session.reverser().id(), fmt(session.speedKmh()), session.notch().id());
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public void clearBoard(Player player) {
        Scoreboard board = scoreboards.remove(player.getUniqueId());
        if (board != null && player.getScoreboard().equals(board)) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
    }

    public void clearBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void clear(Player player) {
        clearBoard(player);
        clearBar(player);
    }

    public void clearAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
        for (UUID id : new java.util.ArrayList<>(scoreboards.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clearBoard(player);
            } else {
                scoreboards.remove(id);
            }
        }
    }
}
