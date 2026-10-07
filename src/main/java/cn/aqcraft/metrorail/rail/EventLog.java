package cn.aqcraft.metrorail.rail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 事件日志：驾驶权、道岔、EB、MA、模式切换等事件的环形缓冲。
 */
public final class EventLog {

    /** 一条事件。 */
    public static final class Entry {
        private final long millis;
        private final String category;
        private final String message;

        Entry(long millis, String category, String message) {
            this.millis = millis;
            this.category = category;
            this.message = message;
        }

        public long millis() {
            return millis;
        }

        public String category() {
            return category;
        }

        public String message() {
            return message;
        }

        public String time() {
            return DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(millis));
        }
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();

    public EventLog(int capacity) {
        this.capacity = Math.max(16, capacity);
    }

    public void add(String category, String message) {
        entries.addFirst(new Entry(System.currentTimeMillis(), category, message));
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    /** 最近的 n 条（新→旧）。 */
    public List<Entry> recent(int n) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : entries) {
            if (list.size() >= n) {
                break;
            }
            list.add(entry);
        }
        return list;
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
