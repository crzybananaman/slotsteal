package net.crzybananaman.slotsteal.mixin;

import net.crzybananaman.slotsteal.event.PlayerEventHandler;
import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * Prevent placing barrier blocks and structure voids (slot voids) in the world
     */
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void onUseOnBlock(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        // Prevent placing locked slot barriers
        if (InventoryUtil.isLockedSlotBarrier(stack)) {
            cir.setReturnValue(ActionResult.FAIL);
            return;
        }

        // Prevent placing slot voids
        if (PlayerEventHandler.isSlotVoidItem(stack)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
