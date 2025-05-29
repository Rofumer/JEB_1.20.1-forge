package jeb.mixin;

import jeb.Jeb;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

@Mixin(RecipeCollection.class)
public abstract class RecipeResultCollectionMixin {

    @Shadow
    @Final
    private List<Recipe<?>> recipes;

    @Shadow
    @Final
    private Set<Recipe<?>> craftable;

    @Shadow
    @Final
    private Set<Recipe<?>> fitsDimensions;

    //@Shadow
    //private List<Recipe<?>> craftableRecipes;


    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private void injectMyVersion(StackedContents p_100502_, int p_100503_, int p_100504_, RecipeBook p_100505_, CallbackInfo ci) {
        for (Recipe<?> recipe : this.recipes) {
            // оригинальное условие
            boolean bl = recipe.canCraftInDimensions(p_100503_, p_100504_) && p_100505_.contains(recipe);

            if (Jeb.customToggleEnabled) {
                // твоя дополнительная строка
                bl = p_100505_.contains(recipe);
            }

            if (bl) {
                this.fitsDimensions.add(recipe);
            } else {
                this.fitsDimensions.remove(recipe);
            }

            if (bl && p_100502_.canCraft(recipe, null)) {
                this.craftable.add(recipe);
            } else {
                this.craftable.remove(recipe);
            }
        }

        // Отменяем оригинальное выполнение метода
        ci.cancel();
    }



    @Inject(method = "hasFitting", at = @At("HEAD"), cancellable = true)
    private void showAllRecipes(CallbackInfoReturnable<Boolean> cir) {
        // Принудительно возвращаем true, чтобы рецепт считался отображаемым
        cir.setReturnValue(true);
    }

    @Shadow
    public abstract List<Recipe<?>> getRecipes();


    /*@Overwrite
    public List<Recipe<?>> getRecipes(boolean craftable) {
        List<Recipe<?>> allRecipes = this.getAllRecipes(); // метод публичный

        return new ArrayList<>(allRecipes); // Просто копируем все рецепты
    }*/



    /**
     * Overwrites getRecipes to use only craftableRecipes condition.
     */
    /*
    @Overwrite
    public List<Recipe<?>> getRecipes(boolean craftable) {
        List<Recipe<?>> list = new ArrayList<>();
        for (Recipe<?> recipe : this.recipes) {
            if (craftable) {
                list.add(recipe);
            }
        }
        return list;
    }*/
}