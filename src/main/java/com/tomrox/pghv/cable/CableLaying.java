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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Laying state stored on the held underground cable: dimension, start box and anchor cells. */
public record CableLaying(ResourceKey<Level> dimension, BlockPos start, List<BlockPos> anchors) {
    public static final Codec<CableLaying> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CableLaying::dimension),
            BlockPos.CODEC.fieldOf("start").forGetter(CableLaying::start),
            BlockPos.CODEC.listOf().fieldOf("anchors").forGetter(CableLaying::anchors)
    ).apply(instance, CableLaying::new));

    public static final StreamCodec<ByteBuf, CableLaying> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), CableLaying::dimension,
            BlockPos.STREAM_CODEC, CableLaying::start,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), CableLaying::anchors,
            CableLaying::new);

    public CableLaying {
        anchors = List.copyOf(anchors);
    }

    public CableLaying withAnchor(BlockPos anchor) {
        List<BlockPos> list = new ArrayList<>(anchors);
        list.add(anchor.immutable());
        return new CableLaying(dimension, start, list);
    }

    /** Last point of the path so far (start box or last anchor). */
    public BlockPos lastPoint() {
        return anchors.isEmpty() ? start : anchors.get(anchors.size() - 1);
    }

    /** Path points: start, anchors and optionally the target box. */
    public List<BlockPos> points(BlockPos target) {
        List<BlockPos> points = new ArrayList<>(anchors.size() + 2);
        points.add(start);
        points.addAll(anchors);
        if (target != null)
            points.add(target);
        return points;
    }
}
