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
package com.dindjiari.pghv.registry;

import com.dindjiari.pghv.Pghv;
import com.dindjiari.pghv.recipe.CableAssemblyRecipe;
import com.dindjiari.pghv.recipe.CableInsulationRecipe;
import com.dindjiari.pghv.recipe.PghvRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe serializers. Both recipes are crafting-table recipes, so their recipe type stays
 * minecraft:crafting (a separate RecipeType would never be offered by the crafting table).
 */
public class PghvRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Pghv.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CableAssemblyRecipe>> CABLE_ASSEMBLY =
            RECIPE_SERIALIZERS.register("cable_assembly",
                    () -> new PghvRecipeSerializer<>(CableAssemblyRecipe.CODEC, CableAssemblyRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CableInsulationRecipe>> CABLE_INSULATION =
            RECIPE_SERIALIZERS.register("cable_insulation",
                    () -> new PghvRecipeSerializer<>(CableInsulationRecipe.CODEC, CableInsulationRecipe.STREAM_CODEC));
}
