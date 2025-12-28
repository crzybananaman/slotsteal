package net.crzybananaman.slotsteal.mixin.client;

import net.crzybananaman.slotsteal.SlotStealClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    /**
     * Prevent dropping locked slot barriers (Q key)
     */
    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true)
    private void onDropSelectedItem(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        ItemStack selectedStack = player.getMainHandStack();

        if (SlotStealClient.isLockedSlotBarrier(selectedStack)) {
            cir.setReturnValue(false);
        }
    }
}