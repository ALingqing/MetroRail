package cn.aqcraft.metrorail.web;

import cn.aqcraft.metrorail.MetroRailPlugin;
import cn.aqcraft.metrorail.config.RailConfig;
import cn.aqcraft.metrorail.drive.DriveManager;
import cn.aqcraft.metrorail.drive.DriveSession;
import cn.aqcraft.metrorail.rail.BaliseRegistry;
import cn.aqcraft.metrorail.rail.EventLog;
import cn.aqcraft.metrorail.rail.RailEdge;
import cn.aqcraft.metrorail.rail.RailNode;
import cn.aqcraft.metrorail.rail.RailServices;
import cn.aqcraft.metrorail.rail.SwitchRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 调度网页（SkyPCC 风格）：实时轨道图数据、列车/设施详情、事件日志、道岔控制。
 *
 * <p>页面文件放在 {@code plugins/MetroRail/web/}：{@code index.html}、{@code style.css}、
 * {@code app.js}。首次启动时从 jar 释放，之后<strong>每次请求都从磁盘读取</strong>，
 * 改完保存刷新浏览器即可生效，无需重启插件。文件缺失时回退到 jar 内置版本。</p>
 *
 * <p>默认仅回环访问；配置 token 后可通过 {@code ?token=} 或
 * {@code X-MetroRail-Token} 头访问。接口：{@code /api/state}、
 * {@code /api/events}、{@code /api/switch}。</p>
 */
public final class DispatchWebServer {

    /** 内置默认页面文件（相对 web/ 目录）。 */
    private static final String[] BUNDLED = {"index.html", "style.css", "app.js"};

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    private final MetroRailPlugin plugin;
    private final RailConfig config;
    private final DriveManager manager;
    private final RailServices rail;
    private final File webDir;
    private HttpServer server;
    private int boundPort = -1;

    public DispatchWebServer(MetroRailPlugin plugin, RailConfig config, DriveManager manager, RailServices rail) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.rail = rail;
        this.webDir = new File(plugin.getDataFolder(), "web");
    }

    public void start() {
        if (!config.webEnabled() || server != null) {
            return;
        }
        extractDefaults();
        int port = config.webPort();
        for (int attempt = 0; attempt <= 10; attempt++) {
            try {
                HttpServer candidate = HttpServer.create(new InetSocketAddress(port + attempt), 0);
                candidate.createContext("/", this::handle);
                candidate.setExecutor(null);
                candidate.start();
                this.server = candidate;
                this.boundPort = port + attempt;
                plugin.getLogger().info("调度网页已启动： http://127.0.0.1:" + boundPort + "/"
                        + "（页面文件夹：" + webDir.getPath() + "）");
                return;
            } catch (IOException ex) {
                plugin.getLogger().warning("调度网页端口 " + (port + attempt) + " 不可用：" + ex.getMessage());
            }
        }
        plugin.getLogger().warning("调度网页启动失败：没有可用端口");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            boundPort = -1;
        }
    }

    public boolean running() {
        return server != null;
    }

    public int port() {
        return boundPort;
    }

    /** 网页文件夹（plugins/MetroRail/web）。 */
    public File webDir() {
        return webDir;
    }

    /** 把 jar 内置的默认页面释放到 web/（已存在的不覆盖，保留玩家自定义）。 */
    public void extractDefaults() {
        if (!webDir.isDirectory() && !webDir.mkdirs()) {
            plugin.getLogger().warning("无法创建网页文件夹 " + webDir.getPath());
            return;
        }
        for (String name : BUNDLED) {
            File target = new File(webDir, name);
            if (!target.isFile()) {
                plugin.saveResource("web/" + name, false);
            }
        }
    }

    // ------------------------------------------------------------ 分发

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/")) {
            if (!authorized(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            switch (path) {
                case "/api/state":
                    send(exchange, 200, CONTENT_TYPES.get("json"), stateJson());
                    return;
                case "/api/events":
                    send(exchange, 200, CONTENT_TYPES.get("json"), eventsJson());
                    return;
                case "/api/switch":
                    handleSwitch(exchange);
                    return;
                default:
                    sendJson(exchange, 404, "{\"error\":\"not-found\"}");
                    return;
            }
        }
        // 静态文件为只读页面资源：回环限制或已带 token 时放行
        if (config.webLoopbackOnly() && !isLoopback(exchange) && !hasToken(exchange)) {
            send(exchange, 403, "text/plain; charset=utf-8", "forbidden");
            return;
        }
        serveStatic(exchange, path);
    }

    private void handleSwitch(HttpExchange exchange) throws IOException {
        SwitchRegistry.Switch sw = rail.switches().get(query(exchange.getRequestURI()).get("name"));
        if (sw == null) {
            sendJson(exchange, 404, "{\"error\":\"switch-not-found\"}");
            return;
        }
        int changed = rail.switches().toggle(sw);
        rail.events().add("switch", "web 转换道岔 " + sw.id());
        sendJson(exchange, 200, "{\"switch\":\"" + escape(sw.id()) + "\",\"changed\":" + changed + "}");
    }

    // ------------------------------------------------------------ 静态文件

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String name = path.equals("/") || path.isEmpty() ? "index.html" : path.substring(1);
        if (name.contains("..")) {
            send(exchange, 400, "text/plain; charset=utf-8", "bad-path");
            return;
        }
        byte[] bytes = readFile(new File(webDir, name));
        if (bytes == null) {
            bytes = readResource("web/" + name);
        }
        if (bytes == null) {
            send(exchange, 404, "text/plain; charset=utf-8", "404 not found: " + name);
            return;
        }
        send(exchange, 200, contentType(name), bytes);
    }

    private static byte[] readFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            return null;
        }
    }

    private byte[] readResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }

    private static String contentType(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    // ------------------------------------------------------------ 鉴权

    private boolean authorized(HttpExchange exchange) {
        String token = config.webToken();
        boolean tokenRequired = token != null && !token.isBlank();
        if (!tokenRequired) {
            return isLoopback(exchange) || !config.webLoopbackOnly();
        }
        return token.equals(providedToken(exchange));
    }

    private boolean hasToken(HttpExchange exchange) {
        String token = config.webToken();
        return token != null && !token.isBlank() && token.equals(providedToken(exchange));
    }

    private static String providedToken(HttpExchange exchange) {
        String provided = exchange.getRequestHeaders().getFirst("X-MetroRail-Token");
        return provided != null ? provided : query(exchange.getRequestURI()).get("token");
    }

    private static boolean isLoopback(HttpExchange exchange) {
        String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
        return remote.equals("127.0.0.1") || remote.equals("::1") || remote.equals("0:0:0:0:0:0:0:1")
                || remote.equals("localhost");
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int index = pair.indexOf('=');
            if (index < 0) {
                map.put(decode(pair), "");
            } else {
                map.put(decode(pair.substring(0, index)), decode(pair.substring(index + 1)));
            }
        }
        return map;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, CONTENT_TYPES.get("json"), body);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] bytes)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------------ JSON 构造

    private String eventsJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (EventLog.Entry entry : rail.events().recent(100)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"time\":\"").append(escape(entry.time()))
                    .append("\",\"category\":\"").append(escape(entry.category()))
                    .append("\",\"message\":\"").append(escape(entry.message())).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private String stateJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"graph\":{\"world\":\"").append(escape(rail.graph().worldName()))
                .append("\",\"nodes\":").append(rail.graph().nodeCount())
                .append(",\"edges\":").append(rail.graph().edgeCount()).append("},");

        sb.append("\"track\":[");
        boolean first = true;
        for (RailEdge edge : rail.graph().edges()) {
            RailNode from = rail.graph().node(edge.from());
            RailNode to = rail.graph().node(edge.to());
            if (from == null || to == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"from\":[").append(from.x()).append(',').append(from.y()).append(',').append(from.z())
                    .append("],\"to\":[").append(to.x()).append(',').append(to.y()).append(',').append(to.z())
                    .append("],\"occupied\":").append(!rail.ledger().isFree(edge.id())).append('}');
        }
        sb.append("],");

        sb.append("\"trains\":[");
        first = true;
        for (DriveSession session : manager.sessions()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"driver\":\"").append(escape(session.driverName()))
                    .append("\",\"train\":\"").append(escape(session.train()))
                    .append("\",\"vehicle\":\"").append(escape(session.vehicleId()))
                    .append("\",\"speed\":").append(round(session.speed()))
                    .append(",\"speedKmh\":").append(round(session.speedKmh()))
                    .append(",\"notch\":\"").append(session.notch().id())
                    .append("\",\"reverser\":\"").append(session.reverser().id())
                    .append("\",\"mode\":\"").append(session.mode().id())
                    .append("\",\"state\":\"").append(session.state() == null ? "-" : session.state().id())
                    .append("\",\"eoa\":").append(round(session.eoaDistance()))
                    .append(",\"intact\":").append(session.integrityOk()).append('}');
        }
        sb.append("],");

        sb.append("\"switches\":[");
        first = true;
        for (SwitchRegistry.Switch sw : rail.switches().all()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"id\":\"").append(escape(sw.id()))
                    .append("\",\"positions\":").append(sw.size())
                    .append(",\"location\":\"").append(escape(String.valueOf(sw.location()))).append("\"}");
        }
        sb.append("],");

        sb.append("\"balises\":[");
        first = true;
        for (BaliseRegistry.Balise balise : rail.balises().all()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"tag\":\"").append(escape(balise.tag()))
                    .append("\",\"name\":\"").append(escape(balise.name()))
                    .append("\",\"id\":\"").append(escape(balise.id()));
            org.bukkit.Location loc = cn.aqcraft.metrorail.rail.SwitchRegistry.locationOf(balise.id());
            if (loc != null) {
                sb.append("\",\"pos\":[").append(loc.getBlockX()).append(',')
                        .append(loc.getBlockY()).append(',').append(loc.getBlockZ()).append(']');
            }
            sb.append('}');
        }
        sb.append(']');

        sb.append('}');
        return sb.toString();
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return -1.0;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
