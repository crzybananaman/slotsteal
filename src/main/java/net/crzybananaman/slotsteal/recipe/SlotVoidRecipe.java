package net.crzybananaman.slotsteal.recipe;

import net.crzybananaman.slotsteal.SlotSteal;
import net.crzybananaman.slotsteal.event.PlayerEventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Handles the custom Slot Void recipe
 */
public class SlotVoidRecipe extends SpecialCraftingRecipe {

    public static final RecipeSerializer<SlotVoidRecipe> SERIALIZER =
            new SpecialCraftingRecipe.SpecialRecipeSerializer<>(SlotVoidRecipe::new);

    public SlotVoidRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        // Must be a 3x3 crafting grid
        if (input.getWidth() != 3 || input.getHeight() != 3) {
            return false;
        }

        // Corners (0, 2, 6, 8)
        if (!input.getStackInSlot(0).isOf(Items.TOTEM_OF_UNDYING)) return false;
        if (!input.getStackInSlot(2).isOf(Items.TOTEM_OF_UNDYING)) return false;
        if (!input.getStackInSlot(6).isOf(Items.TOTEM_OF_UNDYING)) return false;
        if (!input.getStackInSlot(8).isOf(Items.TOTEM_OF_UNDYING)) return false;

        // Plus shape edges (1, 3, 5, 7)
        if (!input.getStackInSlot(1).isOf(Items.DIAMOND_BLOCK)) return false;
        if (!input.getStackInSlot(3).isOf(Items.DIAMOND_BLOCK)) return false;
        if (!input.getStackInSlot(5).isOf(Items.DIAMOND_BLOCK)) return false;
        if (!input.getStackInSlot(7).isOf(Items.DIAMOND_BLOCK)) return false;

        // Center (4)
        if (!input.getStackInSlot(4).isOf(Items.NETHER_STAR)) return false;

        return true;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        return PlayerEventHandler.createSlotVoidItem();
    }

    @Override
    public RecipeSerializer<SlotVoidRecipe> getSerializer() {
        return SERIALIZER;
    }

    public static void register() {
        Registry.register(
                Registries.RECIPE_SERIALIZER,
                Identifier.of(SlotSteal.MOD_ID, "slot_void_crafting"),
                SERIALIZER
        );

        SlotSteal.LOGGER.info("Registered Slot Void recipe");
    }
}