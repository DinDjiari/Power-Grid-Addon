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
 */
package com.tomrox.pghv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.tomrox.pghv.Pghv;
import com.tomrox.pghv.block.entity.DataSocketBoxBlockEntity;
import com.tomrox.pghv.cable.DataPlug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/*
 * Draws the plugged data cable of a data socket box: a plug in the fan socket, the cable (2 px thick, hanging
 * with sag when it has no waypoints, otherwise straight from point to point) and a plug in the box socket.
 * Textures: pghv:block/data_cable (cord, the 2 px wide column x 0..2 repeats along the cable) and
 * pghv:block/data_plug (plug body, x 0..4 / y 0..4 = front, the rest = sides).
 */
public class DataSocketBoxRenderer extends SafeBlockEntityRenderer<DataSocketBoxBlockEntity> {
    private static final ResourceLocation CORD = ResourceLocation.fromNamespaceAndPath(Pghv.MOD_ID, "block/data_cable");
    private static final ResourceLocation PLUG = ResourceLocation.fromNamespaceAndPath(Pghv.MOD_ID, "block/data_plug");
    private static final double RADIUS = 1.0 / 16.0;
    private static final double PIECE = 0.5;

    public DataSocketBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(DataSocketBoxBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    protected void renderSafe(DataSocketBoxBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        BlockPos socket = be.getFanSocket();
        if (level == null || socket == null)
            return;
        BlockPos origin = be.getBlockPos();
        List<Vec3> path = DataPlug.path(level, socket, be.getPoints(), origin);
        if (path == null)
            return;
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite cord = atlas.apply(CORD);
        TextureAtlasSprite plug = atlas.apply(PLUG);
        VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
        Vec3 base = Vec3.atLowerCornerOf(origin);
        PoseStack.Pose pose = ms.last();

        // Plugs
        Vec3 fanFace = DataPlug.fanSocketFace(level, socket);
        Direction fanOut = DataPlug.fanFacing(level, socket);
        Vec3 boxFace = DataPlug.boxSocketFace(level, origin);
        Direction boxOut = DataPlug.boxFacing(level, origin);
        if (fanFace != null && fanOut != null)
            plug(vc, pose, level, plug, fanFace.subtract(base), fanOut, fanFace);
        if (boxFace != null && boxOut != null)
            plug(vc, pose, level, plug, boxFace.subtract(base), boxOut, boxFace);

        // Cable centre line
        List<Vec3> line = new ArrayList<>();
        if (be.getPoints().isEmpty()) {
            Vec3 a = path.get(0), b = path.get(path.size() - 1);
            double len = a.distanceTo(b);
            double sag = Math.min(1.5, 0.04 * len + 0.05);
            int n = Math.max(2, (int) Math.ceil(len / PIECE));
            for (int i = 0; i <= n; ++i) {
                double t = (double) i / n;
                line.add(a.lerp(b, t).add(0, -4 * sag * t * (1 - t), 0));
            }
        } else {
            line.add(path.get(0));
            for (int k = 1; k < path.size(); ++k) {
                Vec3 a = path.get(k - 1), b = path.get(k);
                int n = Math.max(1, (int) Math.ceil(a.distanceTo(b) / PIECE));
                for (int i = 1; i <= n; ++i)
                    line.add(a.lerp(b, (double) i / n));
            }
        }
        for (int i = 1; i < line.size(); ++i)
            tube(vc, pose, level, cord, line.get(i - 1).subtract(base), line.get(i).subtract(base),
                    line.get(i - 1).lerp(line.get(i), 0.5));
    }

    private static int lightAt(Level level, Vec3 world) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(world));
    }

    /** Square tube from a to b (BE-local), side length 2 * RADIUS. */
    private static void tube(VertexConsumer vc, PoseStack.Pose pose, Level level, TextureAtlasSprite sprite,
                             Vec3 a, Vec3 b, Vec3 worldMid) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0e-4)
            return;
        d = d.scale(1 / len);
        Vec3 ref = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(ref).normalize().scale(RADIUS);
        Vec3 s2 = d.cross(s1).normalize().scale(RADIUS);
        int light = lightAt(level, worldMid);
        float u0 = sprite.getU(0), u1 = sprite.getU(2 / 16f);
        float v0 = sprite.getV(0), v1 = sprite.getV((float) Math.min(1.0, len));
        Vec3[] side = { s1, s2, s1.scale(-1), s2.scale(-1) };
        for (int i = 0; i < 4; ++i) {
            Vec3 o1 = side[i], o2 = side[(i + 1) % 4];
            Vec3 n = o1.add(o2).normalize();
            Vec3 p1 = a.add(o1), p2 = a.add(o2), p3 = b.add(o2), p4 = b.add(o1);
            quad(vc, pose, p1, p2, p3, p4, n, u0, v0, u1, v1, light);
        }
    }

    /** Plug body: 4 x 4 px, PLUG_LENGTH long, from the socket face outwards. */
    private static void plug(VertexConsumer vc, PoseStack.Pose pose, Level level, TextureAtlasSprite sprite,
                             Vec3 face, Direction out, Vec3 worldFace) {
        Vec3 n = Vec3.atLowerCornerOf(out.getNormal());
        Vec3 c = face.add(n.scale(DataPlug.PLUG_LENGTH / 2));
        double h = 2.0 / 16.0, l = DataPlug.PLUG_LENGTH / 2;
        double hx = out.getAxis() == Direction.Axis.X ? l : h;
        double hy = out.getAxis() == Direction.Axis.Y ? l : h;
        double hz = out.getAxis() == Direction.Axis.Z ? l : h;
        int light = lightAt(level, worldFace.add(n.scale(0.3)));
        double x0 = c.x - hx, x1 = c.x + hx, y0 = c.y - hy, y1 = c.y + hy, z0 = c.z - hz, z1 = c.z + hz;
        float fu0 = sprite.getU(0), fu1 = sprite.getU(4 / 16f), fv0 = sprite.getV(0), fv1 = sprite.getV(4 / 16f);
        float su0 = sprite.getU(4 / 16f), su1 = sprite.getU(8 / 16f);
        for (Direction dir : Direction.values()) {
            boolean front = dir.getAxis() == out.getAxis();
            float u0 = front ? fu0 : su0, u1 = front ? fu1 : su1;
            Vec3 nn = Vec3.atLowerCornerOf(dir.getNormal());
            Vec3[] q = switch (dir) {
                case DOWN -> new Vec3[] { new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1) };
                case UP -> new Vec3[] { new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0) };
                case NORTH -> new Vec3[] { new Vec3(x1, y0, z0), new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0) };
                case SOUTH -> new Vec3[] { new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1) };
                case WEST -> new Vec3[] { new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0) };
                case EAST -> new Vec3[] { new Vec3(x1, y0, z1), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1) };
            };
            quad(vc, pose, q[0], q[1], q[2], q[3], nn, u0, fv1, u1, fv0, light);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, Vec3 n,
                             float u0, float v0, float u1, float v1, int light) {
        vertex(vc, pose, p1, n, u0, v0, light);
        vertex(vc, pose, p2, n, u1, v0, light);
        vertex(vc, pose, p3, n, u1, v1, light);
        vertex(vc, pose, p4, n, u0, v1, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, Vec3 n, float u, float v, int light) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
