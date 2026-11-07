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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Multiblock logic of the distribution board: board blocks with the same facing that touch in their wall plane
 * (up/down/left/right as seen from the front) form one board, at most MAX x MAX blocks. The top-left block is the
 * controller (feed panel). Directions: left = facing.getClockWise(), right = facing.getCounterClockWise().
 */
public final class BoardStructure {
    public static final int MAX = 8;

    private BoardStructure() {
    }

    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean isBoard(Level level, BlockPos pos, Direction facing) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof DistributionBoardBlock && s.getValue(DistributionBoardBlock.HORIZONTAL_FACING) == facing;
    }

    /** Connected board blocks around start (start included if it is a board block or extra). */
    public static List<BlockPos> component(Level level, BlockPos start, Direction facing, @org.jetbrains.annotations.Nullable BlockPos extra,
                                           @org.jetbrains.annotations.Nullable BlockPos exclude) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> out = new ArrayList<>();
        queue.add(start.immutable());
        seen.add(start.immutable());
        Direction[] dirs = { Direction.UP, Direction.DOWN, left(facing), right(facing) };
        while (!queue.isEmpty() && out.size() < MAX * MAX * 2) {
            BlockPos p = queue.poll();
            if (p.equals(exclude))
                continue;
            if (!p.equals(extra) && !isBoard(level, p, facing))
                continue;
            out.add(p);
            for (Direction d : dirs) {
                BlockPos q = p.relative(d);
                if (seen.add(q))
                    queue.add(q);
            }
        }
        return out;
    }

    /** Coordinate along the left direction (larger = further left as seen from the front). */
    private static int leftCoord(BlockPos p, Direction facing) {
        Direction l = left(facing);
        return p.getX() * l.getStepX() + p.getZ() * l.getStepZ();
    }

    public static boolean fits(List<BlockPos> blocks, Direction facing) {
        if (blocks.isEmpty())
            return true;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, minL = Integer.MAX_VALUE, maxL = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            int l = leftCoord(p, facing);
            minL = Math.min(minL, l);
            maxL = Math.max(maxL, l);
        }
        return maxY - minY < MAX && maxL - minL < MAX;
    }

    /** Top-left block: highest, then furthest left. */
    public static BlockPos controller(List<BlockPos> blocks, Direction facing) {
        BlockPos best = blocks.get(0);
        for (BlockPos p : blocks) {
            if (p.getY() > best.getY() || (p.getY() == best.getY() && leftCoord(p, facing) > leftCoord(best, facing)))
                best = p;
        }
        return best;
    }

    /** Recomputes connections, controller and door state of the board containing pos (server). */
    public static void refresh(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof DistributionBoardBlock))
            return;
        Direction facing = s.getValue(DistributionBoardBlock.HORIZONTAL_FACING);
        List<BlockPos> comp = component(level, pos, facing, null, null);
        if (comp.isEmpty())
            return;
        Set<BlockPos> set = new HashSet<>(comp);
        BlockPos ctrl = controller(comp, facing);
        boolean open = level.getBlockState(ctrl).getValue(DistributionBoardBlock.OPEN);
        for (BlockPos p : comp) {
            BlockState old = level.getBlockState(p);
            BlockState n = old
                    .setValue(DistributionBoardBlock.UP, set.contains(p.above()))
                    .setValue(DistributionBoardBlock.DOWN, set.contains(p.below()))
                    .setValue(DistributionBoardBlock.LEFT, set.contains(p.relative(left(facing))))
                    .setValue(DistributionBoardBlock.RIGHT, set.contains(p.relative(right(facing))))
                    .setValue(DistributionBoardBlock.OPEN, open);
            if (n != old)
                level.setBlock(p, n, Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof DistributionBoardBlockEntity be)
                be.setController(ctrl, p.equals(ctrl));
        }
    }

    /** Opens or closes the door of the whole board. */
    public static void setOpen(Level level, BlockPos pos, boolean open) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof DistributionBoardBlock))
            return;
        Direction facing = s.getValue(DistributionBoardBlock.HORIZONTAL_FACING);
        for (BlockPos p : component(level, pos, facing, null, null)) {
            BlockState old = level.getBlockState(p);
            if (old.getValue(DistributionBoardBlock.OPEN) != open)
                level.setBlock(p, old.setValue(DistributionBoardBlock.OPEN, open), Block.UPDATE_ALL);
        }
    }

    /** Block-local hit (0..1) -> north-local pixels (model coordinates of a north-facing board). */
    public static Vec3 northLocal(Vec3 local, Direction facing) {
        double x = local.x, z = local.z;
        double nx, nz;
        switch (facing) {
            case EAST -> { nx = z; nz = 1 - x; }
            case SOUTH -> { nx = 1 - x; nz = 1 - z; }
            case WEST -> { nx = 1 - z; nz = x; }
            default -> { nx = x; nz = z; }
        }
        return new Vec3(nx * 16, local.y * 16, nz * 16);
    }

    /** Rail place 0..3 (0 = left as seen from the front) at a north-local x (pixels); -1 outside the rail. */
    public static int slotAt(Vec3 northPx) {
        if (northPx.y < 2.5 || northPx.y > 13.5)
            return -1;
        int s = (int) Math.floor((16 - northPx.x) / 4.0);
        return s < 0 || s > 3 ? -1 : s;
    }
}
