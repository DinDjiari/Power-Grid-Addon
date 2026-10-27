/*
 * Copyright 2026 Tomrox GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomrox.pghv.client;

import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.registry.PghvBlocks;
import com.tomrox.pghv.registry.PghvItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Core colours: cable box terminal caps (tintindex 0..2) and cable items (tintindex 1..3). */
@EventBusSubscriber(modid = Pghv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PghvColors {
    private static final int OPAQUE = 0xFF000000;

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex < 0 || tintIndex >= CableCores.COUNT)
                return -1;
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof CableBoxBlockEntity box)
                return OPAQUE | box.coreColor(tintIndex);
            return OPAQUE | CableCores.COLOR_COPPER;
        }, PghvBlocks.CABLE_BOX.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex < 1 || tintIndex > CableCores.COUNT)
                return -1;
            return OPAQUE | CableCores.get(stack).color(tintIndex - 1);
        }, PghvItems.BARE_CABLE.get(), PghvItems.UNDERGROUND_CABLE.get());
    }
}
