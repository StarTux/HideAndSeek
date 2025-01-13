package com.cavetale.hideandseek;

import lombok.Data;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

@Data
public final class BlockDisguise implements Disguise {
    private final BlockData blockData;
    private BlockDisplay blockDisplay;

    private static final Vector3f VECTOR_3F_ONE = new Vector3f(1f, 1f, 1f);
    private static final Vector3f VECTOR_3F_CENTER = new Vector3f(-0.5f, 0f, -0.5f);
    private static final AxisAngle4f AXIS_ANGLE_3F_ZERO = new AxisAngle4f(0f, 0f, 0f, 0f);
    private static final Transformation TRANSFORMATION = new Transformation(VECTOR_3F_CENTER,
                                                                            AXIS_ANGLE_3F_ZERO,
                                                                            VECTOR_3F_ONE,
                                                                            AXIS_ANGLE_3F_ZERO);

    @Override
    public void tick(Player player) {
        if (blockDisplay != null) {
            if (!blockDisplay.isValid()) {
                undisguise(player);
                disguise(player);
            } else {
                blockDisplay.teleport(fixLocation(player.getLocation()));
            }
        }
    }

    @Override
    public void disguise(Player player) {
        blockDisplay = player.getWorld().spawn(fixLocation(player.getLocation()), BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBlock(blockData);
                e.setTransformation(TRANSFORMATION);
            });
    }

    @Override
    public void undisguise(Player player) {
        if (blockDisplay != null) {
            blockDisplay.remove();
            blockDisplay = null;
        }
    }

    private static Location fixLocation(Location in) {
        in.setYaw(0f);
        in.setPitch(0f);
        return in;
    }

    @Override
    public void glow() {
        if (blockDisplay != null) {
            blockDisplay.setGlowing(true);
        }
    }

    @Override
    public void unglow() {
        if (blockDisplay != null) {
            blockDisplay.setGlowing(false);
        }
    }

    @Override
    public void onTeleport(Player player, Location from, Location to) {
        if (blockDisplay != null) {
            // Will be respawned on next tick as long as it is not
            // null
            blockDisplay.remove();
        }
    }
}
