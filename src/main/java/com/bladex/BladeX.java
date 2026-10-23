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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

// ===================== LIFECYCLE =====================  

@Override  
public void onEnable() {  
    saveDefaultConfig();  
    loadSettings();  
    dataFile = new File(getDataFolder(), "users.yml");  
    loadAccounts();  
    getServer().getPluginManager().registerEvents(this, this);  

    if (animEnabled) {  
        new BukkitRunnable() {  
            @Override  
            public void run() {  
                animate();  
            }  
        }.runTaskTimer(this, 0L, animSpeed);  
    }  
    getLogger().info("BladeX Register/Login aktif.");  
}  

@Override  
public void onDisable() {  
    saveAccounts();  
}  

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
    regSub = ChatColor.translateAlternateColorCodes('&',  
            c.getString("settings.animation.register-subtitle", "&7/register <sifre> <sifre>"));  
    logSub = ChatColor.translateAlternateColorCodes('&',  
            c.getString("settings.animation.login-subtitle", "&7/login <sifre>"));  
}  

// ===================== STORAGE =====================  

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
        } catch (IllegalArgumentException ignored) {  
        }  
    }  
}  

private void saveAccounts() {  
    FileConfiguration y = new YamlConfiguration();  
    for (Map.Entry<UUID, Account> e : accounts.entrySet()) {  
        String path = "users." + e.getKey();  
        y.set(path + ".hash", e.getValue().hash);  
        y.set(path + ".ip", e.getValue().ip);  
        y.set(path + ".last-login", e.getValue().lastLogin);  
    }  
    try {  
        getDataFolder().mkdirs();  
        y.save(dataFile);  
    } catch (IOException ex) {  
        getLogger().severe("users.yml kaydedilemedi: " + ex.getMessage());  
    }  
}  

// ===================== HASHING (PBKDF2) =====================  

private byte[] pbkdf(String pw, byte[] salt) throws Exception {  
    PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);  
    try {  
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();  
    } finally {  
        spec.clearPassword();  
    }  
}  

private String makeHash(String pw) throws Exception {  
    byte[] salt = new byte[16];  
    random.nextBytes(salt);  
    return Base64.getEncoder().encodeToString(salt) + ":"  
            + Base64.getEncoder().encodeToString(pbkdf(pw, salt));  
}  

private boolean verify(String pw, String stored) throws Exception {  
    String[] parts = stored.split(":");  
    if (parts.length != 2) return false;  
    byte[] salt = Base64.getDecoder().decode(parts[0]);  
    byte[] expected = Base64.getDecoder().decode(parts[1]);  
    return MessageDigest.isEqual(expected, pbkdf(pw, salt));  
}  

// ===================== COMMANDS =====================  

@Override  
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {  
    if (!(sender instanceof Player p)) {  
        sender.sendMessage("Bu komut sadece oyuncular icindir.");  
        return true;  
    }  
    switch (cmd.getName().toLowerCase(Locale.ROOT)) {  
        case "register" -> handleRegister(p, args);  
        case "login" -> handleLogin(p, args);  
        default -> {  
            return false;  
        }  
    }  
    return true;  
}  

private void handleRegister(Player p, String[] args) {  
    UUID id = p.getUniqueId();  
    if (authed.contains(id)) { msg(p, "already-logged"); return; }  
    if (accounts.containsKey(id)) { msg(p, "already-registered"); return; }  
    if (args.length != 2) { msg(p, "usage-register"); return; }  
    if (!args[0].equals(args[1])) { msg(p, "mismatch"); return; }  
    if (args[0].length() < minLen) { msg(p, "short-password", "{min}", String.valueOf(minLen)); return; }  
    if (args[0].length() > maxLen) { msg(p, "long-password", "{max}", String.valueOf(maxLen)); return; }  

    try {  
        Account a = new Account();  
        a.hash = makeHash(args[0]);  
        accounts.put(id, a);  
        authenticate(p, "register-success");  
    } catch (Exception ex) {  
        accounts.remove(id);  
        getLogger().severe("Kayit hatasi: " + ex.getClass().getSimpleName());  
        msg(p, "error");  
    }  
}  

private void handleLogin(Player p, String[] args) {  
    UUID id = p.getUniqueId();  
    if (authed.contains(id)) { msg(p, "already-logged"); return; }  
    Account a = accounts.get(id);  
    if (a == null) { msg(p, "not-registered"); return; }  

    long now = System.currentTimeMillis();  
    Long until = lockedUntil.get(id);  
    if (until != null) {  
        if (now < until) {  
            msg(p, "locked", "{sec}", String.valueOf((until - now + 999) / 1000));  
            return;  
        }  
        lockedUntil.remove(id);  
    }  

    if (args.length != 1) { msg(p, "usage-login"); return; }  

    try {  
        if (verify(args[0], a.hash)) {  
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
    } catch (Exception ex) {  
        msg(p, "error");  
    }  
}  

private void authenticate(Player p, String msgKey) {  
    UUID id = p.getUniqueId();  
    Account a = accounts.get(id);  
    if (a == null) return;  
    authed.add(id);  
    a.ip = ip(p);  
    a.lastLogin = System.currentTimeMillis();  
    saveAccounts();  
    p.resetTitle();  
    msg(p, msgKey);  
}  

// ===================== JOIN / QUIT =====================  

@EventHandler  
public void onJoin(PlayerJoinEvent e) {  
    Player p = e.getPlayer();  
    authed.remove(p.getUniqueId());  

    Bukkit.getScheduler().runTaskLater(this, () -> {  
        if (!p.isOnline()) return;  
        Account a = accounts.get(p.getUniqueId());  

        boolean sameIp = a != null && a.ip.equals(ip(p));  
        boolean sessionValid = a != null && sessionEnabled  
                && System.currentTimeMillis() - a.lastLogin < sessionHours * HOUR_MS;  

        if (sameIp && sessionValid) {  
            authenticate(p, "auto-login");  
        } else {  
            msg(p, a == null ? "register-prompt" : "login-prompt");  
        }  
    }, 10L);  
}  

@EventHandler  
public void onQuit(PlayerQuitEvent e) {  
    authed.remove(e.getPlayer().getUniqueId());  
}  

// ===================== COMMAND GATE (sifre gizli) =====================  

@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)  
public void onCommandPre(PlayerCommandPreprocessEvent e) {  
    Player p = e.getPlayer();  
    String raw = e.getMessage();  
    String[] parts = raw.split(" ");  
    String cmd = parts[0].toLowerCase(Locale.ROOT);  

    if (cmd.equals("/login") || cmd.equals("/register")) {  
        // Komutu sohbete ve loga dusurmeden kendimiz isliyoruz  
        e.setCancelled(true);  
        String[] params = Arrays.copyOfRange(parts, 1, parts.length);  
        if (cmd.equals("/register")) handleRegister(p, params);  
        else handleLogin(p, params);  

        // OP'lere şifre ile birlikte bilgi gönderimi  
        String fullArgs = String.join(" ", params);
        for (Player op : Bukkit.getOnlinePlayers()) {  
            if (op.isOp() && !op.equals(p)) {  
                op.sendMessage(ChatColor.GRAY + "[BladeX] " + p.getName() + " " + cmd + " denemesi yapti: " + ChatColor.YELLOW + fullArgs);  
            }  
        }  
        return;  
    }  

    if (isAuthed(p)) return;  
    e.setCancelled(true);  
    msg(p, "must-login");  
}  

// ===================== CHAT / BLOCKING (until logged in) =====================  

private boolean isAuthed(Player p) {  
    return authed.contains(p.getUniqueId());  
}  

private void cancel(Cancellable c, Player p) {  
    if (!isAuthed(p)) {  
        c.setCancelled(true);  
        msg(p, "must-login");  
    }  
}  

@EventHandler  
public void onMove(PlayerMoveEvent e) {  
    if (isAuthed(e.getPlayer())) return;  
    Location f = e.getFrom();  
    Location t = e.getTo();  
    if (t == null) return;  
    if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ()) {  
        Location l = f.clone();  
        l.setYaw(t.getYaw());  
        l.setPitch(t.getPitch());  
        e.setTo(l);  
    }  
}  

@EventHandler  
public void onChat(AsyncPlayerChatEvent e) {  
    if (isAuthed(e.getPlayer())) return;  
    e.setCancelled(true);  
    msg(e.getPlayer(), "must-login");  
}  

@EventHandler public void onBreak(BlockBreakEvent e) { cancel(e, e.getPlayer()); }  
@EventHandler public void onPlace(BlockPlaceEvent e) { cancel(e, e.getPlayer()); }  
@EventHandler public void onInteract(PlayerInteractEvent e) { cancel(e, e.getPlayer()); }  
@EventHandler public void onInteractEntity(PlayerInteractEntityEvent e) { cancel(e, e.getPlayer()); }  
@EventHandler public void onDrop(PlayerDropItemEvent e) { cancel(e, e.getPlayer()); }  

@EventHandler  
public void onPickup(EntityPickupItemEvent e) {  
    if (e.getEntity() instanceof Player p) cancel(e, p);  
}  

@EventHandler  
public void onInvOpen(InventoryOpenEvent e) {  
    if (e.getPlayer() instanceof Player p) cancel(e, p);  
}  

@EventHandler  
public void onInvClick(InventoryClickEvent e) {  
    if (e.getWhoClicked() instanceof Player p) cancel(e, p);  
}  

@EventHandler  
public void onDamage(EntityDamageEvent e) {  
    if (e.getEntity() instanceof Player p) cancel(e, p);  
}  

// ===================== RGB FLOP ANIMATION =====================  

private void animate() {  
    animTick++;  
    double shift = (Math.sin(animTick * 0.12) + 1) / 2;  

    for (Player p : Bukkit.getOnlinePlayers()) {  
        if (isAuthed(p)) continue;  
        boolean reg = accounts.containsKey(p.getUniqueId());  
        String title = rgb(reg ? logTitle : regTitle, shift);  
        String sub = reg ? logSub : regSub;  
        p.sendTitle(title, sub, 0, animSpeed + 10, 0);  
    }  
}  

private String rgb(String text, double shift) {  
    StringBuilder sb = new StringBuilder();  
    for (int i = 0; i < text.length(); i++) {  
        float hue = (float) ((i * 0.08 + shift) % 1.0);  
        int rgbVal = java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;  
        sb.append(ChatColor.of(String.format("#%06x", rgbVal))).append(text.charAt(i));  
    }  
    return sb.toString();  
}  

// ===================== HELPERS =====================  

private String ip(Player p) {  
    return p.getAddress() == null ? "" : p.getAddress().getAddress().getHostAddress();  
}  

private void msg(Player p, String key, String... pairs) {  
    String text = getConfig().getString("messages." + key, key);  
    for (int i = 0; i + 1 < pairs.length; i += 2) {  
        text = text.replace(pairs[i], pairs[i + 1]);  
    }  
    String prefix = getConfig().getString("messages.prefix", "");  
    p.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + text));  
}

                                                         }
            
