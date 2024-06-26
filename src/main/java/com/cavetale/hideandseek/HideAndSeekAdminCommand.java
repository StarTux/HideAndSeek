package com.cavetale.hideandseek;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class HideAndSeekAdminCommand extends AbstractCommand<HideAndSeekPlugin> {
    protected HideAndSeekAdminCommand(final HideAndSeekPlugin plugin) {
        super(plugin, "hideandseekadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("setworld").denyTabCompletion()
            .description("Select current world")
            .playerCaller(this::setWorld);
        rootNode.addChild("start").denyTabCompletion()
            .description("Start a game")
            .senderCaller(this::start);
        rootNode.addChild("stop").denyTabCompletion()
            .description("Stop the game")
            .senderCaller(this::stop);
        rootNode.addChild("fair").denyTabCompletion()
            .description("List player fairness")
            .senderCaller(this::fair);
        rootNode.addChild("testdisguise").denyTabCompletion()
            .description("Test disguise")
            .playerCaller(this::testDisguise);
        rootNode.addChild("undisguise").arguments("<player>")
            .completers(CommandArgCompleter.NULL)
            .playerCaller(this::undisguise);
        // Disguise
        final CommandNode disguiseNode = rootNode.addChild("disguise")
            .description("Disguise a player");
        disguiseNode.addChild("block").arguments("<player> <block>")
            .description("Disguise a player as a block")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.enumLowerList(Material.class))
            .senderCaller(this::disguiseBlock);
        disguiseNode.addChild("entity").arguments("<player> <entity>")
            .description("Disguise a player as an entity")
            .completers(CommandArgCompleter.NULL,
                        CommandArgCompleter.enumLowerList(EntityType.class))
            .senderCaller(this::disguiseEntity);
        // Score
        CommandNode scoreNode = rootNode.addChild("score")
            .description("Score commands");
        scoreNode.addChild("reset").denyTabCompletion()
            .description("Reset scores")
            .senderCaller(this::scoreReset);
        scoreNode.addChild("add").arguments("<player> <amount>")
            .description("Manipulate scores")
            .completers(CommandArgCompleter.PLAYER_CACHE,
                        CommandArgCompleter.INTEGER)
            .senderCaller(this::scoreAdd);
        scoreNode.addChild("reward").denyTabCompletion()
            .description("Reward highscores")
            .senderCaller(this::scoreReward);
        // Config
        CommandNode configNode = rootNode.addChild("config")
            .description("Config commands");
        configNode.addChild("save").denyTabCompletion()
            .description("Save the configuration")
            .senderCaller(this::configSave);
        configNode.addChild("load").denyTabCompletion()
            .description("Reload the configuration")
            .senderCaller(this::configLoad);
        configNode.addChild("gametime").arguments("<seconds>")
            .description("Set game time")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::configGameTime);
        configNode.addChild("hidetime").arguments("<seconds>")
            .description("Set hide time")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::configHideTime);
        configNode.addChild("glowtime").arguments("<seconds>")
            .description("Set glow time")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::configGlowTime);
        configNode.addChild("bonustime").arguments("<seconds>")
            .description("Set bonus time")
            .completers(CommandArgCompleter.integer(i -> i > 0))
            .senderCaller(this::configBonusTime);
        configNode.addChild("event").arguments("<boolean>")
            .description("Set event")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::configEvent);
        configNode.addChild("pause").arguments("<boolean>")
            .description("Set pause")
            .completers(CommandArgCompleter.BOOLEAN)
            .senderCaller(this::configPause);
    }

    private void setWorld(Player player) {
        plugin.tag.worldName = player.getWorld().getName();
        plugin.saveTag();
        player.sendMessage(text("World is now " + plugin.tag.worldName, AQUA));
    }

    private void start(CommandSender sender) {
        if (!plugin.startGame()) {
            throw new CommandWarn("Error! See console");
        }
        sender.sendMessage(text("Game started", AQUA));
    }

    private void stop(CommandSender sender) {
        plugin.stopGame();
        sender.sendMessage(text("Game stopped", AQUA));
    }

    private void fair(CommandSender sender) {
        List<Player> players = new ArrayList<>(Bukkit.getServer().getOnlinePlayers());
        sender.sendMessage(text("" + players.size() + " players", AQUA));
        for (Player online : players) {
            sender.sendMessage(join(noSeparators(),
                                    text(plugin.getFairness(online) + " ", YELLOW),
                                    text(" " + online.getName())));
        }
    }

    private void testDisguise(Player player) {
        plugin.disguise(player);
        player.sendMessage(text("Player disguised", YELLOW));
    }

    private boolean undisguise(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final Player target = CommandArgCompleter.requirePlayer(args[0]);
        if (!plugin.undisguise(target)) {
            throw new CommandWarn(target.getName() + " was not disguised");
        }
        sender.sendMessage(text("Undisguised " + sender.getName(), YELLOW));
        return true;
    }
    private boolean disguiseBlock(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        final String blockArg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        final Player target = CommandArgCompleter.requirePlayer(args[0]);
        final BlockData blockData;
        try {
            blockData = Bukkit.createBlockData(blockArg);
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("BlockData expected: " + blockArg);
        }
        plugin.disguise(target, blockData);
        sender.sendMessage(text("Disguised " + sender.getName() + " as " + blockData.getAsString(false), YELLOW));
        return true;
    }

    private boolean disguiseEntity(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        final Player target = CommandArgCompleter.requirePlayer(args[0]);
        final EntityType entityType = CommandArgCompleter.requireEnum(EntityType.class, args[1]);
        plugin.disguise(target, entityType);
        sender.sendMessage(text("Disguised " + sender.getName() + " as " + entityType, YELLOW));
        return true;
    }

    private void scoreReset(CommandSender sender) {
        plugin.tag.scores.clear();
        plugin.computeHighscore();
        sender.sendMessage(text("Scores reset", AQUA));
    }

    private boolean scoreAdd(CommandSender sender, String[] args) {
        PlayerCache target = CommandArgCompleter.requirePlayerCache(args[0]);
        int value = CommandArgCompleter.requireInt(args[1], i -> i != 0);
        plugin.addScore(target.uuid, value);
        plugin.computeHighscore();
        sender.sendMessage(text("Score of " + target.name + " adjusted by " + value, AQUA));
        return true;
    }

    private void scoreReward(CommandSender sender) {
        int res = plugin.rewardHighscore();
        sender.sendMessage(text(res + " players rewarded", AQUA));
    }

    private void configSave(CommandSender sender) {
        plugin.saveTag();
        sender.sendMessage("Tag saved");
    }

    private void configLoad(CommandSender sender) {
        plugin.loadTag();
        plugin.loadHideWorlds();
        sender.sendMessage(text("Tag and config (re)loaded", AQUA));
    }

    private boolean configGameTime(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        plugin.tag.gameTime = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.saveTag();
        sender.sendMessage(text("Game time = " + plugin.tag.gameTime, AQUA));
        return true;
    }

    private boolean configHideTime(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        plugin.tag.hideTime = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.saveTag();
        sender.sendMessage(text("Hide time = " + plugin.tag.hideTime, AQUA));
        return true;
    }

    private boolean configGlowTime(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        plugin.tag.glowTime = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.saveTag();
        sender.sendMessage(text("Glow time = " + plugin.tag.glowTime, AQUA));
        return true;
    }

    private boolean configBonusTime(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        plugin.tag.bonusTime = CommandArgCompleter.requireInt(args[0], i -> i > 0);
        plugin.saveTag();
        sender.sendMessage(text("Bonus time = " + plugin.tag.bonusTime, AQUA));
        return true;
    }

    private boolean configEvent(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.tag.event = CommandArgCompleter.requireBoolean(args[0]);
            plugin.saveTag();
        }
        sender.sendMessage(text("Event mode = " + plugin.tag.event, AQUA));
        return true;
    }

    private boolean configPause(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        if (args.length >= 1) {
            plugin.tag.pause = CommandArgCompleter.requireBoolean(args[0]);
            plugin.saveTag();
        }
        sender.sendMessage(text("Pause mode = " + plugin.tag.pause, AQUA));
        return true;
    }
}
