package net.crzybananaman.slotsteal.mixin;

import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {

    /**
     * Prevent removing locked slot barriers from their slots
     */
    @Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void onRemoveStack(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;

        if (slot >= 0 && slot < inventory.size()) {
            ItemStack stack = inventory.getStack(slot);
            if (InventoryUtil.isLockedSlotBarrier(stack)) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }

    /**
     * Prevent removing locked slot barriers when taking the whole stack
     */
    @Inject(method = "removeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void onRemoveStackFull(int slot, CallbackInfoReturnable<ItemStack> cir) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;

        if (slot >= 0 && slot < inventory.size()) {
            ItemStack stack = inventory.getStack(slot);
            if (InventoryUtil.isLockedSlotBarrier(stack)) {
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }

    /**
     * Prevent setting items in slots that have barriers (unless it's our system setting barriers)
     */
    @Inject(method = "setStack", at = @At("HEAD"), cancellable = true)
    private void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;

        if (slot >= 0 && slot < inventory.size()) {
            ItemStack existing = inventory.getStack(slot);

            // If the slot has a barrier and we're trying to put something else there
            if (InventoryUtil.isLockedSlotBarrier(existing) && !InventoryUtil.isLockedSlotBarrier(stack)) {
                // Only allow if we're clearing it (our system does this)
                if (!stack.isEmpty()) {
                    ci.cancel();
                }
            }
        }
    }
}
