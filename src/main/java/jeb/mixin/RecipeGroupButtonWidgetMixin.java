package jeb.mixin;


import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeGroupButtonWidgetMixin {

    @Inject(method = "updateVisibility", at = @At("HEAD"), cancellable = true)
    private void forceVisibleForCampfire(ClientRecipeBook recipeBook, CallbackInfoReturnable<Boolean> cir) {
        RecipeBookTabButton self = (RecipeBookTabButton)(Object)this;

        if (self.getCategory() == RecipeBookCategories.CAMPFIRE) {
            self.visible = true; // доступ к полю напрямую, так как protected
            cir.setReturnValue(true); // отменить оригинальный метод и вернуть true
        }
    }

    @Inject(method = "renderIcon", at = @At("HEAD"), cancellable = true)
    private void overrideIcons(GuiGraphics p_281802_, ItemRenderer p_282499_, CallbackInfo ci) {
        RecipeBookTabButton self = (RecipeBookTabButton)(Object)this;
        RecipeBookCategories category = self.getCategory();

        // ⚠️ Заменим только если это та самая вкладка
        if (category == RecipeBookCategories.CAMPFIRE) {
            int i = self.isStateTriggered() ? -2 : 0;
            int x = self.getX();
            int y = self.getY();
            p_281802_.renderFakeItem(new ItemStack(Items.WRITABLE_BOOK), x + 9 + i, y + 5);
            ci.cancel();  // предотвратить выполнение оригинального метода
        }
    }
}
