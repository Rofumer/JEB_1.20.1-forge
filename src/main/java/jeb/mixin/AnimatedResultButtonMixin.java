package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;


@Mixin(RecipeButton.class)
public abstract class AnimatedResultButtonMixin implements AnimatedResultButtonExtension {


    @Unique
    private long jeb$flashUntil = 0L; // время до которого будет подсветка

    @Unique
    public void jeb$flash() {
        this.jeb$flashUntil = System.currentTimeMillis() + 300; // подсветка 300 мс
    }

    @Unique
    private boolean jeb$isFlashing() {
        return System.currentTimeMillis() < this.jeb$flashUntil;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void jeb$renderFlash(GuiGraphics p_281385_, int p_282779_, int p_282744_, float p_282439_, CallbackInfo ci) {
        if (jeb$isFlashing()) {
            RecipeButton self = (RecipeButton) (Object) this;
            //p_281385_.drawBorder(self.getX(), self.getY(), self.getWidth(), self.getHeight(), 0xFFFFFF00); // Жёлтая рамка
            drawBorder(p_281385_,self.getX(), self.getY(), self.getWidth(), self.getHeight(), 0xFFFFFF00);

        }
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        // Верхняя граница
        graphics.fill(x, y, x + width, y + 1, color);
        // Нижняя граница
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        // Левая граница
        graphics.fill(x, y, x + 1, y + height, color);
        // Правая граница
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }


    /*@Redirect(
            method = "showResultCollection",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;getResults(Z)Ljava/util/List;"
            )
    )
    private List<Recipe<?>> redirectFilter(RecipeResultCollection instance, boolean craftableOnly) {
        // Возвращаем все рецепты, без фильтрации
        return instance.getAllRecipes();
    }*/

    @Shadow private RecipeCollection collection;

    /**
     * Replaces the return value of getResults() with all recipes from the collection.
     */
    @Inject(method = "getOrderedRecipes", at = @At("TAIL"), cancellable = true)
    private void injectGetResults(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        cir.setReturnValue(this.collection.getRecipes());
    }

    @Unique
    private static final Component MORE_RECIPES_TEXT = Component.translatable("items.craftsfromitem");

    /*@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true)
    private void injectAlwaysAddTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        List<Text> list = cir.getReturnValue();
        try{
        list.add(MORE_RECIPES_TEXT); // всегда добавляем
        } catch (Exception e) {
            e.printStackTrace();
            // Можно также записать лог или безопасно проигнорировать ошибку
        }
        cir.setReturnValue(list);    // возвращаем изменённый список
    }*/

    @Shadow
    private int currentIndex;



    @Shadow
    protected abstract List<Recipe<?>> getOrderedRecipes();

    @Inject(method = "getTooltipText", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(CallbackInfoReturnable<List<Component>> cir) {
        try {
            ItemStack itemStack = ((Recipe<?>)this.getOrderedRecipes().get(this.currentIndex)).getResultItem(this.collection.registryAccess());
            List<Component> list = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack));

            jeb$addModName(list, itemStack);
            list.add(MORE_RECIPES_TEXT);

            cir.setReturnValue(list);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(List.of(Component.literal("§c[Error rendering tooltip]")));
        }
    }

    // Название мода, добавившего предмет (как в JEI). Если JEI или другой мод уже добавил такую строку — не дублируем.
    @Unique
    private static void jeb$addModName(List<Component> list, ItemStack stack) {
        String modId = stack.getItem().getCreatorModId(stack);
        if (modId == null) return;
        String modName = ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
        for (Component line : list) {
            if (modName.equals(line.getString())) return;
        }
        list.add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
    }
}


