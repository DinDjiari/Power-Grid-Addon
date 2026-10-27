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
import com.tomrox.pghv.block.entity.CableBoxBlockEntity;
import com.tomrox.pghv.block.entity.CableSegmentBlockEntity;
import com.tomrox.pghv.block.entity.CoolingFanTopBlockEntity;
import com.tomrox.pghv.block.entity.HvCoolingFanBlockEntity;
import com.tomrox.pghv.block.entity.HvVoltmeterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PghvBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Pghv.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HvVoltmeterBlockEntity>> HV_VOLTMETER =
            BLOCK_ENTITIES.register("hv_voltmeter", () -> BlockEntityType.Builder
                    .of((pos, state) -> new HvVoltmeterBlockEntity(PghvBlockEntities.HV_VOLTMETER.get(), pos, state),
                            PghvBlocks.HV_VOLTMETER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HvCoolingFanBlockEntity>> COOLING_FAN =
            BLOCK_ENTITIES.register("cooling_fan", () -> BlockEntityType.Builder
                    .of((pos, state) -> new HvCoolingFanBlockEntity(PghvBlockEntities.COOLING_FAN.get(), pos, state),
                            PghvBlocks.COOLING_FAN.get())
                    .build(null));

    /** Upper cells of the cooling fan: only the speed value box on top. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolingFanTopBlockEntity>> COOLING_FAN_TOP =
            BLOCK_ENTITIES.register("cooling_fan_top", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CoolingFanTopBlockEntity(PghvBlockEntities.COOLING_FAN_TOP.get(), pos, state),
                            PghvBlocks.COOLING_FAN.get())
                    .build(null));

    /**
     * Brass cooling fan: same classes as the andesite fan, but own block entity types (each type is bound to
     * exactly one block, the variant is read from the block state).
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HvCoolingFanBlockEntity>> BRASS_COOLING_FAN =
            BLOCK_ENTITIES.register("brass_cooling_fan", () -> BlockEntityType.Builder
                    .of((pos, state) -> new HvCoolingFanBlockEntity(PghvBlockEntities.BRASS_COOLING_FAN.get(), pos, state),
                            PghvBlocks.BRASS_COOLING_FAN.get())
                    .build(null));

    /** Upper cells of the brass fan: target temperature value box on top. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolingFanTopBlockEntity>> BRASS_COOLING_FAN_TOP =
            BLOCK_ENTITIES.register("brass_cooling_fan_top", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CoolingFanTopBlockEntity(PghvBlockEntities.BRASS_COOLING_FAN_TOP.get(), pos, state),
                            PghvBlocks.BRASS_COOLING_FAN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableBoxBlockEntity>> CABLE_BOX =
            BLOCK_ENTITIES.register("cable_box", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CableBoxBlockEntity(PghvBlockEntities.CABLE_BOX.get(), pos, state),
                            PghvBlocks.CABLE_BOX.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CableSegmentBlockEntity>> CABLE_SEGMENT =
            BLOCK_ENTITIES.register("cable_segment", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CableSegmentBlockEntity(PghvBlockEntities.CABLE_SEGMENT.get(), pos, state),
                            PghvBlocks.CABLE_SEGMENT.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tomrox.pghv.block.entity.DataSocketBoxBlockEntity>> DATA_SOCKET_BOX =
            BLOCK_ENTITIES.register("data_socket_box", () -> BlockEntityType.Builder
                    .of((pos, state) -> new com.tomrox.pghv.block.entity.DataSocketBoxBlockEntity(PghvBlockEntities.DATA_SOCKET_BOX.get(), pos, state),
                            PghvBlocks.DATA_SOCKET_BOX.get())
                    .build(null));
}
