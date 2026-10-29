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
package com.dindjiari.pghv.registry;

import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.item.BareCableItem;
import com.dindjiari.pghv.item.DataCableItem;
import com.dindjiari.pghv.item.UndergroundCableItem;
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

    public static final DeferredItem<BlockItem> BRASS_COOLING_FAN =
            ITEMS.registerSimpleBlockItem("brass_cooling_fan", PghvBlocks.BRASS_COOLING_FAN, new Item.Properties());

    public static final DeferredItem<BlockItem> DATA_SOCKET_BOX =
            ITEMS.registerSimpleBlockItem("data_socket_box", PghvBlocks.DATA_SOCKET_BOX, new Item.Properties());

    public static final DeferredItem<BlockItem> WALL_BOX =
            ITEMS.registerSimpleBlockItem("wall_box", PghvBlocks.WALL_BOX, new Item.Properties());
    public static final DeferredItem<BlockItem> WALL_SWITCH =
            ITEMS.registerSimpleBlockItem("wall_switch", PghvBlocks.WALL_SWITCH, new Item.Properties());
    public static final DeferredItem<BlockItem> WALL_BUTTON =
            ITEMS.registerSimpleBlockItem("wall_button", PghvBlocks.WALL_BUTTON, new Item.Properties());
    public static final DeferredItem<BlockItem> OUTLET_EU =
            ITEMS.registerSimpleBlockItem("outlet_eu", PghvBlocks.OUTLET_EU, new Item.Properties());
    public static final DeferredItem<BlockItem> OUTLET_US =
            ITEMS.registerSimpleBlockItem("outlet_us", PghvBlocks.OUTLET_US, new Item.Properties());

    /** EU light bulb 230 V / 60 W for Power Grid's light fixtures. */
    public static final DeferredItem<com.dindjiari.pghv.item.EuLightBulb> EU_LIGHT_BULB =
            ITEMS.register("eu_light_bulb", () -> new com.dindjiari.pghv.item.EuLightBulb(new Item.Properties()));

    public static final DeferredItem<BlockItem> DISTRIBUTION_BOARD =
            ITEMS.registerSimpleBlockItem("distribution_board", PghvBlocks.DISTRIBUTION_BOARD, new Item.Properties());
    public static final DeferredItem<BlockItem> HOUSE_CONNECTION_BOX =
            ITEMS.registerSimpleBlockItem("house_connection_box", PghvBlocks.HOUSE_CONNECTION_BOX, new Item.Properties());
    public static final DeferredItem<com.dindjiari.pghv.item.BoardModuleItem> RCD =
            ITEMS.register("rcd", () -> new com.dindjiari.pghv.item.BoardModuleItem(new Item.Properties(), "pghv.module.tooltip.rcd"));
    public static final DeferredItem<com.dindjiari.pghv.item.BoardModuleItem> MAIN_SWITCH =
            ITEMS.register("main_switch", () -> new com.dindjiari.pghv.item.BoardModuleItem(new Item.Properties(), "pghv.module.tooltip.main_switch"));

    public static final DeferredItem<BlockItem> CABLE_BOX =
            ITEMS.registerSimpleBlockItem("cable_box", PghvBlocks.CABLE_BOX, new Item.Properties());

    public static final DeferredItem<BareCableItem> BARE_CABLE =
            ITEMS.register("bare_cable", () -> new BareCableItem(new Item.Properties()));

    public static final DeferredItem<UndergroundCableItem> UNDERGROUND_CABLE =
            ITEMS.register("underground_cable", () -> new UndergroundCableItem(new Item.Properties()));

    public static final DeferredItem<DataCableItem> DATA_CABLE =
            ITEMS.register("data_cable", () -> new DataCableItem(new Item.Properties()));
}
