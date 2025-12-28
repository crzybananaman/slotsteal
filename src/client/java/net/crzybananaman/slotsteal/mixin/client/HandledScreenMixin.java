package net.crzybananaman.slotsteal.mixin.client;

import net.crzybananaman.slotsteal.SlotStealClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    /**
     * Prevent clicking on slots containing locked barriers
     */
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"), cancellable = true)
    private void onSlotClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null) return;

        ItemStack slotStack = slot.getStack();

        // Prevent any interaction with locked slot barriers
        if (SlotStealClient.isLockedSlotBarrier(slotStack)) {
            ci.cancel();
            return;
        }

        // Also check the cursor stack - prevent placing items into barrier slots
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            ItemStack cursorStack = client.player.currentScreenHandler.getCursorStack();

            // If cursor has a barrier, prevent placing it anywhere
            if (SlotStealClient.isLockedSlotBarrier(cursorStack)) {
                ci.cancel();
                return;
            }
        }
    }
}
