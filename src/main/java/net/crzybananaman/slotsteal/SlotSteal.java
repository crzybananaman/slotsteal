package net.crzybananaman.slotsteal;

import net.crzybananaman.slotsteal.command.SlotCommands;
import net.crzybananaman.slotsteal.data.PlayerDataManager;
import net.crzybananaman.slotsteal.event.PlayerEventHandler;
import net.crzybananaman.slotsteal.recipe.SlotVoidRecipe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlotSteal implements ModInitializer {
	public static final String MOD_ID = "slotsteal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Configuration constants
	public static final int STARTING_LOCKED_SLOTS = 18; // Slots 9-26 (18 slots)
	public static final int TOTAL_INVENTORY_SLOTS = 36; // Hotbar (0-8) + Main inventory (9-35)

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Slot Steal");

		// Register events
		ServerPlayConnectionEvents.JOIN.register(PlayerEventHandler::onPlayerJoin);
		ServerLivingEntityEvents.AFTER_DEATH.register(PlayerEventHandler::onPlayerDeath);
		UseItemCallback.EVENT.register(PlayerEventHandler::onUseItem);

		// Register commands
		CommandRegistrationCallback.EVENT.register(SlotCommands::register);

		// Register server lifecycle events for data saving/loading
		ServerLifecycleEvents.SERVER_STARTED.register(PlayerDataManager::loadData);
		ServerLifecycleEvents.SERVER_STOPPING.register(PlayerDataManager::saveData);

		// Register custom recipe
		SlotVoidRecipe.register();

		LOGGER.info("Slots mod initialized!");
	}
}
