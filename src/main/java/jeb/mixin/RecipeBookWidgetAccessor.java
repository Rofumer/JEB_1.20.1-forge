package jeb.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookWidgetAccessor {
    @Invoker("reset")
    void invokeReset();

    @Accessor("searchBox")
    EditBox getSearchField();
    @Accessor("tabButtons")
    List<?> getTabButtons();
    @Accessor("recipeFinder")
    RecipeMatcher getRecipeFinder();
    @Accessor("menu")
    RecipeBookMenu<?> getCraftingScreenHandler();
    @Accessor("recipeBook")
    ClientRecipeBook getRecipeBook();

    /*@Accessor("tabs")
    List<RecipeBookWidget.Tab> getTabs();
    @Invoker("refreshTabButtons")
    void jeb$refreshTabButtons(boolean filteringCraftable);
    @Invoker("populateAllRecipes")
    void jeb$populateAllRecipes();*/
}