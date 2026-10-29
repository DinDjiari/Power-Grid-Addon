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
package com.tomrox.pghv.cable;

import com.tomrox.pghv.block.DataSocketBoxBlock;
import com.tomrox.pghv.block.HvCoolingFanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/*
 * Geometry of the round data sockets and the plugged data cable (shared by server and renderer).
 *
 * Fan socket (brass fan, part 1, north-local pixels): round socket centred at x 8, y 3.5, front face at
 * z = z0 - p (z0 = slot * 16/3, p = HvCoolingFanBlock.socketDepth(slot)), pointing to the fan's FACING.
 * Box socket (data_socket_box, north-local pixels): centred at x 8, y 7, front face at z 9, pointing to FACING.
 * A plug sticks PLUG_LENGTH out of a socket face; the cable starts at the plug's back end.
 */
public final class DataPlug {
    public static final double PLUG_LENGTH = 3.0 / 16.0;
    /** Max. cable length (sum of all pieces) in blocks. */
    public static final double MAX_LENGTH = 16.0;
    /** Max. number of waypoints between the two plugs. */
    public static final int MAX_POINTS = 16;

    private DataPlug() {
    }

    /** Rotates a north-local point (pixels) around the block centre and returns block-local units 0..1. */
    private static Vec3 rotate(double x, double y, double z, Direction facing) {
        double u = x / 16.0, v = y / 16.0, w = z / 16.0;
        return switch (facing) {
            case EAST -> new Vec3(1 - w, v, u);
            case SOUTH -> new Vec3(1 - u, v, 1 - w);
            case WEST -> new Vec3(w, v, 1 - u);
            default -> new Vec3(u, v, w);
        };
    }

    /** Front-face centre of the fan socket (world), or null if pos is no brass fan part 1. */
    @Nullable
    public static Vec3 fanSocketFace(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HvCoolingFanBlock fan) || !fan.isBrass()
                || state.getValue(HvCoolingFanBlock.PART) != 1)
            return null;
        double z0 = state.getValue(HvCoolingFanBlock.SLOT) * HvCoolingFanBlock.SLOT_SIZE;
        double p = HvCoolingFanBlock.socketDepth(state.getValue(HvCoolingFanBlock.SLOT));
        return Vec3.atLowerCornerOf(pos).add(rotate(8, 3.5, z0 - p, state.getValue(HvCoolingFanBlock.FACING)));
    }

    /** Front-face centre of the box socket (world), or null if pos is no data socket box. */
    @Nullable
    public static Vec3 boxSocketFace(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DataSocketBoxBlock))
            return null;
        return Vec3.atLowerCornerOf(pos).add(rotate(8, 7, 9, state.getValue(DataSocketBoxBlock.HORIZONTAL_FACING)));
    }

    @Nullable
    public static Direction fanFacing(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof HvCoolingFanBlock ? state.getValue(HvCoolingFanBlock.FACING) : null;
    }

    @Nullable
    public static Direction boxFacing(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DataSocketBoxBlock ? state.getValue(DataSocketBoxBlock.HORIZONTAL_FACING) : null;
    }

    /** Back end of a plug in a socket whose face is at face, pointing out along out. */
    public static Vec3 plugEnd(Vec3 face, Direction out) {
        return face.add(Vec3.atLowerCornerOf(out.getNormal()).scale(PLUG_LENGTH));
    }

    /**
     * Cable centre line: fan plug end, waypoints, box plug end. Null if one of the sockets is missing
     * (not loaded on the client, block broken).
     */
    @Nullable
    public static List<Vec3> path(BlockGetter level, BlockPos fanSocket, List<Vec3> points, BlockPos box) {
        Vec3 a = fanSocketFace(level, fanSocket);
        Vec3 b = boxSocketFace(level, box);
        Direction da = fanFacing(level, fanSocket), db = boxFacing(level, box);
        if (a == null || b == null || da == null || db == null)
            return null;
        List<Vec3> path = new ArrayList<>(points.size() + 2);
        path.add(plugEnd(a, da));
        path.addAll(points);
        path.add(plugEnd(b, db));
        return path;
    }

    /** Length of a poly line. */
    public static double length(List<Vec3> path) {
        double l = 0;
        for (int i = 1; i < path.size(); ++i)
            l += path.get(i - 1).distanceTo(path.get(i));
        return l;
    }

    /** Length from the fan socket over the points to an arbitrary end point (for checks while laying). */
    public static double lengthTo(BlockGetter level, BlockPos fanSocket, List<Vec3> points, Vec3 end) {
        Vec3 a = fanSocketFace(level, fanSocket);
        Direction da = fanFacing(level, fanSocket);
        if (a == null || da == null)
            return Double.POSITIVE_INFINITY;
        List<Vec3> p = new ArrayList<>(points.size() + 2);
        p.add(plugEnd(a, da));
        p.addAll(points);
        p.add(end);
        return length(p);
    }
}
