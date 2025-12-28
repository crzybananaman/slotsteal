package net.crzybananaman.slotsteal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.crzybananaman.slotsteal.SlotSteal;
import net.crzybananaman.slotsteal.data.PlayerDataManager;
import net.crzybananaman.slotsteal.event.PlayerEventHandler;
import net.crzybananaman.slotsteal.util.InventoryUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.UUID;

public class SlotCommands {

    private static final int REVIVE_COST = 9;
    private static final int REVIVE_SLOTS = 9;

    // Helper method to check if source has required permission level
    private static boolean hasOpPermission(ServerCommandSource source, int level) {
        try {
            if (source.isExecutedByPlayer()) {
                ServerPlayerEntity player = source.getPlayer();
                if (player == null) return false;
                // Check if player is in the operator list
                var opList = source.getServer().getPlayerManager().getOpList();
                // If player is in op list, they have permission
                return opList.get(player.getPlayerConfigEntry()) != null;
            }
            // Console always has permission
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {

        // /slots - Show current slot info (available to all players)
        dispatcher.register(CommandManager.literal("slots")
                .executes(SlotCommands::showSlotInfo));

        // /withdrawslot [amount] - Withdraw slots and get structure voids (available to all players)
        dispatcher.register(CommandManager.literal("withdrawslot")
                .executes(context -> withdrawSlots(context, 1))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 35))
                        .executes(context -> withdrawSlots(context, IntegerArgumentType.getInteger(context, "amount")))));

        // /withdrawslots (alias)
        dispatcher.register(CommandManager.literal("withdrawslots")
                .executes(context -> withdrawSlots(context, 1))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 35))
                        .executes(context -> withdrawSlots(context, IntegerArgumentType.getInteger(context, "amount")))));

        // /revive <player> - Revive a banned player using 9 slot voids (available to all players)
        dispatcher.register(CommandManager.literal("revive")
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(SlotCommands::revivePlayer)));

        // Admin commands (require op level 2)

        // /setslots <player> <amount> - Set a player's unlocked slots
        dispatcher.register(CommandManager.literal("setslots")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("slots", IntegerArgumentType.integer(1, 36))
                                .executes(SlotCommands::setSlots))));

        // /addslots <player> <amount> - Add slots to a player
        dispatcher.register(CommandManager.literal("addslots")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 35))
                                .executes(SlotCommands::addSlots))));

        // /removeslots <player> <amount> - Remove slots from a player
        dispatcher.register(CommandManager.literal("removeslots")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 35))
                                .executes(SlotCommands::removeSlots))));

        // /giveslotvoid <player> [amount] - Give slot voids to a player
        dispatcher.register(CommandManager.literal("giveslotvoid")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.players())
                        .executes(context -> giveSlotVoid(context, 1))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(context -> giveSlotVoid(context, IntegerArgumentType.getInteger(context, "amount"))))));

        // /resetslots <player> - Reset a player to default slots
        dispatcher.register(CommandManager.literal("resetslots")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(SlotCommands::resetSlots)));

        // /checkslots <player> - Check a player's slot info (admin)
        dispatcher.register(CommandManager.literal("checkslots")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(SlotCommands::checkSlots)));

        // /adminrevive <player> - Admin revive without cost
        dispatcher.register(CommandManager.literal("adminrevive")
                .requires(source -> hasOpPermission(source, 2))
                .then(CommandManager.argument("player", StringArgumentType.word())
                        .executes(SlotCommands::adminRevivePlayer)));
    }

    private static int showSlotInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        int lockedSlots = PlayerDataManager.getLockedSlots(player.getUuid());
        int freeSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - lockedSlots;
        int slotsUntilBan = freeSlots - 1;

        player.sendMessage(Text.literal("§6=== Your Slot Steal Status ==="), false);
        player.sendMessage(Text.literal("§7Unlocked Slots: §a" + freeSlots + "§7/§e36"), false);
        player.sendMessage(Text.literal("§7Locked Slots: §c" + lockedSlots), false);
        player.sendMessage(Text.literal("§7Deaths until ban: §4" + Math.max(0, slotsUntilBan)), false);

        return 1;
    }

    private static int withdrawSlots(CommandContext<ServerCommandSource> context, int amount) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();
        int lockedSlots = PlayerDataManager.getLockedSlots(player.getUuid());
        int freeSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - lockedSlots;

        // Check if withdrawal would ban the player (need at least 1 slot remaining)
        if (freeSlots - amount < 1) {
            player.sendMessage(Text.literal("§cCannot withdraw " + amount + " slot(s)!"), false);
            player.sendMessage(Text.literal("§7You can withdraw a maximum of §e" + (freeSlots - 1) + "§7 slot(s)."), false);
            return 0;
        }

        // Perform withdrawal
        for (int i = 0; i < amount; i++) {
            PlayerDataManager.addLockedSlot(player.getUuid());

            // Give a slot void
            player.getInventory().insertStack(PlayerEventHandler.createSlotVoidItem());
        }

        // Update barriers
        InventoryUtil.applyBarriersToInventory(player);

        int newFreeSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - PlayerDataManager.getLockedSlots(player.getUuid());

        player.sendMessage(Text.literal("§aWithdrew " + amount + " slot(s)!"), false);
        player.sendMessage(Text.literal("§7You now have §e" + newFreeSlots + "§7 unlocked slots."), false);

        return amount;
    }

    private static int revivePlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        if (!source.isExecutedByPlayer()) {
            source.sendError(Text.literal("This command can only be used by players!"));
            return 0;
        }

        ServerPlayerEntity reviver = source.getPlayer();
        String targetName = StringArgumentType.getString(context, "player");
        MinecraftServer server = source.getServer();

        // Get the ban list
        BannedPlayerList banList = server.getPlayerManager().getUserBanList();

        // Find the banned entry by iterating through the ban list
        BannedPlayerEntry targetEntry = null;
        String foundName = null;
        UUID foundUuid = null;

        for (BannedPlayerEntry entry : banList.values()) {
            var key = entry.getKey();
            if (key != null) {
                String entryName = key.name();
                if (entryName != null && entryName.equalsIgnoreCase(targetName)) {
                    targetEntry = entry;
                    foundName = entryName;
                    foundUuid = key.id();
                    break;
                }
            }
        }

        if (targetEntry == null || foundUuid == null) {
            reviver.sendMessage(Text.literal("§c" + targetName + " is not banned or player not found!"), false);
            return 0;
        }

        // Count slot voids in reviver's inventory
        int slotVoidCount = countSlotVoids(reviver);

        if (slotVoidCount < REVIVE_COST) {
            reviver.sendMessage(Text.literal("§cYou need " + REVIVE_COST + " Slot Voids to revive a player!"), false);
            reviver.sendMessage(Text.literal("§7You currently have: §e" + slotVoidCount), false);
            return 0;
        }

        // Consume slot voids
        removeSlotVoids(reviver, REVIVE_COST);

        // Unban the player
        banList.remove(targetEntry);

        // Set their slots to have 27 locked (9 free)
        PlayerDataManager.setLockedSlots(foundUuid, SlotSteal.TOTAL_INVENTORY_SLOTS - REVIVE_SLOTS);

        // Broadcast revival
        server.getPlayerManager().broadcast(
                Text.literal("§d" + foundName + " has been revived by " + reviver.getName().getString() + "!"),
                false
        );

        reviver.sendMessage(Text.literal("§aSuccessfully revived " + foundName + "!"), false);

        SlotSteal.LOGGER.info("Player {} revived {} using {} Slot Voids", reviver.getName().getString(), foundName, REVIVE_COST);

        return 1;
    }

    private static int adminRevivePlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String targetName = StringArgumentType.getString(context, "player");
        MinecraftServer server = source.getServer();

        // Get the ban list
        BannedPlayerList banList = server.getPlayerManager().getUserBanList();

        // Find the banned entry by iterating through the ban list
        BannedPlayerEntry targetEntry = null;
        String foundName = null;
        UUID foundUuid = null;

        for (BannedPlayerEntry entry : banList.values()) {
            var key = entry.getKey();
            if (key != null) {
                String entryName = key.name();
                if (entryName != null && entryName.equalsIgnoreCase(targetName)) {
                    targetEntry = entry;
                    foundName = entryName;
                    foundUuid = key.id();
                    break;
                }
            }
        }

        if (targetEntry == null || foundUuid == null) {
            source.sendError(Text.literal(targetName + " is not banned or player not found!"));
            return 0;
        }

        // Unban the player
        banList.remove(targetEntry);

        // Set their slots to have 27 locked (9 free)
        PlayerDataManager.setLockedSlots(foundUuid, SlotSteal.TOTAL_INVENTORY_SLOTS - REVIVE_SLOTS);

        // Broadcast revival
        final String finalName = foundName;

        source.sendFeedback(() -> Text.literal("§aSuccessfully revived " + finalName + "!"), true);

        SlotSteal.LOGGER.info("Admin {} revived {}", source.getName(), finalName);

        return 1;
    }

    private static int countSlotVoids(ServerPlayerEntity player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (PlayerEventHandler.isSlotVoidItem(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeSlotVoids(ServerPlayerEntity player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (PlayerEventHandler.isSlotVoidItem(stack)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.decrement(toRemove);
                remaining -= toRemove;
            }
        }
    }

    private static int setSlots(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            int unlockedSlots = IntegerArgumentType.getInteger(context, "slots");
            int lockedSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - unlockedSlots;

            PlayerDataManager.setLockedSlots(target.getUuid(), lockedSlots);
            InventoryUtil.applyBarriersToInventory(target);

            context.getSource().sendFeedback(
                    () -> Text.literal("§aSet " + target.getName().getString() + "'s unlocked slots to " + unlockedSlots),
                    true
            );

            target.sendMessage(Text.literal("§eYour slots have been set to " + unlockedSlots + " by an admin."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int addSlots(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            int amount = IntegerArgumentType.getInteger(context, "amount");

            int currentLocked = PlayerDataManager.getLockedSlots(target.getUuid());
            int newLocked = Math.max(0, currentLocked - amount);

            PlayerDataManager.setLockedSlots(target.getUuid(), newLocked);
            InventoryUtil.applyBarriersToInventory(target);

            int newFree = SlotSteal.TOTAL_INVENTORY_SLOTS - newLocked;

            context.getSource().sendFeedback(
                    () -> Text.literal("§aAdded " + amount + " slots to " + target.getName().getString() +
                            ". They now have " + newFree + " unlocked slots."),
                    true
            );

            target.sendMessage(Text.literal("§a+" + amount + " slots! You now have " + newFree + " unlocked slots."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeSlots(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            int amount = IntegerArgumentType.getInteger(context, "amount");

            int currentLocked = PlayerDataManager.getLockedSlots(target.getUuid());
            int newLocked = Math.min(SlotSteal.TOTAL_INVENTORY_SLOTS, currentLocked + amount);

            // Check if this would ban them
            if (newLocked >= SlotSteal.TOTAL_INVENTORY_SLOTS) {
                context.getSource().sendError(Text.literal("§cThis would ban " + target.getName().getString() + "! Use manual ban if intended."));
                return 0;
            }

            PlayerDataManager.setLockedSlots(target.getUuid(), newLocked);
            InventoryUtil.applyBarriersToInventory(target);

            int newFree = SlotSteal.TOTAL_INVENTORY_SLOTS - newLocked;

            context.getSource().sendFeedback(
                    () -> Text.literal("§cRemoved " + amount + " slots from " + target.getName().getString() +
                            ". They now have " + newFree + " unlocked slots."),
                    true
            );

            target.sendMessage(Text.literal("§c-" + amount + " slots! You now have " + newFree + " unlocked slots."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int giveSlotVoid(CommandContext<ServerCommandSource> context, int amount) {
        try {
            Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(context, "player");

            for (ServerPlayerEntity target : targets) {
                for (int i = 0; i < amount; i++) {
                    target.getInventory().insertStack(PlayerEventHandler.createSlotVoidItem());
                }

                target.sendMessage(Text.literal("§dYou received " + amount + " Slot Void(s)!"), false);
            }

            context.getSource().sendFeedback(
                    () -> Text.literal("§aGave " + amount + " Slot Void(s) to " + targets.size() + " player(s)."),
                    true
            );

            return targets.size();
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetSlots(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

            PlayerDataManager.setLockedSlots(target.getUuid(), SlotSteal.STARTING_LOCKED_SLOTS);
            InventoryUtil.applyBarriersToInventory(target);

            int freeSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - SlotSteal.STARTING_LOCKED_SLOTS;

            context.getSource().sendFeedback(
                    () -> Text.literal("§aReset " + target.getName().getString() + "'s slots to default (" + freeSlots + " unlocked)."),
                    true
            );

            target.sendMessage(Text.literal("§eYour slots have been reset to default by an admin."), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int checkSlots(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            int lockedSlots = PlayerDataManager.getLockedSlots(target.getUuid());
            int freeSlots = SlotSteal.TOTAL_INVENTORY_SLOTS - lockedSlots;
            int barrierCount = InventoryUtil.countBarriers(target);

            context.getSource().sendFeedback(
                    () -> Text.literal("§6=== " + target.getName().getString() + "'s Slot Steal Status ==="),
                    false
            );
            context.getSource().sendFeedback(
                    () -> Text.literal("§7Unlocked Slots: §a" + freeSlots + "§7/§e36"),
                    false
            );
            context.getSource().sendFeedback(
                    () -> Text.literal("§7Locked Slots (data): §c" + lockedSlots),
                    false
            );
            context.getSource().sendFeedback(
                    () -> Text.literal("§7Barriers in inventory: §c" + barrierCount),
                    false
            );

            if (barrierCount != lockedSlots) {
                context.getSource().sendFeedback(
                        () -> Text.literal("§e⚠ Mismatch detected! Running sync..."),
                        false
                );
                InventoryUtil.applyBarriersToInventory(target);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}