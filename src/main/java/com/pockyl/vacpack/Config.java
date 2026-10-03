package com.pockyl.vacpack;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config: synced to clients, so the HUD shows the server's slot count.
 * Read values through the accessors РІР‚вЂќ they fall back to defaults while no world is loaded.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue RANGE;
    private static final ModConfigSpec.IntValue CONE_ANGLE;
    private static final ModConfigSpec.DoubleValue PULL_STRENGTH;
    private static final ModConfigSpec.DoubleValue CAPTURE_DISTANCE;
    private static final ModConfigSpec.BooleanValue VACUUM_ITEMS;
    private static final ModConfigSpec.BooleanValue VACUUM_MOBS;
    private static final ModConfigSpec.DoubleValue MAX_MOB_SIZE;
    private static final ModConfigSpec.BooleanValue HARVEST_BERRIES;
    private static final ModConfigSpec.BooleanValue HOLD_MOBS;
    private static final ModConfigSpec.DoubleValue MAX_HOLD_SIZE;

    private static final ModConfigSpec.IntValue SLOT_COUNT;
    private static final ModConfigSpec.IntValue ITEM_CAPACITY;
    private static final ModConfigSpec.IntValue MOB_CAPACITY;

    private static final ModConfigSpec.DoubleValue SHOOT_SPEED;
    private static final ModConfigSpec.IntValue SHOOT_COOLDOWN;
    private static final ModConfigSpec.DoubleValue SHOT_DAMAGE;

    private static final ModConfigSpec.BooleanValue PULSE_ENABLED;
    private static final ModConfigSpec.DoubleValue PULSE_RANGE;
    private static final ModConfigSpec.DoubleValue PULSE_STRENGTH;
    private static final ModConfigSpec.IntValue PULSE_COOLDOWN;

    static {
        BUILDER.translation(key("vacuum")).push("vacuum");
        RANGE = BUILDER.translation(key("range"))
                .comment("How far the vacpack reaches, in blocks.")
                .defineInRange("range", 14.0, 2.0, 32.0);
        CONE_ANGLE = BUILDER.translation(key("cone_angle"))
                .comment("Half-angle of the suction cone, in degrees.")
                .defineInRange("coneAngle", 30, 5, 90);
        PULL_STRENGTH = BUILDER.translation(key("pull_strength"))
                .comment("How hard entities are pulled towards the nozzle each tick.")
                .defineInRange("pullStrength", 0.35, 0.05, 2.0);
        CAPTURE_DISTANCE = BUILDER.translation(key("capture_distance"))
                .comment("Distance from the nozzle at which an entity is sucked into the tank.")
                .defineInRange("captureDistance", 1.4, 0.5, 4.0);
        VACUUM_ITEMS = BUILDER.translation(key("vacuum_items"))
                .comment("Whether dropped items can be vacuumed.")
                .define("vacuumItems", true);
        VACUUM_MOBS = BUILDER.translation(key("vacuum_mobs"))
                .comment("Whether mobs from the #vacpack:vacuumable tag can be vacuumed.")
                .define("vacuumMobs", true);
        MAX_MOB_SIZE = BUILDER.translation(key("max_mob_size"))
                .comment("Mobs wider or taller than this (in blocks) cannot be vacuumed, e.g. big slimes.")
                .defineInRange("maxMobSize", 1.0, 0.1, 4.0);
        HARVEST_BERRIES = BUILDER.translation(key("harvest_berries"))
                .comment("Whether aiming the vacpack at ripe sweet berry bushes and glow berry vines picks their berries.")
                .define("harvestBerries", true);
        HOLD_MOBS = BUILDER.translation(key("hold_mobs"))
                .comment("Whether a mob that cannot go into the tank is held floating in the air stream instead.")
                .define("holdMobs", true);
        MAX_HOLD_SIZE = BUILDER.translation(key("max_hold_size"))
                .comment("Mobs wider or taller than this (in blocks) cannot be held. Bosses can never be held.")
                .defineInRange("maxHoldSize", 2.1, 0.1, 8.0);
        BUILDER.pop();

        BUILDER.translation(key("tank")).push("tank");
        SLOT_COUNT = BUILDER.translation(key("slot_count"))
                .comment("Number of tank slots. Each slot holds one kind of item or mob.")
                .defineInRange("slotCount", 4, 1, 9);
        ITEM_CAPACITY = BUILDER.translation(key("item_capacity"))
                .comment("How many items fit into one slot.")
                .defineInRange("itemCapacity", 64, 1, 9999);
        MOB_CAPACITY = BUILDER.translation(key("mob_capacity"))
                .comment("How many mobs fit into one slot.")
                .defineInRange("mobCapacity", 10, 1, 64);
        BUILDER.pop();

        BUILDER.translation(key("shooting")).push("shooting");
        SHOOT_SPEED = BUILDER.translation(key("shoot_speed"))
                .comment("Launch speed of shot items and mobs.")
                .defineInRange("shootSpeed", 1.4, 0.2, 5.0);
        SHOOT_COOLDOWN = BUILDER.translation(key("shoot_cooldown"))
                .comment("Ticks between shots while the attack button is held.")
                .defineInRange("shootCooldown", 4, 1, 40);
        SHOT_DAMAGE = BUILDER.translation(key("shot_damage"))
                .comment("Damage dealt by a shot item hitting a mob. 0 only knocks back.")
                .defineInRange("shotDamage", 2.0, 0.0, 20.0);
        BUILDER.pop();

        BUILDER.translation(key("pulse")).push("pulseWave");
        PULSE_ENABLED = BUILDER.translation(key("pulse_enabled"))
                .comment("Shooting with an empty slot releases a pulse of air that knocks everything in front away.")
                .define("enabled", true);
        PULSE_RANGE = BUILDER.translation(key("pulse_range"))
                .comment("Reach of the pulse wave, in blocks.")
                .defineInRange("range", 6.0, 1.0, 16.0);
        PULSE_STRENGTH = BUILDER.translation(key("pulse_strength"))
                .comment("Knockback strength of the pulse wave.")
                .defineInRange("strength", 1.6, 0.1, 5.0);
        PULSE_COOLDOWN = BUILDER.translation(key("pulse_cooldown"))
                .comment("Ticks between pulse waves.")
                .defineInRange("cooldown", 16, 1, 200);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    private static String key(String name) {
        return Vacpack.MOD_ID + ".configuration." + name;
    }

    private static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double range() {
        return get(RANGE);
    }

    public static int coneAngle() {
        return get(CONE_ANGLE);
    }

    public static double pullStrength() {
        return get(PULL_STRENGTH);
    }

    public static double captureDistance() {
        return get(CAPTURE_DISTANCE);
    }

    public static boolean vacuumItems() {
        return get(VACUUM_ITEMS);
    }

    public static boolean vacuumMobs() {
        return get(VACUUM_MOBS);
    }

    public static double maxMobSize() {
        return get(MAX_MOB_SIZE);
    }

    public static boolean harvestBerries() {
        return get(HARVEST_BERRIES);
    }

    public static boolean holdMobs() {
        return get(HOLD_MOBS);
    }

    public static double maxHoldSize() {
        return get(MAX_HOLD_SIZE);
    }

    public static int slotCount() {
        return get(SLOT_COUNT);
    }

    public static int itemCapacity() {
        return get(ITEM_CAPACITY);
    }

    public static int mobCapacity() {
        return get(MOB_CAPACITY);
    }

    public static double shootSpeed() {
        return get(SHOOT_SPEED);
    }

    public static int shootCooldown() {
        return get(SHOOT_COOLDOWN);
    }

    public static double shotDamage() {
        return get(SHOT_DAMAGE);
    }

    public static boolean pulseEnabled() {
        return get(PULSE_ENABLED);
    }

    public static double pulseRange() {
        return get(PULSE_RANGE);
    }

    public static double pulseStrength() {
        return get(PULSE_STRENGTH);
    }

    public static int pulseCooldown() {
        return get(PULSE_COOLDOWN);
    }
}
