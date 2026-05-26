package com.loginx;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BladeX — Fear Craft Özel Kılıçlar & İade Sistemi
 * Paper 1.21.x | Java 21
 */
public class BladeX extends JavaPlugin implements Listener {

    // ─── State ───────────────────────────────────────────────────────────────
    /** UUID → (abilityKey → expireMs) */
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** UUID → expireMs  (elytra yasağı) */
    private final Map<UUID, Long> elytraBanned = new HashMap<>();
    /** Ölüm kayıtları – en yeni başta */
    private final List<DeathRecord> deathRecords = new ArrayList<>();
    /** Animasyonlu başlık için tick sayacı */
    private int flopTick = 0;

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // Flop tick – her 2 game-tick'te bir artar
        new BukkitRunnable() {
            @Override public void run() { flopTick++; }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("BladeX aktif! Paper 1.21.x — Fear Craft");
    }

    @Override
    public void onDisable() {
        getLogger().info("BladeX devre dışı.");
    }

    // ─── Commands ─────────────────────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncular kullanabilir."); return true; }
        if (!p.hasPermission("bladex.admin")) { p.sendMessage(color("&cYetkin yok.")); return true; }

        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu"  -> openSwordMenu(p);
            case "iade"          -> openIadeMenu(p);
            case "bladexreload"  -> {
                reloadConfig();
                p.sendMessage(color(cfg("prefix") + "&aConfig yeniden yüklendi!"));
            }
        }
        return true;
    }

    // ─── Sword Menu ───────────────────────────────────────────────────────────
    private void openSwordMenu(Player p) {
        String title = color(cfg("menu.title"));
        int size     = getConfig().getInt("menu.size", 36);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Kılıçlar
        ConfigurationSection swords = getConfig().getConfigurationSection("swords");
        if (swords != null) {
            for (String key : swords.getKeys(false)) {
                ConfigurationSection sec = swords.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", 0);
                inv.setItem(slot, buildSword(key, sec));
            }
        }

        // Alt 3 item
        ConfigurationSection bottom = getConfig().getConfigurationSection("menu_bottom_items");
        if (bottom != null) {
            for (String key : bottom.getKeys(false)) {
                ConfigurationSection sec = bottom.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", 0);
                inv.setItem(slot, buildSpecialItem(key, sec));
            }
        }

        p.openInventory(inv);
    }

    /** Config'den kılıç ItemStack oluşturur. {cooldown} ve {radius} lore'da yerine geçer. */
    private ItemStack buildSword(String key, ConfigurationSection sec) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta  meta = item.getItemMeta();

        meta.setDisplayName(color(sec.getString("display_name", "&fKılıç")));

        int cd  = sec.getInt("cooldown", 30);
        int rad = sec.getInt("radius", 6);
        List<String> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(color(line.replace("{cooldown}", String.valueOf(cd))
                              .replace("{radius}", String.valueOf(rad))));
        }
        if (lore.isEmpty()) {
            lore.add(getFlopText("✦ Fear Craft Özel ✦", flopTick));
            lore.add(color("&7Sağ tıkla yeteneği kullan!"));
        }
        meta.setLore(lore);
        // Kılıcı tanımlayabilmek için NBT yerine custom model data kullanıyoruz.
        // Ayrıca itemin iç tanımlayıcısını persistent data'ya yazıyoruz.
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, "bladex_type"),
            org.bukkit.persistence.PersistentDataType.STRING, key
        );
        item.setItemMeta(meta);
        return item;
    }

    /** Config'den alt menü özel item oluşturur. */
    private ItemStack buildSpecialItem(String key, ConfigurationSection sec) {
        Material mat = Material.matchMaterial(sec.getString("material", "ENDER_EYE").toUpperCase());
        if (mat == null) mat = Material.ENDER_EYE;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();

        meta.setDisplayName(color(sec.getString("display_name", "&fItem")));

        int cd = sec.getInt("cooldown", 60);
        List<String> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(color(line.replace("{cooldown}", String.valueOf(cd))));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, "bladex_type"),
            org.bukkit.persistence.PersistentDataType.STRING, "bottom_" + key
        );
        item.setItemMeta(meta);
        return item;
    }

    // ─── Interact Handler ────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p    = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!held.hasItemMeta()) return;

        String bladeKey = held.getItemMeta().getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey(this, "bladex_type"),
                 org.bukkit.persistence.PersistentDataType.STRING);
        if (bladeKey == null) return;

        e.setCancelled(true);

        // ─ Alt item eylemler
        if (bladeKey.startsWith("bottom_")) {
            String sub = bladeKey.replace("bottom_", "");
            ConfigurationSection sec = getConfig().getConfigurationSection("menu_bottom_items." + sub);
            if (sec == null) return;
            int cd = sec.getInt("cooldown", 60);
            String action = sec.getString("action", "");
            if (isCoolingDown(p, bladeKey)) return;
            applyCooldown(p, bladeKey, cd);
            handleBottomAction(p, action, cd);
            return;
        }

        // ─ Kılıç yetenekleri
        ConfigurationSection swordSec = getConfig().getConfigurationSection("swords." + bladeKey);
        int cd     = swordSec != null ? swordSec.getInt("cooldown", 30) : 30;
        int radius = swordSec != null ? swordSec.getInt("radius", 6)    : 6;

        if (isCoolingDown(p, bladeKey)) return;

        List<Player> nearby = p.getNearbyEntities(radius, radius, radius).stream()
            .filter(en -> en instanceof Player && en != p)
            .map(en -> (Player) en)
            .toList();

        if (nearby.isEmpty()) {
            p.sendMessage(color(cfg("messages.no_nearby_players")));
            return;
        }

        applyCooldown(p, bladeKey, cd);
        sendActionBar(p, "» " + bladeKey.toUpperCase() + " YETENEĞİ «");
        handleSwordSkill(p, bladeKey, nearby);
    }

    // ─── Kılıç Yetenekleri ───────────────────────────────────────────────────
    private void handleSwordSkill(Player p, String type, List<Player> nearby) {
        switch (type) {
            case "creeper" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> {
                    t.damage(6, p);
                    t.setVelocity(t.getLocation().toVector()
                        .subtract(p.getLocation().toVector())
                        .normalize().multiply(1.5).setY(0.5));
                });
            }
            case "orumcek" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1f);
                nearby.forEach(t -> {
                    Location loc = t.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                    loc.getBlock().setType(Material.COBWEB);
                    new BukkitRunnable() {
                        @Override public void run() { loc.getBlock().setType(Material.AIR); }
                    }.runTaskLater(this, 70L);
                });
            }
            case "ejderha" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                nearby.forEach(t -> {
                    t.setFireTicks(100);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                });
            }
            case "phantom" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                nearby.forEach(t -> {
                    long banMs = 3000L;
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + banMs);
                    t.setGliding(false);
                    t.sendTitle("", color(cfg("messages.elytra_broken")), 5, 30, 5);
                });
            }
            case "enderman" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                nearby.forEach(t -> {
                    t.teleport(t.getLocation().add(0, 5, 0));
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation(), 40, 0.5, 1, 0.5, 0.1);
                });
            }
            case "shulker" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 80, 1));
                    t.getWorld().spawnParticle(Particle.END_ROD, t.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                });
            }
            case "yildirim" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
                nearby.forEach(t -> t.getWorld().strikeLightning(t.getLocation()));
            }
            case "gardiyan" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 2));
                    t.getWorld().spawnParticle(Particle.NAUTILUS, t.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                });
            }
            case "wither" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                });
            }
        }
    }

    // ─── Alt Menü Eylemler ───────────────────────────────────────────────────
    /**
     * SELF_ELYTRA_LOCK  → 80sn kendi elytra'nı kilitle
     * SELF_POWER_BOOST  → 30sn Hız II + Güç I
     * SELF_INVINCIBILITY→ 10sn hasar almama
     */
    private void handleBottomAction(Player p, String action, int cdSec) {
        switch (action) {
            case "SELF_ELYTRA_LOCK" -> {
                long banMs = cdSec * 1000L; // config'den alınan süre kadar kilitle
                elytraBanned.put(p.getUniqueId(), System.currentTimeMillis() + banMs);
                p.setGliding(false);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_HURT, 1f, 0.8f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                String msg = cfg("messages.elytra_locked_self").replace("{time}", String.valueOf(cdSec));
                p.sendTitle("", color(msg), 5, 40, 10);
                sendActionBar(p, "&#cc33ff» Elytra " + cdSec + " saniye kilitlendi!");
            }
            case "SELF_POWER_BOOST" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,  600, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color(cfg("messages.power_boost_active")), 5, 40, 10);
                sendActionBar(p, "&#ffdd00» Güç Yıldızı aktif! 30 saniye.");
            }
            case "SELF_INVINCIBILITY" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 4)); // Res V = tam koruma
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.1);
                p.sendTitle("", color(cfg("messages.invincibility_active")), 5, 40, 10);
                sendActionBar(p, "&#ff6600» Yenilmezlik aktif! 10 saniye.");
            }
        }
    }

    // ─── İade Menüsü ─────────────────────────────────────────────────────────
    private void openIadeMenu(Player p) {
        String title = getFlopText(cfg("iade_menu.title"), flopTick);
        int size     = getConfig().getInt("iade_menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        int limit = size - 9;
        for (int i = 0; i < deathRecords.size() && i < limit; i++) {
            DeathRecord dr   = deathRecords.get(i);
            ItemStack chest  = new ItemStack(Material.CHEST);
            ItemMeta  m      = chest.getItemMeta();
            m.setDisplayName(color("&#ffcc00" + dr.playerName + " &7#" + dr.id));
            m.setLore(List.of(
                getFlopText("Ölüm Verisi", flopTick + i),
                color("&fKatil: &c" + dr.killerName),
                color("&7Konum: &e" + fmtLoc(dr.loc)),
                color("&aTıkla → İade Et")
            ));
            chest.setItemMeta(m);
            inv.setItem(i, chest);
        }
        inv.setItem(size - 5, createBtn(Material.BARRIER, "&cKapat"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String rawTitle = ChatColor.stripColor(e.getView().getTitle());
        boolean isSword = rawTitle.contains("Kılıç Menü") || rawTitle.contains("Kiliclar");
        boolean isIade  = rawTitle.contains("ade"); // İade veya iade

        if (!isSword && !isIade) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        Player clicker = (Player) e.getWhoClicked();

        // Kılıç menüsünden item ver
        if (isSword) {
            if (e.getCurrentItem().hasItemMeta()) {
                clicker.getInventory().addItem(e.getCurrentItem().clone());
                clicker.sendMessage(color(cfg("prefix") + "&aEşya envanterine eklendi."));
            }
            return;
        }

        // İade menüsü
        if (e.getCurrentItem().getType() == Material.CHEST) {
            String dispName = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            if (!dispName.contains("#")) return;
            String id = dispName.split("#")[1].trim();
            DeathRecord dr = deathRecords.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
            if (dr == null) { clicker.sendMessage(color("&cKayıt bulunamadı.")); return; }

            Player target = Bukkit.getPlayer(dr.playerUUID);
            if (target != null && target.isOnline()) {
                target.getInventory().setContents(dr.items);
                String msg = cfg("messages.items_returned").replace("{player}", dr.playerName);
                clicker.sendMessage(color(cfg("prefix") + msg));
            } else {
                clicker.sendMessage(color("&cOyuncu çevrimiçi değil."));
            }
        } else if (e.getCurrentItem().getType() == Material.BARRIER) {
            clicker.closeInventory();
        }
    }

    // ─── Ölüm Kaydı ──────────────────────────────────────────────────────────
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead   = e.getEntity();
        String killer = dead.getKiller() != null ? dead.getKiller().getName() : "Bilinmiyor";
        deathRecords.add(0, new DeathRecord(dead, killer, dead.getInventory().getContents().clone()));
    }

    // ─── Elytra Koruması ─────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    // ─── Cooldown Yardımcıları ───────────────────────────────────────────────
    private boolean isCoolingDown(Player p, String key) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(key, 0L);
        if (System.currentTimeMillis() < expire) {
            long left = (expire - System.currentTimeMillis() + 999) / 1000;
            String msg = cfg("messages.cooldown_message").replace("{time}", String.valueOf(left));
            p.sendTitle("", color(msg), 0, 30, 5);
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.get(p.getUniqueId()).put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Yardımcı Metodlar ───────────────────────────────────────────────────
    private void sendActionBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(getFlopText(text, flopTick)));
    }

    private String cfg(String path) {
        return getConfig().getString(path, "");
    }

    private String fmtLoc(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private ItemStack createBtn(Material m, String name) {
        ItemStack item = new ItemStack(m);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        item.setItemMeta(meta);
        return item;
    }

    /** &#RRGGBB ve &x renk kodlarını çevirir. */
    public String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append("§").append(c);
            matcher.appendReplacement(sb, rep.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** Config'deki flop renkleriyle animasyonlu metin üretir. */
    public String getFlopText(String t, int tick) {
        List<String> colors = getConfig().getStringList("flop_colors");
        if (colors.isEmpty()) {
            colors = List.of("&#00ccff","&#33ffff","&#66ffff","&#ffffff","&#66ffff","&#33ffff");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            sb.append(colors.get((i + tick) % colors.size())).append(t.charAt(i));
        }
        return color(sb.toString());
    }

    // ─── İç Sınıf: Ölüm Kaydı ───────────────────────────────────────────────
    private static class DeathRecord {
        final String      id;
        final UUID        playerUUID;
        final String      playerName;
        final String      killerName;
        final Location    loc;
        final ItemStack[] items;

        DeathRecord(Player p, String killer, ItemStack[] items) {
            this.id         = Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
            this.playerUUID = p.getUniqueId();
            this.playerName = p.getName();
            this.killerName = killer;
            this.loc        = p.getLocation().clone();
            this.items      = items;
        }
    }
}
