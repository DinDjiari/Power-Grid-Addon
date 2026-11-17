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
package com.dindjiari.pghv.util;

import com.dindjiari.pghv.PghvConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Overvoltage protection by explosion (Discord request "when power go too high explode"): above the limit the device
 * smokes and sizzles; after {@link PghvConfig#OVERVOLTAGE_DELAY_TICKS} ticks in a row it is destroyed (no drop) with an
 * explosion. One instance per block entity, server side only, not saved (a reload restarts the countdown).
 */
public final class OvervoltageGuard {
    private int ticksOver;

    /** True while the voltage is above the limit (for goggles). */
    public boolean isOver() {
        return ticksOver > 0;
    }

    /**
     * Call every server tick with the measured voltage. Returns true when the device must explode now; the caller
     * removes its block(s) and then calls {@link #explode}.
     */
    public boolean tick(ServerLevel level, BlockPos pos, double voltage, double limit) {
        if (!PghvConfig.OVERVOLTAGE_EXPLOSIONS.get() || !(Math.abs(voltage) > limit)) {
            ticksOver = 0;
            return false;
        }
        ++ticksOver;
        if (ticksOver % 5 == 1) {
            Vec3 c = Vec3.atCenterOf(pos);
            level.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.3, c.z, 3, 0.2, 0.1, 0.2, 0.01);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 1.6f);
        }
        if (ticksOver % 10 == 0)
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, 0.25, 0.25, 0.25, 0.1);
        if (ticksOver >= PghvConfig.OVERVOLTAGE_DELAY_TICKS.get()) {
            ticksOver = 0;
            return true;
        }
        return false;
    }

    /** Explosion at the device (entities take damage; blocks only if the config says so). */
    public static void explode(ServerLevel level, BlockPos pos, float power) {
        Vec3 c = Vec3.atCenterOf(pos);
        level.explode(null, c.x, c.y, c.z, power,
                PghvConfig.EXPLOSION_BREAKS_BLOCKS.get() ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
    }
}
