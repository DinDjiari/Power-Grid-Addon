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
 *
 * Modified from Create: Power Grid by patryk3211, Apache-2.0.
 */
package com.dindjiari.pghv.block;

import com.dindjiari.pghv.registry.PghvBlockEntities;
import com.dindjiari.pghv.block.entity.HvVoltmeterBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.patryk3211.powergrid.electricity.gauge.GaugeBlock;
import org.patryk3211.powergrid.electricity.info.IHaveElectricProperties;
import org.patryk3211.powergrid.electricity.info.Voltage;

import java.util.List;

/** Brass high-voltage voltmeter (20 kV / 200 kV / 2 MV). */
public class HvVoltmeterBlock extends GaugeBlock<HvVoltmeterBlockEntity> implements IHaveElectricProperties {
    public HvVoltmeterBlock(Properties settings) {
        super(settings);
    }

    @Override
    public Class<HvVoltmeterBlockEntity> getBlockEntityClass() {
        return HvVoltmeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HvVoltmeterBlockEntity> getBlockEntityType() {
        return PghvBlockEntities.HV_VOLTMETER.get();
    }

    @Override
    public void appendProperties(ItemStack stack, Player player, List<Component> tooltip) {
        Voltage.max(HvVoltmeterBlockEntity.MAX_RANGE, player, tooltip);
    }
}
