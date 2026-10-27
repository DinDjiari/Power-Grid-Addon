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
import com.tomrox.pghv.item.BareCableItem;
import com.tomrox.pghv.item.UndergroundCableItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class PghvItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Pghv.MOD_ID);

    public static final DeferredItem<BlockItem> HV_VOLTMETER =
            ITEMS.registerSimpleBlockItem("hv_voltmeter", PghvBlocks.HV_VOLTMETER, new Item.Properties());

    public static final DeferredItem<BlockItem> COOLING_FAN =
            ITEMS.registerSimpleBlockItem("cooling_fan", PghvBlocks.COOLING_FAN, new Item.Properties());

    public static final DeferredItem<BlockItem> CABLE_BOX =
            ITEMS.registerSimpleBlockItem("cable_box", PghvBlocks.CABLE_BOX, new Item.Properties());

    public static final DeferredItem<BareCableItem> BARE_CABLE =
            ITEMS.register("bare_cable", () -> new BareCableItem(new Item.Properties()));

    public static final DeferredItem<UndergroundCableItem> UNDERGROUND_CABLE =
            ITEMS.register("underground_cable", () -> new UndergroundCableItem(new Item.Properties()));
}
