package net.crzybananaman.slotsteal.event;

import net.crzybananaman.slotsteal.SlotSteal;
import net.crzybananaman.slotsteal.data.PlayerDataManager;
import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.Date;
import java.util.List;

public class PlayerEventHandler {

    /**
     * Called when a player joins the server
     */
    public static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        ServerPlayerEntity player = handler.player;

        if (!PlayerDataManager.hasJoinedBefore(player.getUuid())) {
            // First time joining - set up initial locked slots
            PlayerDataManager.markAsJoined(player.getUuid());
            PlayerDataManager.setLockedSlots(player.getUuid(), SlotSteal.STARTING_LOCKED_SLOTS);

            SlotSteal.LOGGER.info("New player {} joined, setting up {} locked slots",
                    player.getName().getString(), SlotSteal.STARTING_LOCKED_SLOTS);
        }

        // Apply barriers to inventory
        InventoryUtil.applyBarriersToInventory(player);

        // Sync inventory to client
        player.currentScreenHandler.sendContentUpdates();
    }

    /**
     * Called when any living entity dies
     */
    public static void onPlayerDeath(LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayerEntity victim)) {
            return;
        }

        // Clear all barriers from inventory BEFORE death drops are calculated
        InventoryUtil.clearAllBarriers(victim);

        // Schedule killing of any dropped barriers on next tick
        victim.getEntityWorld().getServer().execute(() -> {
            InventoryUtil.killDroppedBarriers(victim);
        });

        // Get server from the entity world
        MinecraftServer server = victim.getEntityWorld().getServer();
        if (server == null) return;

        Entity attackerEntity = damageSource.getAttacker();

        if (attackerEntity instanceof ServerPlayerEntity killer && killer != victim) {
            // PvP death - killer gains a slot, victim loses a slot
            handlePvPDeath(killer, victim, server);
        } else {
            // Natural/environmental death - victim just loses a slot
            handleNaturalDeath(victim, server);
        }
    }

    private static void handlePvPDeath(ServerPlayerEntity killer, ServerPlayerEntity victim, MinecraftServer server) {
        // Check if killer has any locked slots to remove
        int killerLockedSlots = PlayerDataManager.getLockedSlots(killer.getUuid());

        if (killerLockedSlots > 0) {
            // Killer gains a slot (loses a barrier)
            PlayerDataManager.removeLockedSlot(killer.getUuid());
            InventoryUtil.applyBarriersToInventory(killer);
            killer.sendMessage(Text.literal("§a+1 inventory slot!"), false);
        } else {
            // Killer already has all slots unlocked - drop a structure void
            dropSlotVoid(victim);
        }

        // Victim loses a slot
        addLockedSlotToPlayer(victim, server);
    }

    private static void handleNaturalDeath(ServerPlayerEntity victim, MinecraftServer server) {
        // Victim loses a slot (gains a barrier)
        addLockedSlotToPlayer(victim, server);
    }

    private static void addLockedSlotToPlayer(ServerPlayerEntity player, MinecraftServer server) {
        int currentLocked = PlayerDataManager.getLockedSlots(player.getUuid());

        // Check if this would fill all slots
        if (currentLocked >= SlotSteal.TOTAL_INVENTORY_SLOTS - 1) {
            // Ban the player
            PlayerDataManager.addLockedSlot(player.getUuid());
            banPlayer(player, server);
            return;
        }

        // Add a locked slot
        PlayerDataManager.addLockedSlot(player.getUuid());
    }

    private static void banPlayer(ServerPlayerEntity player, MinecraftServer server) {
        String playerName = player.getName().getString();

        // Kick with ban message
        player.networkHandler.disconnect(Text.literal(
                "§4§lBANNED FROM SLOT STEAL\n\n" +
                        "§cYou have lost all your inventory slots!"
        ));

        // Add to ban list using the ban command approach
        server.getPlayerManager().getUserBanList().add(
                new net.minecraft.server.BannedPlayerEntry(
                        player.getPlayerConfigEntry(),
                        new Date(),
                        "Slot Steal",
                        null,
                        "Lost all inventory slots"
                )
        );

        // Broadcast to server
        server.getPlayerManager().broadcast(
                Text.literal("§4" + playerName + " has been banned!"),
                false
        );

        SlotSteal.LOGGER.info("Player {} has been banned - lost all inventory slots", playerName);
    }

    private static void dropSlotVoid(ServerPlayerEntity player) {
        ItemStack slotVoid = createSlotVoidItem();
        player.dropItem(slotVoid, true, false);
    }

    /**
     * Creates a special structure void item that can be used to gain a slot
     */
    public static ItemStack createSlotVoidItem() {
        ItemStack stack = new ItemStack(Items.STRUCTURE_VOID, 1);

        // Set custom name
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§d§lSlot Void"));

        // Add lore
        List<Text> lore = List.of(
                Text.literal("§7Right-click to gain one slot")
        );
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));

        // Mark it as our special item using custom data
        NbtCompound customData = new NbtCompound();
        customData.putBoolean("SlotVoid", true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        return stack;
    }

    /**
     * Check if an item is our special slot void
     */
    public static boolean isSlotVoidItem(ItemStack stack) {
        if (!stack.isOf(Items.STRUCTURE_VOID)) return false;

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;

        NbtCompound nbt = customData.copyNbt();
        return nbt.contains("SlotVoid") && nbt.getBoolean("SlotVoid").orElse(false);
    }

    /**
     * Called when a player uses an item
     */
    public static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient()) {
            return ActionResult.PASS;
        }

        ItemStack stack = player.getStackInHand(hand);

        if (isSlotVoidItem(stack)) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            int lockedSlots = PlayerDataManager.getLockedSlots(serverPlayer.getUuid());

            if (lockedSlots > 0) {
                // Remove a locked slot
                PlayerDataManager.removeLockedSlot(serverPlayer.getUuid());
                InventoryUtil.applyBarriersToInventory(serverPlayer);

                // Consume the item
                stack.decrement(1);

                serverPlayer.sendMessage(Text.literal("§a+1 inventory slot!"), false);

                return ActionResult.SUCCESS;
            } else {
                // No barriers to remove
                serverPlayer.sendMessage(Text.literal("§7You already have all slots unlocked!"), false);
                return ActionResult.FAIL;
            }
        }

        return ActionResult.PASS;
    }
}