package net.forxmc.bridge;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class ForxBridge extends JavaPlugin implements CommandExecutor {

    private BridgeHttpServer httpServer;
    private final Set<String> processedOrders = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadPluginConfig();

        if (getCommand("forx") != null) {
            getCommand("forx").setExecutor(this);
        }

        getLogger().info("FORX Bridge has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        stopHttpServer();
        getLogger().info("FORX Bridge has been safely shut down.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        stopHttpServer();

        int port = getConfig().getInt("port", 8080);
        String apiKey = getConfig().getString("api-key", "CHANGE_ME_SECURE_KEY");
        java.util.List<String> allowedIps = getConfig().getStringList("allowed-ips");

        httpServer = new BridgeHttpServer(this, port, apiKey, allowedIps);
        httpServer.start();
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    public Set<String> getProcessedOrders() {
        return this.processedOrders;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("forxbridge.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
                return true;
            }
            reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "[FORX Bridge] Configuration and server endpoints reloaded successfully.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            if (!sender.hasPermission("forxbridge.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
                return true;
            }
            sender.sendMessage(ChatColor.AQUA + "=== FORX Bridge Status ===");
            sender.sendMessage(ChatColor.YELLOW + "API Port: " + ChatColor.WHITE + getConfig().getInt("port"));
            sender.sendMessage(ChatColor.YELLOW + "Players Online: " + ChatColor.WHITE + getServer().getOnlinePlayers().size());
            sender.sendMessage(ChatColor.YELLOW + "Server Version: " + ChatColor.WHITE + getServer().getMinecraftVersion());
            sender.sendMessage(ChatColor.GREEN + "Bridge HTTP status: Active and listening.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /forx [reload|status]");
        return true;
    }
}
