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
package com.dindjiari.pghv.wiring;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Automatic route for in-wall cables: A* through full solid blocks (6 neighbours). */
public final class InWallRouting {
    public static final int MAX_NODES = 4096;

    private InWallRouting() {
    }

    /** Full solid block a cable may run through. */
    public static boolean isWall(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && level.getBlockEntity(pos) == null && s.isCollisionShapeFullBlock(level, pos);
    }

    /** Where a route starts/ends for a device: the wall block behind an own wall device, else the device itself. */
    public static BlockPos anchor(Level level, BlockPos device) {
        BlockState s = level.getBlockState(device);
        if (s.getBlock() instanceof WallDeviceBlock<?>) {
            BlockPos m = WallDeviceBlock.mountPos(device, s);
            if (isWall(level, m))
                return m;
        }
        return device;
    }

    /** Block path from -> to (both included) through wall blocks, or null. from/to themselves need not be walls. */
    @Nullable
    public static List<BlockPos> route(Level level, BlockPos from, BlockPos to) {
        record Node(BlockPos pos, int g, int f) {
        }
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> cost = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> a.f() != b.f() ? Integer.compare(a.f(), b.f()) : Integer.compare(b.g(), a.g()));
        open.add(new Node(from, 0, from.distManhattan(to)));
        cost.put(from, 0);
        int visited = 0;
        while (!open.isEmpty() && visited < MAX_NODES) {
            Node n = open.poll();
            if (n.g() > cost.getOrDefault(n.pos(), Integer.MAX_VALUE))
                continue;
            ++visited;
            if (n.pos().equals(to)) {
                List<BlockPos> path = new ArrayList<>();
                for (BlockPos p = to; p != null; p = parent.get(p))
                    path.add(p);
                Collections.reverse(path);
                return path;
            }
            for (Direction d : Direction.values()) {
                BlockPos q = n.pos().relative(d);
                if (!q.equals(to) && !isWall(level, q))
                    continue;
                if (!level.isLoaded(q))
                    continue;
                int g = n.g() + 1;
                if (g < cost.getOrDefault(q, Integer.MAX_VALUE)) {
                    cost.put(q, g);
                    parent.put(q, n.pos());
                    open.add(new Node(q, g, g + q.distManhattan(to)));
                }
            }
        }
        return null;
    }

    /** Waypoints for a block path: centres of the corner blocks (collinear blocks dropped), ends excluded if they are devices. */
    public static List<Vec3> waypoints(List<BlockPos> path, BlockPos startDevice, BlockPos targetDevice) {
        List<BlockPos> pts = new ArrayList<>(path);
        if (!pts.isEmpty() && pts.get(0).equals(startDevice))
            pts.remove(0);
        if (!pts.isEmpty() && pts.get(pts.size() - 1).equals(targetDevice))
            pts.remove(pts.size() - 1);
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); ++i) {
            if (i > 0 && i < pts.size() - 1) {
                BlockPos a = pts.get(i - 1), b = pts.get(i), c = pts.get(i + 1);
                if (b.subtract(a).equals(c.subtract(b)))
                    continue; // straight on
            }
            out.add(Vec3.atCenterOf(pts.get(i)));
        }
        return out;
    }
}
