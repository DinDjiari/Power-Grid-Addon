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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/** House connection box: main switch L grid (0) -> L house (2), N grid (1) -> N house (3) always connected. */
public class HouseConnectionBoxBlockEntity extends WallDeviceBlockEntity {
    private static final float CONTACT = 1e-3f;
    private SwitchedWire mainSwitch;

    public HouseConnectionBoxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void buildCircuit(CircuitBuilder builder) {
        builder.setTerminalCount(4);
        boolean on = !getBlockState().hasProperty(HouseConnectionBoxBlock.POWERED) || getBlockState().getValue(HouseConnectionBoxBlock.POWERED);
        mainSwitch = builder.connectSwitch(CONTACT, builder.terminalNode(0), builder.terminalNode(2), on);
        builder.connect(CONTACT, builder.terminalNode(1), builder.terminalNode(3));
    }

    public void setOn(boolean on) {
        if (mainSwitch != null)
            mainSwitch.setState(on);
        notifyUpdate();
    }

    public boolean isOn() {
        return getBlockState().getValue(HouseConnectionBoxBlock.POWERED);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        line(tooltip, Component.translatable("block.pghv.house_connection_box").withStyle(ChatFormatting.GRAY), 0);
        line(tooltip, Component.translatable("pghv.hak.goggles.main",
                Component.translatable(isOn() ? "pghv.wall.goggles.on" : "pghv.wall.goggles.off"))
                .withStyle(isOn() ? ChatFormatting.GREEN : ChatFormatting.GOLD), 1);
        line(tooltip, Component.translatable("pghv.wall.goggles.voltage",
                String.format(Locale.ROOT, "%.0f", Math.abs(portVoltage(0)))).withStyle(ChatFormatting.AQUA), 1);
        cableLines(tooltip);
        return true;
    }
}
