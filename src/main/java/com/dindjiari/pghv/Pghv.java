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
package com.dindjiari.pghv;

import com.dindjiari.pghv.registry.PghvSounds;
import com.dindjiari.pghv.registry.PghvBlockEntities;
import com.dindjiari.pghv.registry.PghvBlocks;
import com.dindjiari.pghv.registry.PghvDataComponents;
import com.dindjiari.pghv.registry.PghvItems;
import com.dindjiari.pghv.registry.PghvRecipes;
import com.dindjiari.pghv.registry.PghvTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Pghv.MOD_ID)
public class Pghv {
    public static final String MOD_ID = "pghv";

    public Pghv(IEventBus modBus, ModContainer container) {
        PghvBlocks.BLOCKS.register(modBus);
        PghvItems.ITEMS.register(modBus);
        PghvBlockEntities.BLOCK_ENTITIES.register(modBus);
        PghvTabs.TABS.register(modBus);
        PghvDataComponents.DATA_COMPONENTS.register(modBus);
        PghvRecipes.RECIPE_SERIALIZERS.register(modBus);
        PghvSounds.SOUNDS.register(modBus);
        modBus.addListener(com.dindjiari.pghv.wiring.AutoRoutePayload::register);
    }
}
