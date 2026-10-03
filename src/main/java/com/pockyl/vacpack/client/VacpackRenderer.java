package com.pockyl.vacpack.client;

import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import com.pockyl.vacpack.Vacpack;
import com.pockyl.vacpack.item.VacpackItem;

/** Renders assets/vacpack/{geo,animations,textures}/item/vacpack.*. */
public final class VacpackRenderer extends GeoItemRenderer<VacpackItem> {
    public VacpackRenderer() {
        super(new DefaultedItemGeoModel<>(Vacpack.id("vacpack")));
    }
}
