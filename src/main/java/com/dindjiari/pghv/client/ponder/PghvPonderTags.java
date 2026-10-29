/*
 * Copyright 2026 DinDjiari
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
package com.dindjiari.pghv.client.ponder;

import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.registry.PghvItems;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/** Ponder tag "Power Grid: High Voltage" with all items that have a pghv scene. */
public class PghvPonderTags {
    public static final ResourceLocation HIGH_VOLTAGE = ResourceLocation.fromNamespaceAndPath(Pghv.MOD_ID, "high_voltage");

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(HIGH_VOLTAGE)
                .title("Power Grid: High Voltage")
                .description("Transformer cooling, overheat protection and multi-core underground cables")
                .item(PghvItems.COOLING_FAN.get(), true, false)
                .addToIndex()
                .register();

        helper.addToTag(HIGH_VOLTAGE)
                .add(PghvItems.COOLING_FAN.getId())
                .add(PghvItems.BRASS_COOLING_FAN.getId())
                .add(PghvItems.DATA_CABLE.getId())
                .add(PghvItems.CABLE_BOX.getId())
                .add(PghvItems.UNDERGROUND_CABLE.getId())
                .add(PghvItems.BARE_CABLE.getId());
    }
}
