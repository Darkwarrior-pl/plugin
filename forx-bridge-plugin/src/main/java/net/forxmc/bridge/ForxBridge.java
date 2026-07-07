package net.forxmc.bridge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public final class ForxBridge extends JavaPlugin implements CommandExecutor {

    private BridgeHttpServer httpServer;
    private final Set<String> processedOrders = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onEnable() {
        // Save default config if not present
        saveDefaultConfig();
        
        // Start HTTP Server
        startHttpServer();

        // Register Command
        if (getCommand("forxbridge") != null) {
            getCommand("forxbridge").setExecutor(this);
        } else {
            getLogger().info("No system command registered via plugin.yml. Using internal command registration.");
        }

        getLogger().info("=========================================");
        getLogger().info("  FORX Bridge has been successfully enabled! ");
        getLogger().info("  Listening on port: " + getConfig().getInt("port", 8080));
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        stopHttpServer();
        getLogger().info("FORX Bridge has been successfully disabled.");
    }

    public void startHttpServer() {
        stopHttpServer();
        
        int port = getConfig().getInt("port", 8080);
        String apiKey = getConfig().getString("api-key", "CHANGE_ME_SECURE_KEY");
        boolean debug = getConfig().getBoolean("debug", false);
        java.util.List<String> allowedIps = getConfig().getStringList("allowed-ips");

        if ("CHANGE_ME_SECURE_KEY".equals(apiKey) || apiKey.trim().isEmpty()) {
            getLogger().warning("=========================================================================");
            getLogger().warning("  WARNING: YOU ARE USING THE DEFAULT OR AN EMPTY API KEY!");
            getLogger().warning("  Please change 'api-key' in config.yml to secure your server execution!");
            getLogger().warning("=========================================================================");
        }

        try {
            httpServer = new BridgeHttpServer(this, port, apiKey, allowedIps, debug);
            httpServer.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize secure FORX Bridge HTTP Server on port " + port, e);
        }
    }

    public void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public boolean isOrderProcessed(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) return false;
        return processedOrders.contains(orderId.trim());
    }

    public void markOrderProcessed(String orderId) {
        if (orderId != null && !orderId.trim().isEmpty()) {
            processedOrders.add(orderId.trim());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("forxbridge.admin")) {
                sender.sendMessage("§cYou do not have permission to execute this command!");
                return true;
            }
            
            reloadConfig();
            startHttpServer();
            sender.sendMessage("§a[FORX Bridge] Configuration reloaded successfully and Server restarted!");
            return true;
        }

        sender.sendMessage("§e=== FORX Bridge Commands ===");
        sender.sendMessage("§6/forxbridge reload §7- Reloads the config file and restarts the server.");
        return true;
    }
}
