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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PghvTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Pghv.MOD_ID);

    /** Core combinations shown in the creative tab. */
    private static final CableCores[] CREATIVE_CORES = {
            CableCores.DEFAULT,
            CableCores.of(CableCores.COPPER, CableCores.COPPER, CableCores.IRON),
            CableCores.of(CableCores.GOLD, CableCores.GOLD, CableCores.GOLD)
    };

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pghv.main"))
                    .icon(() -> new ItemStack(PghvItems.HV_VOLTMETER.get()))
                    .displayItems((params, output) -> {
                        output.accept(PghvItems.HV_VOLTMETER.get());
                        output.accept(PghvItems.COOLING_FAN.get());
                        output.accept(PghvItems.BRASS_COOLING_FAN.get());
                        output.accept(PghvItems.DATA_CABLE.get());
                        output.accept(PghvItems.DATA_SOCKET_BOX.get());
                        output.accept(PghvItems.CABLE_BOX.get());
                        for (CableCores cores : CREATIVE_CORES)
                            output.accept(cable(PghvItems.BARE_CABLE.get(), cores));
                        for (CableCores cores : CREATIVE_CORES)
                            output.accept(cable(PghvItems.UNDERGROUND_CABLE.get(), cores));
                    })
                    .build());

    private static ItemStack cable(Item item, CableCores cores) {
        ItemStack stack = new ItemStack(item);
        stack.set(PghvDataComponents.CABLE_CORES.get(), cores);
        return stack;
    }
}
