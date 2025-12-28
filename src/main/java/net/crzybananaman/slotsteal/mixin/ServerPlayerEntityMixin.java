package net.crzybananaman.slotsteal.mixin;

import net.crzybananaman.slotsteal.data.PlayerDataManager;
import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    /**
     * Re-apply barriers after player respawns
     */
    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void onCopyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Schedule barrier application for next tick to ensure inventory is ready
        player.getEntityWorld().getServer().execute(() -> {
            InventoryUtil.applyBarriersToInventory(player);
        });
    }

    /**
     * Apply barriers when player ticks (ensures consistency)
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Every 100 ticks (5 seconds) - force sync inventory to client
        if (player.age % 100 == 0) {
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
        }

        // Every 20 ticks (1 second) - verify barrier count matches locked slots
        if (player.age % 20 == 0) {
            int lockedSlots = PlayerDataManager.getLockedSlots(player.getUuid());
            int barrierCount = InventoryUtil.countBarriers(player);

            if (barrierCount != lockedSlots) {
                InventoryUtil.applyBarriersToInventory(player);
            }
        }
    }
}