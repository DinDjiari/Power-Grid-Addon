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
package com.tomrox.pghv.wiring;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.List;
import java.util.Locale;

/**
 * 230 V lamp (ceiling lamp, wall light): filament as a fixed resistor R = U_rated^2 / P_rated between terminal 0 (L)
 * and 1 (N). Every 5 ticks the brightness (block light 0..15) follows the electrical power: 15 at rated power and above,
 * proportionally less below (120 V gives about a quarter). No burn-out; goggles warn on overvoltage.
 */
public class LampBlockEntity extends WallDeviceBlockEntity {
    public static final double RATED_VOLTAGE = 230.0;
    public static final double RATED_POWER = 60.0;
    public static final float RESISTANCE = (float) (RATED_VOLTAGE * RATED_VOLTAGE / RATED_POWER);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public LampBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2);
        builder.connect(RESISTANCE, builder.terminalNode(0), builder.terminalNode(1));
    }

    /** Voltage across the filament. */
    public double voltage() {
        if (getElectricBehaviour() == null)
            return 0;
        var a = getElectricBehaviour().getTerminal(0);
        var b = getElectricBehaviour().getTerminal(1);
        if (a == null || b == null)
            return 0;
        double u = a.getVoltage() - b.getVoltage();
        return Double.isFinite(u) ? Math.abs(u) : 0;
    }

    @Override
    public double portVoltage(int port) {
        return voltage();
    }

    /** Light level for a voltage. */
    public static int lightFor(double voltage) {
        double p = voltage * voltage / RESISTANCE;
        if (p < 1.0)
            return 0;
        return (int) Math.max(1, Math.min(15, Math.round(15 * p / RATED_POWER)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || level.getGameTime() % 5 != 0)
            return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LIGHT))
            return;
        int light = lightFor(voltage());
        if (state.getValue(LIGHT) != light)
            level.setBlock(worldPosition, state.setValue(LIGHT, light).setValue(LIT, light > 0), Block.UPDATE_ALL);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        double u = voltage();
        line(tooltip, getBlockState().getBlock().getName().withStyle(ChatFormatting.GRAY), 0);
        line(tooltip, Component.translatable("pghv.wall.goggles.outlet_voltage",
                String.format(Locale.ROOT, "%.0f", u), String.format(Locale.ROOT, "%.0f", RATED_VOLTAGE))
                .withStyle(ChatFormatting.AQUA), 1);
        String st = WallOutletBlockEntity.status(u, RATED_VOLTAGE);
        line(tooltip, Component.translatable("pghv.wall.goggles.status." + st)
                .withStyle(st.equals("ok") ? ChatFormatting.GREEN : st.equals("dead") ? ChatFormatting.DARK_GRAY : ChatFormatting.RED), 1);
        line(tooltip, Component.translatable("pghv.lamp.goggles.power",
                String.format(Locale.ROOT, "%.0f", u * u / RESISTANCE), String.format(Locale.ROOT, "%.0f", RATED_POWER))
                .withStyle(ChatFormatting.GOLD), 1);
        cableLines(tooltip);
        return true;
    }
}
