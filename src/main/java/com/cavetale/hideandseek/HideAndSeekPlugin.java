package com.cavetale.hideandseek;

import com.cavetale.afk.AFKPlugin;
import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class HideAndSeekPlugin extends JavaPlugin implements Listener {
    Phase phase = Phase.IDLE;
    int ticks = 0;
    int phaseTicks = 0;
    Set<UUID> hiders = new HashSet<>();
    Set<UUID> seekers = new HashSet<>();
    Random random = new Random();
    Tag tag;
    File tagFile;
    Map<UUID, Enum> disguises = new HashMap<>();
    Map<UUID, Long> itemCooldown = new HashMap<>();
    Set<Entity> distractions = new HashSet<>();

    static final class Tag {
        String worldName;
        Map<UUID, Integer> fairness = new HashMap<>();
        int gameTime = 60 * 5;
        int hideTime = 30;
        int glowTime = 30;
    }

    enum Phase {
        IDLE,
        HIDE,
        SEEK,
        END;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1L, 1L);
        tagFile = new File(getDataFolder(), "save.json");
        loadTag();
    }

    @Override
    public void onDisable() {
        saveTag();
        for (Player player : getServer().getOnlinePlayers()) {
            undisguise(player);
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender,
                             final Command command,
                             final String alias,
                             final String[] args) {
        if (args.length == 0) return false;
        return onCommand(sender, args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    void loadTag() {
        tag = Json.load(tagFile, Tag.class, Tag::new);
    }

    void saveTag() {
        Json.save(tagFile, tag, true);
    }

    boolean onCommand(CommandSender sender, String cmd, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        switch (cmd) {
        case "setworld": {
            tag.worldName = player.getWorld().getName();
            saveTag();
            player.sendMessage("World is now " + tag.worldName);
            return true;
        }
        case "start": {
            if (startGame()) {
                sender.sendMessage("Game started");
            } else {
                sender.sendMessage("Error! See console.");
            }
            return true;
        }
        case "stop": {
            stopGame();
            sender.sendMessage("Game stopped");
            return true;
        }
        case "fair": {
            List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
            sender.sendMessage("" + players.size() + " players");
            for (Player online : players) {
                sender.sendMessage(" " + online.getName() + ": " + getFairness(online));
            }
            return true;
        }
        case "save": {
            saveTag();
            sender.sendMessage("Tag saved");
            return true;
        }
        case "load": {
            loadTag();
            sender.sendMessage("Tag (re)loaded");
            return true;
        }
        case "testdisguise": {
            disguise(player);
            sender.sendMessage("Player disguised");
            return true;
        }
        case "gametime": {
            if (args.length == 1) {
                tag.gameTime = Integer.parseInt(args[0]);
                saveTag();
            }
            sender.sendMessage("Game time = " + tag.gameTime);
            return true;
        }
        case "hidetime": {
            if (args.length == 1) {
                tag.hideTime = Integer.parseInt(args[0]);
                saveTag();
            }
            sender.sendMessage("Hide time = " + tag.hideTime);
            return true;
        }
        case "glowtime": {
            if (args.length == 1) {
                tag.glowTime = Integer.parseInt(args[0]);
                saveTag();
            }
            sender.sendMessage("Glow time = " + tag.glowTime);
            return true;
        }
        default:
            sender.sendMessage("Unknown subcommand: " + cmd);
            return true;
        }
    }

    void stopGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
        }
        disguises.clear();
        for (Player hider : getHiders()) {
            undisguise(hider);
        }
        hiders.clear();
        seekers.clear();
        setPhase(Phase.IDLE);
        for (Entity entity : distractions) {
            entity.remove();
        }
        distractions.clear();
    }

    boolean startGame() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
        }
        List<Player> players = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
            .filter(p -> !AFKPlugin.isAfk(p))
            .collect(Collectors.toList());
        Collections.shuffle(players);
        Collections.sort(players, (a, b) -> Integer.compare(getFairness(b), getFairness(a)));
        int half = players.size() <= 2 ? 1 : players.size() / 2 + 1;
        hiders.clear();
        seekers.clear();
        for (int i = 0; i < players.size(); i += 1) {
            Player player = players.get(i);
            undisguise(player);
            if (i < half) {
                hiders.add(player.getUniqueId());
            } else {
                seekers.add(player.getUniqueId());
            }
            consoleCommand("ml add " + player.getName());
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.125f, 2.0f);
            player.getInventory().clear();
        }
        for (Player hider : getHiders()) {
            addFairness(hider, -2);
            disguise(hider);
            hider.teleport(getHideLocation());
            hider.sendTitle(ChatColor.GREEN + "Hide!",
                            ChatColor.GREEN + "You're a Hider");
            if (disguises.get(hider.getUniqueId()) instanceof EntityType) {
                hider.getInventory().addItem(summonWheat(1));
            }
            if (random.nextBoolean()) {
                hider.getInventory().addItem(copySlime(1));
            } else {
                hider.getInventory().addItem(rerollFoot(3));
                hider.getInventory().addItem(makeInvisItem(1));
            }
        }
        for (Player seeker : getSeekers()) {
            addFairness(seeker, 1);
            seeker.teleport(getSeekLocation());
            seeker.sendTitle(ChatColor.RED + "Wait!", ChatColor.RED + "You're a Seeker");
            seeker.getInventory().addItem(hintEye(3));
            seeker.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 3));
        }
        setPhase(Phase.HIDE);
        return true;
    }

    void disguise(Player player) {
        List<EntityType> animals = Arrays
            .asList(EntityType.COW, EntityType.CHICKEN,
                    EntityType.SHEEP, EntityType.PIG, EntityType.BAT,
                    EntityType.BOAT, EntityType.MINECART,
                    EntityType.GLOW_SQUID, EntityType.BEE,
                    EntityType.CAT, EntityType.WOLF,
                    EntityType.SNOWMAN, EntityType.RABBIT,
                    EntityType.AXOLOTL, EntityType.GOAT);
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
            enume = blocks.get(random.nextInt(blocks.size()));
        } else {
            enume = animals.get(random.nextInt(animals.size()));
        }
        disguise(player, enume);
    }

    void disguise(Player player, Enum enume) {
        player.setMetadata("nostream", new FixedMetadataValue(this, true));
        if (enume instanceof EntityType) {
            disguises.put(player.getUniqueId(), enume);
            EntityType type = (EntityType) enume;
            consoleCommand("disguiseplayer " + player.getName() + " " + type.name().toLowerCase());
            TitlePlugin.getInstance().setPlayerListPrefix(player, Component.text("[" + entityName(type) + "]", NamedTextColor.GREEN));
        } else if (enume instanceof Material) {
            disguises.put(player.getUniqueId(), enume);
            Material material = (Material) enume;
            consoleCommand("disguiseplayer " + player.getName() + " falling_block " + material.name().toLowerCase());
            TitlePlugin.getInstance().setPlayerListPrefix(player, Component.text("[" + blockName(material) + "]", NamedTextColor.GREEN));
        }
    }

    void redisguise(Player player) {
        Enum enume = disguises.get(player.getUniqueId());
        if (enume == null) {
            disguise(player);
        } else {
            disguise(player, enume);
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

    String blockName(Material material) {
        return material.isItem()
            ? new ItemStack(material).getI18NDisplayName()
            : toCamelCase(material);
    }

    String entityName(EntityType type) {
        return toCamelCase(type);
    }

    void undisguise(Player player) {
        player.removeMetadata("nostream", this);
        consoleCommand("undisguiseplayer " + player.getName());
        TitlePlugin.getInstance().setPlayerListPrefix(player, (Component) null);
    }

    List<Player> getHiders() {
        return hiders.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    List<Player> getSeekers() {
        return seekers.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    void setPhase(Phase newPhase) {
        phase = newPhase;
        phaseTicks = 0;
        switch (newPhase) {
        case SEEK:
            for (Player player : getServer().getOnlinePlayers()) {
                player.sendTitle(ChatColor.GREEN + "Seek!", "");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.125f, 2.0f);
            }
            break;
        case END:
            if (hiders.isEmpty()) {
                for (Player player : getServer().getOnlinePlayers()) {
                    player.sendTitle(ChatColor.GREEN + "Seekers win!", "");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.125f, 2.0f);
                }
            } else {
                for (Player hider : getHiders()) {
                    addFairness(hider, 1);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + hider.getName() + " Hider Sneaky");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + hider.getName());
                }
                for (Player player : getServer().getOnlinePlayers()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.125f, 2.0f);
                    player.sendTitle(ChatColor.GREEN + "Hiders win!", "");
                }
            }
            saveTag();
            break;
        default:
            break;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (phase != Phase.IDLE) {
            seekers.remove(event.getPlayer().getUniqueId());
            hiders.remove(event.getPlayer().getUniqueId());
        }
        undisguise(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (phase != Phase.IDLE) {
            seekers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (phase == Phase.IDLE) return;
        if (event.getPlayer().isOp()) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        Player player = event.getPlayer();
        if (isSeeker(player)) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You're not a seeker!");
    }

    boolean consoleCommand(String cmd) {
        getLogger().info("Console command: " + cmd);
        return getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
    }

    void onTick() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
        switch (phase) {
        case HIDE:
            if (getTimeLeft() <= 0) {
                setPhase(Phase.SEEK);
                return;
            }
            break;
        case SEEK:
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
                if (ticks % 20 == 0) {
                    if (getTimeLeft() < tag.glowTime) {
                        hider.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 1, true, false), true);
                    }
                    hider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, true, false), true);
                }
            }
            if (ticks % 10 == 0) {
                for (Player seeker : getSeekers()) {
                    seeker.sendActionBar(getHint(seeker));
                }
            }
            break;
        case END:
            if (getTimeLeft() <= 0) {
                stopGame();
                startGame();
                return;
            }
            break;
        default: return;
        }
        ticks += 1;
        phaseTicks += 1;
    }

    void hint() {
        for (Player hider : getHiders()) {
            hider.getWorld().playSound(hider.getLocation(), Sound.ENTITY_CAT_AMBIENT, SoundCategory.MASTER, 1.0f, 2.0f);
        }
    }

    int getTimeLeft() {
        switch (phase) {
        case HIDE: return tag.hideTime - phaseTicks / 20;
        case SEEK: return tag.gameTime - phaseTicks / 20;
        case END: return 30 - phaseTicks / 20;
        default: return 0;
        }
    }

    String getHint(Player player) {
        Location loc = player.getLocation();
        if (seekers.contains(player.getUniqueId())) {
            double min = Double.MAX_VALUE;
            for (Player hider : getHiders()) {
                if (!hider.getWorld().equals(player.getWorld())) continue;
                min = Math.min(min, hider.getLocation().distanceSquared(loc));
            }
            if (min < 12 * 12) {
                return "" + ChatColor.GOLD + ChatColor.BOLD + "HOT";
            } else if (min < 24 * 24) {
                return "" + ChatColor.GOLD + ChatColor.ITALIC + "Warmer";
            } else if (min < 48 * 48) {
                return "" + ChatColor.YELLOW + ChatColor.ITALIC + "Warm";
            } else {
                return "" + ChatColor.AQUA + ChatColor.ITALIC + "Cold";
            }
        } else {
            return "";
        }
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        Player player = event.getPlayer();
        String identity = "";
        if (seekers.contains(player.getUniqueId())) {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You're a Seeker!";
        } else if (hiders.contains(player.getUniqueId())) {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You're a Hider!";
        } else {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You were found!";
        }
        switch (phase) {
        case IDLE: return;
        case HIDE: {
            int timeLeft = getTimeLeft();
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;
            event.addLines(this, Priority.DEFAULT,
                           identity,
                           ChatColor.DARK_GREEN + "Hiding " + ChatColor.WHITE + String.format("%02d:%02d", minutes, seconds));
            for (Player seeker : getSeekers()) {
                if (seeker.getLocation().distance(getSeekLocation()) > 4.0) {
                    Location ploc = seeker.getLocation();
                    Location to = getSeekLocation().clone();
                    to.setPitch(ploc.getPitch());
                    to.setYaw(ploc.getYaw());
                    seeker.teleport(to);
                    seeker.sendMessage(ChatColor.RED + "You can start seeking in " + getTimeLeft() + " seconds!");
                }
            }
            break;
        }
        case SEEK: {
            int timeLeft = getTimeLeft();
            int minutes = timeLeft / 60;
            int seconds = timeLeft % 60;
            List<String> lines = new ArrayList<>();
            lines.add(identity);
            lines.add(ChatColor.GREEN + "Seeking " + ChatColor.WHITE + String.format("%02d:%02d", minutes, seconds));
            lines.add(ChatColor.GRAY + "Seekers: " + ChatColor.WHITE + seekers.size());
            lines.add(ChatColor.GRAY + "Hiders: " + ChatColor.WHITE + hiders.size());
            if (seekers.contains(player.getUniqueId())) {
                lines.add(ChatColor.GRAY + "Hint: " + getHint(player));
            }
            event.addLines(this, Priority.DEFAULT, lines);
            break;
        }
        case END:
            if (hiders.isEmpty()) {
                event.addLines(this, Priority.DEFAULT,
                               "" + ChatColor.GREEN + ChatColor.BOLD + "Seekers Win!!!");
            } else {
                event.addLines(this, Priority.DEFAULT,
                               "" + ChatColor.GREEN + ChatColor.BOLD + "Hiders Win!!!");
            }
            break;
        default:
            break;
        }
    }

    @EventHandler
    void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
        if (phase != Phase.SEEK) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        Player seeker = (Player) event.getDamager();
        Player hider = (Player) event.getEntity();
        discover(seeker, hider);
    }

    @EventHandler
    void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
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
    void onPlayerInteractBlock(PlayerInteractEvent event) {
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

    boolean discover(Player seeker, Player hider) {
        if (!hiders.contains(hider.getUniqueId())) return false;
        if (!seekers.contains(seeker.getUniqueId())) {
            seeker.sendMessage(ChatColor.RED + "You're not a seeker!");
            seeker.sendActionBar(ChatColor.RED + "You're not a seeker!");
            return false;
        }
        undisguise(hider);
        hiders.remove(hider.getUniqueId());
        seekers.add(hider.getUniqueId());
        addFairness(seeker, 1);
        for (Player target : getServer().getOnlinePlayers()) {
            target.sendMessage(ChatColor.GREEN + seeker.getName() + " discovered " + hider.getName() + "!");
            target.sendTitle("", ChatColor.GREEN + seeker.getName() + " discovered " + hider.getName() + "!");
            target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 2.0f);
        }
        seeker.getInventory().addItem(hintEye(3));
        seeker.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 3));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + seeker.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "titles unlockset " + seeker.getName() + " Seeker Detective");
        return true;
    }

    @EventHandler
    void onArmor(PlayerArmorStandManipulateEvent event) {
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
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
        int val = tag.fairness.computeIfAbsent(uuid, u -> 0);
        int newVal = Math.max(0, val + amount);
        tag.fairness.put(uuid, newVal);
        return val + newVal;
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

    @EventHandler
    void onInventoryOpen(InventoryOpenEvent event) {
        if (!isGameWorld(event.getPlayer().getWorld())) return;
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    void onPlayerInteractItem(PlayerInteractEvent event) {
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
            if (phase != Phase.SEEK) {
                return;
            }
            itemCooldown.put(player.getUniqueId(), then);
            player.sendMessage(ChatColor.GREEN + "All hiders are meowing...");
            player.sendActionBar(ChatColor.GREEN + "All hiders are meowing...");
            hint();
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
                player.sendMessage(ChatColor.GREEN + "Summoning a distraction...");
                player.sendActionBar(ChatColor.GREEN + "Summoning a distraction...");
            } else {
                player.sendMessage(ChatColor.RED + "You're not hiding!");
                player.sendActionBar(ChatColor.RED + "You're not hiding!");
            }
            break;
        }
        case RABBIT_FOOT:
            event.setCancelled(true);
            if (phase != Phase.SEEK && phase != Phase.HIDE) {
                return;
            }
            if (hiders.contains(player.getUniqueId())) {
                itemCooldown.put(player.getUniqueId(), then);
                player.sendMessage(ChatColor.GREEN + "Rerolling disguise...");
                player.sendActionBar(ChatColor.GREEN + "Rerolling disguise...");
                undisguise(player);
                disguise(player);
            } else {
                player.sendMessage(ChatColor.RED + "You're not hiding!");
                player.sendActionBar(ChatColor.RED + "You're not hiding!");
            }
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
                    return;
                default:
                    if (org.bukkit.Tag.BUTTONS.isTagged(material)) return;
                    if (org.bukkit.Tag.SIGNS.isTagged(material)) return;
                    break;
                }
                itemCooldown.put(player.getUniqueId(), then);
                player.sendMessage(ChatColor.GREEN + "Disguising as " + blockName(material));
                player.sendActionBar(ChatColor.GREEN + "Disguising as " + blockName(material));
                undisguise(player);
                disguise(player, material);
            } else {
                player.sendMessage(ChatColor.RED + "You're not hiding!");
                player.sendActionBar(ChatColor.RED + "You're not hiding!");
            }
            break;
        case GLASS:
            event.setCancelled(true);
            if (phase != Phase.SEEK) {
                return;
            }
            itemCooldown.put(player.getUniqueId(), then);
            useInvisItem(player);
            break;
        default: return;
        }
        event.setCancelled(true);
        item.subtract(1);
    }

    @EventHandler
    void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!isGameWorld(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    ItemStack hintEye(int amount) {
        ItemStack item = new ItemStack(Material.ENDER_EYE, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Hint (Meow)");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Make all hiders meow",
                                   ChatColor.GREEN + "Right-Click " + ChatColor.GRAY + " to use"));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack summonWheat(int amount) {
        ItemStack item = new ItemStack(Material.WHEAT, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Summon distraction");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Spawn in a mob near you",
                                   ChatColor.GREEN + "Right-Click " + ChatColor.GRAY + " to use"));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack rerollFoot(int amount) {
        ItemStack item = new ItemStack(Material.RABBIT_FOOT, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Reroll disguise");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Switch to a different random disguise",
                                   ChatColor.GREEN + "Right-Click " + ChatColor.GRAY + " to use"));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack copySlime(int amount) {
        ItemStack item = new ItemStack(Material.SLIME_BALL, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Copy slime");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Disguise as a certain block",
                                   ChatColor.GREEN + "Right-Click " + ChatColor.GRAY + " to use"));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack makeInvisItem(int amount) {
        ItemStack item = new ItemStack(Material.GLASS, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Become invisible");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Become invisible for 10 seconds",
                                   ChatColor.GREEN + "Right-Click " + ChatColor.GRAY + " to use"));
        item.setItemMeta(meta);
        return item;
    }

    void summonDistraction(Player player) {
        Enum enume = disguises.get(player.getUniqueId());
        EntityType type;
        if (enume instanceof EntityType) {
            type = (EntityType) enume;
        } else {
            List<EntityType> types = Arrays
                .asList(EntityType.BEE,
                        EntityType.COW,
                        EntityType.SHEEP,
                        EntityType.PIG,
                        EntityType.BOAT,
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

    void useInvisItem(Player player) {
        if (!hiders.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You're not hiding!");
            return;
        }
        undisguise(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 1, true, false), true);
        getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isValid() && hiders.contains(player.getUniqueId())) {
                    redisguise(player);
                }
            }, 200L);
        player.sendMessage(ChatColor.AQUA + "Invisible for 10 seconds! GOGOGO");
        player.sendActionBar(ChatColor.AQUA + "Invisible for 10 seconds! GOGOGO");
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
}
