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
package com.tomrox.pghv.registry;

import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.CableBoxBlock;
import com.tomrox.pghv.block.CableSegmentBlock;
import com.tomrox.pghv.block.HvCoolingFanBlock;
import com.tomrox.pghv.block.HvVoltmeterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PghvBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Pghv.MOD_ID);

    public static final DeferredBlock<HvVoltmeterBlock> HV_VOLTMETER = BLOCKS.register("hv_voltmeter",
            () -> new HvVoltmeterBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<HvCoolingFanBlock> COOLING_FAN = BLOCKS.register("cooling_fan",
            () -> new HvCoolingFanBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final DeferredBlock<CableBoxBlock> CABLE_BOX = BLOCKS.register("cable_box",
            () -> new CableBoxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** Cable piece placed by the laying code; no item, drops handled in code. */
    public static final DeferredBlock<CableSegmentBlock> CABLE_SEGMENT = BLOCKS.register("cable_segment",
            () -> new CableSegmentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.5f)
                    .sound(SoundType.WOOL)
                    .noOcclusion()));
}
