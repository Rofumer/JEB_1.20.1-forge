package jeb.mixin;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
//target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;",
@Mixin(OverlayRecipeComponent.class)
public class RecipeAlternativesWidgetMixin {
    /*@Redirect(
            method = "showAlternativesForResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;getRecipes(Z)Ljava/util/List;",
                    ordinal = 1
            )
    )
    private List<Recipe<?>> redirectList2Filter(RecipeResultCollection instance, boolean craftable) {
        return instance.getAllRecipes();
    }*/

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getDisplayRecipes(Z)Ljava/util/List;",
                    ordinal = 0
            )
    )
    private List<Recipe<?>> redirectList1Filter(RecipeCollection instance, boolean p_100514_) {
        return instance.getRecipes();
    }



}
