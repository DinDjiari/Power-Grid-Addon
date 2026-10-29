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
package com.dindjiari.pghv.wiring;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * DIN-rail modules of the distribution board.
 * FUSE = Power Grid fuse holder (1 place, blows above its rating, reset with iron wire like Power Grid),
 * METER = Power Grid energy meter (all 4 places of a block, measures the whole board),
 * RCD = residual current device 30 mA (1 place), MAIN_SWITCH = main switch (1 place).
 * Main switches and RCDs of a board act together on the board's single feed link (L_in/N_in -> L/N bus).
 */
public enum BoardModule {
    FUSE("powergrid", "fuse_holder", 1),
    METER("powergrid", "energy_meter", 4),
    RCD("pghv", "rcd", 1),
    MAIN_SWITCH("pghv", "main_switch", 1);

    public final ResourceLocation itemId;
    public final int width;

    BoardModule(String ns, String path, int width) {
        this.itemId = ResourceLocation.fromNamespaceAndPath(ns, path);
        this.width = width;
    }

    public String id() {
        return itemId.getPath();
    }

    public Item item() {
        return BuiltInRegistries.ITEM.get(itemId);
    }

    public boolean hasLever() {
        return this == RCD || this == MAIN_SWITCH;
    }

    @Nullable
    public static BoardModule of(ItemStack stack) {
        if (stack.isEmpty())
            return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (BoardModule m : values())
            if (m.itemId.equals(id))
                return m;
        return null;
    }

    @Nullable
    public static BoardModule byName(String name) {
        for (BoardModule m : values())
            if (m.name().equals(name))
                return m;
        return null;
    }
}
