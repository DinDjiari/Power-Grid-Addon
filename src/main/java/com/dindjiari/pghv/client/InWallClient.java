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

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.wiring.AutoRoutePayload;
import com.dindjiari.pghv.wiring.InWallCable;
import com.dindjiari.pghv.wiring.InWallLaying;
import com.dindjiari.pghv.wiring.WallDeviceBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/*
 * Client side of the in-wall installation:
 *   - key "Unterputz: Autoroute" (default R): sends AutoRoutePayload for the block under the crosshair while laying;
 *   - x-ray view: while the player holds insulated copper wire, all in-wall cables of loaded wall devices within
 *     48 blocks are drawn as lines through the walls (no depth test), plus the cable being laid up to the crosshair.
 */
public final class InWallClient {
    public static final KeyMapping AUTOROUTE = new KeyMapping("key.pghv.autoroute", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, "key.categories.pghv");
    private static final double RANGE = 48.0;

    private InWallClient() {
    }

    @EventBusSubscriber(modid = Pghv.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(AUTOROUTE);
        }
    }

    @EventBusSubscriber(modid = Pghv.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (AUTOROUTE.consumeClick()) {
                LocalPlayer player = mc.player;
                if (player == null || layingStack(player) == null)
                    continue;
                if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
                    PacketDistributor.sendToServer(new AutoRoutePayload(hit.getBlockPos()));
            }
        }

        @SubscribeEvent
        public static void render(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null)
                return;
            if (!InWallLaying.isWire(player.getMainHandItem()) && !InWallLaying.isWire(player.getOffhandItem()))
                return;
            Vec3 cam = event.getCamera().getPosition();
            PoseStack ms = event.getPoseStack();
            ms.pushPose();
            ms.translate(-cam.x, -cam.y, -cam.z);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(XRay.LINES);
            PoseStack.Pose pose = ms.last();
            for (WallDeviceBlockEntity be : WallDeviceBlockEntity.CLIENT_LOADED) {
                if (be.isRemoved() || be.getLevel() != mc.level
                        || be.getBlockPos().distToCenterSqr(player.position()) > RANGE * RANGE)
                    continue;
                for (InWallCable c : be.getOwned())
                    polyline(vc, pose, c.line(), 0xFFE07A3C);
            }
            ItemStack stack = layingStack(player);
            if (stack != null) {
                CompoundTag t = InWallLaying.laying(stack);
                List<Vec3> line = new ArrayList<>();
                line.add(Vec3.atCenterOf(InWallLaying.start(t)));
                line.addAll(InWallLaying.points(t));
                if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
                    line.add(Vec3.atCenterOf(hit.getBlockPos()));
                polyline(vc, pose, line, 0xFFFFE34D);
            }
            // NO_DEPTH_TEST (always) does not switch the depth test off itself; it is on at this stage.
            RenderSystem.disableDepthTest();
            buffers.endBatch(XRay.LINES);
            RenderSystem.enableDepthTest();
            ms.popPose();
        }
    }

    @Nullable
    private static ItemStack layingStack(LocalPlayer player) {
        if (InWallLaying.laying(player.getMainHandItem()) != null)
            return player.getMainHandItem();
        if (InWallLaying.laying(player.getOffhandItem()) != null)
            return player.getOffhandItem();
        return null;
    }

    private static void polyline(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        for (int i = 1; i < pts.size(); ++i) {
            Vec3 p = pts.get(i - 1), q = pts.get(i);
            Vec3 n = q.subtract(p);
            if (n.lengthSqr() < 1e-8)
                continue;
            n = n.normalize();
            vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a)
                    .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
            vc.addVertex(pose, (float) q.x, (float) q.y, (float) q.z).setColor(r, g, b, a)
                    .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }

    /** Line render type without depth test (visible through walls). */
    private static final class XRay extends RenderType {
        static final RenderType LINES = create("pghv_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 1536,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(4.0)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .createCompositeState(false));

        private XRay(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort,
                     Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }
}
