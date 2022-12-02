package com.cavetale.hideandseek;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@Getter @RequiredArgsConstructor
public final class GameScheduler {
    private final HideAndSeekPlugin plugin;
    private static final int TOTAL_TICKS = 20 * 60;
    private int ticks = 0;
    private boolean started = false;
    private final Map<UUID, HideWorld> votes = new HashMap<>();

    public void enable() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    protected void tick() {
        if (plugin.phase != Phase.IDLE || plugin.tag.event || Bukkit.getOnlinePlayers().size() < 2) {
            started = false;
            votes.clear();
            return;
        }
        if (!started) {
            started = true;
            ticks = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                remindToVote(player);
            }
        }
        if (ticks > TOTAL_TICKS) {
            final HideWorld hideWorld;
            if (votes.isEmpty()) {
                List<HideWorld> hideWorlds = List.copyOf(plugin.hideWorlds.values());
                hideWorld = hideWorlds.get(plugin.random.nextInt(hideWorlds.size()));
            } else {
                List<HideWorld> hideWorlds = new ArrayList<>();
                for (HideWorld it : votes.values()) {
                    hideWorlds.add(it);
                }
                hideWorld = hideWorlds.get(plugin.random.nextInt(hideWorlds.size()));
            }
            hideWorld.load();
            plugin.tag.worldName = hideWorld.getPath();
            plugin.saveTag();
            plugin.startGame();
        }
        ticks += 1;
    }

    public void onPlayerHud(PlayerHudEvent event) {
        if (!started) return;
        event.bossbar(PlayerHudPriority.DEFAULT,
                      text("Starting Game", DARK_PURPLE),
                      BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS,
                      (float) ticks / (float) TOTAL_TICKS);
    }

    public void vote(Player player, HideWorld hideWorld) {
        votes.put(player.getUniqueId(), hideWorld);
    }

    public void remindToVote(Player player) {
        player.sendMessage(text("\nClick here to vote on the next map\n", GREEN)
                           .hoverEvent(showText(text("Map Selection", GRAY)))
                           .clickEvent(runCommand("/hideandseek vote")));
    }
}
