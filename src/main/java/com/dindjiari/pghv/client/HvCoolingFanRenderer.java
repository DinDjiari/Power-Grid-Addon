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
 * Modified from Create: Power Grid by patryk3211, Apache-2.0 (based on ElectricFanRenderer).
 */
package com.dindjiari.pghv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.dindjiari.pghv.block.HvCoolingFanBlock;
import com.dindjiari.pghv.block.entity.HvCoolingFanBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/**
 * Draws the rotor of the cooling fan. The rotor model is centred at (8,8,8) of a cell, blades in the XY plane,
 * axis z. It is moved to the hub (x = 0, y = 16, z = slot * 16/3 + 8/3 px, facing NORTH, main cell local)
 * and rotated to the real facing.
 */
public class HvCoolingFanRenderer extends SafeBlockEntityRenderer<HvCoolingFanBlockEntity> {
    public HvCoolingFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(HvCoolingFanBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if (!state.hasProperty(HvCoolingFanBlock.FACING) || !state.hasProperty(HvCoolingFanBlock.SLOT))
            return;
        Direction facing = state.getValue(HvCoolingFanBlock.FACING);
        int slot = state.getValue(HvCoolingFanBlock.SLOT);

        // Hub position relative to the cell centre, facing NORTH frame, in blocks.
        double hubZ = (slot * HvCoolingFanBlock.SLOT_SIZE + HvCoolingFanBlock.SLOT_SIZE / 2.0) / 16.0;
        float dx = -0.5f;
        float dy = 0.5f;
        float dz = (float) (hubZ - 0.5);

        float spin = be.getFanAngle(partialTicks) / 180.0f * (float) Math.PI;
        // NORTH -> 0, EAST -> -90, SOUTH -> 180, WEST -> 90 degrees around Y.
        float yaw = 180.0f - facing.toYRot();

        VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(yaw));
        ms.translate(dx, dy, dz);
        ms.mulPose(Axis.ZP.rotation(spin));
        ms.translate(-0.5, -0.5, -0.5);
        var rotor = CachedBuffers.partial(be.isBrass() ? PghvPartialModels.BRASS_COOLING_FAN_ROTOR : PghvPartialModels.COOLING_FAN_ROTOR, state);
        if (be.isBrass()) {
            // Same Create blade texture as the andesite fan, tinted brass (block models use tintindex + PghvColors).
            int tint = PghvColors.BRASS_TINT;
            rotor.color((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 255);
        }
        rotor.light(light)
                .renderInto(ms, vb);
        ms.popPose();
    }
}
