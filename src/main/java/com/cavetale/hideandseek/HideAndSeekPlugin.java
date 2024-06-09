package com.cavetale.hideandseek;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.event.minigame.MinigameFlag;
import com.cavetale.core.event.minigame.MinigameMatchCompleteEvent;
import com.cavetale.core.event.minigame.MinigameMatchType;
import com.cavetale.core.event.player.PlayerTPAEvent;
import com.cavetale.core.font.VanillaItems;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.money.Money;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.mobface.MobFace;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import com.destroystokyo.paper.MaterialTags;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.winthier.spawn.Spawn;
import com.winthier.title.TitlePlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import static com.cavetale.core.font.Unicode.subscript;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class HideAndSeekPlugin extends JavaPlugin implements Listener {
    protected static HideAndSeekPlugin instance;
    protected Phase phase = Phase.IDLE;
    protected int ticks = 0;
    protected int phaseTicks = 0;
    protected int bonusTicks = 0;
    protected Set<UUID> hiders = new HashSet<>();
    protected Set<UUID> seekers = new HashSet<>();
    protected Random random = new Random();
    protected Tag tag;
    protected File tagFile;
    protected Map<UUID, Disguise> disguiseMap = new HashMap<>();
    protected Map<UUID, Long> itemCooldown = new HashMap<>();
    protected Map<UUID, Component> hiderPrefixMap = new HashMap<>();
    protected Set<Entity> distractions = new HashSet<>();
    protected boolean teleporting;
    protected List<Highscore> highscore = List.of();
    protected List<Component> highscoreLines = List.of();
    protected final GameScheduler gameScheduler = new GameScheduler(this);
    protected final Map<String, HideWorld> hideWorlds = new HashMap<>();

    public static final Component TITLE = join(noSeparators(),
                                               Mytems.EYES.component,
                                               text("Hide and Seek", LIGHT_PURPLE));

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1L, 1L);
        tagFile = new File(getDataFolder(), "save.json");
        loadTag();
        new HideAndSeekCommand(this).enable();
        new HideAndSeekAdminCommand(this).enable();
        gameScheduler.enable();
        loadHideWorlds();
    }

    @Override
    public void onDisable() {
        stopGame();
        saveTag();
    }

    protected void loadTag() {
        tag = Json.load(tagFile, Tag.class, Tag::new);
        computeHighscore();
    }

    protected void saveTag() {
        Json.save(tagFile, tag, true);
    }

    protected void loadHideWorlds() {
        hideWorlds.clear();
        for (var it : getConfig().getMapList("worlds")) {
            ConfigurationSection section = getConfig().createSection("_tmp", it);
            HideWorld hideWorld = new HideWorld();
            hideWorld.load(section);
            if (!hideWorld.isValid()) {
                getLogger().severe("Invalid hide world: " + hideWorld + ", " + it);
                continue;
            }
            hideWorlds.put(hideWorld.getPath(), hideWorld);
        }
        getLogger().info("" + hideWorlds.size() + " world configurations loaded");
    }

    protected void stopGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            undisguise(player);
        }
        disguiseMap.clear();
        hiders.clear();
        seekers.clear();
        setPhase(Phase.IDLE);
        for (Entity entity : distractions) {
            entity.remove();
        }
        distractions.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Spawn.warp(player);
        }
    }

    protected boolean startGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            undisguise(player);
            player.setHealth(20.0);
            player.getInventory().clear();
            for (var potionEffect : player.getActivePotionEffects()) {
                player.removePotionEffect(potionEffect.getType());
            }
            player.teleport(getWorld().getSpawnLocation());
        }
        List<Player> players = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .collect(Collectors.toList());
        Collections.shuffle(players);
        Collections.sort(players, (a, b) -> Integer.compare(getFairness(b), getFairness(a)));
        int half = Math.max(1, (players.size() * 3) / 4);
        hiders.clear();
        hiderPrefixMap.clear();
        seekers.clear();
        if (tag.event) {
            List<String> names = new ArrayList<>();
            for (Player player : players) names.add(player.getName());
            consoleCommand("ml add " + String.join(" ", names));
        }
        for (int i = 0; i < players.size(); i += 1) {
            Player player = players.get(i);
            if (i < half) {
                hiders.add(player.getUniqueId());
            } else {
                seekers.add(player.getUniqueId());
            }
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.125f, 2.0f);
            player.getInventory().clear();
        }
        for (Player hider : getHiders()) {
            addFairness(hider, -2);
            disguise(hider);
            hider.teleport(getHideLocation());
            hider.showTitle(Title.title(text("Hide!", GREEN),
                                        text("You're a Hider", GREEN)));
            if (disguiseMap.get(hider.getUniqueId()) instanceof EntityDisguise) {
                hider.getInventory().addItem(summonWheat(1));
            }
            if (random.nextBoolean()) {
                hider.getInventory().addItem(copySlime(1));
            } else {
                hider.getInventory().addItem(rerollFoot(3));
                hider.getInventory().addItem(makeInvisItem(1));
            }
            ItemStack potion = new ItemStack(Material.POTION);
            potion.editMeta(m -> {
                    if (m instanceof PotionMeta meta) {
                        meta.addCustomEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 12, 1, false, true, true), true);
                        meta.setColor(Color.PURPLE);
                    }
                    m.displayName(text("Potion of Levitation", WHITE).decoration(ITALIC, false));
                });
            hider.getInventory().addItem(potion);
        }
        for (Player seeker : getSeekers()) {
            addFairness(seeker, 1);
            seeker.teleport(getSeekLocation());
            seeker.showTitle(Title.title(text("Wait!", RED),
                                         text("You're a Seeker", RED)));
            giveSeekerItems(seeker);
        }
        setPhase(Phase.HIDE);
        return true;
    }

    protected void giveSeekerItems(Player seeker) {
        seeker.getInventory().clear();
        seeker.getEquipment().setHelmet(Mytems.EASTER_HELMET.createItemStack());
        seeker.getEquipment().setChestplate(Mytems.EASTER_CHESTPLATE.createItemStack());
        seeker.getEquipment().setLeggings(Mytems.EASTER_LEGGINGS.createItemStack());
        seeker.getEquipment().setBoots(Mytems.EASTER_BOOTS.createItemStack());
        seeker.getInventory().addItem(makeCompass(1));
        seeker.getInventory().addItem(hintEye(1));
        seeker.getInventory().addItem(new ItemStack(Material.ENDER_PEARL));
        seeker.getInventory().addItem(new ItemStack(Material.SPYGLASS));
        ItemStack potion = new ItemStack(Material.POTION);
        potion.editMeta(m -> {
                if (m instanceof PotionMeta meta) {
                    meta.setBasePotionType(PotionType.LONG_NIGHT_VISION);
                }
            });
        seeker.getInventory().addItem(potion);
        potion = new ItemStack(Material.POTION);
        potion.editMeta(m -> {
                if (m instanceof PotionMeta meta) {
                    meta.setBasePotionType(PotionType.SLOW_FALLING);
                }
            });
        seeker.getInventory().addItem(potion);
        potion = new ItemStack(Material.POTION);
        potion.editMeta(m -> {
                if (m instanceof PotionMeta meta) {
                    meta.setBasePotionType(PotionType.LONG_SWIFTNESS);
                }
            });
        seeker.getInventory().addItem(potion);
        potion = new ItemStack(Material.POTION);
        potion.editMeta(m -> {
                if (m instanceof PotionMeta meta) {
                    meta.addCustomEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20 * 30, 0, false, true, true), true);
                    meta.setColor(Color.BLUE);
                }
                m.displayName(text("Potion of Dolphin's Grace", WHITE).decoration(ITALIC, false));
            });
        seeker.getInventory().addItem(potion);
    }

    protected void disguise(Player player) {
        List<EntityType> animals = Arrays
            .asList(EntityType.COW, EntityType.CHICKEN,
                    EntityType.SHEEP, EntityType.PIG, EntityType.BAT,
                    EntityType.GLOW_SQUID, EntityType.BEE,
                    EntityType.CAT, EntityType.WOLF,
                    EntityType.SNOW_GOLEM, EntityType.RABBIT,
                    EntityType.AXOLOTL, EntityType.GOAT,
                    EntityType.ARMADILLO);
        List<Material> blocks = Arrays
            .asList(Material.GRASS_BLOCK,
                    Material.DIRT,
                    Material.BRICKS,
                    Material.STONE,
                    Material.STONE_BRICKS,
                    Material.GOLD_BLOCK,
                    Material.IRON_BLOCK,
                    Material.DIAMOND_BLOCK,
                    Material.HAY_BLOCK,
                    Material.GLOWSTONE,
                    Material.OAK_LOG,
                    Material.BIRCH_LOG,
                    Material.OAK_LEAVES,
                    Material.SPRUCE_LEAVES,
                    Material.DARK_OAK_LEAVES,
                    Material.COBBLESTONE,
                    Material.GOLD_ORE,
                    Material.DIAMOND_ORE,
                    Material.IRON_ORE,
                    Material.SAND,
                    Material.GRAVEL,
                    Material.SNOW_BLOCK,
                    Material.FARMLAND,
                    Material.SPRUCE_LOG,
                    Material.COARSE_DIRT,
                    Material.BOOKSHELF
                    );
        Enum enume;
        if (random.nextBoolean()) {
            final Material material = blocks.get(random.nextInt(blocks.size()));
            disguise(player, material.createBlockData());
        } else {
            final EntityType entityType = animals.get(random.nextInt(animals.size()));
            disguise(player, entityType);
        }
    }

    public void disguise(Player player, BlockData blockData) {
        undisguise(player);
        VanillaItems vi = VanillaItems.of(blockData.getMaterial());
        final Component prefix;
        if (vi != null) {
            prefix = textOfChildren(text("[", GREEN), vi, text("]", GREEN));
        } else {
            prefix = text("[" + blockName(blockData.getMaterial()) + "]", GREEN);
        }
        hiderPrefixMap.put(player.getUniqueId(), prefix);
        final BlockDisguise disguise = new BlockDisguise(blockData);
        disguiseMap.put(player.getUniqueId(), disguise);
        disguise.disguise(player);
        hidePlayer(player);
    }

    public void disguise(Player player, EntityType entityType) {
        undisguise(player);
        MobFace mobFace = MobFace.of(entityType);
        Component prefix = mobFace != null
            ? textOfChildren(text("["), mobFace.mytems, text("]")).color(GREEN)
            : text("[" + entityName(entityType) + "]", GREEN);
        hiderPrefixMap.put(player.getUniqueId(), prefix);
        final EntityDisguise disguise = new EntityDisguise(entityType);
        disguiseMap.put(player.getUniqueId(), disguise);
        disguise.disguise(player);
        hidePlayer(player);
    }

    public void redisguise(Player player) {
        final Disguise disguise = disguiseMap.remove(player.getUniqueId());
        if (disguise == null) {
            disguise(player);
        } else {
            disguise.undisguise(player);
            disguise.disguise(player);
            hidePlayer(player);
        }
    }

    public boolean undisguise(Player player) {
        final Disguise disguise = disguiseMap.remove(player.getUniqueId());
        if (disguise == null) return false;
        disguise.undisguise(player);
        unhidePlayer(player);
        TitlePlugin.getInstance().setPlayerListPrefix(player, (Component) null);
        return true;
    }

    private void hidePlayer(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) continue;
            other.hidePlayer(this, player);
        }
    }

    private void unhidePlayer(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) continue;
            other.showPlayer(this, player);
        }
    }

    public static String toCamelCase(String in) {
        return in.substring(0, 1).toUpperCase()
            + in.substring(1).toLowerCase();
    }

    public static String toCamelCase(String[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i += 1) {
            out[i] = toCamelCase(in[i]);
        }
        return String.join(" ", out);
    }

    public static String toCamelCase(Enum en) {
        return toCamelCase(en.name().split("_"));
    }

    private String blockName(Material material) {
        return material.isItem()
            ? ItemKinds.name(new ItemStack(material))
            : toCamelCase(material);
    }

    private String entityName(EntityType type) {
        return toCamelCase(type);
    }

    private List<Player> getHiders() {
        return hiders.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<Player> getSeekers() {
        return seekers.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private void setPhase(Phase newPhase) {
        phase = newPhase;
        phaseTicks = 0;
        bonusTicks = 0;
        switch (newPhase) {
        case SEEK:
            for (Player player : getServer().getOnlinePlayers()) {
                player.showTitle(Title.title(text("Seek!", GREEN),
                                             empty()));
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.125f, 2.0f);
                // Find hiders that didn't move
                if (hiders.contains(player.getUniqueId())) {
                    Location location = player.getLocation();
                    Location location2 = getHideLocation();
                    if (!location.getWorld().equals(location2.getWorld())
                        || location.distanceSquared(location2) < 9.0) {
                        hiders.remove(player.getUniqueId());
                        undisguise(player);
                        hiders.remove(player.getUniqueId());
                        seekers.add(player.getUniqueId());
                        giveSeekerItems(player);
                        tag.fairness.remove(player.getUniqueId());
                    }
                }
            }
            break;
        case END:
            MinigameMatchCompleteEvent event = new MinigameMatchCompleteEvent(MinigameMatchType.HIDE_AND_SEEK);
            event.addPlayers(getHiders());
            event.addPlayers(getSeekers());
            if (tag.event) event.addFlags(MinigameFlag.EVENT);
            if (hiders.isEmpty()) {
                for (Player player : getServer().getOnlinePlayers()) {
                    player.showTitle(Title.title(text("Seekers win!", GREEN),
                                                 empty()));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.125f, 2.0f);
                }
                event.addWinners(getSeekers());
            } else {
                List<String> names = new ArrayList<>();
                for (Player hider : getHiders()) {
                    addFairness(hider, 5);
                    names.add(hider.getName());
                    if (tag.event) {
                        addScore(hider.getUniqueId(), 3);
                        consoleCommand("titles unlockset " + hider.getName() + " Hider Sneaky");
                        Money.get().give(hider.getUniqueId(), 1000.0, this, "Hide and Seek");
                    }
                }
                if (tag.event) computeHighscore();
                for (Player player : getServer().getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.125f, 2.0f);
                    player.showTitle(Title.title(text("Hiders win!", GREEN),
                                                 empty()));
                    player.sendMessage(textOfChildren(text("Hiders win: ", GREEN),
                                                      text(String.join(" ", names), WHITE)));
                }
                event.addWinners(getHiders());
            }
            saveTag();
            event.callEvent();
            break;
        default:
            break;
        }
    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        if (phase != Phase.IDLE) {
            seekers.remove(event.getPlayer().getUniqueId());
            hiders.remove(event.getPlayer().getUniqueId());
        }
        undisguise(event.getPlayer());
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == player) continue;
            if (disguiseMap.get(online.getUniqueId()) != null) {
                player.hidePlayer(online);
            }
        }
        if (phase != Phase.IDLE) {
            seekers.add(player.getUniqueId());
            giveSeekerItems(player);
        } else if (gameScheduler.isStarted()) {
            gameScheduler.remindToVote(event.getPlayer());
        }
    }

    @EventHandler
    private void onPlayerTeleport(PlayerTeleportEvent event) {
        if (teleporting) return;
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (phase == Phase.IDLE) return;
        if (event.getPlayer().isOp()) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        if (isSeeker(player)) return;
        event.setCancelled(true);
        Component message = text("You're not a seeker!", RED);
        player.sendMessage(message);
        player.sendActionBar(message);
    }

    private boolean consoleCommand(String cmd) {
        getLogger().info("Console command: " + cmd);
        return getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
    }

    private void onTick() {
        for (Player player : getServer().getOnlinePlayers()) {
            final Disguise disguise = disguiseMap.get(player.getUniqueId());
            if (disguise != null) {
                disguise.tick(player);
            }
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            if (isSeeker(player)) {
                player.setRemainingAir(player.getMaximumAir());
                player.setFireTicks(0);
            }
        }
        switch (phase) {
        case HIDE: {
            if (getTimeLeft() <= 0) {
                setPhase(Phase.SEEK);
                return;
            }
            Location anchor = getSeekLocation();
            for (Player seeker : getSeekers()) {
                Location ploc = seeker.getLocation();
                if (!ploc.getWorld().equals(anchor.getWorld()) || ploc.distance(anchor) > 8.0) {
                    Location to = anchor.clone();
                    to.setPitch(ploc.getPitch());
                    to.setYaw(ploc.getYaw());
                    seeker.teleport(to);
                    Component message = text("You can start seeking in " + getTimeLeft() + " seconds!",
                                             RED);
                    seeker.sendMessage(message);
                    seeker.sendActionBar(message);
                }
            }
            break;
        }
        case SEEK:
            if (bonusTicks > 0) {
                bonusTicks -= 1;
            }
            if (getTimeLeft() <= 0) {
                for (Player hider : getHiders()) {
                    undisguise(hider);
                }
                setPhase(Phase.END);
                return;
            }
            if (hiders.isEmpty()) {
                for (Player hider : getHiders()) {
                    undisguise(hider);
                }
                setPhase(Phase.END);
                return;
            }
            for (Player hider : getHiders()) {
                if (hider.getLocation().getBlock().isLiquid() || hider.isSwimming()) {
                    hider.setHealth(Math.max(0.0, hider.getHealth() - 0.25));
                }
                if (ticks % 20 == 0) {
                    if (getTimeLeft() < tag.glowTime) {
                        hider.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 1, true, false));
                    }
                    hider.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, true, false));
                }
            }
            for (Player seeker : getSeekers()) {
                seeker.sendActionBar(getHint(seeker));
                updateCompassTarget(seeker);
            }
            break;
        case END:
            if (getTimeLeft() <= 0) {
                stopGame();
                return;
            }
            break;
        default: return;
        }
        ticks += 1;
        phaseTicks += 1;
    }

    private void hint() {
        for (Player hider : getHiders()) {
            hider.getWorld().playSound(hider.getLocation(), Sound.ENTITY_CAT_AMBIENT, SoundCategory.MASTER, 1.0f, 2.0f);
        }
    }

    private int getTimeLeft() {
        switch (phase) {
        case HIDE: return tag.hideTime - phaseTicks / 20;
        case SEEK: return Math.max(tag.gameTime - phaseTicks / 20, bonusTicks / 20);
        case END: return 30 - phaseTicks / 20;
        default: return 0;
        }
    }

    protected Component getHint(Player player) {
        Location loc = player.getLocation();
        double min = Double.MAX_VALUE;
        for (Player hider : getHiders()) {
            if (!hider.getWorld().equals(player.getWorld())) continue;
            if (hider.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
            min = Math.min(min, hider.getLocation().distanceSquared(loc));
        }
        if (min < 12 * 12) {
            return text("HOT", (ticks % 4 == 2 ? GOLD : AQUA), BOLD);
        } else if (min < 24 * 24) {
            return text("Warmer", GOLD);
        } else if (min < 48 * 48) {
            return text("Warm", YELLOW);
        } else {
            return text("Cold", AQUA);
        }
    }

    protected void updateCompassTarget(Player seeker) {
        double min = Double.MAX_VALUE;
        Location loc = seeker.getLocation();
        Location target = null;
        for (Player hider : getHiders()) {
            if (!hider.getWorld().equals(seeker.getWorld())) continue;
            Location hiderLoc = hider.getLocation();
            double dist = hiderLoc.distanceSquared(loc);
            if (dist < min) {
                min = dist;
                target = hiderLoc;
            }
        }
        final int minDistance = 32;
        if (target == null || min < (minDistance * minDistance)) {
            // Spin in circles
            double fraction = ((double) (ticks % 50)) / 50.0;
            double dx = 32.0 * Math.cos(fraction * Math.PI * 2.0);
            double dz = 32.0 * Math.sin(fraction * Math.PI * 2.0);
            seeker.setCompassTarget(loc.add(dx, 0.0, dz));
            return;
        }
        Location targetLoc = new Location(seeker.getWorld(),
                                          (double) (((target.getBlockX() / minDistance) * minDistance) + (minDistance / 2)),
                                          loc.getBlockY(),
                                          (double) (((target.getBlockZ() / minDistance) * minDistance) + (minDistance / 2)));
        seeker.setCompassTarget(targetLoc);
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        gameScheduler.onPlayerHud(event);
        List<Component> lines = new ArrayList<>();
        lines.add(TITLE);
        Player player = event.getPlayer();
        Component identity = empty();
        if (seekers.contains(player.getUniqueId())) {
            identity = text("You're a Seeker!", GOLD);
        } else if (hiders.contains(player.getUniqueId())) {
            identity = text("You're a Hider!", LIGHT_PURPLE);
        } else {
            identity = text("You were found!", GRAY);
        }
        switch (phase) {
        case IDLE: break;
        case HIDE: {
            int timeLeft = getTimeLeft();
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;
            lines.add(identity);
            lines.add(textOfChildren(text(subscript("hiding "), LIGHT_PURPLE), text(String.format("%02d:%02d", minutes, seconds), WHITE)));
            event.bossbar(PlayerHudPriority.HIGH, text("Hiding", LIGHT_PURPLE),
                          BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS,
                          (float) phaseTicks / (float) (tag.hideTime * 20));
            break;
        }
        case SEEK: {
            int timeLeft = getTimeLeft();
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;
            lines.add(identity);
            if (seekers.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
                lines.add(textOfChildren(text(subscript("hint "), GRAY), getHint(player)));
            }
            lines.add(textOfChildren(text(subscript("seeking "), GRAY), text(String.format("%02d:%02d", minutes, seconds), WHITE)));
            lines.add(textOfChildren(text(subscript("hiders "), GRAY), text(hiders.size(), WHITE)));
            List<Player> hiderList = getHiders();
            Collections.sort(hiderList, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (Player hider : hiderList) {
                Component prefix = hiderPrefixMap.get(hider.getUniqueId());
                if (prefix == null) prefix = Mytems.BLIND_EYE.component;
                lines.add(textOfChildren(prefix, space(), text(hider.getName(), WHITE)));
            }
            lines.add(textOfChildren(text(subscript("seekers "), GRAY), text(seekers.size(), WHITE)));
            final int ticksLeft = Math.max(tag.gameTime * 20 - phaseTicks, bonusTicks);
            final int totalTicks = Math.max(bonusTicks, (tag.gameTime * 20));
            event.bossbar(PlayerHudPriority.HIGH, text("Seeking", LIGHT_PURPLE),
                          BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS,
                          (float) ticksLeft / (float) totalTicks);
            break;
        }
        case END:
            if (hiders.isEmpty()) {
                lines.add(text("Seekers Win!!!", GOLD));
            } else {
                lines.add(text("Hiders Win!!!", LIGHT_PURPLE));
            }
            break;
        default:
            break;
        }
        if (tag.event) {
            lines.addAll(highscoreLines);
        }
        if (lines.isEmpty()) return;
        event.sidebar(PlayerHudPriority.HIGHEST, lines);
    }

    @EventHandler
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
        if (phase != Phase.SEEK) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player seeker = (Player) event.getDamager();
        Player hider = (Player) event.getEntity();
        discover(seeker, hider);
    }

    @EventHandler
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(true);
        if (phase != Phase.SEEK) return;
        if (!(event.getRightClicked() instanceof Player)) return;
        Player seeker = event.getPlayer();
        if (!seekers.contains(seeker.getUniqueId())) return;
        Player hider = (Player) event.getRightClicked();
        if (!hiders.contains(hider.getUniqueId())) {
            Entity target = seeker.getTargetEntity(4);
            if (target instanceof Player) hider = (Player) target;
        }
        discover(seeker, hider);
    }

    @EventHandler
    private void onPlayerInteractBlock(PlayerInteractEvent event) {
        switch (event.getAction()) {
        case LEFT_CLICK_AIR:
        case LEFT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
        case RIGHT_CLICK_BLOCK:
            break;
        default:
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (phase != Phase.SEEK) return;
        Player seeker = event.getPlayer();
        if (!seekers.contains(seeker.getUniqueId())) return;
        if (!seeker.getWorld().getName().equals(tag.worldName)) return;
        Entity target = seeker.getTargetEntity(4);
        if (target instanceof Player) {
            discover(seeker, (Player) target);
            return;
        }
        Location loc = seeker.getEyeLocation();
        Vector vec = loc.getDirection().normalize().multiply(0.25);
        for (int i = 0; i < 8; i += 1) {
            loc = loc.add(vec);
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.25, 0.25, 0.25)) {
                if (!(entity instanceof Player)) continue;
                if (discover(seeker, (Player) entity)) return;
            }
        }
    }

    protected boolean discover(Player seeker, Player hider) {
        if (!hiders.contains(hider.getUniqueId())) return false;
        if (!seekers.contains(seeker.getUniqueId())) {
            Component message = text("You're not a seeker!", RED);
            seeker.sendMessage(message);
            seeker.sendActionBar(message);
            return false;
        }
        undisguise(hider);
        hiders.remove(hider.getUniqueId());
        seekers.add(hider.getUniqueId());
        giveSeekerItems(hider);
        addFairness(seeker, 1);
        if (tag.event) {
            addScore(seeker.getUniqueId(), 1);
            computeHighscore();
            Money.get().give(seeker.getUniqueId(), 100.0, this, "Hide and Seek");
        }
        bonusTicks = Math.max(bonusTicks, tag.bonusTime * 20);
        Component message = text(seeker.getName() + " discovered " + hider.getName() + "!",
                                 GREEN);
        for (Player target : getServer().getOnlinePlayers()) {
            target.sendMessage(message);
            target.showTitle(Title.title(empty(),
                                         message));
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 2.0f);
        }
        if (tag.event) {
            consoleCommand("ml add " + seeker.getName());
            consoleCommand("titles unlockset " + seeker.getName() + " Seeker Detective");
        }
        return true;
    }

    @EventHandler
    private void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof BlockInventoryHolder || event.getInventory().getHolder() instanceof Entity) {
            if (!isGameWorld(event.getPlayer().getWorld())) return;
            if (event.getPlayer().isOp()) return;
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onPlayerLaunchProjectile(PlayerLaunchProjectileEvent event) {
        Player player = event.getPlayer();
        if (!isGameWorld(player.getWorld())) return;
        if (event.getItemStack().getType() != Material.ENDER_PEARL) return;
        if (!isSeeker(player) || phase != Phase.SEEK) {
            event.setCancelled(true);
        }
        event.setShouldConsume(false);
        if (player.getCooldown(Material.ENDER_PEARL) > 0) {
            event.setCancelled(true);
        }
        Bukkit.getScheduler().runTask(this, () -> player.setCooldown(Material.ENDER_PEARL, 20 * 10));
    }

    @EventHandler
    private void onPlayerInteractItem(PlayerInteractEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.hasBlock()) {
            switch (event.getClickedBlock().getType()) {
            case FARMLAND:
            case FLOWER_POT:
                event.setCancelled(true);
            default: break;
            }
        }
        switch (event.getAction()) {
        case RIGHT_CLICK_BLOCK:
        case RIGHT_CLICK_AIR:
            break;
        default:
            return;
        }
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(tag.worldName)) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.hasItem()) return;
        ItemStack item = event.getItem();
        Block block = event.hasBlock() ? event.getClickedBlock() : null;
        if (item == null) return;
        Long cooldown = itemCooldown.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        long then = now + 1000;
        if (cooldown != null && cooldown > now) return;
        switch (item.getType()) {
        case ENDER_EYE: {
            event.setCancelled(true);
            if (phase != Phase.SEEK) {
                return;
            }
            if (player.getCooldown(Material.ENDER_EYE) > 0) {
                event.setCancelled(true);
                return;
            }
            itemCooldown.put(player.getUniqueId(), then);
            Component message = text("All hiders are meowing...", GREEN);
            player.sendMessage(message);
            player.sendActionBar(message);
            hint();
            Bukkit.getScheduler().runTask(this, () -> player.setCooldown(Material.ENDER_EYE, 20 * 10));
            break;
        }
        case WHEAT: {
            event.setCancelled(true);
            if (phase != Phase.SEEK) {
                return;
            }
            if (hiders.contains(player.getUniqueId())) {
                summonDistraction(player);
                itemCooldown.put(player.getUniqueId(), then);
                Component message = text("Summoning a distraction...", GREEN);
                player.sendMessage(message);
                player.sendActionBar(message);
            } else {
                Component message = text("You're not hiding!", RED);
                player.sendMessage(message);
                player.sendActionBar(message);
            }
            item.subtract(1);
            break;
        }
        case RABBIT_FOOT:
            event.setCancelled(true);
            if (phase != Phase.SEEK && phase != Phase.HIDE) {
                return;
            }
            if (hiders.contains(player.getUniqueId())) {
                itemCooldown.put(player.getUniqueId(), then);
                Component message = text("Rerolling disguise...", GREEN);
                player.sendMessage(message);
                player.sendActionBar(message);
                undisguise(player);
                disguise(player);
            } else {
                Component message = text("You're not hiding!", GREEN);
                player.sendMessage(message);
                player.sendActionBar(message);
            }
            item.subtract(1);
            break;
        case SLIME_BALL:
            event.setCancelled(true);
            if (phase != Phase.SEEK && phase != Phase.HIDE) {
                return;
            }
            if (hiders.contains(player.getUniqueId())) {
                if (block == null) {
                    return;
                }
                if (block.isEmpty() || block.isLiquid()) {
                    return;
                }
                Material material = block.getType();
                switch (material) {
                case BARRIER:
                case CHEST:
                case TRAPPED_CHEST:
                case ENDER_CHEST:
                case LADDER:
                case VINE:
                case PLAYER_HEAD:
                    return;
                default:
                    if (org.bukkit.Tag.BUTTONS.isTagged(material)) return;
                    if (org.bukkit.Tag.SIGNS.isTagged(material)) return;
                    if (MaterialTags.SKULLS.isTagged(material)) return;
                    if (block.getBlockData() instanceof Bisected) return;
                    if (org.bukkit.Tag.BANNERS.isTagged(material)) return;
                    break;
                }
                itemCooldown.put(player.getUniqueId(), then);
                Component message = text("Disguising as " + blockName(material), GREEN);
                player.sendMessage(message);
                player.sendActionBar(message);
                undisguise(player);
                disguise(player, block.getBlockData());
            } else {
                Component message = text("You're not hiding!", RED);
                player.sendMessage(message);
                player.sendActionBar(message);
            }
            item.subtract(1);
            break;
        case GLASS:
            event.setCancelled(true);
            if (phase != Phase.SEEK) {
                return;
            }
            itemCooldown.put(player.getUniqueId(), then);
            useInvisItem(player);
            item.subtract(1);
            break;
        default: return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!isGameWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (!isGameWorld(event.getEntity().getWorld())) return;
        if (event.getRemover() instanceof Player && ((Player) event.getRemover()).isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!isGameWorld(event.getEntity().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!isGameWorld(player.getWorld())) return;
        double minDist = Double.MAX_VALUE;
        Player target = null;
        Location ploc = player.getLocation();
        for (Player seeker : getSeekers()) {
            if (!seeker.getWorld().equals(ploc.getWorld())) continue;
            double dist = seeker.getLocation().distanceSquared(ploc);
            if (target == null || dist < minDist) {
                target = seeker;
                minDist = dist;
            }
        }
        if (target != null) {
            event.setRespawnLocation(target.getLocation());
        } else {
            event.setRespawnLocation(getWorld().getSpawnLocation());
        }
        if (isHider(player)) {
            Bukkit.getScheduler().runTask(this, () -> redisguise(player));
        }
    }

    @EventHandler
    private void onEntityDamage(EntityDamageEvent event) {
        if (phase != Phase.HIDE && phase != Phase.SEEK) return;
        if (!isGameWorld(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Player player)) return;
        event.setCancelled(true);
        switch (event.getCause()) {
        case VOID:
            Bukkit.getScheduler().runTask(this, () -> {
                    teleporting = true;
                    player.teleport(getHideLocation());
                    teleporting = false;
                });
            break;
        case FALL:
        case DROWNING:
            break;
        case FIRE:
        case LAVA:
            if (!hiders.contains(player.getUniqueId())) return;
            Bukkit.getScheduler().runTask(this, () -> {
                    player.setFireTicks(0);
                    player.sendMessage(text("Burning returns you to spawn!",
                                            RED));
                    teleporting = true;
                    player.teleport(getHideLocation());
                    teleporting = false;
                });
            break;
        default:
            break;
        }
    }

    @EventHandler
    private void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isGameWorld(player.getWorld())) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        player.sendMessage(text("Item dropping not allowed!", RED));
        event.setCancelled(true);
    }


    @EventHandler
    private void onPlayerTPA(PlayerTPAEvent event) {
        Player player = event.getTarget();
        if (!isGameWorld(player.getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler
    private void onArmor(PlayerArmorStandManipulateEvent event) {
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        if (phase == Phase.IDLE) {
            event.setSpawnLocation(Spawn.get());
        } else {
            event.setSpawnLocation(getWorld().getSpawnLocation());
        }
    }

    public boolean isGameWorld(World world) {
        return world.getName().equals(tag.worldName);
    }

    public int getFairness(Player player) {
        Integer val = tag.fairness.get(player.getUniqueId());
        return val != null ? val : 0;
    }

    public int addFairness(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int val = tag.fairness.getOrDefault(uuid, 0);
        int newVal = Math.max(0, val + amount);
        tag.fairness.put(uuid, newVal);
        return val + newVal;
    }

    public int getScore(UUID uuid) {
        return tag.scores.getOrDefault(uuid, 0);
    }

    public void addScore(UUID uuid, int value) {
        tag.scores.put(uuid, Math.max(0, getScore(uuid) + value));
    }

    public World getWorld() {
        return Bukkit.getWorld(tag.worldName);
    }

    public Location getHideLocation() {
        return getWorld().getSpawnLocation();
    }

    public Location getSeekLocation() {
        return getWorld().getSpawnLocation();
    }

    public boolean isSeeker(Player player) {
        return seekers.contains(player.getUniqueId());
    }

    public boolean isHider(Player player) {
        return hiders.contains(player.getUniqueId());
    }

    protected ItemStack hintEye(int amount) {
        return Items.text(new ItemStack(Material.ENDER_EYE, amount),
                          text("Hint (Meow)", LIGHT_PURPLE),
                          text("Make all hiders meow", GRAY),
                          textOfChildren(Mytems.MOUSE_RIGHT, text(" to use", GRAY)));
    }

    protected ItemStack summonWheat(int amount) {
        return Items.text(new ItemStack(Material.WHEAT, amount),
                          text("Summon distraction", LIGHT_PURPLE),
                          text("Spawn in a mob near you"),
                          textOfChildren(Mytems.MOUSE_RIGHT, text(" to use", GRAY)));
    }

    protected ItemStack rerollFoot(int amount) {
        return Items.text(new ItemStack(Material.RABBIT_FOOT, amount),
                          text("Reroll disguise", LIGHT_PURPLE),
                          text("Switch to a different", GRAY),
                          text("random disguise", GRAY),
                          textOfChildren(Mytems.MOUSE_RIGHT, text(" to use", GRAY)));
    }

    protected ItemStack copySlime(int amount) {
        return Items.text(new ItemStack(Material.SLIME_BALL, amount),
                          text("Copy slime", GREEN),
                          text("Disguise as a", GRAY),
                          text("certain block", GRAY),
                          textOfChildren(Mytems.MOUSE_RIGHT, text(" a block to use", GRAY)));
    }

    protected ItemStack makeInvisItem(int amount) {
        return Items.text(new ItemStack(Material.GLASS, amount),
                          text("Become Invisible", LIGHT_PURPLE),
                          text("Become invisible", GRAY),
                          text("for 10 seconds", GRAY),
                          textOfChildren(Mytems.MOUSE_RIGHT, text(" to vanish", GRAY)));
    }

    protected ItemStack makeCompass(int amount) {
        return Items.text(new ItemStack(Material.COMPASS, amount),
                          List.of(text("Hider Finder", GOLD),
                                  text("Points to the nearest", GRAY),
                                  text("hider but goes wild", GRAY),
                                  text("when you're too close", GRAY)));
    }

    protected void summonDistraction(Player player) {
        final Disguise disguise = disguiseMap.get(player.getUniqueId());
        if (disguise == null) return;
        EntityType type;
        if (disguise instanceof EntityDisguise entityDisguise) {
            type = entityDisguise.getEntityType();
        } else {
            List<EntityType> types = Arrays
                .asList(EntityType.BEE,
                        EntityType.COW,
                        EntityType.SHEEP,
                        EntityType.PIG,
                        EntityType.CAT,
                        EntityType.WOLF);
            type = types.get(random.nextInt(types.size()));
        }
        Entity entity = player.getWorld().spawn(player.getLocation(), type.getEntityClass(), e -> {
                e.setPersistent(false);
                if (e instanceof LivingEntity) {
                    ((LivingEntity) e).setRemoveWhenFarAway(true);
                    ((LivingEntity) e).setSilent(true);
                }
            });
        distractions.add(entity);
    }

    private void useInvisItem(Player player) {
        if (!hiders.contains(player.getUniqueId())) {
            Component message = text("You're not hiding!", RED);
            player.sendMessage(message);
            player.sendActionBar(message);
            return;
        }
        undisguise(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 20, 1, true, false));
        getServer().getScheduler().runTaskLater(this, () -> {
                if (phase == Phase.SEEK && player.isValid() && hiders.contains(player.getUniqueId())) {
                    redisguise(player);
                }
            }, 200L);
        Component message = text("Invisible for 10 seconds! GOGOGO", AQUA);
        player.sendMessage(message);
        player.sendActionBar(message);
    }

    protected void computeHighscore() {
        highscore = Highscore.of(tag.scores);
        highscoreLines = Highscore.sidebar(highscore);
    }

    protected int rewardHighscore() {
        return Highscore.reward(tag.scores,
                                "hide_and_seek",
                                TrophyCategory.HIDE_AND_SEEK,
                                TITLE,
                                hi -> "You collected " + hi.score + " point" + (hi.score == 1 ? "" : "s"));
    }
}
