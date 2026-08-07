package jeb.mixin;

import jeb.accessor.RecipeBookWidgetBridge;
import jeb.client.RecipeSearchQueries;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(RecipeBookPage.class)
public class RecipeBookResultsMixin {


    @Unique
    private RecipeBookComponent jeb$widget;

    @Shadow
    private RecipeButton hoveredButton;

    @Shadow
    private Recipe<?> lastClickedRecipe;

    @Shadow
    private Minecraft minecraft;

    @Shadow
    @Nullable
    private RecipeCollection lastClickedRecipeCollection;

    @Final
    @Shadow
    private OverlayRecipeComponent overlay;



    @Inject(method = "addListener", at = @At("HEAD"))
    private void captureWidget(RecipeBookComponent p_100433_, CallbackInfo ci) {
        this.jeb$widget = p_100433_;
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;mouseClicked(DDI)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onRightClickInject(double p_100410_, double p_100411_, int p_100412_, int p_100413_, int p_100414_, int p_100415_, int p_100416_, CallbackInfoReturnable<Boolean> cir) {

        //ContextMap context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        RecipeButton hovered = this.hoveredButton;

        //if (animatedResultButton.mouseClicked(mouseX, mouseY, button)) {
        if (hovered != null) {


            if (p_100412_ == 2) {

                ItemStack stack = hovered.getRecipe().getResultItem(minecraft.level.registryAccess());
                String searchText = RecipeSearchQueries.forResult(stack);

                ((RecipeBookWidgetBridge) jeb$widget).jeb$pushHistory(
                        ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().getValue(),
                        ((RecipeBookWidgetAccessor) jeb$widget).getSelectedTab()
                );

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) jeb$widget).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) jeb$widget).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) jeb$widget).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();

            }

            if (p_100412_ == 1) {
                ItemStack stack = hovered.getRecipe().getResultItem(minecraft.level.registryAccess());
                String searchText = RecipeSearchQueries.forIngredient(stack);

                ((RecipeBookWidgetBridge) jeb$widget).jeb$pushHistory(
                        ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().getValue(),
                        ((RecipeBookWidgetAccessor) jeb$widget).getSelectedTab()
                );

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) jeb$widget).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) jeb$widget).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) jeb$widget).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) jeb$widget).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (p_100412_ == 0) {


                //System.out.println(animatedResultButton.getCurrentId().toString());

                Minecraft client = Minecraft.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                RecipeCollection entry = hovered.getCollection();

                if(entry != null) {

                    if(!canDisplay(hovered.getRecipe()) && !hovered.getRecipe().getIngredients().isEmpty())
                    {
                        //int p_100413_, int p_100414_, int p_100415_, int p_100416_
                        overlay.init(minecraft,entry, hovered.getX(), hovered.getY(), p_100413_ + p_100415_ / 2, p_100414_ + 13 + p_100416_ / 2, (float) hovered.getWidth());
                    }
                    else
                    {

                        this.lastClickedRecipe = hovered.getRecipe();
                        this.lastClickedRecipeCollection = hovered.getCollection();
                        //recipeBook.shouldDisplay(animatedResultButton.currentRecipe());
                        recipeBook.removeHighlight(hovered.getRecipe());
                        ///ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        ///networkHandler.sendPacket(new RecipeBookDataC2SPacket(animatedResultButton.currentRecipe()));
                        var connection = Minecraft.getInstance().getConnection();
                        if (connection != null) {
                            connection.send(new ServerboundRecipeBookSeenRecipePacket(hovered.getRecipe()));
                        }
                        /*this.lastClickedRecipe = animatedResultButton.getCurrentId();
                        this.resultCollection = animatedResultButton.getResultCollection();
                        recipeBook.unmarkHighlighted(animatedResultButton.getCurrentId());
                        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        networkHandler.sendPacket(new RecipeBookDataC2SPacket(animatedResultButton.getCurrentId()));*/
                    }
                }

                cir.setReturnValue(true);
                cir.cancel();
            }
        }

    }

    @Final
    @Shadow
    private List<RecipeShownListener> showListeners;


    @Unique
    private boolean canDisplay(Recipe<?> display) {

        RecipeBookMenu<?> handler = null;

        for(RecipeShownListener recipeDisplayListener : this.showListeners) {
            handler = ((RecipeBookWidgetAccessor) recipeDisplayListener).getCraftingScreenHandler();
        }

     return display.canCraftInDimensions(handler.getGridWidth(),handler.getGridHeight());

    }

}
