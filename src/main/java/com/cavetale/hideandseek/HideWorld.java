package com.cavetale.hideandseek;

import lombok.Data;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.ConfigurationSection;

@Data
public final class HideWorld {
    private String path;
    private String displayName;
    private String description;
    private boolean valid;

    public void load(ConfigurationSection config) {
        this.path = config.getString("path");
        this.displayName = config.getString("displayName", path);
        this.description = config.getString("description", path);
        this.valid = path != null;
    }

    public World load() {
        World result = Bukkit.getWorld(path);
        if (result != null) return result;
        HideAndSeekPlugin.instance.getLogger().info("Loading world " + path);
        WorldCreator creator = WorldCreator.name(path);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.generator("VoidGenerator");
        creator.seed(0L);
        creator.type(WorldType.NORMAL);
        creator.keepSpawnLoaded(TriState.FALSE);
        result = creator.createWorld();
        result.setSpawnFlags(true, true);
        result.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        result.setGameRule(GameRule.DO_INSOMNIA, false);
        result.setGameRule(GameRule.SEND_COMMAND_FEEDBACK, true);
        result.setGameRule(GameRule.FREEZE_DAMAGE, true);
        result.setGameRule(GameRule.LOG_ADMIN_COMMANDS, true);
        result.setGameRule(GameRule.DO_FIRE_TICK, false);
        result.setGameRule(GameRule.DO_MOB_LOOT, false);
        result.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        result.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        result.setGameRule(GameRule.DO_WARDEN_SPAWNING, true);
        result.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        result.setGameRule(GameRule.FALL_DAMAGE, false);
        result.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        result.setGameRule(GameRule.FORGIVE_DEAD_PLAYERS, true);
        result.setGameRule(GameRule.UNIVERSAL_ANGER, false);
        result.setGameRule(GameRule.MAX_ENTITY_CRAMMING, 0);
        result.setGameRule(GameRule.NATURAL_REGENERATION, true);
        result.setGameRule(GameRule.SPAWN_RADIUS, 0);
        result.setGameRule(GameRule.COMMAND_BLOCK_OUTPUT, true);
        result.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        result.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
        result.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        result.setGameRule(GameRule.DROWNING_DAMAGE, true);
        result.setGameRule(GameRule.DO_TILE_DROPS, false);
        result.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        result.setGameRule(GameRule.MOB_GRIEFING, false);
        result.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        result.setGameRule(GameRule.DO_LIMITED_CRAFTING, false);
        result.setGameRule(GameRule.MAX_COMMAND_CHAIN_LENGTH, 1);
        result.setGameRule(GameRule.KEEP_INVENTORY, true);
        result.setGameRule(GameRule.FIRE_DAMAGE, true);
        result.setGameRule(GameRule.DISABLE_RAIDS, true);
        result.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        result.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        result.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        result.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
        result.setDifficulty(Difficulty.EASY);
        result.setKeepSpawnInMemory(false);
        return result;
    }
}
