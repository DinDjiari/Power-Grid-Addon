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
package com.dindjiari.pghv.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block entity of the two upper cells (part 2 and 3) of the cooling fan. It only carries the speed value box,
 * centred on top of the fan (on the edge shared by part 2 and 3, so both halves are clickable), like Create's
 * rotation speed controller. The value is stored in the main part (HvCoolingFanBlockEntity) and mirrored here.
 * Brass fan: the same box sets the target temperature (40..160 degC); the mode follows the block variant.
 */
public class CoolingFanTopBlockEntity extends SmartBlockEntity {
    // Set in addBehaviours (super constructor) - no initializer.
    private TopSpeedBehaviour speed;

    public CoolingFanTopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Runs in the super constructor; the block state (and thus the variant) is already known.
        BlockState state = getBlockState();
        boolean brass = state.getBlock() instanceof HvCoolingFanBlock block && block.isBrass();
        speed = new TopSpeedBehaviour(this, brass);
        behaviours.add(speed);
    }

    @Nullable
    public HvCoolingFanBlockEntity getMain() {
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof HvCoolingFanBlock))
            return null;
        BlockPos main = HvCoolingFanBlock.mainPos(worldPosition, state);
        return level.getBlockEntity(main) instanceof HvCoolingFanBlockEntity be ? be : null;
    }

    @Override
    public void tick() {
        super.tick();
        HvCoolingFanBlockEntity main = getMain();
        if (main != null && speed != null && speed.value != main.getSpeedValue())
            speed.value = main.getSpeedValue(); // mirror only, no callback/sync needed
    }

    /** Same menu as the main part; changes are forwarded to the main part. */
    public static class TopSpeedBehaviour extends HvCoolingFanBlockEntity.FanSpeedBehaviour {
        private final CoolingFanTopBlockEntity owner;

        public TopSpeedBehaviour(CoolingFanTopBlockEntity be) {
            this(be, false);
        }

        public TopSpeedBehaviour(CoolingFanTopBlockEntity be, boolean brass) {
            super(Component.translatable(brass ? "pghv.gui.brass_cooling_fan.target" : "pghv.gui.cooling_fan.speed"),
                    be, new TopBox(), brass);
            this.owner = be;
        }

        @Override
        public void setValue(int value) {
            super.setValue(value);
            HvCoolingFanBlockEntity main = owner.getMain();
            if (main != null && owner.getLevel() != null && !owner.getLevel().isClientSide)
                main.setSpeedValue(this.value);
        }
    }

    /** Value box lying flat on the top face, centred on the edge between part 2 and part 3, at the panel depth. */
    public static class TopBox extends ValueBoxTransform {
        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            // Expressed for facing SOUTH like the main SpeedBox; part 2 touches part 3 on its x = 16 side (south frame),
            // part 3 touches part 2 on its x = 0 side.
            int part = state.getValue(HvCoolingFanBlock.PART);
            double z0 = state.getValue(HvCoolingFanBlock.SLOT) * HvCoolingFanBlock.SLOT_SIZE;
            double x = part == 2 ? 16 : 0;
            double z = 16 - z0 - HvCoolingFanBlock.SLOT_SIZE / 2.0;
            return rotateHorizontally(state, VecHelper.voxelSpace(x, 16.05, z));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            // Like ValueBoxTransform.Sided for the UP side, text readable from the fan's front.
            float yRot = AngleHelper.horizontalAngle(state.getValue(HvCoolingFanBlock.FACING)) + 180;
            TransformStack.of(ms).rotateYDegrees(yRot).rotateXDegrees(90);
        }
    }
}
