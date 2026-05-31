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
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
    private final Set<UUID> openMenus = new HashSet<>();

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
                p.sendMessage(color(cfg("prefix") + "&aBladeX Reload Atıldı!"));
            }
        }
        return true;
    }

    // ─── Sword Menu ───────────────────────────────────────────────────────────
    private void openSwordMenu(Player p) {
        String title = color(cfg("menu.title"));
        int size = getConfig().getInt("menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

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

        openMenus.add(p.getUniqueId());
        p.openInventory(inv);
    }

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
        lore.add(color("&#ff3333Guvenli bolgede kilic yetenekleri calismaz. [Silvera]"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSword(String key, ConfigurationSection sec) {
        Material mat = Material.matchMaterial(sec.getString("material", "NETHERITE_SWORD").toUpperCase());
        if (mat == null) mat = Material.NETHERITE_SWORD;
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player clicker)) return;
        if (!openMenus.contains(clicker.getUniqueId())) return;

        e.setCancelled(true);

        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta()) return;

        if (clicked.getType() == Material.BOOK) return;

        if (e.getClick() != org.bukkit.event.inventory.ClickType.LEFT
         && e.getClick() != org.bukkit.event.inventory.ClickType.RIGHT) return;

        HashMap<Integer, ItemStack> leftover = clicker.getInventory().addItem(clicked.clone());
        if (leftover.isEmpty()) {
            clicker.sendMessage(color(cfg("prefix") + "&aEsya envanterine eklendi."));
        } else {
            clicker.sendMessage(color(cfg("prefix") + "&eEnvanterinde yer yok!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!openMenus.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        openMenus.remove(p.getUniqueId());
    }

    // ─── Elytra Kilidi — ziplama + kanat acma engeli ─────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long bannedUntil = elytraBanned.get(p.getUniqueId());
        if (bannedUntil == null) return;
        if (System.currentTimeMillis() < bannedUntil) {
            // Kanat acilisini engelle
            e.setCancelled(true);
            p.setGliding(false);
        } else {
            // Sure doldu, temizle
            elytraBanned.remove(p.getUniqueId());
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
            playItemEffect(p);
            handleBottomAction(p, action, sec);
            return;
        }

        // WorldGuard bolge kontrolu — sadece kilic yetenekleri icin
        if (worldGuardEnabled && isInProtectedRegion(p)) {
            p.sendMessage(color("&#ff3333Guvenli bolgede kilic ozelliklerini kullanamazsin."));
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

    private boolean isInProtectedRegion(Player p) {
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(p.getLocation());
            StateFlag.State state = query.queryState(loc, WorldGuardPlugin.inst().wrapPlayer(p), Flags.PVP);
            return state == StateFlag.State.DENY;
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

            // ── Creeper Kılıcı ──────────────────────────────────────────────
            case "creeper" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> {
                    t.damage(6, p);
                    t.setVelocity(t.getLocation().toVector()
                        .subtract(p.getLocation().toVector())
                        .normalize().multiply(1.5).setY(0.5));
                    t.sendTitle("", color("&#ff4400&l💥 Patlama dalgası!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ff4400&l💥 Creeper Kılıcı &7— Patlama darbesi aldın!")));
                });
            }

            // ── Örümcek Kılıcı ──────────────────────────────────────────────
            case "orumcek" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.7f, 0.8f);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                nearby.forEach(t -> {
                    // Hitbox tam ortası: ayaklarının bastığı blok
                    Block webBlock = t.getLocation().getBlock();
                    if (webBlock.getType() == Material.AIR) {
                        webBlock.setType(Material.COBWEB);
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (webBlock.getType() == Material.COBWEB)
                                    webBlock.setType(Material.AIR);
                            }
                        }.runTaskLater(BladeX.this, 58L); // 58 tick = ~2.9 saniye
                    }
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.03);
                    t.sendTitle("", color("&#aaaaaa&l🕷 Ağa yakalandın!"), 5, 40, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#aaaaaa&l🕷 Örümcek Kılıcı &7— Ağa yakalandın! &8(2.9sn)")));
                    t.playSound(t.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
                });
            }

            // ── Ejderha Kılıcı ──────────────────────────────────────────────
            case "ejderha" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                nearby.forEach(t -> {
                    t.setFireTicks(100);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                    t.sendTitle("", color("&#cc3300&l🔥 Ejderha nefesi!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#cc3300&l🔥 Ejderha Kılıcı &7— Yanıyorsun ve soluyorsun!")));
                });
            }

            // ── Phantom Kılıcı ──────────────────────────────────────────────
            case "phantom" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 0.9f);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);
                nearby.forEach(t -> {
                    // Elytra kilitini 3 saniye (3000ms) koy
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    t.setGliding(false);

                    // Büyük, renkli uyarı title
                    t.sendTitle(
                        color("&#ff2222&l⚠ DİKKAT!"),
                        color("&#ff6666Elytran &fbu &c&l3 saniyeliğine kitlendi&f!"),
                        5, 50, 5
                    );
                    // Action bar
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ff4444&l👻 Phantom Kılıcı &7— Elytra kitli! &c(3sn)")));

                    t.playSound(t.getLocation(), Sound.ENTITY_PHANTOM_HURT, 1f, 0.7f);
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 35, 0.4, 0.6, 0.4, 0.15);
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);

                    // 3 saniye sonra serbest mesajı
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (t.isOnline()) {
                                elytraBanned.remove(t.getUniqueId());
                                t.sendTitle("", color("&#44ff88&l✔ Elytra serbest kaldı!"), 5, 25, 5);
                                t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    new TextComponent(color("&#44ff88Elytra kilidi kalktı, tekrar uçabilirsin.")));
                                t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                            }
                        }
                    }.runTaskLater(this, 60L); // 60 tick = 3 saniye
                });
            }

            // ── Enderman Kılıcı ─────────────────────────────────────────────
            case "enderman" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                nearby.forEach(t -> {
                    t.teleport(t.getLocation().add(0, 5, 0));
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation(), 40, 0.5, 1, 0.5, 0.1);
                    t.sendTitle("", color("&#aa44ff&l⚡ Işınlandın!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#aa44ff&l⚡ Enderman Kılıcı &7— Yukarı ışınlandın!")));
                });
            }

            // ── Shulker Kılıcı ──────────────────────────────────────────────
            case "shulker" -> {
                // Kullanan oyuncu sesi + efekti
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_OPEN, 0.8f, 0.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_CLOSE, 0.6f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                nearby.forEach(t -> {
                    // 60 tick = 3 saniye levitation
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
                    t.playSound(t.getLocation(), Sound.ENTITY_SHULKER_HURT, 1f, 0.8f);

                    // Güzel mor-beyaz parçacıklar
                    t.getWorld().spawnParticle(Particle.END_ROD, t.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.05);

                    // Title + action bar
                    t.sendTitle(
                        color("&#cc88ff&l☁ LEVITATION!"),
                        color("&#cc88ff3 saniyeliğine havaya kaldırıldın!"),
                        5, 45, 5
                    );
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#cc88ff&l☁ Shulker Kılıcı &7— Havaya kaldırıldın! &d(3sn)")));

                    // 3 saniye sonra bitti mesajı
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (t.isOnline()) {
                                t.sendTitle("", color("&#44ff88&l✔ Yere inebilirsin!"), 5, 20, 5);
                                t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                            }
                        }
                    }.runTaskLater(this, 60L); // 60 tick = 3 saniye
                });
            }

            // ── Yıldırım Kılıcı ─────────────────────────────────────────────
            case "yildirim" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
                nearby.forEach(t -> {
                    t.getWorld().strikeLightning(t.getLocation());
                    t.sendTitle("", color("&#ffff00&l⚡ Yıldırım çarptı!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ffff00&l⚡ Yıldırım Kılıcı &7— Yıldırım çarptı!")));
                });
            }

            // ── Gardiyan Kılıcı ─────────────────────────────────────────────
            case "gardiyan" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 2));
                    t.getWorld().spawnParticle(Particle.NAUTILUS, t.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    t.sendTitle("", color("&#0088ff&l🔱 Gardiyan laneti!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#0088ff&l🔱 Gardiyan Kılıcı &7— Madencilik yorgunluğu aldın!")));
                });
            }
        }
    }

    // ─── Bottom Item Aksiyonlari ──────────────────────────────────────────────
    private void handleBottomAction(Player p, String action, ConfigurationSection sec) {
        switch (action) {
            case "SELF_PHANTOM_LOCK" -> {
                int dur = sec.getInt("duration_seconds", 5) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, dur, 4));
                p.sendTitle(color("&#ff6600&l🛡 YENİLMEZLİK!"), color("&#ff8800Hasar almıyorsun!"), 5, 40, 5);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(color("&#ff6600&l🛡 Yenilmezlik aktif!")));
                p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.1);
            }
            case "SELF_POWER_BOOST" -> {
                int dur = sec.getInt("duration_seconds", 5) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, dur, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur, 1));
                p.sendTitle(color("&#0099ff&l⭐ GÜÇ YILDIZI!"), color("&#00bbffGüç ve hız aldın!"), 5, 40, 5);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(color("&#0099ff&l⭐ Güç Yıldızı aktif!")));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.1);
            }
            case "SELF_INVINCIBILITY" -> {
                int dur = sec.getInt("duration_seconds", 5) * 20;
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, dur, 3));
                p.sendTitle(color("&#00ee66&l💚 HASAR KORUMA!"), color("&#00ff77Absorpsiyon kalkanı aldın!"), 5, 40, 5);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(color("&#00ee66&l💚 Hasar Koruma aktif!")));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.9f);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.05);
            }
        }
    }

    // ─── Cooldown ─────────────────────────────────────────────────────────────
    private boolean isCoolingDown(Player p, String key) {
        Map<String, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return false;
        Long until = map.get(key);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) {
            long left = (until - System.currentTimeMillis()) / 1000 + 1;
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(color("&#ff4444⏳ Bekleme süresi: &f" + left + " &csaniye kaldı.")));
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
            .put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Flop (Animasyonlu Isim) ──────────────────────────────────────────────
    private String getFlopTextCustom(String text, int tick, String[] colors) {
        if (colors == null || colors.length == 0) return color(text);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String hex = colors[(i + tick) % colors.length];
            sb.append(hex).append(text.charAt(i));
        }
        return color(sb.toString());
    }

    // ─── Hex Renk Cevirici ───────────────────────────────────────────────────
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

    private String cfg(String path) {
        return getConfig().getString(path, "");
    }
}
