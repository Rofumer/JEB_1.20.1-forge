package jeb.mixin;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING;
import static net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT;


@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin  {
    @Final
    @Shadow
    private static final Logger LOGGER= LogUtils.getLogger();;

    @Inject(method = "categorizeAndGroupRecipes", at = @At("HEAD"), cancellable = true)
    private static void injectToGroupedMap(Iterable<Recipe<?>> recipes, CallbackInfoReturnable<Map<RecipeBookCategories, List<List<Recipe<?>>>>> cir) {
        Map<RecipeBookCategories, List<List<Recipe<?>>>> map = Maps.newHashMap();

        for(Recipe<?> recipe : recipes) {
            RecipeBookCategories recipeBookGroup = getGroupForRecipe(recipe);

            // Т.к. optionalInt всегда пустой, всё будет сюда
            ((List) map.computeIfAbsent(recipeBookGroup, (group) -> new ArrayList()))
                    .add(List.of(recipe));
        }

        cir.setReturnValue(map);
    }

    @Unique
    private static RecipeBookCategories getGroupForRecipe(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe craftingRecipe) {
            RecipeBookCategories var6;
            switch (craftingRecipe.category()) {
                case BUILDING -> var6 = RecipeBookCategories.CRAFTING_BUILDING_BLOCKS;
                case EQUIPMENT -> var6 = RecipeBookCategories.CRAFTING_EQUIPMENT;
                case REDSTONE -> var6 = RecipeBookCategories.CRAFTING_REDSTONE;
                case MISC -> var6 = RecipeBookCategories.CRAFTING_MISC;
                default -> throw new IncompatibleClassChangeError();
            }

            return var6;
        } else {
            RecipeType<?> recipeType = recipe.getType();
            if (recipe instanceof AbstractCookingRecipe abstractCookingRecipe) {
                CookingBookCategory cookingRecipeCategory = abstractCookingRecipe.category();
                if (recipeType == RecipeType.SMELTING) {
                    RecipeBookCategories var5;
                    switch (cookingRecipeCategory) {
                        case BLOCKS -> var5 = RecipeBookCategories.FURNACE_BLOCKS;
                        case FOOD -> var5 = RecipeBookCategories.FURNACE_FOOD;
                        case MISC -> var5 = RecipeBookCategories.FURNACE_MISC;
                        default -> throw new IncompatibleClassChangeError();
                    }

                    return var5;
                }

                if (recipeType == RecipeType.BLASTING) {
                    return cookingRecipeCategory == CookingBookCategory.BLOCKS ? RecipeBookCategories.BLAST_FURNACE_BLOCKS : RecipeBookCategories.BLAST_FURNACE_MISC;
                }

                if (recipeType == RecipeType.SMOKING) {
                    return RecipeBookCategories.SMOKER_FOOD;
                }

                if (recipeType == RecipeType.CAMPFIRE_COOKING) {
                    return RecipeBookCategories.CAMPFIRE;
                }
            }

            if (recipeType == RecipeType.STONECUTTING) {
                return RecipeBookCategories.STONECUTTER;
            } else if (recipeType == RecipeType.SMITHING) {
                return RecipeBookCategories.SMITHING;
            } else {
                RecipeBookCategories categories = net.minecraftforge.client.RecipeBookManager.findCategories((RecipeType) recipeType, recipe);
                if (categories != null) return categories;
                LOGGER.warn("Unknown recipe category: {}/{}", LogUtils.defer(() -> {
                    return BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
                }), LogUtils.defer(recipe::getId));
                return RecipeBookCategories.UNKNOWN;
            }
        }
    }

}




