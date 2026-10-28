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

import com.tomrox.pghv.Pghv;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: "auto route the in-wall cable I am laying to this block" (key R). */
public record AutoRoutePayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<AutoRoutePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Pghv.MOD_ID, "inwall_autoroute"));
    public static final StreamCodec<ByteBuf, AutoRoutePayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(AutoRoutePayload::new, AutoRoutePayload::target);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AutoRoutePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                InWallLaying.autoRoute(player, payload.target());
        });
    }

    /** Mod bus listener (registered in Pghv). */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, AutoRoutePayload::handle);
    }
}
