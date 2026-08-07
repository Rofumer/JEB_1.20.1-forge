package jeb.mixin;

import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.JebClient;
import jeb.client.RecipeSearchQueries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * Хоткеи наведения (как в JEI): R — «рецепты этого предмета», U — «где используется».
 * Работают в любом экране с книгой рецептов (верстак, печи, инвентарь игрока):
 * наводим курсор на любой слот и жмём клавишу — книга откроется (если была закрыта)
 * с подставленным поиском.
 *
 * <p>В 1.20.1 нет общего AbstractRecipeBookScreen, поэтому цель — AbstractContainerScreen,
 * а наличие книги проверяется через RecipeUpdateListener.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class RecipeBookHoverHotkeyMixin {

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void jeb$onHoverHotkey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        boolean viewRecipe = JebClient.keyViewRecipe != null
                && JebClient.keyViewRecipe.matches(keyCode, scanCode);
        boolean viewUses = JebClient.keyViewUses != null
                && JebClient.keyViewUses.matches(keyCode, scanCode);

        if (!viewRecipe && !viewUses) {
            return;
        }

        if (!((Object) this instanceof RecipeUpdateListener listener)) {
            return;
        }

        Slot hovered = this.hoveredSlot;
        if (hovered == null || !hovered.hasItem()) {
            return;
        }

        RecipeBookComponent component = listener.getRecipeBookComponent();
        if (component == null) {
            return;
        }

        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) component;

        // Инжект стоит перед тем, как событие дойдёт до поля поиска, поэтому во время
        // ввода текста хоткей срабатывать не должен.
        EditBox searchField = accessor.getSearchField();
        if (searchField != null && searchField.isFocused()) {
            return;
        }

        ItemStack stack = hovered.getItem();
        String searchText = viewRecipe
                ? RecipeSearchQueries.forResult(stack)
                : RecipeSearchQueries.forIngredient(stack);

        if (!component.isVisible()) {
            component.toggleVisibility();
            // Открытая книга сдвигает GUI: пересобираем экран, чтобы leftPos и кнопка
            // книги встали на свои места (ванильная кнопка делает это же вручную).
            Screen screen = (Screen) (Object) this;
            screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        }

        // После открытия книги виджеты пересоздаются — берём поле поиска заново.
        searchField = accessor.getSearchField();
        if (searchField == null) {
            return;
        }

        ((RecipeBookWidgetBridge) component).jeb$pushHistory(searchField.getValue(), accessor.getSelectedTab());
        searchField.setValue(searchText);
        accessor.setSelectedTab((RecipeBookTabButton) accessor.getTabButtons().get(0));
        accessor.invokeReset();

        cir.setReturnValue(true);
    }
}
