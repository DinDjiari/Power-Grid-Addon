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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * One in-wall cable (2 cores L/N) from port {@code portA} of the owning wall device at {@code a} to port
 * {@code portB} of the device at {@code b} (own wall device or any Power Grid electric block, port 0 = terminals 0/1).
 * {@code points} are the waypoints between the two devices (world coordinates), {@code wires} the number of
 * insulated copper wire items it is made of (dropped when it is removed).
 */
public record InWallCable(BlockPos a, int portA, BlockPos b, int portB, List<Vec3> points, int wires, boolean creative) {
    public InWallCable {
        a = a.immutable();
        b = b.immutable();
        points = List.copyOf(points);
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putLong("A", a.asLong());
        t.putInt("PA", portA);
        t.putLong("B", b.asLong());
        t.putInt("PB", portB);
        t.put("P", pointsTag(points));
        t.putInt("W", wires);
        if (creative)
            t.putBoolean("C", true);
        return t;
    }

    public static InWallCable fromTag(CompoundTag t) {
        return new InWallCable(BlockPos.of(t.getLong("A")), t.getInt("PA"), BlockPos.of(t.getLong("B")), t.getInt("PB"),
                points(t.getList("P", Tag.TAG_COMPOUND)), t.getInt("W"), t.getBoolean("C"));
    }

    public static ListTag pointsTag(List<Vec3> pts) {
        ListTag list = new ListTag();
        for (Vec3 p : pts) {
            CompoundTag c = new CompoundTag();
            c.putDouble("X", p.x);
            c.putDouble("Y", p.y);
            c.putDouble("Z", p.z);
            list.add(c);
        }
        return list;
    }

    public static List<Vec3> points(ListTag list) {
        List<Vec3> out = new ArrayList<>(list.size());
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            out.add(new Vec3(c.getDouble("X"), c.getDouble("Y"), c.getDouble("Z")));
        }
        return out;
    }

    /** Full centre line: centre of device a, waypoints, centre of device b. */
    public List<Vec3> line() {
        List<Vec3> l = new ArrayList<>(points.size() + 2);
        l.add(Vec3.atCenterOf(a));
        l.addAll(points);
        l.add(Vec3.atCenterOf(b));
        return l;
    }

    public double length() {
        return InWallCable.length(line());
    }

    public static double length(List<Vec3> line) {
        double l = 0;
        for (int i = 1; i < line.size(); ++i)
            l += line.get(i - 1).distanceTo(line.get(i));
        return l;
    }
}
