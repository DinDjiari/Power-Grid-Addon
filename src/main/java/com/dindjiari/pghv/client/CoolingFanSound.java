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
package com.dindjiari.pghv.client;

import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import com.dindjiari.pghv.registry.PghvSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/*
 * Running sound of a cooling fan (client only, started from the block entity's client tick).
 * Loop pghv:block.cooling_fan.run, recorded for 100 % andesite speed. Pitch and volume follow the rotor speed
 * (rel = rotor speed / speed at 100 %; brass reaches rel 2): pitch 0.55 + 0.45 rel, volume 0.2 + 0.5 rel, faded
 * in and out below rel 0.25. Positioned in the middle of the 2 x 2 fan.
 */
public class CoolingFanSound extends AbstractTickableSoundInstance {
    private static final Map<HvCoolingFanBlockEntity, CoolingFanSound> PLAYING = new WeakHashMap<>();

    private final HvCoolingFanBlockEntity fan;

    private CoolingFanSound(HvCoolingFanBlockEntity fan) {
        super(PghvSounds.COOLING_FAN_RUN.get(), SoundSource.BLOCKS, RandomSource.create());
        this.fan = fan;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        Direction facing = fan.getBlockState().getValue(HvCoolingFanBlock.FACING);
        BlockPos main = fan.getBlockPos();
        Vec3 c = Vec3.atCenterOf(HvCoolingFanBlock.cellOf(main, facing, 0))
                .add(Vec3.atCenterOf(HvCoolingFanBlock.cellOf(main, facing, 3))).scale(0.5);
        this.x = c.x;
        this.y = c.y;
        this.z = c.z;
        update();
    }

    /** Called every client tick by the fan. Starts the loop when the rotor turns. */
    public static void tick(HvCoolingFanBlockEntity fan) {
        CoolingFanSound s = PLAYING.get(fan);
        if (s != null && !s.isStopped() && Minecraft.getInstance().getSoundManager().isActive(s))
            return;
        if (fan.getRotorRelativeSpeed() < 0.02f)
            return;
        s = new CoolingFanSound(fan);
        PLAYING.put(fan, s);
        Minecraft.getInstance().getSoundManager().play(s);
    }

    private void update() {
        float rel = fan.getRotorRelativeSpeed();
        float fade = Mth.clamp(rel / 0.25f, 0.0f, 1.0f);
        this.pitch = Mth.clamp(0.55f + 0.45f * rel, 0.5f, 2.0f);
        this.volume = Mth.clamp(0.2f + 0.5f * rel, 0.0f, 1.2f) * fade;
    }

    @Override
    public void tick() {
        if (fan.isRemoved() || fan.getLevel() != Minecraft.getInstance().level || fan.getRotorRelativeSpeed() < 0.02f) {
            stop();
            return;
        }
        update();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
