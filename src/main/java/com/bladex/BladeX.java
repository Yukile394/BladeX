package com.bladex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * BladeX — Fear Craft Özel Kılıçlar
 * Paper 1.21.x | Java 21
 */
public class BladeX extends JavaPlugin implements Listener {

    private final Set<UUID>                      swordMenuOpen = new HashSet<>();
    private final Map<UUID, Map<String, Long>>   cooldowns     = new HashMap<>();
    private final Map<UUID, Long>                elytraBanned  = new HashMap<>();
    private int flopTick = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
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
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Sadece oyuncular kullanabilir.");
            return true;
        }
        if (!p.hasPermission("bladex.admin")) {
            p.sendMessage(color("&cYetkin yok."));
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu" -> openSwordMenu(p);
            case "bladex" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    p.sendMessage(color(cfg("prefix") + "&aConfig yeniden yüklendi!"));
                } else {
                    p.sendMessage(color(cfg("prefix") + "&eKullanım: &f/bladex reload"));
                }
            }
            case "bladexreload" -> {
                reloadConfig();
                p.sendMessage(color(cfg("prefix") + "&aConfig yeniden yüklendi!"));
            }
        }
        return true;
    }

    // ─── Sword Menu ───────────────────────────────────────────────────────────
    private void openSwordMenu(Player p) {
        int size = getConfig().getInt("menu.size", 36);
        if (size % 9 != 0 || size < 9 || size > 54) size = 36;

        // Paper 1.21: title için Adventure Component kullan
        String titleRaw = color(cfg("menu.title"));
        Component titleComp = LegacyComponentSerializer.legacySection().deserialize(titleRaw);
        Inventory inv = Bukkit.createInventory(null, size, titleComp);

        // Kılıçlar
        ConfigurationSection swords = getConfig().getConfigurationSection("swords");
        if (swords != null) {
            for (String key : swords.getKeys(false)) {
                ConfigurationSection sec = swords.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", 0);
                if (slot < 0 || slot >= size) continue;
                inv.setItem(slot, buildSword(key, sec));
            }
        }

        // Alt itemler
        ConfigurationSection bottom = getConfig().getConfigurationSection("menu_bottom_items");
        if (bottom != null) {
            for (String key : bottom.getKeys(false)) {
                ConfigurationSection sec = bottom.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot", 0);
                if (slot < 0 || slot >= size) continue;
                inv.setItem(slot, buildSpecialItem(key, sec));
            }
        }

        p.openInventory(inv);
        swordMenuOpen.add(p.getUniqueId());
    }

    private ItemStack buildSword(String key, ConfigurationSection sec) {
        String matStr = sec.getString("material", "NETHERITE_SWORD").toUpperCase();
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.NETHERITE_SWORD;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(sec.getString("display_name", "&fKılıç")));

        int cd  = sec.getInt("cooldown", 30);
        int rad = sec.getInt("radius", 6);
        List<String> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(color(line
                .replace("{cooldown}", String.valueOf(cd))
                .replace("{radius}",   String.valueOf(rad))));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(this, "bladex_type"),
            org.bukkit.persistence.PersistentDataType.STRING, key
        );
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSpecialItem(String key, ConfigurationSection sec) {
        String matStr = sec.getString("material", "MUSIC_DISC_13").toUpperCase();
        Material mat = Material.matchMaterial(matStr);
        if (mat == null) mat = Material.MUSIC_DISC_13;

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

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

    // ─── Inventory Events ────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player clicker)) return;
        if (!swordMenuOpen.contains(clicker.getUniqueId())) return;

        e.setCancelled(true);

        // Sadece üst envanter (menü) tıklamalarını işle
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() == clicker.getInventory()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta()) return;

        HashMap<Integer, ItemStack> leftover = clicker.getInventory().addItem(clicked.clone());
        if (leftover.isEmpty()) {
            clicker.sendMessage(color(cfg("prefix") + "&aEşya envanterine eklendi."));
        } else {
            clicker.sendMessage(color(cfg("prefix") + "&eEnvanterinde yer yok!"));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            swordMenuOpen.remove(p.getUniqueId());
        }
    }

    // ─── Interact Handler ────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().name().contains("RIGHT")) return;
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!held.hasItemMeta()) return;

        String bladeKey = held.getItemMeta().getPersistentDataContainer()
            .get(new org.bukkit.NamespacedKey(this, "bladex_type"),
                 org.bukkit.persistence.PersistentDataType.STRING);
        if (bladeKey == null) return;

        e.setCancelled(true);

        if (bladeKey.startsWith("bottom_")) {
            String sub = bladeKey.replace("bottom_", "");
            ConfigurationSection sec = getConfig().getConfigurationSection("menu_bottom_items." + sub);
            if (sec == null) return;
            int    cd     = sec.getInt("cooldown", 60);
            String action = sec.getString("action", "");
            if (isCoolingDown(p, bladeKey)) return;
            applyCooldown(p, bladeKey, cd);
            handleBottomAction(p, action);
            return;
        }

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

        String usedMsg = cfg("messages.ability_used")
            .replace("{ability}", getSwordDisplayName(bladeKey));
        sendActionBar(p, usedMsg);
        playCommonSwordEffect(p);
        handleSwordSkill(p, bladeKey, nearby);
    }

    private String getSwordDisplayName(String key) {
        ConfigurationSection sec = getConfig().getConfigurationSection("swords." + key);
        if (sec != null) return ChatColor.stripColor(color(sec.getString("display_name", key)));
        return key.toUpperCase();
    }

    private void playCommonSwordEffect(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);
        p.getWorld().spawnParticle(Particle.CRIT,         p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
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
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.7f, 0.8f);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                nearby.forEach(t -> {
                    Location foot = t.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                    Location head = t.getLocation().getBlock().getLocation().add(0.5, 1.0, 0.5);
                    foot.getBlock().setType(Material.COBWEB);
                    head.getBlock().setType(Material.COBWEB);
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (foot.getBlock().getType() == Material.COBWEB) foot.getBlock().setType(Material.AIR);
                            if (head.getBlock().getType() == Material.COBWEB) head.getBlock().setType(Material.AIR);
                        }
                    }.runTaskLater(this, 70L);
                    t.sendTitle("", color("&#bd0000&l🕷 Örümcek Ağına Yakalandın!"), 5, 30, 5);
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.03);
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
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 4000L);
                    t.setGliding(false);
                    t.sendTitle("", color(cfg("messages.elytra_broken")), 5, 30, 5);
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.1);
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
    private void handleBottomAction(Player p, String action) {
        switch (action) {
            case "SELF_PHANTOM_LOCK" -> {
                elytraBanned.put(p.getUniqueId(), System.currentTimeMillis() + 4000L);
                p.setGliding(false);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_HURT, 1f, 0.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 0.7f, 1.2f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#0080ff&lPhantom Kilidi Aktif! 4 saniye."), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#0080ff&lPhantom Kilidi aktif! Elytra 4 saniye çalışmaz."));
            }
            case "SELF_POWER_BOOST" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      600, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,   600, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#ffdd00&l⭐ Güç Yıldızı Aktif!"), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#ffdd00&l⭐ Güç Yıldızı aktif! 30 saniye Hız II + Direnç + Güç I."));
            }
            case "SELF_INVINCIBILITY" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 4));
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.1);
                p.sendTitle("", color("&#ff6600&l🛡 Yenilmezlik Aktif! 2.5 saniye."), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#ff6600&l🛡 Yenilmezlik aktif! 2.5 saniye hasar almayacaksın."));
            }
        }
    }

    // ─── Elytra Koruması ─────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    // ─── Cooldown ────────────────────────────────────────────────────────────
    private boolean isCoolingDown(Player p, String key) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(key, 0L);
        if (System.currentTimeMillis() < expire) {
            long left = (expire - System.currentTimeMillis() + 999) / 1000;
            String msg = cfg("messages.cooldown_message").replace("{time}", String.valueOf(left));
            p.sendMessage(color(msg));
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.get(p.getUniqueId()).put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Yardımcı Metodlar ───────────────────────────────────────────────────
    private void sendActionBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(color(text)));
    }

    private String cfg(String path) {
        return getConfig().getString(path, "");
    }

    public String color(String text) {
        if (text == null) return "";
        // &#RRGGBB → §x§R§R§G§G§B§B
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
}
