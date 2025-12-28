package net.crzybananaman.slotsteal.util;

import net.crzybananaman.slotsteal.SlotSteal;
import net.crzybananaman.slotsteal.data.PlayerDataManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class InventoryUtil {

    /**
     * Creates a barrier block that marks a locked slot
     */
    public static ItemStack createLockedSlotBarrier() {
        ItemStack barrier = new ItemStack(Items.BARRIER, 1);

        // Set a blank name (using a space character)
        barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));

        // Mark it as our special locked slot barrier
        NbtCompound customData = new NbtCompound();
        customData.putBoolean("LockedSlot", true);
        barrier.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        return barrier;
    }

    /**
     * Check if an item is our locked slot barrier
     */
    public static boolean isLockedSlotBarrier(ItemStack stack) {
        if (!stack.isOf(Items.BARRIER)) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        NbtCompound nbt = customData.copyNbt();
        return nbt.contains("LockedSlot") && nbt.getBoolean("LockedSlot").orElse(false);
    }

    /**
     * Applies barriers to a player's inventory based on their locked slot count
     * Barriers are placed starting from slot 9 (first main inventory slot)
     */
    public static void applyBarriersToInventory(ServerPlayerEntity player) {
        int lockedSlots = PlayerDataManager.getLockedSlots(player.getUuid());

        // Clear all existing barriers first
        clearAllBarriers(player);

        // Place barriers in slots 9-35 first (main inventory), then 0-8 (hotbar) if needed
        int barriersPlaced = 0;

        // Fill main inventory slots first (9-35 = 27 slots)
        for (int slot = 9; slot <= 35 && barriersPlaced < lockedSlots; slot++) {
            placeBarrierInSlot(player, slot);
            barriersPlaced++;
        }

        // If still need more barriers, fill hotbar (0-8 = 9 slots)
        for (int slot = 0; slot <= 8 && barriersPlaced < lockedSlots; slot++) {
            placeBarrierInSlot(player, slot);
            barriersPlaced++;
        }

        // Sync inventory
        player.currentScreenHandler.sendContentUpdates();
        player.playerScreenHandler.sendContentUpdates();
    }

    /**
     * Places a barrier in a specific slot, dropping any existing item
     */
    private static void placeBarrierInSlot(ServerPlayerEntity player, int slot) {
        ItemStack existingItem = player.getInventory().getStack(slot);

        // Drop the existing item if it's not empty and not already a barrier
        if (!existingItem.isEmpty() && !isLockedSlotBarrier(existingItem)) {
            dropItemForPlayer(player, existingItem.copy());
        }

        // Place the barrier
        player.getInventory().setStack(slot, createLockedSlotBarrier());
    }

    /**
     * Clears all locked slot barriers from a player's inventory
     */
    public static void clearAllBarriers(ServerPlayerEntity player) {
        for (int slot = 0; slot < SlotSteal.TOTAL_INVENTORY_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isLockedSlotBarrier(stack)) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Drops an item at the player's location
     */
    public static void dropItemForPlayer(ServerPlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemEntity itemEntity = new ItemEntity(
                player.getEntityWorld(),
                player.getX(),
                player.getY() + 0.5,
                player.getZ(),
                stack
        );
        itemEntity.setPickupDelay(20); // 1 second delay
        player.getEntityWorld().spawnEntity(itemEntity);
    }

    /**
     * Counts the number of barriers in a player's inventory
     */
    public static int countBarriers(ServerPlayerEntity player) {
        int count = 0;
        for (int slot = 0; slot < SlotSteal.TOTAL_INVENTORY_SLOTS; slot++) {
            if (isLockedSlotBarrier(player.getInventory().getStack(slot))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the count of free (unlocked) slots
     */
    public static int getFreeSlotCount(ServerPlayerEntity player) {
        int lockedSlots = PlayerDataManager.getLockedSlots(player.getUuid());
        return SlotSteal.TOTAL_INVENTORY_SLOTS - lockedSlots;
    }

    /**
     * Kills all dropped barrier item entities near a player
     */
    public static void killDroppedBarriers(ServerPlayerEntity player) {
        player.getEntityWorld().getEntitiesByClass(
                ItemEntity.class,
                player.getBoundingBox().expand(10.0),
                itemEntity -> isLockedSlotBarrier(itemEntity.getStack())
        ).forEach(itemEntity -> itemEntity.discard());
    }
}