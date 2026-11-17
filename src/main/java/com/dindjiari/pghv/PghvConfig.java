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

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (config/pghv-server.toml). */
public final class PghvConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue OVERVOLTAGE_EXPLOSIONS;
    public static final ModConfigSpec.BooleanValue EXPLOSION_BREAKS_BLOCKS;
    public static final ModConfigSpec.IntValue OVERVOLTAGE_DELAY_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("overvoltage");
        OVERVOLTAGE_EXPLOSIONS = b.comment("Devices explode when their voltage stays above the limit (Discord request).")
                .define("explosions", true);
        EXPLOSION_BREAKS_BLOCKS = b.comment("Explosions also break surrounding blocks (otherwise only the device is destroyed).")
                .define("breakBlocks", false);
        OVERVOLTAGE_DELAY_TICKS = b.comment("Ticks above the limit before the device explodes (smoke and sizzling as a warning).")
                .defineInRange("delayTicks", 40, 1, 1200);
        b.pop();
        SPEC = b.build();
    }

    private PghvConfig() {
    }
}
