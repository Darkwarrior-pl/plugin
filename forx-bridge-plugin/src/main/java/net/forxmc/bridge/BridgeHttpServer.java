package net.forxmc.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BridgeHttpServer {

    private final ForxBridge plugin;
    private final int port;
    private final String apiKey;
    private final List<String> allowedIps;
    private final boolean debug;
    private HttpServer server;

    public BridgeHttpServer(ForxBridge plugin, int port, String apiKey, List<String> allowedIps, boolean debug) {
        this.plugin = plugin;
        this.port = port;
        this.apiKey = apiKey;
        this.allowedIps = allowedIps;
        this.debug = debug;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        server.createContext("/execute", new ExecuteHandler());
        server.createContext("/status", new StatusHandler());
        
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        plugin.getLogger().info("Bridge HTTP server started listening on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            plugin.getLogger().info("Bridge HTTP server stopped.");
        }
    }

    private void logDebug(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    private boolean isIpAllowed(String ipAddress) {
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }
        return allowedIps.contains(ipAddress);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Custom lightweight JSON field extractor to prevent library dependencies
    private static String getJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"[\\s]*:[\\s]*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private class ExecuteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Only POST is allowed\"}");
                return;
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            logDebug("Received execute request from: " + clientIp);

            if (!isIpAllowed(clientIp)) {
                plugin.getLogger().warning("Blocked command execution request from unauthorized IP: " + clientIp);
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"IP address not whitelisted\"}");
                return;
            }

            // Read Body
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            String jsonPayload = body.toString();
            logDebug("Payload: " + jsonPayload);

            String requestApiKey = getJsonField(jsonPayload, "apiKey");
            String orderId = getJsonField(jsonPayload, "orderId");
            String player = getJsonField(jsonPayload, "player");
            String command = getJsonField(jsonPayload, "command");

            // Validate API Key
            if (requestApiKey == null || !requestApiKey.equals(apiKey)) {
                plugin.getLogger().warning("Unauthorized execute request (Invalid API Key) from: " + clientIp);
                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid API Key\"}");
                return;
            }

            // Validate input fields
            if (orderId == null || orderId.trim().isEmpty() || command == null || command.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Missing orderId or command parameter\"}");
                return;
            }

            // Duplicate prevention check
            if (plugin.isOrderProcessed(orderId)) {
                plugin.getLogger().info("Prevented duplicate execution for Order ID: " + orderId);
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Order already delivered\"}");
                return;
            }

            plugin.getLogger().info("[FORX Bridge] Order " + orderId + " - Queued execution for player: " + player + " | Command: " + command);

            // Execute command on main server thread synchronously to ensure thread safety
            CompletableFuture<Boolean> executionFuture = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    executionFuture.complete(success);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Exception running command: " + command, e);
                    executionFuture.complete(false);
                }
            });

            try {
                boolean result = executionFuture.get();
                if (result) {
                    plugin.markOrderProcessed(orderId);
                    plugin.getLogger().info("[FORX Bridge] Order " + orderId + " - Executed successfully!");
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Executed command successfully\"}");
                } else {
                    plugin.getLogger().warning("[FORX Bridge] Order " + orderId + " - Execution failed on server console!");
                    sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Command execution failed on console\"}");
                }
            } catch (InterruptedException | ExecutionException e) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Server internal execution error: " + e.getMessage() + "\"}");
            }
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Only GET is allowed\"}");
                return;
            }

            String response = String.format(
                "{\"success\":true,\"plugin\":\"FORX Bridge\",\"version\":\"1.0.0\",\"serverVersion\":\"%s\",\"onlinePlayers\":%d,\"status\":\"ONLINE\"}",
                Bukkit.getVersion().replace("\"", "\\\""),
                Bukkit.getOnlinePlayers().size()
            );
            sendResponse(exchange, 200, response);
        }
    }
}
