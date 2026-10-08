package com.pockyl.vacpack.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.tank.VacTank;
import com.pockyl.vacpack.vacuum.VacuumHandler;
import com.pockyl.vacpack.vacuum.VacuumState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}.
 * Tests use the 1x1x1 {@code empty} structure unless they need a prepared one.
 */
public final class ModGameTests {
    private static final int MAX_TICKS = 100;
    /**
     * Tests run at random spots far from spawn, and only the chunk of a test's 1x1x1 structure is force-loaded when it is
     * placed. The tests act a few blocks around it, so they wait until the neighbouring chunks have loaded as well;
     * otherwise mobs and items spawned across a chunk border can be invisible to entity queries.
     */
    private static final int SETUP_TICKS = 20;
    private static final Identifier STRUCTURE = Vacpack.id("empty");
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Vacpack.MOD_ID);
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("mod_loads", ModGameTests::modLoads);
        TESTS.put("vacpack_recipe_is_loaded", ModGameTests::vacpackRecipeIsLoaded);
        TESTS.put("tank_merges_and_overflows_items", ModGameTests::tankMergesAndOverflowsItems);
        TESTS.put("tank_shoots_last_captured_mob_first", ModGameTests::tankShootsLastCapturedMobFirst);
        TESTS.put("vacuum_captures_item_at_nozzle", ModGameTests::vacuumCapturesItemAtNozzle);
        TESTS.put("vacuum_takes_babies_and_cats_but_not_adult_cows", ModGameTests::vacuumTakesBabiesAndCatsButNotAdultCows);
        TESTS.put("vacuum_takes_only_small_slimes", ModGameTests::vacuumTakesOnlySmallSlimes);
        TESTS.put("shot_slime_flies_as_projectile_and_lands_alive", ModGameTests::shotSlimeFliesAsProjectileAndLandsAlive);
        TESTS.put("ragdoll_bounces_rolls_and_settles", ModGameTests::ragdollBouncesRollsAndSettles);
        TESTS.put("released_mob_keeps_momentum_and_is_protected", ModGameTests::releasedMobKeepsMomentumAndIsProtected);
        TESTS.put("big_slime_is_held_in_air_stream_and_launched", ModGameTests::bigSlimeIsHeldInAirStreamAndLaunched);
        TESTS.put("suction_pulls_distant_item_towards_nozzle", ModGameTests::suctionPullsDistantItemTowardsNozzle);
        TESTS.put("empty_slot_shot_releases_pulse_wave", ModGameTests::emptySlotShotReleasesPulseWave);
        TESTS.put("pulse_at_the_floor_bursts_like_a_wind_charge", ModGameTests::pulseAtTheFloorBurstsLikeAWindCharge);
        TESTS.put("vacuum_picks_ripe_sweet_berries", ModGameTests::vacuumPicksRipeSweetBerries);
        TESTS.put("shot_item_hits_mob", ModGameTests::shotItemHitsMob);
        TESTS.put("creative_vacpack_takes_big_mobs_and_bottomless_stacks", ModGameTests::creativeVacpackTakesBigMobsAndBottomlessStacks);
        TESTS.put("creative_vacpack_shoots_every_tick", ModGameTests::creativeVacpackShootsEveryTick);
    }

    private ModGameTests() {
    }

    /** Registers the tests when the game test framework is enabled (dev runs and the game test server). */
    public static void register(IEventBus modBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Vacpack.id("default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, STRUCTURE, MAX_TICKS, SETUP_TICKS, true);
        for (String name : TESTS.keySet()) {
            Identifier id = Vacpack.id(name);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    public static void vacpackRecipeIsLoaded(GameTestHelper helper) {
        var recipe = helper.getLevel().recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, Vacpack.id("vacpack")));
        helper.assertTrue(recipe.isPresent(), "the vacpack crafting recipe is loaded");
        Recipe<?> value = recipe.get().value();
        helper.assertTrue(value instanceof ShapedRecipe shaped && shaped.assemble(CraftingInput.EMPTY).is(ModItems.VACPACK.get()),
                "the recipe crafts a vacpack");
        helper.succeed();
    }

    public static void tankMergesAndOverflowsItems(GameTestHelper helper) {
        VacTank.Insertion first = VacTank.EMPTY.insertItem(new ItemStack(Items.COBBLESTONE, 15), 2, 10);
        helper.assertTrue(first.inserted() == 15, "all cobblestone fits into two slots");
        helper.assertTrue(first.tank().slot(0).count() == 10 && first.tank().slot(1).count() == 5, "first slot fills before the second");

        VacTank.Insertion second = first.tank().insertItem(new ItemStack(Items.DIRT, 3), 2, 10);
        helper.assertTrue(second.inserted() == 0, "a full tank rejects a new item type");

        VacTank.Insertion third = first.tank().insertItem(new ItemStack(Items.COBBLESTONE, 9), 2, 10);
        helper.assertTrue(third.inserted() == 5, "only the free capacity is filled");
        helper.succeed();
    }

    public static void tankShootsLastCapturedMobFirst(GameTestHelper helper) {
        CompoundTag first = mobTag("first");
        CompoundTag second = mobTag("second");
        VacTank tank = VacTank.EMPTY.insertMob(EntityTypes.CHICKEN, first, 4, 10).insertMob(EntityTypes.CHICKEN, second, 4, 10);
        helper.assertTrue(tank.slot(0).amount() == 2 && tank.slot(1).isEmpty(), "same mob type shares a slot");

        VacTank.Taken taken = tank.takeFromSelected();
        helper.assertTrue(second.equals(taken.mob()), "the last captured mob comes out first");
        helper.assertTrue(taken.tank().slot(0).amount() == 1, "one mob stays in the tank");
        helper.succeed();
    }

    public static void vacuumCapturesItemAtNozzle(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        ItemEntity item = new ItemEntity(helper.getLevel(), nozzle.x, nozzle.y, nozzle.z, new ItemStack(Items.SLIME_BALL, 7));
        helper.getLevel().addFreshEntity(item);

        VacuumHandler.vacuumTick(player, vacpack);

        VacTank tank = vacpack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
        helper.assertTrue(tank.slot(0).item().is(Items.SLIME_BALL) && tank.slot(0).count() == 7, "slime balls are stored");
        helper.assertTrue(item.isRemoved(), "the item entity is consumed");
        helper.succeed();
    }

    public static void vacuumTakesBabiesAndCatsButNotAdultCows(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Cow calf = create(helper, EntityTypes.COW);
        calf.setBaby(true);
        Cow adult = create(helper, EntityTypes.COW);
        Cat cat = create(helper, EntityTypes.CAT);
        for (Mob mob : new Mob[]{calf, adult, cat}) {
            mob.snapTo(nozzle.x, nozzle.y - mob.getBbHeight() / 2, nozzle.z, 0, 0);
            mob.setNoAi(true);
            helper.getLevel().addFreshEntity(mob);
        }

        VacuumHandler.vacuumTick(player, vacpack);

        helper.assertTrue(calf.isRemoved(), "a calf is vacuumed");
        helper.assertTrue(cat.isRemoved(), "a cat is vacuumed");
        helper.assertTrue(!adult.isRemoved(), "an adult cow is too big");
        adult.discard();
        helper.succeed();
    }

    public static void vacuumTakesOnlySmallSlimes(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Slime small = spawnSlime(helper, nozzle, 1);
        Slime big = spawnSlime(helper, nozzle, 4);

        VacuumHandler.vacuumTick(player, vacpack);

        VacTank tank = vacpack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
        helper.assertTrue(small.isRemoved(), "a small slime is captured");
        helper.assertTrue(!big.isRemoved(), "a big slime is too large");
        helper.assertTrue(tank.slot(0).matchesMob(EntityTypes.SLIME) && tank.slot(0).amount() == 1, "the slime is stored");
        big.discard();
        helper.succeed();
    }

    public static void shotSlimeFliesAsProjectileAndLandsAlive(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Slime slime = spawnSlime(helper, VacuumHandler.nozzlePos(player), 1);
        slime.setCustomName(Component.literal("Pinky"));
        VacuumHandler.vacuumTick(player, vacpack);

        VacuumHandler.shoot(player, vacpack);

        AABB area = new AABB(player.blockPosition()).inflate(4);
        var shots = helper.getLevel().getEntitiesOfClass(TankShot.class, area, TankShot::carriesMob);
        helper.assertTrue(shots.size() == 1, "the slime flies as a projectile");
        helper.assertTrue(vacpack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY).isEmpty(), "the tank is empty again");

        TankShot shot = shots.getFirst();
        shot.release(shot.position(), Vec3.ZERO, false);
        var released = helper.getLevel().getEntitiesOfClass(Slime.class, area, Slime::isAlive);
        helper.assertTrue(released.size() == 1, "exactly one slime lands");
        helper.assertTrue("Pinky".equals(released.getFirst().getCustomName().getString()), "the slime keeps its name");
        helper.assertTrue(shot.isRemoved(), "the projectile is gone");
        released.forEach(Slime::discard);
        helper.succeed();
    }

    public static void ragdollBouncesRollsAndSettles(GameTestHelper helper) {
        Player player = player(helper);
        for (int x = -1; x <= 8; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
        CompoundTag chicken = new CompoundTag();
        chicken.putString("id", "minecraft:chicken");
        TankShot shot = TankShot.ofMob(helper.getLevel(), player, chicken);
        Vec3 start = helper.absoluteVec(new Vec3(0.5, 4.0, 0.5));
        shot.snapTo(start.x, start.y, start.z, 0, 0);
        shot.setDeltaMovement(0.35, -0.6, 0);
        helper.getLevel().addFreshEntity(shot);

        boolean bounced = false;
        for (int i = 0; i < 200 && shot.isAlive(); i++) {
            shot.tick();
            bounced |= shot.isAlive() && shot.getDeltaMovement().y > 0;
        }

        helper.assertTrue(bounced, "the ragdoll bounces off the floor");
        helper.assertTrue(shot.isRemoved(), "the ragdoll settles once it has almost stopped");
        AABB area = new AABB(helper.absolutePos(new BlockPos(0, 2, 0))).inflate(10);
        var released = helper.getLevel().getEntitiesOfClass(Chicken.class, area, Chicken::isAlive);
        helper.assertTrue(released.size() == 1, "the chicken stands up where the ragdoll stopped");
        helper.assertTrue(released.getFirst().getX() > start.x + 0.5, "it slid forward with its momentum");
        released.forEach(Chicken::discard);
        helper.succeed();
    }

    public static void releasedMobKeepsMomentumAndIsProtected(GameTestHelper helper) {
        Player player = player(helper);
        CompoundTag chicken = new CompoundTag();
        chicken.putString("id", "minecraft:chicken");
        TankShot shot = TankShot.ofMob(helper.getLevel(), player, chicken);
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 6.0, 0.5));
        shot.snapTo(pos.x, pos.y, pos.z, 0, 0);
        helper.getLevel().addFreshEntity(shot);

        shot.release(pos, new Vec3(0.9, 0.2, 0), false);

        var released = helper.getLevel().getEntitiesOfClass(Chicken.class, new AABB(pos, pos).inflate(2), Chicken::isAlive);
        helper.assertTrue(released.size() == 1, "the chicken is released");
        Chicken bird = released.getFirst();
        helper.assertTrue(bird.getDeltaMovement().x > 0.8, "the chicken keeps its momentum");
        float health = bird.getHealth();
        bird.hurt(helper.getLevel().damageSources().generic(), 2.0F);
        helper.assertTrue(bird.getHealth() == health, "the chicken is invulnerable right after release");
        helper.assertTrue(!bird.causeFallDamage(12.0F, 1.0F, helper.getLevel().damageSources().fall()),
                "the first landing deals no fall damage");
        bird.discard();
        helper.succeed();
    }

    public static void bigSlimeIsHeldInAirStreamAndLaunched(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Vec3 ahead = player.getEyePosition().add(player.getLookAngle().scale(5));
        Slime big = spawnSlime(helper, ahead, 4);
        VacuumState state = player.getData(ModAttachments.VACUUM_STATE);

        VacuumHandler.holdTick(player, vacpack, state);

        Vec3 toHold = VacuumHandler.holdPoint(player, big).subtract(big.getBoundingBox().getCenter());
        helper.assertTrue(big.getDeltaMovement().dot(toHold) > 0, "the big slime is pulled to the hold point");
        helper.assertTrue(big.isAlive(), "a held slime is not stored");

        VacuumHandler.shoot(player, vacpack);

        helper.assertTrue(big.isRemoved(), "shooting launches the held slime");
        AABB area = new AABB(player.blockPosition()).inflate(8);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(TankShot.class, area, TankShot::carriesMob).size() == 1,
                "the held slime flies as a projectile");
        helper.getLevel().getEntitiesOfClass(TankShot.class, area, e -> true).forEach(TankShot::discard);
        helper.succeed();
    }

    public static void suctionPullsDistantItemTowardsNozzle(GameTestHelper helper) {
        Player player = player(helper);
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(6));
        ItemEntity item = new ItemEntity(helper.getLevel(), start.x, start.y, start.z, new ItemStack(Items.APPLE));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);

        VacuumHandler.vacuumTick(player, player.getMainHandItem());

        Vec3 motion = item.getDeltaMovement();
        helper.assertTrue(motion.dot(nozzle.subtract(start)) > 0, "the item moves towards the nozzle");
        helper.assertTrue(motion.y >= item.getGravity() - 1.0E-6 || motion.y > 0, "gravity is cancelled so the item floats");
        item.discard();
        helper.succeed();
    }

    public static void emptySlotShotReleasesPulseWave(GameTestHelper helper) {
        Player player = player(helper);
        Vec3 ahead = player.position().add(player.getLookAngle().scale(3));
        Cow cow = create(helper, EntityTypes.COW);
        cow.snapTo(ahead.x, ahead.y, ahead.z, 0, 0);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);

        int cooldown = VacuumHandler.shoot(player, player.getMainHandItem());

        helper.assertTrue(cooldown == Config.pulseCooldown(), "an empty slot fires a pulse wave");
        helper.assertTrue(cow.isRemoved(), "the cow turns into a ragdoll");
        AABB area = new AABB(player.blockPosition()).inflate(8);
        var ragdolls = helper.getLevel().getEntitiesOfClass(TankShot.class, area, TankShot::carriesMob);
        helper.assertTrue(ragdolls.size() == 1, "exactly one ragdoll flies");
        helper.assertTrue(ragdolls.getFirst().getDeltaMovement().dot(player.getLookAngle()) > 0.5, "the ragdoll is blown away");
        ragdolls.forEach(TankShot::discard);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    public static void pulseAtTheFloorBurstsLikeAWindCharge(GameTestHelper helper) {
        // The explosion only affects entities that are in the level, so this test needs a real (mock) server player.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 4.0, 0.5));
        player.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.VACPACK.get()));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(x, 3, z, Blocks.STONE);
            }
        }
        player.setXRot(90.0F);
        player.setDeltaMovement(Vec3.ZERO);

        // Burst only: the full pulse also triggers a GeckoLib animation packet, which the mock connection cannot send.
        VacuumHandler.windBurst(player, player.getEyePosition(), player.getLookAngle());

        helper.assertTrue(player.getDeltaMovement().y > 0.3, "the wind burst launches the player upwards");
        helper.assertTrue(player.isIgnoringFallDamageFromCurrentImpulse(), "like a wind charge, the jump protects from fall damage");
        player.discard();
        helper.succeed();
    }

    public static void vacuumPicksRipeSweetBerries(GameTestHelper helper) {
        Player player = player(helper);
        BlockPos bush = new BlockPos(3, 5, 0);
        helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        VacuumState state = new VacuumState();

        for (int i = 0; i < 10; i++) {
            VacuumHandler.harvestTick(player, state);
        }

        helper.assertBlockProperty(bush, SweetBerryBushBlock.AGE, 1);
        AABB around = new AABB(helper.absolutePos(bush)).inflate(1.5);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.SWEET_BERRIES)).isEmpty(),
                "berries pop off the bush");
        helper.succeed();
    }

    public static void shotItemHitsMob(GameTestHelper helper) {
        Player player = player(helper);
        Cow cow = create(helper, EntityTypes.COW);
        Vec3 pos = helper.absoluteVec(new Vec3(3.5, 2.0, 0.5));
        cow.snapTo(pos.x, pos.y, pos.z, 0, 0);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        TankShot shot = TankShot.ofItem(helper.getLevel(), player, new ItemStack(Items.COBBLESTONE));
        shot.snapTo(pos.x - 1.5, pos.y + 0.5, pos.z, 0, 0);
        shot.setDeltaMovement(1.2, 0, 0);
        helper.getLevel().addFreshEntity(shot);

        shot.tick();

        helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "the cow takes damage");
        helper.assertTrue(shot.isAlive() && shot.getDeltaMovement().x < 0, "the shot bounces off the cow and keeps tumbling");
        shot.release(shot.position(), shot.getDeltaMovement(), false);
        AABB area = new AABB(cow.blockPosition()).inflate(3);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.COBBLESTONE)).isEmpty(),
                "the cobblestone drops");
        cow.discard();
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> true).forEach(ItemEntity::discard);
        helper.succeed();
    }

    public static void creativeVacpackTakesBigMobsAndBottomlessStacks(GameTestHelper helper) {
        Player player = player(helper, new ItemStack(ModItems.CREATIVE_VACPACK.get()));
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Cow adult = create(helper, EntityTypes.COW);
        adult.snapTo(nozzle.x, nozzle.y - adult.getBbHeight() / 2, nozzle.z, 0, 0);
        adult.setNoAi(true);
        helper.getLevel().addFreshEntity(adult);
        Slime big = spawnSlime(helper, nozzle, 4);
        ItemEntity item = new ItemEntity(helper.getLevel(), nozzle.x, nozzle.y, nozzle.z, new ItemStack(Items.SLIME_BALL, 64));
        item.getItem().setCount(300);
        helper.getLevel().addFreshEntity(item);

        VacuumHandler.vacuumTick(player, vacpack);

        VacTank tank = vacpack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
        helper.assertTrue(adult.isRemoved(), "the creative vacpack takes an adult cow");
        helper.assertTrue(big.isRemoved(), "the creative vacpack takes a big slime");
        helper.assertTrue(item.isRemoved() && tank.slot(0).count() == 300, "300 slime balls fit into one creative slot");
        helper.succeed();
    }

    public static void creativeVacpackShootsEveryTick(GameTestHelper helper) {
        int regular = shotsInTwoTicks(helper, new ItemStack(ModItems.VACPACK.get()));
        int creative = shotsInTwoTicks(helper, new ItemStack(ModItems.CREATIVE_VACPACK.get()));
        helper.assertTrue(regular == 1, "the regular vacpack waits between shots, fired " + regular);
        helper.assertTrue(creative == 2, "the creative vacpack fires every tick, fired " + creative);
        helper.succeed();
    }

    private static int shotsInTwoTicks(GameTestHelper helper, ItemStack vacpack) {
        vacpack.set(ModDataComponents.TANK, VacTank.EMPTY.insertItem(new ItemStack(Items.SNOWBALL, 5), 4, 64).tank());
        Player player = player(helper, vacpack);
        VacuumHandler.setInput(player, false, true);
        VacuumHandler.tick(player);
        VacuumHandler.tick(player);
        int left = player.getMainHandItem().getOrDefault(ModDataComponents.TANK, VacTank.EMPTY).slot(0).count();
        helper.getLevel().getEntitiesOfClass(TankShot.class, new AABB(player.blockPosition()).inflate(8)).forEach(TankShot::discard);
        return 5 - left;
    }

    private static Player player(GameTestHelper helper) {
        return player(helper, new ItemStack(ModItems.VACPACK.get()));
    }

    private static Player player(GameTestHelper helper, ItemStack vacpack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 4.0, 0.5));
        player.snapTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, vacpack);
        return player;
    }

    private static Slime spawnSlime(GameTestHelper helper, Vec3 center, int size) {
        Slime slime = create(helper, EntityTypes.SLIME);
        slime.setSize(size, true);
        slime.snapTo(center.x, center.y - slime.getBbHeight() / 2, center.z, 0, 0);
        slime.setNoAi(true);
        helper.getLevel().addFreshEntity(slime);
        return slime;
    }

    private static <T extends Mob> T create(GameTestHelper helper, EntityType<T> type) {
        return type.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
    }

    private static CompoundTag mobTag(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:chicken");
        tag.putString("CustomName", name);
        return tag;
    }
}
