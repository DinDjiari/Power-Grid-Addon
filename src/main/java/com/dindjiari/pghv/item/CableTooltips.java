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
package com.dindjiari.pghv.item;

import com.dindjiari.pghv.cable.CableCores;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Shared tooltip lines for cable items: cores with Ohm/block and maximum current. */
public final class CableTooltips {
    private CableTooltips() {
    }

    public static void addCores(ItemStack stack, @Nullable HolderLookup.Provider registries, List<Component> tooltip) {
        CableCores cores = CableCores.get(stack);
        tooltip.add(Component.translatable("pghv.cable.tooltip.cores").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < CableCores.COUNT; ++i) {
            var id = cores.core(i);
            tooltip.add(Component.literal(" ")
                    .append(Component.translatable("pghv.cable.tooltip.core", i + 1, CableCores.name(id)))
                    .withStyle(ChatFormatting.GOLD));
            if (registries != null) {
                tooltip.add(Component.literal("   ")
                        .append(Component.translatable("pghv.cable.tooltip.core_values",
                                CableCores.formatOhm(CableCores.ohmPerBlock(registries, id)),
                                CableCores.formatAmps(CableCores.maxCurrent(registries, id))))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
