package net.forxmc.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BridgeHttpServer {

    private final ForxBridge plugin;
    private final int port;
    private final String apiKey;
    private final List<String> allowedIps;
    private HttpServer server;

    // Simple 4-argument constructor match
    public BridgeHttpServer(ForxBridge plugin, int port, String apiKey, List<String> allowedIps) {
        this.plugin = plugin;
        this.port = port;
        this.apiKey = apiKey;
        this.allowedIps = allowedIps;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/execute", new ExecuteHandler());
            server.createContext("/status", new StatusHandler());
            server.setExecutor(null); 
            server.start();
            plugin.getLogger().info("Bridge HTTP Server started cleanly on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not start Bridge HTTP Server on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Bridge HTTP Server stopped successfully.");
        }
    }

    private boolean isIpAuthorized(String ipAddress) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true; 
        }
        return allowedIps.contains(ipAddress);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(([^\"]|\\\\\")*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private class ExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!isIpAuthorized(remoteIp)) {
                sendResponse(exchange, 403, "{\"error\":\"Unauthorized IP address\"}");
                return;
            }

            String body;
            try (InputStream is = exchange.getRequestBody()) {
                body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            String requestApiKey = getJsonValue(body, "apiKey");
            String orderId = getJsonValue(body, "orderId");
            String command = getJsonValue(body, "command");

            if (apiKey == null || apiKey.isEmpty() || !apiKey.equals(requestApiKey)) {
                sendResponse(exchange, 401, "{\"error\":\"Invalid API key authentication\"}");
                return;
            }

            if (orderId.isEmpty() || command.isEmpty()) {
                sendResponse(exchange, 400, "{\"error\":\"Missing orderId or command payloads\"}");
                return;
            }

            // Using explicit methods to avoid symbol issues
            if (plugin.isOrderProcessed(orderId)) {
                sendResponse(exchange, 409, "{\"error\":\"Duplicate order detection. Already executed.\"}");
                return;
            }

            plugin.markOrderProcessed(orderId);

            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Executing custom store command for Order #" + orderId + ": " + command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });

            sendResponse(exchange, 200, "{\"success\":true}");
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            String remoteIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!isIpAuthorized(remoteIp)) {
                sendResponse(exchange, 403, "{\"error\":\"Unauthorized IP address\"}");
                return;
            }

            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            double currentTps = 20.0;
            String version = Bukkit.getMinecraftVersion();

            String response = String.format(
                    "{\"online\":true,\"players\":%d,\"tps\":%.1f,\"version\":\"%s\"}",
                    onlinePlayers, currentTps, version
            );

            sendResponse(exchange, 200, response);
        }
    }
}
