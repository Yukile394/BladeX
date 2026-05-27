package com.bladex;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
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

public class BladeX extends JavaPlugin implements Listener {

    private final Map<UUID, Map<String, Long>> cooldowns    = new HashMap<>();
    private final Map<UUID, Long>              elytraBanned = new HashMap<>();
    private int flopTick = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            @Override public void run() { flopTick++; }
        }.runTaskTimer(this, 0L, 2L);
        getLogger().info("BladeX aktif!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BladeX devre disi.");
    }

    // ─── Commands ─────────────────────────────────────────────────────────────
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Sadece oyuncular."); return true; }
        if (!p.hasPermission("bladex.admin")) { p.sendMessage(color("&cYetkin yok.")); return true; }

        switch (cmd.getName().toLowerCase()) {
            case "kilicvermenu" -> openSwordMenu(p);
            case "bladex" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    reloadConfig();
                    p.sendMessage(color(cfg("prefix") + "&aConfig yeniden yuklendi!"));
                } else {
                    p.sendMessage(color(cfg("prefix") + "&eKullanimim: &f/bladex reload"));
                }
            }
            case "bladexreload" -> {
                reloadConfig();
                p.sendMessage(color(cfg("prefix") + "&aConfig yeniden yuklendi!"));
            }
        }
        return true;
    }

    // ─── Sword Menu ───────────────────────────────────────────────────────────
    private void openSwordMenu(Player p) {
        String title = color(cfg("menu.title"));
        int size = getConfig().getInt("menu.size", 36);
        Inventory inv = Bukkit.createInventory(null, size, title);

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
    }

    private ItemStack buildSword(String key, ConfigurationSection sec) {
        Material mat = Material.matchMaterial(sec.getString("material", "NETHERITE_SWORD").toUpperCase());
        if (mat == null) mat = Material.NETHERITE_SWORD;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(color(sec.getString("display_name", "&fKilic")));
        int cd  = sec.getInt("cooldown", 30);
        int rad = sec.getInt("radius", 6);
        List<String> lore = new ArrayList<>();
        for (String line : sec.getStringList("lore")) {
            lore.add(color(line.replace("{cooldown}", String.valueOf(cd))
                              .replace("{radius}", String.valueOf(rad))));
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
        Material mat = Material.matchMaterial(sec.getString("material", "MUSIC_DISC_13").toUpperCase());
        if (mat == null) mat = Material.MUSIC_DISC_13;
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

    // ─── Inventory Click ─────────────────────────────────────────────────────
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String rawTitle = ChatColor.stripColor(e.getView().getTitle());
        if (!rawTitle.contains("Kiliclar")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        if (!e.getCurrentItem().hasItemMeta()) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() == e.getView().getBottomInventory()) return;
        Player clicker = (Player) e.getWhoClicked();
        HashMap<Integer, ItemStack> leftover = clicker.getInventory().addItem(e.getCurrentItem().clone());
        if (leftover.isEmpty()) {
            clicker.sendMessage(color(cfg("prefix") + "&aEsya envanterine eklendi."));
        } else {
            clicker.sendMessage(color(cfg("prefix") + "&eEnvanterinde yer yok!"));
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

        // Alt item
        if (bladeKey.startsWith("bottom_")) {
            String sub = bladeKey.replace("bottom_", "");
            ConfigurationSection sec = getConfig().getConfigurationSection("menu_bottom_items." + sub);
            if (sec == null) return;
            int    cd     = sec.getInt("cooldown", 60);
            String action = sec.getString("action", "");
            if (isCoolingDown(p, bladeKey)) return;
            applyCooldown(p, bladeKey, cd);
            // Item kullanim efekti
            playItemEffect(p);
            handleBottomAction(p, action);
            return;
        }

        // Kilic
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

        // Kullanim mesaji - action bar, duzgun renk
        String displayName = getSwordDisplayName(bladeKey);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            new TextComponent(color(cfg("messages.ability_used").replace("{ability}", displayName))));

        // Tum kiliclarda ortak ses + efekt
        playSwordEffect(p);

        handleSwordSkill(p, bladeKey, nearby);
    }

    private String getSwordDisplayName(String key) {
        ConfigurationSection sec = getConfig().getConfigurationSection("swords." + key);
        if (sec != null) return ChatColor.stripColor(color(sec.getString("display_name", key)));
        return key;
    }

    // Kilic ortak ses + partikul
    private void playSwordEffect(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 3);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.05);
    }

    // Item ortak ses + partikul
    private void playItemEffect(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.05);
    }

    // ─── Kilic Yetenekleri ────────────────────────────────────────────────────
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
                    // Hem ayak hem bas bloguna ag koy
                    Location foot = t.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                    Location head = t.getLocation().getBlock().getLocation().add(0.5, 1.0, 0.5);
                    if (foot.getBlock().getType() == Material.AIR) foot.getBlock().setType(Material.COBWEB);
                    if (head.getBlock().getType() == Material.AIR) head.getBlock().setType(Material.COBWEB);
                    // 3.5 saniye sonra kaldir
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (foot.getBlock().getType() == Material.COBWEB) foot.getBlock().setType(Material.AIR);
                            if (head.getBlock().getType() == Material.COBWEB) head.getBlock().setType(Material.AIR);
                        }
                    }.runTaskLater(this, 70L);
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

    // ─── Alt Item Eylemler ────────────────────────────────────────────────────
    private void handleBottomAction(Player p, String action) {
        switch (action) {
            case "SELF_PHANTOM_LOCK" -> {
                // Lera Raine - Phantom gibi elytra kilidi, 4 saniye
                elytraBanned.put(p.getUniqueId(), System.currentTimeMillis() + 4000L);
                p.setGliding(false);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_HURT, 0.7f, 0.8f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#0080ff&lPhantom Kilidi! 4 saniye."), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#0080ff&lElytra 4 saniye kilitlendi."));
            }
            case "SELF_POWER_BOOST" -> {
                // Aaron Cherof - Direnc + Hiz II, 3 saniye (60 tick)
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0)); // Direnc I, 3sn
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      60, 1)); // Hiz II, 3sn
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#ffdd00&l Guc Yildizi Aktif! 3 saniye."), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#ffdd00&l Guc Yildizi! Direnc + Hiz II, 3 saniye."));
            }
            case "SELF_INVINCIBILITY" -> {
                // Lena Raine - 2.5 saniye hasar almama (50 tick, Resistance V)
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 4));
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.1);
                p.sendTitle("", color("&#ff6600&l Yenilmezlik! 2.5 saniye."), 5, 40, 10);
                p.sendMessage(color(cfg("prefix") + "&#ff6600&l Yenilmezlik aktif! 2.5 saniye hasar almayacaksin."));
            }
        }
    }

    // ─── Elytra Korumasi ──────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (elytraBanned.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
        }
    }

    // ─── Cooldown ─────────────────────────────────────────────────────────────
    private boolean isCoolingDown(Player p, String key) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long expire = cooldowns.get(p.getUniqueId()).getOrDefault(key, 0L);
        if (System.currentTimeMillis() < expire) {
            long left = (expire - System.currentTimeMillis() + 999) / 1000;
            // Sadece chat'e yaz
            p.sendMessage(color(cfg("messages.cooldown_message").replace("{time}", String.valueOf(left))));
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.get(p.getUniqueId()).put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Yardimci Metodlar ────────────────────────────────────────────────────
    private String cfg(String path) {
        return getConfig().getString(path, "");
    }

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

    public String getFlopText(String t, int tick) {
        List<String> colors = getConfig().getStringList("flop_colors");
        if (colors.isEmpty()) colors = List.of("&#00ccff","&#33ffff","&#66ffff","&#ffffff","&#66ffff","&#33ffff");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            sb.append(colors.get((i + tick) % colors.size())).append(t.charAt(i));
        }
        return color(sb.toString());
    }
}
