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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BladeX extends JavaPlugin implements Listener {

    private final Map<UUID, Map<String, Long>> cooldowns    = new HashMap<>();
    private final Map<UUID, Long>              elytraBanned = new HashMap<>();
    private int flopTick = 0;
    private boolean worldGuardEnabled = false;
    private final Set<UUID> openMenus = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
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
                p.sendMessage(color(cfg("prefix") + "&aBladeX Reload Atildi!"));
            }
        }
        return true;
    }

    // ─── Sword Menu ───────────────────────────────────────────────────────────
    private void openSwordMenu(Player p) {
        String title = color(cfg("menu.title"));
        int size = getConfig().getInt("menu.size", 54);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Bilgi itemi — slot 4 (üst orta, eski konum)
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

        openMenus.add(p.getUniqueId());
        p.openInventory(inv);
    }

    /** Bilgi itemi — menünün üstüne, ortaya (slot 4) */
    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(color("&#00ccff&lBladeX &8| &7Bilgi"));
        List<String> lore = new ArrayList<>();
        lore.add(color("&8"));
        lore.add(color("&7Tiklayarak kilici envanterine ekleyebilirsin."));
        lore.add(color("&7Her kilicin benzersiz bir yetenegi var."));
        lore.add(color("&7Sag tikla yetenegi kullan, bekleme suresi dolar."));
        lore.add(color("&8"));
        lore.add(color("&#ff3333Guvenli bolgede kilic yetenekleri calismaz. [Silvera]"));
        lore.add(color("&8"));
        lore.add(color("&#00ccff&lYazar: &fYukile"));
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
        String[] flopColors = getConfig().getStringList("flop_colors").toArray(new String[0]);
        meta.setDisplayName(getFlopTextCustom(ChatColor.stripColor(color(rawName)), flopTick, flopColors));
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

    // ─── Inventory Events ─────────────────────────────────────────────────────
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

    // ─── Elytra Kilidi ────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Long bannedUntil = elytraBanned.get(p.getUniqueId());
        if (bannedUntil == null) return;
        if (System.currentTimeMillis() < bannedUntil) {
            e.setCancelled(true);
            p.setGliding(false);
        } else {
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

        // WorldGuard bolge kontrolu
        if (worldGuardEnabled && isInProtectedRegion(p)) {
            p.sendMessage(color("&#ff3333Guvenli bolgede kilic ozelliklerini kullanamazsin."));
            return;
        }

        ConfigurationSection swordSec = getConfig().getConfigurationSection("swords." + bladeKey);
        int cd     = swordSec != null ? swordSec.getInt("cooldown", 30) : 30;
        int radius = swordSec != null ? swordSec.getInt("radius", 6)    : 6;

        if (isCoolingDown(p, bladeKey)) return;

        // Enderman kılıcı oyuncu gerektirmez — direkt çalıştır
        if (bladeKey.equals("enderman")) {
            applyCooldown(p, bladeKey, cd);
            playSwordEffect(p);
            handleSwordSkill(p, bladeKey, Collections.emptyList());
            return;
        }

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

    // ─── Kilic Yetenekleri ────────────────────────────────────────────────────
    private void handleSwordSkill(Player p, String type, List<Player> nearby) {
        switch (type) {

            // ── Creeper ──────────────────────────────────────────────────────
            case "creeper" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                nearby.forEach(t -> {
                    t.damage(6, p);
                    t.setVelocity(t.getLocation().toVector()
                        .subtract(p.getLocation().toVector())
                        .normalize().multiply(1.5).setY(0.5));
                    t.sendTitle("", color("&#ff4400&l💥 Patlama dalgasi!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ff4400&l💥 Creeper Kilici &7— Patlama darbesi aldin!")));
                });
            }

            // ── Örümcek ──────────────────────────────────────────────────────
            case "orumcek" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.7f, 0.8f);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                nearby.forEach(t -> {
                    Block webBlock = t.getLocation().getBlock();
                    if (webBlock.getType() == Material.AIR) {
                        webBlock.setType(Material.COBWEB);
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (webBlock.getType() == Material.COBWEB)
                                    webBlock.setType(Material.AIR);
                            }
                        }.runTaskLater(BladeX.this, 58L); // ~2.9 saniye
                    }
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.03);
                    t.sendTitle("", color("&#aaaaaa&l🕷 Aga yakalandin!"), 5, 40, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#aaaaaa&l🕷 Örümcek Kilici &7— Aga yakalandin! &8(2.9sn)")));
                    t.playSound(t.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
                });
            }

            // ── Ejderha ──────────────────────────────────────────────────────
            case "ejderha" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                nearby.forEach(t -> {
                    t.setFireTicks(100);
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    t.getWorld().spawnParticle(Particle.DRAGON_BREATH, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
                    t.sendTitle("", color("&#cc3300&l🔥 Ejderha nefesi!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#cc3300&l🔥 Ejderha Kilici &7— Yaniyorsun ve soluyorsun!")));
                });
            }

            // ── Phantom ──────────────────────────────────────────────────────
            case "phantom" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 0.9f);
                p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);
                nearby.forEach(t -> {
                    elytraBanned.put(t.getUniqueId(), System.currentTimeMillis() + 3000L);
                    t.setGliding(false);
                    t.sendTitle(
                        color("&#ff2222&l⚠ DIKKAT!"),
                        color("&#ff6666Elytran &f3 saniyeligine &c&lkitlendi&f!"),
                        5, 50, 5
                    );
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ff4444&l👻 Phantom Kilici &7— Elytra kitli! &c(3sn)")));
                    t.playSound(t.getLocation(), Sound.ENTITY_PHANTOM_HURT, 1f, 0.7f);
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 35, 0.4, 0.6, 0.4, 0.15);
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (t.isOnline()) {
                                elytraBanned.remove(t.getUniqueId());
                                t.sendTitle("", color("&#44ff88&l✔ Elytra serbest kaldi!"), 5, 25, 5);
                                t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                    new TextComponent(color("&#44ff88Elytra kilidi kalktı, tekrar ucabilirsin.")));
                                t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                            }
                        }
                    }.runTaskLater(this, 60L);
                });
            }

            // ── Enderman — baktığın yöne 2.5 blok ileriye ışınla ────────────
            case "enderman" -> {
                Location origin = p.getLocation().clone();
                p.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                p.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.1);

                Location dest = findSafeTeleportForward(p, 2.5);
                if (dest != null) {
                    p.teleport(dest);
                    p.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.1);
                    p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                    p.sendTitle("", color("&#aa44ff&l⚡ Isinlandin!"), 5, 30, 5);
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#aa44ff&l⚡ Enderman Kilici &7— Ileriye isinlandin!")));
                } else {
                    p.sendMessage(color("&#cc44ff&7Isinlamak icin uygun yer bulunamadi."));
                }
            }

            // ── Shulker ──────────────────────────────────────────────────────
            case "shulker" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1f, 1f);
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_OPEN, 0.8f, 0.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_SHULKER_CLOSE, 0.6f, 1.2f);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
                    t.playSound(t.getLocation(), Sound.ENTITY_SHULKER_HURT, 1f, 0.8f);
                    t.getWorld().spawnParticle(Particle.END_ROD, t.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    t.getWorld().spawnParticle(Particle.PORTAL, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                    t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.05);
                    t.sendTitle(
                        color("&#cc88ff&l☁ LEVITATION!"),
                        color("&#cc88ff3 saniyeligine havaya kaldirildin!"),
                        5, 45, 5
                    );
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#cc88ff&l☁ Shulker Kilici &7— Havaya kaldirildin! &d(3sn)")));
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (t.isOnline()) {
                                t.sendTitle("", color("&#44ff88&l✔ Yere inebilirsin!"), 5, 20, 5);
                                t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                            }
                        }
                    }.runTaskLater(this, 60L);
                });
            }

            // ── Yıldırım ─────────────────────────────────────────────────────
            case "yildirim" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);
                nearby.forEach(t -> {
                    t.getWorld().strikeLightning(t.getLocation());
                    t.sendTitle("", color("&#ffff00&l⚡ Yildirim carpti!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#ffff00&l⚡ Yildirim Kilici &7— Yildirim carpti!")));
                });
            }

            // ── Gardiyan ─────────────────────────────────────────────────────
            case "gardiyan" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 2));
                    t.getWorld().spawnParticle(Particle.NAUTILUS, t.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    t.sendTitle("", color("&#0088ff&l🔱 Gardiyan laneti!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#0088ff&l🔱 Gardiyan Kilici &7— Madencilik yorgunlugu aldin!")));
                });
            }

            // ── Wither ───────────────────────────────────────────────────────
            case "wither" -> {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 1f);
                nearby.forEach(t -> {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    t.getWorld().spawnParticle(Particle.ASH, t.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.05);
                    t.sendTitle("", color("&#999999&lKaranlik Lanet!"), 5, 30, 5);
                    t.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(color("&#999999&l💀 Wither Kilici &7— Wither II etkisi aldin!")));
                });
            }
        }
    }

    /**
     * Oyuncunun baktığı yatay yönde maxDist bloğa kadar güvenli ışınlanma noktası bulur.
     * Hava, örümcek ağı ve odun bloklarından geçer; diğer katı bloklarda durur.
     */
    private Location findSafeTeleportForward(Player p, double maxDist) {
        Location origin = p.getLocation().clone();
        World world = p.getWorld();

        // Sadece yatay yön (Y=0, normalize)
        org.bukkit.util.Vector dir = new org.bukkit.util.Vector(
            Math.sin(-Math.toRadians(origin.getYaw())),
            0,
            Math.cos(-Math.toRadians(origin.getYaw() + 180))
        ).normalize();

        Location lastSafe = null;
        for (double d = 0.4; d <= maxDist; d += 0.4) {
            Location check = origin.clone().add(dir.clone().multiply(d));
            Block feet = world.getBlockAt(check);
            Block head = world.getBlockAt(check.clone().add(0, 1, 0));
            if (isPassable(feet) && isPassable(head)) {
                lastSafe = check.clone();
            } else {
                break;
            }
        }

        if (lastSafe == null) return null;
        lastSafe.setYaw(origin.getYaw());
        lastSafe.setPitch(origin.getPitch());
        return lastSafe;
    }

    /**
     * Blok geçilebilir mi?
     * Hava, örümcek ağı ve odun (log/wood/stripped) türleri geçilebilir sayılır.
     */
    private boolean isPassable(Block block) {
        Material m = block.getType();
        if (m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR) return true;
        if (m == Material.COBWEB) return true;
        // Odun türleri — log + stripped log + wood + stripped wood
        String name = m.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD")
            || name.startsWith("STRIPPED_") && (name.endsWith("_LOG") || name.endsWith("_WOOD"));
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
                new TextComponent(color("&#ff4444⏳ Bekleme suresi: &f" + left + " &csaniye kaldi.")));
            return true;
        }
        return false;
    }

    private void applyCooldown(Player p, String key, int seconds) {
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
            .put(key, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ─── Flop Animasyonu ──────────────────────────────────────────────────────
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
