package com.cavetale.hideandseek;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * One instance per player, provided they are disguised.
 */
public interface Disguise {
    void tick(Player player);

    void disguise(Player player);

    void undisguise(Player player);

    void glow();

    void unglow();

    void onTeleport(Player player, Location from, Location to);
}
