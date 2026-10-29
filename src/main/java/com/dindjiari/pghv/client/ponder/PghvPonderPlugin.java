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
 * Structure follows Create: Power Grid's PowerGridPonderPlugin by patryk3211, Apache-2.0.
 */
package com.dindjiari.pghv.client.ponder;

import com.dindjiari.pghv.Pghv;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder plugin of pghv (client only, registered in PghvClient).
 * Scene texts: Ponder reads them from the language files under
 * {@code pghv.ponder.<scene>.header} / {@code pghv.ponder.<scene>.text_N} (N = order of the
 * {@code text(...)} calls, starting at 1); the English strings in the code are only the defaults
 * for Ponder's lang generation and editing mode. Tag: {@code pghv.ponder.tag.high_voltage[.description]}.
 */
public class PghvPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Pghv.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PghvPonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PghvPonderTags.register(helper);
    }
}
