package com.cavetale.hideandseek;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
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
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HideAndSeekPlugin extends JavaPlugin implements Listener {
    Phase phase = Phase.IDLE;
    int ticks = 0;
    int phaseTicks = 0;
    World world;
    Location hideLocation;
    Location seekLocation;
    Set<UUID> hiders = new HashSet<>();
    Set<UUID> seekers = new HashSet<>();
    Random random = new Random();
    Tag tag;
    File tagFile;
    int hintCooldown;

    static final class Tag {
        Map<UUID, Integer> fairness = new HashMap<>();
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
        case "sethide": {
            hideLocation = player.getLocation();
            world = hideLocation.getWorld();
            player.sendMessage("Hider start location set!");
            return true;
        }
        case "setseek": {
            seekLocation = player.getLocation();
            world = seekLocation.getWorld();
            player.sendMessage("Seeker start location set!");
            return true;
        }
        case "start": {
            if (startGame()) {
                sender.sendMessage("OK");
            } else {
                sender.sendMessage("Error! See console.");
            }
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
        default:
            sender.sendMessage("Unknown subcommand: " + cmd);
            return true;
        }
    }

    boolean startGame() {
        if (world == null) {
            reloadConfig();
            String worldName = getConfig().getString("world");
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("Cannot start: World not found: " + worldName);
                return false;
            }
            getLogger().info("Using world: " + world.getName());
            hideLocation = world.getSpawnLocation();
            seekLocation = hideLocation;
        }
        List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
        Collections.shuffle(players);
        Collections.sort(players, (a, b) -> Integer.compare(getFairness(b), getFairness(a)));
        int half = players.size() / 2;
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
        }
        for (Player hider : getHiders()) {
            addFairness(hider, -1);
            disguise(hider);
            hider.teleport(hideLocation);
            hider.sendTitle(ChatColor.GREEN + "Hide!",
                            ChatColor.GREEN + "You're a Hider");
        }
        for (Player seeker : getSeekers()) {
            addFairness(seeker, 1);
            seeker.teleport(seekLocation);
            seeker.sendTitle(ChatColor.RED + "Wait!",
                            ChatColor.RED + "You're a Seeker");
        }
        setPhase(Phase.HIDE);
        hintCooldown = 100;
        return true;
    }

    void disguise(Player player) {
        @SuppressWarnings("checkstyle:LineLength")
        List<String> animals = Arrays.asList("cow", "chicken", "sheep", "creeper", "zombie", "enderman", "skeleton", "pig", "llama", "minecart", "squid", "guardian", "spider", "slime", "witch", "wandering_trader", "cave_spider", "mule", "donkey", "strider", "bee", "piglin", "blaze", "husk", "drowned", "wither_skeleton", "hoglin", "pillager", "magma_cube", "stray", "armor_stand", "cat", "wolf");
        String animal = animals.get(random.nextInt(animals.size()));
        consoleCommand("disguiseplayer " + player.getName() + " " + animal);
        player.setPlayerListName(ChatColor.GREEN + "[Hiding]" + player.getName());
    }

    void undisguise(Player player) {
        consoleCommand("undisguiseplayer " + player.getName());
        player.setPlayerListName(null);
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
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
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
        // if (phase != Phase.IDLE) {
        //seekers.add(event.getPlayer().getUniqueId());
        // }
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
                setPhase(Phase.END);
                return;
            }
            if (hiders.isEmpty()) {
                setPhase(Phase.END);
                return;
            }
            for (Player hider : getHiders()) {
                if (ticks % 20 == 0) {
                    if (getTimeLeft() < 15) {
                        hider.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 1, true, false), true);
                    } else {
                        hider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1, true, false), true);
                    }
                }
            }
            if (hintCooldown > 0) {
                hintCooldown -= 1;
            } else {
                hintCooldown = 20 * 60;
                for (Player hider : getHiders()) {
                    hider.getWorld().playSound(hider.getLocation(), Sound.ENTITY_CAT_AMBIENT, SoundCategory.MASTER, 1.0f, 1.0f);
                }
            }
            break;
        case END:
            if (getTimeLeft() <= 0) {
                for (Player hider : getHiders()) {
                    consoleCommand("undisguiseplayer " + hider.getName());
                }
                setPhase(Phase.IDLE);
                return;
            }
            break;
        default: return;
        }
        ticks += 1;
        phaseTicks += 1;
    }

    int getTimeLeft() {
        switch (phase) {
        case HIDE: return 30 - phaseTicks / 20;
        case SEEK: return 5 * 60 - phaseTicks / 20;
        case END: return 30 - phaseTicks / 20;
        default: return 0;
        }
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        String identity = "";
        if (seekers.contains(event.getPlayer().getUniqueId())) {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You're a Seeker!";
        } else if (hiders.contains(event.getPlayer().getUniqueId())) {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You're a Hider!";
        } else {
            identity = "" + ChatColor.GOLD + ChatColor.BOLD + "You were found!";
        }
        switch (phase) {
        case IDLE:
            event.addLines(this, Priority.DEFAULT, "Please Wait...");
            break;
        case HIDE:
            event.addLines(this, Priority.DEFAULT,
                           identity,
                           ChatColor.DARK_GREEN + "Hiding...",
                           ChatColor.WHITE + "  " + getTimeLeft());
            for (Player seeker : getSeekers()) {
                if (seeker.getLocation().distance(seekLocation) > 4.0) {
                    Location ploc = seeker.getLocation();
                    Location to = seekLocation.clone();
                    to.setPitch(ploc.getPitch());
                    to.setYaw(ploc.getYaw());
                    seeker.teleport(to);
                    seeker.sendMessage(ChatColor.RED + "You can start seeking in " + getTimeLeft() + " seconds!");
                }
            }
            break;
        case SEEK:
            event.addLines(this, Priority.DEFAULT,
                           identity,
                           ChatColor.GREEN + "Seeking...",
                           ChatColor.WHITE + "  " + getTimeLeft(),
                           ChatColor.GRAY + "Hint in: " + ChatColor.WHITE + (hintCooldown / 20),
                           ChatColor.GRAY + "Seekers: " + ChatColor.WHITE + seekers.size(),
                           ChatColor.GRAY + "Hiders: " + ChatColor.WHITE + hiders.size());
            break;
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
        if (!hiders.contains(hider.getUniqueId())) return;
        if (!seekers.contains(seeker.getUniqueId())) return;
        undisguise(hider);
        hiders.remove(hider.getUniqueId());
        addFairness(seeker, 1);
        for (Player target : getServer().getOnlinePlayers()) {
            target.sendMessage(ChatColor.GREEN + seeker.getName() + " discovered " + hider.getName() + "!");
        }
    }

    @EventHandler
    void onArmor(PlayerArmorStandManipulateEvent event) {
        if (event.getPlayer().isOp()) return;
        event.setCancelled(true);
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
}
