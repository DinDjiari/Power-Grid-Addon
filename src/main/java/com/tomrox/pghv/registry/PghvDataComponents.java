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
import com.tomrox.pghv.cable.CableCores;
import com.tomrox.pghv.cable.CableLaying;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PghvDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Pghv.MOD_ID);

    /** Exactly 3 wire item ids (core 0, 1, 2) on bare_cable and underground_cable. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CableCores>> CABLE_CORES =
            DATA_COMPONENTS.register("cable_cores", () -> DataComponentType.<CableCores>builder()
                    .persistent(CableCores.CODEC)
                    .networkSynchronized(CableCores.STREAM_CODEC)
                    .build());

    /** Laying state on the held underground_cable (only while laying). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CableLaying>> CABLE_LAYING =
            DATA_COMPONENTS.register("cable_laying", () -> DataComponentType.<CableLaying>builder()
                    .persistent(CableLaying.CODEC)
                    .networkSynchronized(CableLaying.STREAM_CODEC)
                    .build());

    /** Data cable: socket cell (part 1 of a brass fan) chosen as start, only while connecting. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> DATA_CABLE_START =
            DATA_COMPONENTS.register("data_cable_start", () -> DataComponentType.<GlobalPos>builder()
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC)
                    .build());

    /**
     * Data cable laid on the ground: the three open block-wire ends (keys "W0".."W2", each a Power Grid
     * BlockWireEntityEndpoint written with write(CompoundTag)). Only while laying; DATA_CABLE_START stays set.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>> DATA_CABLE_ROUTE =
            DATA_COMPONENTS.register("data_cable_route", () -> DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                    .persistent(net.minecraft.nbt.CompoundTag.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG)
                    .build());
}
