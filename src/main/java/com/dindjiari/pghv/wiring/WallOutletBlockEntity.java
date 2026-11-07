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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** Outlet: two terminals; goggles show the voltage against the nominal value (EU 230 V, US 120 V). */
public class WallOutletBlockEntity extends WallDeviceBlockEntity {
    /** Below this the outlet counts as dead. */
    public static final double DEAD_VOLTAGE = 5.0;
    /** Allowed deviation from the nominal voltage. */
    public static final double TOLERANCE = 0.10;

    public WallOutletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(2 + 1); // + wall connector
        buildWallConnector(builder);
    }

    public double nominal() {
        return getBlockState().getBlock() instanceof WallOutletBlock o ? o.nominalVoltage() : 230.0;
    }

    /** "dead", "ok", "under" or "over" for the measured voltage. */
    public static String status(double u, double nominal) {
        u = Math.abs(u);
        if (u < DEAD_VOLTAGE)
            return "dead";
        if (u < nominal * (1 - TOLERANCE))
            return "under";
        if (u > nominal * (1 + TOLERANCE))
            return "over";
        return "ok";
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        double u = Math.abs(portVoltage(0));
        double n = nominal();
        String st = status(u, n);
        line(tooltip, getBlockState().getBlock().getName().withStyle(ChatFormatting.GRAY), 0);
        line(tooltip, Component.translatable("pghv.wall.goggles.outlet_voltage",
                String.format(Locale.ROOT, "%.0f", u), String.format(Locale.ROOT, "%.0f", n)).withStyle(ChatFormatting.AQUA), 1);
        ChatFormatting color = switch (st) {
            case "ok" -> ChatFormatting.GREEN;
            case "dead" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.RED;
        };
        line(tooltip, Component.translatable("pghv.wall.goggles.status." + st).withStyle(color), 1);
        cableLines(tooltip);
        return true;
    }
}
