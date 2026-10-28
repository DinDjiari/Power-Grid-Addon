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

    private static PartialModel pgLamp(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath("powergrid", "block/lamps/" + path));
    }

    /** Power Grid's light bulb models, used by the EU 230 V bulb (same look). */
    public static final java.util.Map<org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State, PartialModel> EU_BULB;
    public static final java.util.Map<org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState, PartialModel> EU_BULB_DYED;

    static {
        var plain = new java.util.EnumMap<org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State, PartialModel>(
                org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.class);
        plain.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.OFF, pgLamp("light_bulb"));
        plain.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.LOW_POWER, pgLamp("light_bulb_on"));
        plain.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.ON, pgLamp("light_bulb_on"));
        plain.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.BROKEN, pgLamp("light_bulb_broken"));
        plain.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.State.LIGHT, pgLamp("light_bulb_light"));
        EU_BULB = plain;
        var dyed = new java.util.EnumMap<org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState, PartialModel>(
                org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.class);
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.OFF, pgLamp("dyed_light_bulb"));
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.LOW_POWER, pgLamp("dyed_light_bulb_on"));
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.ON, pgLamp("dyed_light_bulb_on"));
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.BROKEN, pgLamp("dyed_light_bulb_broken"));
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.LIGHT, pgLamp("dyed_light_bulb_light"));
        dyed.put(org.patryk3211.powergrid.electricity.light.bulb.LightBulb.DyedState.BULB, pgLamp("dyed_light_bulb_bulb"));
        EU_BULB_DYED = dyed;
    }

    public static void init() {
    }
}
