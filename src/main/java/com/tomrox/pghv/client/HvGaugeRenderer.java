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
 *
 * Modified from Create: Power Grid by patryk3211, Apache-2.0.
 */
package com.tomrox.pghv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.tomrox.pghv.block.entity.HvVoltmeterBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.patryk3211.powergrid.electricity.gauge.IGaugeBlock;

/** Renders dial and head of the HV voltmeter on each visible face. */
public class HvGaugeRenderer extends SafeBlockEntityRenderer<HvVoltmeterBlockEntity> {
    public HvGaugeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(HvVoltmeterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var gaugeState = be.getBlockState();

        var headBuffer = CachedBuffers.partial(PghvPartialModels.HV_VOLTAGE_HEAD, gaugeState);
        var dialBuffer = CachedBuffers.partial(AllPartialModels.GAUGE_DIAL, gaugeState);

        float progress = Mth.lerp(partialTicks, be.prevDialState, be.dialState);

        for (Direction facing : Iterate.directions) {
            if (!((IGaugeBlock) gaugeState.getBlock()).shouldRenderHeadOnFace(be.getLevel(), be.getBlockPos(), gaugeState, facing))
                continue;

            float dialPivot = 5.75f / 16;
            VertexConsumer vb = buffer.getBuffer(RenderType.solid());
            rotateBufferTowards(dialBuffer, facing).translate(0, dialPivot, dialPivot)
                    .rotate(Direction.EAST.getAxis(), (float) (Math.PI / 2 * -progress))
                    .translate(0, -dialPivot, -dialPivot)
                    .light(light)
                    .renderInto(ms, vb);
            rotateBufferTowards(headBuffer, facing).light(light)
                    .renderInto(ms, vb);
        }
    }

    protected SuperByteBuffer rotateBufferTowards(SuperByteBuffer buffer, Direction target) {
        return buffer.rotateCentered((float) ((-target.toYRot() - 90) / 180 * Math.PI), Direction.UP);
    }
}
