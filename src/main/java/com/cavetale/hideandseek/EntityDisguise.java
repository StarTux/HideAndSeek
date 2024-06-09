package com.cavetale.hideandseek;

import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

@Data
public final class EntityDisguise implements Disguise {
    private final EntityType entityType;
    private Entity entity;

    @Override
    public void tick(Player player) {
        if (entity != null) {
            entity.teleport(player);
        }
    }

    @Override
    public void disguise(Player player) {
        entity = player.getWorld().spawn(player.getLocation(), entityType.getEntityClass(), e -> {
                e.setPersistent(false);
                e.setSilent(true);
            });
        if (entity instanceof LivingEntity living) {
            living.setInvulnerable(true);
        }
        if (entity instanceof Mob mob) {
            mob.setAware(false);
            mob.setAggressive(false);
            Bukkit.getMobGoals().removeAllGoals(mob);
        }
    }

    @Override
    public void undisguise(Player player) {
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }
}
