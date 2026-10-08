package com.pockyl.vacpack.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.pockyl.vacpack.tank.MobData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small spinning 3D models of stored mobs for the tank HUD. Render copies are cached per saved mob data. */
public final class MobIcons {
    private static final int CACHE_SIZE = 32;
    private static final Map<CompoundTag, LivingEntity> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CompoundTag, LivingEntity> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private MobIcons() {
    }

    /**
     * Draws the mob centred in a box.
     *
     * @return false if no model could be created (unknown or non-living entity), so the caller can draw a fallback
     */
    public static boolean render(GuiGraphicsExtractor graphics, CompoundTag mob, int x, int y, int size, float partialTick) {
        LivingEntity entity = entity(mob);
        if (entity == null) {
            return false;
        }
        float extent = Math.max(entity.getBbWidth(), entity.getBbHeight());
        float scale = size * 0.75F / Math.max(extent, 0.3F);
        float time = (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + partialTick;
        Quaternionf pose = Axis.ZP.rotation((float) Math.PI)
                .mul(Axis.XP.rotationDegrees(-12.0F))
                .mul(Axis.YP.rotationDegrees(time * 2.0F));
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .createRenderState(entity, partialTick);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        graphics.enableScissor(x, y, x + size, y + size);
        graphics.entity(state, scale, new Vector3f(0, entity.getBbHeight() / 2.0F, 0), pose, null, x, y, x + size, y + size);
        graphics.disableScissor();
        return true;
    }

    private static LivingEntity entity(CompoundTag mob) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        return CACHE.computeIfAbsent(mob, tag -> MobData.createForDisplay(tag, minecraft.level)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .map(entity -> {
                    entity.yBodyRot = entity.yBodyRotO = 0;
                    entity.yHeadRot = entity.yHeadRotO = 0;
                    entity.setYRot(0);
                    entity.setXRot(0);
                    return entity;
                })
                .orElse(null));
    }

    public static void clear() {
        CACHE.clear();
    }
}
