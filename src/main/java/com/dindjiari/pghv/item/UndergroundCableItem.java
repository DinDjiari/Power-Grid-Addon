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
package com.dindjiari.pghv.item;

import com.dindjiari.pghv.cable.CableLaying;
import com.dindjiari.pghv.cable.CableLayingHandler;
import com.dindjiari.pghv.registry.PghvDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Insulated underground cable; one item = one block of cable length. */
public class UndergroundCableItem extends Item {
    public UndergroundCableItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return CableLayingHandler.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (CableLayingHandler.cancel(level, player, stack))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        return super.use(level, player, hand);
    }

    /** Glint while laying. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(PghvDataComponents.CABLE_LAYING.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CableTooltips.addCores(stack, context.registries(), tooltip);
        CableLaying laying = stack.get(PghvDataComponents.CABLE_LAYING.get());
        if (laying != null) {
            tooltip.add(Component.translatable("pghv.cable.tooltip.laying", laying.start().toShortString(),
                    laying.anchors().size()).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("pghv.cable.tooltip.usage1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.cable.tooltip.usage2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("pghv.cable.tooltip.usage3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
