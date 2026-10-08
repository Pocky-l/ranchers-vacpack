package com.pockyl.vacpack.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.entity.TankShot;
import com.pockyl.vacpack.registry.ModAttachments;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.tank.VacTank;
import com.pockyl.vacpack.vacuum.VacuumHandler;
import com.pockyl.vacpack.vacuum.VacuumState;

import java.util.function.Consumer;

/**
 * In-game tests, run headless by {@code gradlew runGameTestServer}.
 * Tests use the 1x1x1 {@code empty} structure unless they need a prepared one.
 */
@GameTestHolder(Vacpack.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModGameTests {
    private ModGameTests() {
    }

    @GameTest(template = "empty")
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vacpackRecipeIsLoaded(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(Vacpack.id("vacpack"));
        helper.assertTrue(recipe.isPresent(), "the vacpack crafting recipe is loaded");
        helper.assertTrue(recipe.get().value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.VACPACK.get()),
                "the recipe crafts a vacpack");
        helper.succeed();
    }

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
    public static void tankShootsLastCapturedMobFirst(GameTestHelper helper) {
        CompoundTag first = mobTag("first");
        CompoundTag second = mobTag("second");
        VacTank tank = VacTank.EMPTY.insertMob(EntityType.CHICKEN, first, 4, 10).insertMob(EntityType.CHICKEN, second, 4, 10);
        helper.assertTrue(tank.slot(0).amount() == 2 && tank.slot(1).isEmpty(), "same mob type shares a slot");

        VacTank.Taken taken = tank.takeFromSelected();
        helper.assertTrue(second.equals(taken.mob()), "the last captured mob comes out first");
        helper.assertTrue(taken.tank().slot(0).amount() == 1, "one mob stays in the tank");
        helper.succeed();
    }

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
    public static void vacuumTakesBabiesAndCatsButNotAdultCows(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Cow calf = EntityType.COW.create(helper.getLevel());
        calf.setBaby(true);
        Cow adult = EntityType.COW.create(helper.getLevel());
        Cat cat = EntityType.CAT.create(helper.getLevel());
        for (Mob mob : new Mob[]{calf, adult, cat}) {
            mob.moveTo(nozzle.x, nozzle.y - mob.getBbHeight() / 2, nozzle.z, 0, 0);
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

    @GameTest(template = "empty")
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
        helper.assertTrue(tank.slot(0).matchesMob(EntityType.SLIME) && tank.slot(0).amount() == 1, "the slime is stored");
        big.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
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
        shot.moveTo(start.x, start.y, start.z, 0, 0);
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

    @GameTest(template = "empty")
    public static void releasedMobKeepsMomentumAndIsProtected(GameTestHelper helper) {
        Player player = player(helper);
        CompoundTag chicken = new CompoundTag();
        chicken.putString("id", "minecraft:chicken");
        TankShot shot = TankShot.ofMob(helper.getLevel(), player, chicken);
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 6.0, 0.5));
        shot.moveTo(pos.x, pos.y, pos.z, 0, 0);
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

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
    public static void emptySlotShotReleasesPulseWave(GameTestHelper helper) {
        Player player = player(helper);
        Vec3 ahead = player.position().add(player.getLookAngle().scale(3));
        Cow cow = EntityType.COW.create(helper.getLevel());
        cow.moveTo(ahead.x, ahead.y, ahead.z, 0, 0);
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

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void pulseAtTheFloorBurstsLikeAWindCharge(GameTestHelper helper) {
        // The explosion only affects entities that are in the level, so this test needs a real (mock) server player.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 4.0, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
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

    @GameTest(template = "empty")
    public static void vacuumPicksRipeSweetBerries(GameTestHelper helper) {
        Player player = player(helper);
        BlockPos bush = new BlockPos(3, 5, 0);
        helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
        VacuumState state = new VacuumState();
        BlockPos absolute = helper.absolutePos(bush);
        boolean[] heard = new boolean[2];
        Consumer<BlockEvent.NeighborNotifyEvent> neighbours = event -> heard[0] |= event.getPos().equals(absolute);
        Consumer<VanillaGameEvent> gameEvents = event -> heard[1] |= event.getVanillaEvent().is(GameEvent.BLOCK_CHANGE)
                && BlockPos.containing(event.getEventPosition()).equals(absolute);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.NeighborNotifyEvent.class, neighbours);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, VanillaGameEvent.class, gameEvents);
        try {
            for (int i = 0; i < 10; i++) {
                VacuumHandler.harvestTick(player, state);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(neighbours);
            NeoForge.EVENT_BUS.unregister(gameEvents);
        }

        helper.assertBlockProperty(bush, SweetBerryBushBlock.AGE, 1);
        AABB around = new AABB(absolute).inflate(1.5);
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.SWEET_BERRIES)).isEmpty(),
                "berries pop off the bush");
        helper.assertTrue(heard[0], "neighbours are told about the picked bush");
        helper.assertTrue(heard[1], "picking emits a block change game event");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shotItemHitsMob(GameTestHelper helper) {
        Player player = player(helper);
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(3.5, 2.0, 0.5));
        cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        TankShot shot = TankShot.ofItem(helper.getLevel(), player, new ItemStack(Items.COBBLESTONE));
        shot.moveTo(pos.x - 1.5, pos.y + 0.5, pos.z, 0, 0);
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

    @GameTest(template = "empty")
    public static void boneMealShotGrowsCrop(GameTestHelper helper) {
        BlockPos wheat = plantWheat(helper);
        TankShot shot = effectShot(helper, new ItemStack(Items.BONE_MEAL), new Vec3(2.5, 3.5, 0.5));

        fly(shot, 20);

        helper.assertTrue(helper.getBlockState(wheat).getValue(CropBlock.AGE) > 0, "the wheat grows");
        helper.assertTrue(shot.isRemoved() && droppedItems(helper, wheat, Items.BONE_MEAL) == 0, "the bone meal is used up");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snowballShotFreezesWater(GameTestHelper helper) {
        for (int x = 1; x <= 5; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                boolean rim = x == 1 || x == 5 || Math.abs(z) == 2;
                helper.setBlock(x, 2, z, rim ? Blocks.STONE : Blocks.WATER);
            }
        }
        TankShot shot = effectShot(helper, new ItemStack(Items.SNOWBALL), new Vec3(3.5, 4.5, 0.5));

        fly(shot, 20);

        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(3, 2, 0));
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(2, 2, -1));
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(4, 2, 1));
        helper.assertTrue(shot.isRemoved() && droppedItems(helper, new BlockPos(3, 2, 0), Items.SNOWBALL) == 0, "the snowball is used up");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fireChargeShotPlacesFire(GameTestHelper helper) {
        helper.setBlock(2, 1, 0, Blocks.STONE);
        TankShot shot = effectShot(helper, new ItemStack(Items.FIRE_CHARGE), new Vec3(2.5, 3.5, 0.5));

        fly(shot, 20);

        helper.assertBlockPresent(Blocks.FIRE, new BlockPos(2, 2, 0));
        helper.assertTrue(shot.isRemoved() && droppedItems(helper, new BlockPos(2, 2, 0), Items.FIRE_CHARGE) == 0,
                "the fire charge is used up");
        helper.setBlock(2, 2, 0, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shotItemEffectsCanBeTurnedOff(GameTestHelper helper) {
        ModConfigSpec.BooleanValue option = Config.SPEC.getValues().get("shooting.itemEffects");
        helper.assertTrue(Config.shotItemEffects(), "item effects are on by default");
        BlockPos wheat = plantWheat(helper);
        TankShot shot;
        option.set(false);
        try {
            shot = effectShot(helper, new ItemStack(Items.BONE_MEAL), new Vec3(2.5, 3.5, 0.5));
            fly(shot, 200);
        } finally {
            option.set(true);
        }

        helper.assertTrue(helper.getBlockState(wheat).getValue(CropBlock.AGE) == 0, "the wheat does not grow");
        helper.assertTrue(shot.isRemoved() && droppedItems(helper, wheat, Items.BONE_MEAL) == 1, "the bone meal drops as an item");
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(wheat)).inflate(4), e -> true)
                .forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** Wheat at age 0 on farmland; returns the wheat's relative position. */
    private static BlockPos plantWheat(GameTestHelper helper) {
        BlockPos wheat = new BlockPos(2, 2, 0);
        helper.setBlock(wheat.below(), Blocks.FARMLAND);
        helper.setBlock(wheat, Blocks.WHEAT);
        return wheat;
    }

    /** An item shot from the tank falling straight down from {@code relative}. */
    private static TankShot effectShot(GameTestHelper helper, ItemStack item, Vec3 relative) {
        TankShot shot = TankShot.ofItem(helper.getLevel(), player(helper), item);
        shot.enableItemEffect();
        Vec3 pos = helper.absoluteVec(relative);
        shot.moveTo(pos.x, pos.y, pos.z, 0, 0);
        shot.setDeltaMovement(0, -0.5, 0);
        helper.getLevel().addFreshEntity(shot);
        return shot;
    }

    private static void fly(TankShot shot, int maxTicks) {
        for (int i = 0; i < maxTicks && shot.isAlive(); i++) {
            shot.tick();
        }
    }

    private static int droppedItems(GameTestHelper helper, BlockPos relative, Item item) {
        AABB area = new AABB(helper.absolutePos(relative)).inflate(4);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(template = "empty")
    public static void creativeVacpackTakesBigMobsAndBottomlessStacks(GameTestHelper helper) {
        Player player = player(helper, new ItemStack(ModItems.CREATIVE_VACPACK.get()));
        ItemStack vacpack = player.getMainHandItem();
        Vec3 nozzle = VacuumHandler.nozzlePos(player);
        Cow adult = EntityType.COW.create(helper.getLevel());
        adult.moveTo(nozzle.x, nozzle.y - adult.getBbHeight() / 2, nozzle.z, 0, 0);
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

    @GameTest(template = "empty")
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
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, vacpack);
        return player;
    }

    private static Slime spawnSlime(GameTestHelper helper, Vec3 center, int size) {
        Slime slime = EntityType.SLIME.create(helper.getLevel());
        slime.setSize(size, true);
        slime.moveTo(center.x, center.y - slime.getBbHeight() / 2, center.z, 0, 0);
        slime.setNoAi(true);
        helper.getLevel().addFreshEntity(slime);
        return slime;
    }

    private static CompoundTag mobTag(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:chicken");
        tag.putString("CustomName", "\"" + name + "\"");
        return tag;
    }
}
