package com.pockyl.vacpack.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.registry.ModItems;
import com.pockyl.vacpack.tank.VacTank;
import com.pockyl.vacpack.vacuum.VacuumHandler;

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
    public static void shootReleasesStoredSlime(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack vacpack = player.getMainHandItem();
        Slime slime = spawnSlime(helper, VacuumHandler.nozzlePos(player), 1);
        slime.setCustomName(Component.literal("Pinky"));
        VacuumHandler.vacuumTick(player, vacpack);

        boolean shot = VacuumHandler.shoot(player, vacpack);

        helper.assertTrue(shot, "something was shot");
        AABB area = new AABB(player.blockPosition()).inflate(4);
        var released = helper.getLevel().getEntitiesOfClass(Slime.class, area, Slime::isAlive);
        helper.assertTrue(released.size() == 1, "exactly one slime is released");
        helper.assertTrue("Pinky".equals(released.getFirst().getCustomName().getString()), "the slime keeps its name");
        helper.assertTrue(vacpack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY).isEmpty(), "the tank is empty again");
        released.forEach(Slime::discard);
        helper.succeed();
    }

    private static Player player(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(0.5, 4.0, 0.5));
        player.moveTo(pos.x, pos.y, pos.z, -90.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.VACPACK.get()));
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
