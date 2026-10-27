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

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

/** Partial models used by the renderers. Call {@link #init()} during client setup to load the class. */
public class PghvPartialModels {
    public static final PartialModel HV_VOLTAGE_HEAD =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("pghv", "block/gauge/hv_voltage_head"));

    public static final PartialModel COOLING_FAN_ROTOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("pghv", "block/cooling_fan/rotor"));

    /** Rotor of the brass fan (models/block/brass_cooling_fan/rotor.json, from the asset worker). */
    public static final PartialModel BRASS_COOLING_FAN_ROTOR =
            PartialModel.of(ResourceLocation.fromNamespaceAndPath("pghv", "block/brass_cooling_fan/rotor"));

    public static void init() {
    }
}
