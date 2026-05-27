package com.bladex;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
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
    private boolean worldGuardEnabled = false;

    // Disk isim renkleri — her disk icin farkli
    private static final String[] COLORS_LERA   = {"&#ff6600","&#ff7700","&#ff8800","&#ff9900","&#ffaa00","&#ff9900","&#ff8800","&#ff7700"};
    private static final String[] COLORS_AARON  = {"&#0055ff","&#0077ff","&#0099ff","&#00bbff","&#00ddff","&#00bbff","&#0099ff","&#0077ff"};
    private static final String[] COLORS_LENA   = {"&#00cc44","&#00dd55","&#00ee66","&#00ff77","&#00ee66","&#00dd55","&#00cc44","&#00bb33"};

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        // WorldGuard kontrolu
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardEnabled = true;
            getLogger().info("WorldGuard bulundu, bolge korumasi aktif.");
        }

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
                    p.sendMessage(color(cfg("prefix") + "&eKullanim: &f/bladex reload"));
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
        int size = getConfig().getInt("menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Bilgi itemi — sol ust kose slot 0
        inv.setItem(4, buildInfoItem());

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

    /** Bilgi itemi — menu ortasina */
    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(color("&#00ccff&lBladeX &8| &7Bilgi"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&8"));
        lore.add(color("&#00ccff&lYazar: &fYukile"));
        lore.add(color("&8"));
        lore.add(color("&7Tiklayarak kilici envanterine ekleyebilirsin."));
        lore.add(color("&7Her kilicin benzersiz bir yetenegi var."));
        lore.add(color("&7Sag tikla yetenegi kullan, bekleme suresi dolar."));
        lore.add(color("&8"));
        lore.add(color("&#ff3333&lGuvenli bolgede kilic yetenekleri calismaz."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSword(String key, ConfigurationSection sec) {
        Material mat = Material.matchMaterial(sec.getString("material", "NETHERITE_SWORD").toUpperCase());
        if (mat == null) mat = Material.NETHERITE_SWORD;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        // Kılic ismi — flop animasyonu ile
        String rawName = sec.getString("display_name", "&fKilic");
        meta.setDisplayName(getFlopTextCustom(ChatColor.stripColor(color(rawName)), flopTick,
            getConfig().getStringList("flop_colors").toArray(new String[0])));
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

        // Her disk icin farkli flop rengi
        String rawName = sec.getString("display_name", "&fItem");
        String cleanName = ChatColor.stripColor(color(rawName));
        String flopName;
        String action = sec.getString("action", "");
        switch (action) {
            case "SELF_PHANTOM_LOCK"  -> flopName = getFlopTextCustom(cleanName, flopTick, COLORS_LERA);
            case "SELF_POWER_BOOST"   -> flopName = getFlopTextCustom(cleanName, flopTick, COLORS_AARON);
            case "SELF_INVINCIBILITY" -> flopName = getFlopTextCustom(cleanName, flopTick, COLORS_LENA);
            default                   -> flopName = color(rawName);
        }
        meta.setDisplayName(flopName);

        int cd = sec.getInt("cooldown", 60);
        List<String> lore = new ArrayList<>();

        // Disk ust kisim — isim etiketi (flop rengi ile)
        String label = switch (action) {
            case "SELF_PHANTOM_LOCK"  -> color("&#ff6600&lYenilmezlik Itemi");
            case "SELF_POWER_BOOST"   -> color("&#0099ff&lGuc Yildizi Itemi");
            case "SELF_INVINCIBILITY" -> color("&#00ee66&lHasar Koruma Itemi");
            default -> color("&fOzel Item");
        };
        lore.add(label);
        lore.add(color("&8"));

        for (String line : sec.getStringList("lore")) {
            lore.add(color(line.replace("{cooldown}", String.valueOf(cd))));
        }

        // Kaybolma suresi satiri
        int vanish = sec.getInt("vanish_seconds", 0);
        if (vanish > 0) {
            lore.add(color("&8"));
            lore.add(color("&#aaaaaa&o" + vanish + " saniye sonra envanterden kaybolur."));
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
        // Sadece ust envanter (menu) tiklama
        if (e.getClickedInventory() == e.getView().getBottomInventory()) return;

        Player clicker = (Player) e.getWhoClicked();

        // Kalkan slotuna koymayi engelle — dupe olmaz
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) return;

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
            playItemEffect(p);
            handleBottomAction(p, action, sec);
            return;
        }

        // WorldGuard bolge kontrolu — sadece kilic yetenekleri icin
        if (worldGuardEnabled && isInProtectedRegion(p)) {
            p.sendMessage(color("&#ff3333&lGuvenli bolgede kilic ozelliklerini kullanamazsin."));
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

        String displayName = getSwordDisplayName(bladeKey);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            new TextComponent(color(cfg("messages.ability_used").replace("{ability}", displayName))));

        playSwordEffect(p);
        handleSwordSkill(p, bladeKey, nearby);
    }

    /** WorldGuard ile oyuncunun PVP yasak bolgede olup olmadigini kontrol eder */
    private boolean isInProtectedRegion(Player p) {
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(p.getLocation());
            return !query.testState(loc, WorldGuardPlugin.inst().wrapPlayer(p), Flags.PVP);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getSwordDisplayName(String key) {
        ConfigurationSection sec = getConfig().getConfigurationSection("swords." + key);
        if (sec != null) return ChatColor.stripColor(color(sec.getString("display_name", key)));
        return key;
    }

    private void playSwordEffect(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1f);
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 3);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.05);
    }

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
                    Location foot = t.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
                    Location head = t.getLocation().getBlock().getLocation().add(0.5, 1.0, 0.5);
                    if (foot.getBlock().getType() == Material.AIR) foot.getBlock().setType(Material.COBWEB);
                    if (head.getBlock().getType() == Material.AIR) head.getBlock().setType(Material.COBWEB);
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
                    // 3 saniye elytra kilidi (58 tick = ~2.9sn, en yakin 3sn)
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
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
    private void handleBottomAction(Player p, String action, ConfigurationSection sec) {
        int vanishSec = sec.getInt("vanish_seconds", 0);

        switch (action) {
            case "SELF_PHANTOM_LOCK" -> {
                // 1.8 saniye elytra kilidi (36 tick)
                elytraBanned.put(p.getUniqueId(), System.currentTimeMillis() + 1800L);
                p.setGliding(false);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_HURT, 0.7f, 0.8f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#ff6600&lElytra Kilidi! 1.8 saniye."), 5, 36, 5);
                p.sendMessage(color(cfg("prefix") + "&#ff6600&lElytra 1.8 saniye kilitlendi."));
            }
            case "SELF_POWER_BOOST" -> {
                // Direnc + Hiz II — 3 saniye
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      60, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.1);
                p.sendTitle("", color("&#0099ff&lGuc Yildizi! 3 saniye."), 5, 60, 5);
                p.sendMessage(color(cfg("prefix") + "&#0099ff&lGuc Yildizi! Direnc + Hiz II, 3 saniye."));
            }
            case "SELF_INVINCIBILITY" -> {
                // 2.5 saniye hasar almama — Resistance V (50 tick)
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 4));
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1.0, 0.5, 0.1);
                p.sendTitle("", color("&#00ee66&lYenilmezlik! 2.5 saniye."), 5, 50, 5);
                p.sendMessage(color(cfg("prefix") + "&#00ee66&lYenilmezlik aktif! 2.5 saniye hasar almayacaksin."));
            }
        }

        // Envanterden kaybolma
        if (vanishSec > 0) {
            ItemStack held = p.getInventory().getItemInMainHand();
            new BukkitRunnable() {
                @Override public void run() {
                    // Elde tutuyorsa kaldir
                    ItemStack current = p.getInventory().getItemInMainHand();
                    if (current.hasItemMeta() && current.getItemMeta().getPersistentDataContainer()
                        .has(new org.bukkit.NamespacedKey(BladeX.this, "bladex_type"),
                             org.bukkit.persistence.PersistentDataType.STRING)) {
                        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    } else {
                        // Envanterde ara
                        for (int i = 0; i < p.getInventory().getSize(); i++) {
                            ItemStack it = p.getInventory().getItem(i);
                            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer()
                                .has(new org.bukkit.NamespacedKey(BladeX.this, "bladex_type"),
                                     org.bukkit.persistence.PersistentDataType.STRING)) {
                                p.getInventory().setItem(i, new ItemStack(Material.AIR));
                                break;
                            }
                        }
                    }
                }
            }.runTaskLater(this, vanishSec * 20L);
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
            p.sendMessage(color(cfg("messages.cooldown_message").replace("{time}", String.valueOf(left))));
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.get(p.getUniqueId()).put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Yardimci ─────────────────────────────────────────────────────────────
    private String cfg(String path) {
        return getConfig().getString(path, "");
    }

    /** Verilen renk dizisiyle flop metin uretir */
    private String getFlopTextCustom(String text, int tick, String[] colors) {
        if (colors == null || colors.length == 0) return color(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(colors[(i + tick) % colors.length]).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    public String getFlopText(String t, int tick) {
        List<String> colors = getConfig().getStringList("flop_colors");
        if (colors.isEmpty()) colors = List.of("&#00ccff","&#33ffff","&#66ffff","&#ffffff","&#66ffff","&#33ffff");
        return getFlopTextCustom(t, tick, colors.toArray(new String[0]));
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
}
