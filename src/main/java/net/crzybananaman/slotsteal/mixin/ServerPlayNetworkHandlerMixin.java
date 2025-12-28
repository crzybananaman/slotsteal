package net.crzybananaman.slotsteal.mixin;

import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.EquipmentSlot;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerCommonNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {

    /**
     * Intercept equipment update packets and hide barrier items from other players
     */
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        // Check if this handler is for a player
        if (!((Object) this instanceof ServerPlayNetworkHandler handler)) {
            return;
        }

        ServerPlayerEntity player = handler.player;

        if (packet instanceof EntityEquipmentUpdateS2CPacket equipmentPacket) {
            // Only modify packets about OTHER players, not the receiving player's own equipment
            if (equipmentPacket.getEntityId() != player.getId()) {
                List<Pair<EquipmentSlot, ItemStack>> originalList = equipmentPacket.getEquipmentList();
                List<Pair<EquipmentSlot, ItemStack>> modifiedList = new ArrayList<>();
                boolean modified = false;

                for (Pair<EquipmentSlot, ItemStack> pair : originalList) {
                    ItemStack stack = pair.getSecond();
                    if (InventoryUtil.isLockedSlotBarrier(stack)) {
                        modifiedList.add(Pair.of(pair.getFirst(), ItemStack.EMPTY));
                        modified = true;
                    } else {
                        modifiedList.add(pair);
                    }
                }

                if (modified) {
                    ci.cancel();
                    // Send modified packet with barriers hidden
                    EntityEquipmentUpdateS2CPacket newPacket = new EntityEquipmentUpdateS2CPacket(
                            equipmentPacket.getEntityId(),
                            modifiedList
                    );
                    handler.send(newPacket, listener);
                }
            }
        }
    }
}