package com.cavetale.hideandseek;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.mytems.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

public final class HideAndSeekCommand extends AbstractCommand<HideAndSeekPlugin> {
    protected HideAndSeekCommand(final HideAndSeekPlugin plugin) {
        super(plugin, "hideandseek");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("vote").arguments("[path]")
            .completers(CommandArgCompleter.supplyList(() -> List.copyOf(plugin.hideWorlds.keySet())))
            .description("Vote on a map")
            .hidden(true)
            .playerCaller(this::vote);
    }

    private boolean vote(Player player, String[] args) {
        if (args.length == 0) {
            if (!plugin.gameScheduler.isStarted()) throw new CommandWarn("The vote is over");
            List<HideWorld> hideWorlds = new ArrayList<>();
            hideWorlds.addAll(plugin.hideWorlds.values());
            Collections.sort(hideWorlds, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getDisplayName(), b.getDisplayName()));
            List<Component> lines = new ArrayList<>();
            for (HideWorld hideWorld : hideWorlds) {
                List<Component> tooltip = new ArrayList<>();
                Component displayName = text(hideWorld.getDisplayName(), BLUE);
                tooltip.add(displayName);
                tooltip.addAll(Text.wrapLore(hideWorld.getDescription(), c -> c.color(GRAY)));
                lines.add(displayName
                          .hoverEvent(showText(join(separator(newline()), tooltip)))
                          .clickEvent(runCommand("/hideandseek vote " + hideWorld.getPath())));
            }
            bookLines(player, lines);
            return true;
        } else if (args.length == 1) {
            if (!plugin.gameScheduler.isStarted()) throw new CommandWarn("The vote is over");
            HideWorld hideWorld = plugin.hideWorlds.get(args[0]);
            if (hideWorld == null) throw new CommandWarn("Map not found!");
            plugin.gameScheduler.vote(player, hideWorld);
            player.sendMessage(text("You voted for " + hideWorld.getDisplayName(), GREEN));
            return true;
        } else {
            return false;
        }
    }

    private static List<Component> toPages(List<Component> lines) {
        final int lineCount = lines.size();
        final int linesPerPage = 14;
        List<Component> pages = new ArrayList<>((lineCount - 1) / linesPerPage + 1);
        for (int i = 0; i < lineCount; i += linesPerPage) {
            List<Component> subLines = lines.subList(i, Math.min(lines.size(), i + linesPerPage));
            pages.add(join(separator(newline()), subLines));
        }
        return pages;
    }

    private static void bookLines(Player player, List<Component> lines) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(m -> {
                if (m instanceof BookMeta meta) {
                    meta.author(text("Cavetale"));
                    meta.title(text("Title"));
                    meta.pages(toPages(lines));
                }
            });
        player.openBook(book);
    }
}
