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
package com.tomrox.pghv.item;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.world.item.Item;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.light.bulb.ILightBulb;
import org.patryk3211.powergrid.electricity.light.bulb.LightBulb;

import java.util.Map;

/*
 * EU light bulb 230 V / 60 W (pghv:eu_light_bulb) for Power Grid's light fixtures. Same look as Power Grid's
 * light bulb: its models are referenced (Apache-2.0), nothing copied. Electrical values like Power Grid's
 * LightBulb.setProperties(ratedPower, ratedVoltage, minResistance, thermalMass): R_max = U^2 / P, operating
 * temperature 1450, overheat 1850; R_min scaled like the 120 V bulb (120 of 480 ohm = 1/4).
 */
public class EuLightBulb extends LightBulb {
    public static final float RATED_POWER = 60f;
    public static final float RATED_VOLTAGE = 230f;

    public EuLightBulb(Item.Properties settings) {
        super(settings);
        float rMax = RATED_VOLTAGE * RATED_VOLTAGE / RATED_POWER;
        float operating = 1450f;
        this.T_max = operating;
        this.R_max = rMax;
        this.R_min = rMax / 4f;
        this.thermalProperties = new ILightBulb.Properties(
                RATED_POWER / (operating - ThermalBehaviour.STANDARD_TEMPERATURE), 0.006f, operating + 400f);
        this.power = RATED_POWER;
        this.voltage = RATED_VOLTAGE;
        this.canBeDyed = true;
    }

    /** Client: Power Grid's bulb models (plain and dyed, created in PghvPartialModels). Called from client setup. */
    public void setupClient(Map<State, PartialModel> plain, Map<DyedState, PartialModel> dyed) {
        this.modelSupplier = () -> plain::get;
        this.dyedModelSupplier = () -> dyed::get;
    }
}
