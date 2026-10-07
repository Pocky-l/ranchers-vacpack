package com.pockyl.vacpack.client;

import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;

/**
 * Renders assets/vacpack/{geo,animations,textures}/item/vacpack.*; the Creative Vacpack shares the model and
 * animations and only swaps the texture.
 */
public final class VacpackRenderer extends GeoItemRenderer<VacpackItem> {
    public VacpackRenderer(boolean creative) {
        super(creative
                ? new DefaultedItemGeoModel<VacpackItem>(Vacpack.id("vacpack")).withAltTexture(Vacpack.id("creative_vacpack"))
                : new DefaultedItemGeoModel<>(Vacpack.id("vacpack")));
    }
}
