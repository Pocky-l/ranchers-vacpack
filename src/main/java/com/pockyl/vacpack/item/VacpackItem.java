package com.pockyl.vacpack.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import com.pockyl.vacpack.Config;
import com.pockyl.vacpack.client.VacpackRenderer;
import com.pockyl.vacpack.registry.ModDataComponents;
import com.pockyl.vacpack.tank.TankSlot;
import com.pockyl.vacpack.tank.VacTank;

import java.util.List;
import java.util.function.Consumer;

/**
 * The vacpack. Input is handled through custom packets (see {@code ClientInputHandler}) instead of the vanilla
 * "use item" flow, so the player is not slowed down while vacuuming.
 */
public final class VacpackItem extends Item implements GeoItem {
    public static final String FAN_CONTROLLER = "fan";
    public static final String RECOIL_CONTROLLER = "recoil";
    public static final String GULP_CONTROLLER = "gulp";
    public static final String VACUUM_ANIM = "vacuum";
    public static final String SHOOT_ANIM = "shoot";
    public static final String PULSE_ANIM = "pulse";
    public static final String SWITCH_ANIM = "switch";
    public static final String GULP_ANIM = "gulp";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final int BAR_COLOR = 0x6FD3FF;
    /** Slot capacity of the Creative Vacpack: effectively unlimited, but far from integer overflow. */
    private static final int CREATIVE_CAPACITY = 1_000_000;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final boolean creative;

    public VacpackItem(Properties properties, boolean creative) {
        super(properties);
        this.creative = creative;
        GeoItem.registerSyncedAnimatable(this);
    }

    public boolean isCreative() {
        return creative;
    }

    /** Whether the stack is a Creative Vacpack: no slot limits, no shot cooldown, takes any non-boss mob. */
    public static boolean isCreative(ItemStack stack) {
        return stack.getItem() instanceof VacpackItem vacpack && vacpack.creative;
    }

    public static int itemCapacity(ItemStack stack) {
        return isCreative(stack) ? CREATIVE_CAPACITY : Config.itemCapacity();
    }

    public static int mobCapacity(ItemStack stack) {
        return isCreative(stack) ? CREATIVE_CAPACITY : Config.mobCapacity();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // The tank component changes constantly while vacuuming; do not bob the item on every change.
        return slotChanged || !newStack.is(this);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !creative && !tank(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        VacTank tank = tank(stack);
        int slots = Config.slotCount();
        float fill = 0;
        for (int i = 0; i < slots; i++) {
            TankSlot slot = tank.slot(i);
            int capacity = slot.holdsMobs() ? mobCapacity(stack) : itemCapacity(stack);
            fill += Math.min(1.0F, (float) slot.amount() / capacity);
        }
        return Mth.clamp(Math.round(13.0F * fill / slots), 1, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        VacTank tank = tank(stack);
        if (creative) {
            tooltip.add(Component.translatable("tooltip.vacpack.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (tank.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.vacpack.empty").withStyle(ChatFormatting.GRAY));
        } else {
            for (int i = 0; i < Math.max(Config.slotCount(), tank.slots().size()); i++) {
                TankSlot slot = tank.slot(i);
                if (!slot.isEmpty()) {
                    ChatFormatting color = i == tank.selected() ? ChatFormatting.AQUA : ChatFormatting.GRAY;
                    tooltip.add(Component.translatable("tooltip.vacpack.slot", contentName(slot), slot.amount()).withStyle(color));
                }
            }
        }
        tooltip.add(Component.translatable("tooltip.vacpack.controls").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component contentName(TankSlot slot) {
        if (slot.holdsItems()) {
            return slot.item().getHoverName();
        }
        return slot.mobType().map(type -> type.getDescription()).orElse(Component.literal("?"));
    }

    private static VacTank tank(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK, VacTank.EMPTY);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // The fan idles slowly and spins up while vacuuming; transitions are blended over a few ticks.
        controllers.add(new AnimationController<>(this, FAN_CONTROLLER, 4, state -> state.setAndContinue(IDLE))
                .triggerableAnim(VACUUM_ANIM, RawAnimation.begin().thenLoop("vacuum")));
        controllers.add(new AnimationController<>(this, RECOIL_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(SHOOT_ANIM, RawAnimation.begin().thenPlay("shoot"))
                .triggerableAnim(PULSE_ANIM, RawAnimation.begin().thenPlay("pulse"))
                .triggerableAnim(SWITCH_ANIM, RawAnimation.begin().thenPlay("switch")));
        controllers.add(new AnimationController<>(this, GULP_CONTROLLER, 0, state -> PlayState.STOP)
                .triggerableAnim(GULP_ANIM, RawAnimation.begin().thenPlay("gulp")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // GeckoLib only invokes this on the client, so the renderer class is never loaded on a dedicated server.
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private VacpackRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new VacpackRenderer(creative);
                }
                return renderer;
            }
        });
    }
}
