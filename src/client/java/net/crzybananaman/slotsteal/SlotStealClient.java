package net.crzybananaman.slotsteal;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlotStealClient implements ClientModInitializer {

	public static final String MOD_ID = "slotsteal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Slot Steal Client initialized - ghost block prevention active");
	}

	/**
	 * Check if an item is a locked slot barrier
	 */
	public static boolean isLockedSlotBarrier(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (!stack.isOf(Items.BARRIER)) return false;

		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) return false;

		NbtCompound nbt = customData.copyNbt();
		return nbt.contains("LockedSlot") && nbt.getBoolean("LockedSlot").orElse(false);
	}

	/**
	 * Check if an item is a slot void
	 */
	public static boolean isSlotVoidItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		if (!stack.isOf(Items.STRUCTURE_VOID)) return false;

		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null) return false;

		NbtCompound nbt = customData.copyNbt();
		return nbt.contains("SlotVoid") && nbt.getBoolean("SlotVoid").orElse(false);
	}
}
