package jeb.mixin;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public interface RecipeBookWidgetAccessor {
    @Invoker("initVisuals")
    void invokeReset();


    @Accessor("searchBox")
    EditBox getSearchField();
    @Accessor("tabButtons")
    List<?> getTabButtons();
    @Accessor("stackedContents")
    StackedContents getRecipeFinder();
    @Accessor("menu")
    RecipeBookMenu<?> getCraftingScreenHandler();
    @Accessor("ghostRecipe")
    GhostRecipe getGhostRecipe();
    @Accessor("book")
    ClientRecipeBook getRecipeBook();
    @Accessor("selectedTab")
    RecipeBookTabButton getSelectedTab();

    @Accessor("selectedTab")
    void setSelectedTab(RecipeBookTabButton tab);

    /*@Accessor("tabs")
    List<RecipeBookWidget.Tab> getTabs();
    @Invoker("refreshTabButtons")
    void jeb$refreshTabButtons(boolean filteringCraftable);
    @Invoker("populateAllRecipes")
    void jeb$populateAllRecipes();*/
}