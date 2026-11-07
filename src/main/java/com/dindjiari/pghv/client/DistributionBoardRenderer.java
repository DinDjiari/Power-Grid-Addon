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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.dindjiari.pghv.wiring.BoardModule;
import com.dindjiari.pghv.wiring.DistributionBoardBlock;
import com.dindjiari.pghv.wiring.DistributionBoardBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedPartialModels;
import org.patryk3211.powergrid.electricity.base.DirectionalElectricBlock;
import org.patryk3211.powergrid.electricity.base.HorizontalElectricBlock;
import org.patryk3211.powergrid.electricity.base.SurfaceElectricBlock;
import org.patryk3211.powergrid.electricity.fuse.FuseHolderBlock;
import org.patryk3211.powergrid.electricity.fuse.FuseState;
import org.patryk3211.powergrid.electricity.gauge.EnergyMeterScreen;


/*
 * Draws the modules of an open distribution board block in the north-local frame:
 *  - Power Grid fuse holder (its own block model, standing against the back wall, vertical) per place;
 *  - Power Grid energy meter (its own block model, scaled 0.6, across all 4 places) with moving needles (board kWh);
 *  - own RCD / main switch partial models (lever on/off), defined at place 0 (x 12..16) and shifted per place.
 */
public class DistributionBoardRenderer extends SafeBlockEntityRenderer<DistributionBoardBlockEntity> {
    public DistributionBoardRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(DistributionBoardBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(DistributionBoardBlock.OPEN) || !state.getValue(DistributionBoardBlock.OPEN))
            return;
        Direction facing = state.getValue(DistributionBoardBlock.HORIZONTAL_FACING);
        float yaw = 180.0f - facing.toYRot();
        var blocks = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer cutout = buffer.getBuffer(RenderType.cutoutMipped());

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(yaw));
        ms.translate(-0.5, -0.5, -0.5);
        for (int s = 0; s < 4; ++s) {
            BoardModule m = be.module(s);
            if (m == null)
                continue;
            switch (m) {
                case FUSE -> {
                    FuseState fs = switch (be.state(s)) {
                        case 1 -> FuseState.CLOSED;
                        case 2 -> FuseState.BLOWN;
                        default -> FuseState.OPEN;
                    };
                    BlockState fuse = ModdedBlocks.FUSE_HOLDER.getDefaultState()
                            .setValue(DirectionalElectricBlock.FACING, Direction.SOUTH)
                            .setValue(SurfaceElectricBlock.ALONG_FIRST_AXIS, false)
                            .setValue(FuseHolderBlock.STATE, fs);
                    ms.pushPose();
                    // fuse model stands on its surface at z 12..16; move it onto the back plate (z 7)
                    ms.translate((16 - 4 * s - 2) / 16.0 - 0.5, 0, -9 / 16.0);
                    blocks.renderSingleBlock(fuse, ms, buffer, light, overlay);
                    ms.popPose();
                }
                case METER -> {
                    if (s != 0)
                        continue;
                    BlockState meter = ModdedBlocks.ENERGY_METER.getDefaultState()
                            .setValue(HorizontalElectricBlock.HORIZONTAL_FACING, Direction.SOUTH);
                    ms.pushPose();
                    ms.translate(0.5, 0.42, 7 / 16.0);
                    ms.scale(0.6f, 0.6f, 0.6f);
                    ms.translate(-0.5, -0.5, -1.0);
                    blocks.renderSingleBlock(meter, ms, buffer, light, overlay);
                    DistributionBoardBlockEntity c = be.controllerBe();
                    if (c != null)
                        needles(ms, buffer, meter, light, c.energyWh() / 1000.0);
                    ms.popPose();
                }
                case RCD, MAIN_SWITCH -> {
                    boolean on = be.state(s) == 1;
                    var model = m == BoardModule.RCD
                            ? (on ? PghvPartialModels.RCD_ON : PghvPartialModels.RCD_OFF)
                            : (on ? PghvPartialModels.MAIN_SWITCH_ON : PghvPartialModels.MAIN_SWITCH_OFF);
                    ms.pushPose();
                    ms.translate(-4 * s / 16.0, 0, 0);
                    CachedBuffers.partial(model, state).light(light).renderInto(ms, cutout);
                    ms.popPose();
                }
            }
        }
        ms.popPose();
    }

    /**
     * The five dials of Power Grid's energy meter (10000 ... 1 kWh), drawn like Power Grid's EnergyMeterRenderer with its
     * needle model, inside the scaled meter transform. Energy = consumption of the whole board in kWh.
     */
    private static void needles(PoseStack ms, MultiBufferSource buffer, BlockState meter, int light, double kwh) {
        var consumer = buffer.getBuffer(RenderType.solid());
        int index = 4;
        for (int i = 10000; i >= 1; i /= 10) {
            float angle = EnergyMeterScreen.getDialAngle(kwh, kwh, i, 0);
            double y = index % 2 == 0 ? 11.5 / 16 : 7.5 / 16;
            double x = (12.0 - index * 2) / 16;
            CachedBuffers.partial(ModdedPartialModels.ENERGY_METER_NEEDLE, meter)
                    .rotateYCenteredDegrees(-Direction.SOUTH.toYRot() - 180)
                    .translate(x, y, 0)
                    .rotateZ(-angle)
                    .light(light)
                    .renderInto(ms, consumer);
            --index;
        }
    }
}
