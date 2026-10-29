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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cable route between path points (start box, anchors, target box).
 * Each leg is made of axis-parallel runs. Leg order (so that the cable leaves and enters
 * the boxes horizontally whenever possible):
 *   first leg: X, Z, Y   middle legs: X, Z, Y   last leg: Y, Z, X   single leg: X, Y, Z
 */
public final class CablePath {
    public static final int MAX_LENGTH = 256;
    public static final int MAX_ANCHORS = 32;

    private static final Direction.Axis[] FIRST = { Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y };
    private static final Direction.Axis[] LAST = { Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X };
    private static final Direction.Axis[] SINGLE = { Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z };

    private CablePath() {
    }

    /**
     * All cells from the first to the last point, both included, each adjacent to the previous one.
     * If {@code endsAtBox} is false (laying still in progress) the last leg uses the middle order.
     */
    public static List<BlockPos> cells(List<BlockPos> points, boolean endsAtBox) {
        List<BlockPos> out = new ArrayList<>();
        if (points.isEmpty())
            return out;
        out.add(points.get(0).immutable());
        int legs = points.size() - 1;
        for (int i = 0; i < legs; ++i) {
            Direction.Axis[] order;
            if (endsAtBox && legs == 1)
                order = SINGLE;
            else if (endsAtBox && i == legs - 1)
                order = LAST;
            else
                order = FIRST;
            appendLeg(out, points.get(i), points.get(i + 1), order);
        }
        return out;
    }

    public static List<BlockPos> cells(List<BlockPos> points) {
        return cells(points, true);
    }

    private static void appendLeg(List<BlockPos> out, BlockPos from, BlockPos to, Direction.Axis[] order) {
        BlockPos cur = from;
        for (Direction.Axis axis : order) {
            int target = to.get(axis);
            while (cur.get(axis) != target) {
                int step = target > cur.get(axis) ? 1 : -1;
                cur = cur.relative(axis, step);
                out.add(cur.immutable());
            }
        }
    }

    /** Cable length of a finished path: cells without both box cells, at least 1. */
    public static int length(List<BlockPos> cells) {
        return Math.max(1, cells.size() - 2);
    }

    /** Direction from {@code from} to the adjacent cell {@code to}, null if not adjacent. */
    @Nullable
    public static Direction direction(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        for (Direction d : Direction.values()) {
            if (d.getStepX() == dx && d.getStepY() == dy && d.getStepZ() == dz)
                return d;
        }
        return null;
    }

    public static long[] pack(List<BlockPos> points) {
        long[] out = new long[points.size()];
        for (int i = 0; i < out.length; ++i)
            out[i] = points.get(i).asLong();
        return out;
    }

    public static List<BlockPos> unpack(long[] data) {
        List<BlockPos> out = new ArrayList<>(data.length);
        for (long l : data)
            out.add(BlockPos.of(l));
        return out;
    }
}
