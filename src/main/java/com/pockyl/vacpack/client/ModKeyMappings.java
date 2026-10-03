package com.pockyl.vacpack.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.vacpack";

    public static final KeyMapping CYCLE_SLOT = new KeyMapping("key.vacpack.cycle_slot",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);

    private ModKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_SLOT);
    }
}
