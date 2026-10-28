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
import com.tomrox.pghv.client.ponder.PghvPonderPlugin;
import com.tomrox.pghv.registry.PghvBlockEntities;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Pghv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PghvClient {
    static {
        // Partial-Modelle so früh wie möglich anmelden (vor dem Laden der Zusatzmodelle)
        PghvPartialModels.init();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PghvBlockEntities.HV_VOLTMETER.get(), HvGaugeRenderer::new);
        event.registerBlockEntityRenderer(PghvBlockEntities.COOLING_FAN.get(), HvCoolingFanRenderer::new);
        event.registerBlockEntityRenderer(PghvBlockEntities.BRASS_COOLING_FAN.get(), HvCoolingFanRenderer::new);
        event.registerBlockEntityRenderer(PghvBlockEntities.DATA_SOCKET_BOX.get(), DataSocketBoxRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Partial-Modelle (Create/Flywheel) beim Client-Setup anmelden
        PghvPartialModels.init();
        event.enqueueWork(() -> com.tomrox.pghv.registry.PghvItems.EU_LIGHT_BULB.get()
                .setupClient(PghvPartialModels.EU_BULB, PghvPartialModels.EU_BULB_DYED));
        // Ponder scenes (W in the inventory); Ponder registers all plugins at FMLLoadCompleteEvent.
        event.enqueueWork(() -> PonderIndex.addPlugin(new PghvPonderPlugin()));
    }
}
