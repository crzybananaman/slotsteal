package net.crzybananaman.slotsteal.mixin.client;

import net.crzybananaman.slotsteal.SlotStealClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    /**
     * Prevent attacking blocks while holding a locked slot barrier
     */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            ItemStack mainHand = client.player.getMainHandStack();
            if (SlotStealClient.isLockedSlotBarrier(mainHand)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * Prevent placing barriers or slot voids on blocks
     */
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                 CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player.getStackInHand(hand);

        // Prevent barrier placement
        if (SlotStealClient.isLockedSlotBarrier(stack)) {
            cir.setReturnValue(ActionResult.FAIL);
        }

        // Prevent slot void placement
        if (SlotStealClient.isSlotVoidItem(stack)) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    /**
     * Prevent interacting with barriers
     */
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player.getStackInHand(hand);

        // Prevent any interaction with barriers
        if (SlotStealClient.isLockedSlotBarrier(stack)) {
            cir.setReturnValue(ActionResult.FAIL);
        }

        // Slot void can be used (right-click in air) - let it pass through to server
    }
}
