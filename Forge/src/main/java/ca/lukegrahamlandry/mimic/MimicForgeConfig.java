package ca.lukegrahamlandry.mimic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.List;

public class MimicForgeConfig {
    private static final ForgeConfigSpec.Builder server_builder = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec server_config;

    public static ForgeConfigSpec.IntValue mimicSpawnChance;
    public static ForgeConfigSpec.DoubleValue health, damage, armor, chaseSpeed;
    public static ForgeConfigSpec.IntValue pursuitRange, memoryTicks;
    public static ForgeConfigSpec.BooleanValue eatChests;
    public static ForgeConfigSpec.BooleanValue horrorEnabled, surpriseSpawns, paranoiaSounds;
    public static ForgeConfigSpec.IntValue escalationDays, maxTrackedPlayers, switchTicks;
    public static ForgeConfigSpec.IntValue encounterMinTicks, encounterMaxTicks, loginGraceTicks;
    public static ForgeConfigSpec.IntValue maxWildPerLevel, maxWildTotal, maxNearbyWild;
    public static ForgeConfigSpec.DoubleValue dailyHealth, dailyDamage, dailySpeed, cleaveDamage;
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> healthBarBlacklist;

    static {
        mimicSpawnChance = server_builder
                .comment("When a chest is generated in a supported structure it will have a 1/x chance of being a mimic. Higher value means fewer mimics. A value of 1 means always a mimic.")
                .defineInRange("mimicSpawnChance", 5, 1, Integer.MAX_VALUE);

        healthBarBlacklist = server_builder
                .comment("S: registry names of entities that should not have a displayed ToroHealth bar (ex. [\"minecraft:wither\", \"minecraft:villager\"]).", "Mimics are hard coded and will included while stealthed regardless of the list", "Since this is in the server config, all clients will be effected. Nothing will happen if ToroHealth is not installed")
                .defineList("healthBarBlacklist", Collections.emptyList(), i -> i instanceof String && ResourceLocation.tryParse((String) i) != null);


        health = server_builder.defineInRange("health", 80.0, 1.0, 1024.0);
        damage = server_builder.defineInRange("damage", 18.0, 0.0, 1024.0);
        armor = server_builder.defineInRange("armor", 6.0, 0.0, 30.0);
        chaseSpeed = server_builder.defineInRange("chaseSpeed", 0.85, 0.1, 2.0);
        pursuitRange = server_builder.defineInRange("pursuitRange", 32, 8, 64);
        memoryTicks = server_builder.comment("How long to search the last seen position after losing sight (20 ticks = 1 second).")
                .defineInRange("memoryTicks", 160, 20, 1200);
        eatChests = server_builder.comment("Allow wild mimics to consume chests; mobGriefing must also be enabled.")
                .define("eatChests", true);
        server_builder.push("horror");
        horrorEnabled = server_builder.define("enabled", true);
        surpriseSpawns = server_builder.define("surpriseSpawns", true);
        paranoiaSounds = server_builder.comment("Private positional sounds; respects the player's Hostile Creatures volume.")
                .define("paranoiaSounds", true);
        escalationDays = server_builder.comment("Maximum additional days of escalation; day one uses base stats.")
                .defineInRange("escalationDays", 100, 0, 365);
        dailyHealth = server_builder.defineInRange("dailyHealth", 0.05, 0.0, 0.25);
        dailyDamage = server_builder.defineInRange("dailyDamage", 0.03, 0.0, 0.15);
        dailySpeed = server_builder.defineInRange("dailySpeed", 0.015, 0.0, 0.05);
        cleaveDamage = server_builder.comment("Damage fraction for additional nearby players in the bite's forward cone; zero disables cleaving.")
                .defineInRange("cleaveDamage", 0.35, 0.0, 1.0);
        maxTrackedPlayers = server_builder.defineInRange("maxTrackedPlayers", 4, 1, 16);
        switchTicks = server_builder.comment("Minimum focus duration; switches never interrupt a bite.")
                .defineInRange("switchTicks", 80, 40, 400);
        encounterMinTicks = server_builder.defineInRange("encounterMinTicks", 2400, 400, 72000);
        encounterMaxTicks = server_builder.defineInRange("encounterMaxTicks", 6000, 400, 72000);
        loginGraceTicks = server_builder.comment("Also applies after death or changing dimension; rejoining never resets a later saved cooldown.")
                .defineInRange("loginGraceTicks", 1200, 200, 12000);
        maxWildPerLevel = server_builder.defineInRange("maxWildPerLevel", 12, 1, 128);
        maxWildTotal = server_builder.defineInRange("maxWildTotal", 48, 1, 256);
        maxNearbyWild = server_builder.defineInRange("maxNearbyWild", 3, 1, 12);
        server_builder.pop();
        server_config = server_builder.build();
    }

    public static boolean shouldHaveNoHealthbar(Entity entity){
        if (entity == null) return false;
        EntityType type = entity.getType();
        if (type == null) return false;
        ResourceLocation registryName = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (registryName == null) return false;
        String name = registryName.toString();
        for (String check : healthBarBlacklist.get()){
            if (name.equals(check)) return true;
        }
        return false;
    }

    public static void loadConfig(){
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, server_config);
    }
}
