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
package com.tomrox.pghv.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.collections.ModdedBlocks;

import java.util.UUID;
import java.util.function.Predicate;

/** Shared helpers for the pghv game tests. Positions are relative to the test structure unless noted. */
public final class PghvTestUtil {
    public static final String TEMPLATE = "empty";

    private PghvTestUtil() {
    }

    /** Stone floor at y = 0 over the whole 16x16 test area; tests build from y = 1. */
    public static void floor(GameTestHelper helper) {
        for (int x = 0; x < 16; ++x)
            for (int z = 0; z < 16; ++z)
                helper.setBlock(x, 0, z, Blocks.STONE);
    }

    /** Fresh fake server player (unique profile) in the given game mode, standing at {@code pos}. */
    public static FakePlayer player(GameTestHelper helper, GameType mode, BlockPos pos) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pghv_test"));
        player.setGameMode(mode);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        player.moveTo(at.x, at.y, at.z, 0, 0);
        player.getInventory().clearContent();
        return player;
    }

    /** Right-click with the main-hand item on face {@code face} of block {@code pos}, like a real player. */
    public static InteractionResult use(GameTestHelper helper, FakePlayer player, BlockPos pos, Direction face) {
        BlockPos abs = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        ItemStack stack = player.getMainHandItem();
        return player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, abs, false));
    }

    /** Breaks a block as the player (survival: tool and loot rules apply). */
    public static boolean breakBlock(GameTestHelper helper, FakePlayer player, BlockPos pos) {
        return player.gameMode.destroyBlock(helper.absolutePos(pos));
    }

    /** Sum of the item counts of all item entities in the test area matching {@code filter}. */
    public static int countDrops(GameTestHelper helper, Predicate<ItemStack> filter) {
        int count = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
            if (e.isAlive() && filter.test(e.getItem()))
                count += e.getItem().getCount();
        }
        return count;
    }

    public static int countDrops(GameTestHelper helper, Item item) {
        return countDrops(helper, s -> s.is(item));
    }

    /** Power Grid creative voltage source at pos with the given voltage (terminal 0 = +, 1 = -). */
    public static CreativeSourceBlockEntity voltageSource(GameTestHelper helper, BlockPos pos, float volts) {
        helper.setBlock(pos, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        CreativeSourceBlockEntity be = helper.getBlockEntity(pos);
        be.setValue(volts);
        return be;
    }

    /** Plain Power Grid wire between two terminals (absolute positions are computed here). */
    public static ElectricWire wire(GameTestHelper helper, BlockPos a, int terminalA, BlockPos b, int terminalB, float ohm) {
        ElectricWire wire = GlobalElectricNetworks.makeSimpleConnection(helper.getLevel(),
                new BlockWireEndpoint(helper.absolutePos(a), terminalA),
                new BlockWireEndpoint(helper.absolutePos(b), terminalB), ohm);
        if (wire == null)
            throw new IllegalStateException("Could not connect " + a + "/" + terminalA + " to " + b + "/" + terminalB);
        return wire;
    }

    /**
     * Simulates a chunk unload + reload of one block entity: the old instance is unloaded the way
     * LevelChunk does it (onChunkUnloaded, then removal), a new instance is loaded from the saved NBT.
     */
    public static <T extends BlockEntity> T reload(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        BlockEntity old = level.getBlockEntity(abs);
        if (old == null)
            throw new IllegalStateException("No block entity at " + pos);
        CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
        old.onChunkUnloaded();
        level.getChunkAt(abs).removeBlockEntity(abs);
        BlockState state = level.getBlockState(abs);
        BlockEntity loaded = BlockEntity.loadStatic(abs, state, tag, level.registryAccess());
        if (loaded == null)
            throw new IllegalStateException("Reload failed at " + pos);
        level.setBlockEntity(loaded);
        @SuppressWarnings("unchecked")
        T result = (T) level.getBlockEntity(abs);
        return result;
    }

    public static void assertNear(GameTestHelper helper, double actual, double expected, double relTolerance, String what) {
        double tol = Math.abs(expected) * relTolerance;
        if (Math.abs(actual - expected) > tol)
            helper.fail(what + ": expected " + expected + " +- " + tol + ", got " + actual);
    }
}
