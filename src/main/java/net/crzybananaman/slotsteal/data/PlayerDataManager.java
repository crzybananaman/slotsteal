package net.crzybananaman.slotsteal.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.crzybananaman.slotsteal.SlotSteal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "slotsteal_data.json";

    private static Map<UUID, PlayerSlotData> playerData = new HashMap<>();

    public static class PlayerSlotData {
        public int lockedSlots;
        public boolean hasJoinedBefore;

        public PlayerSlotData() {
            this.lockedSlots = SlotSteal.STARTING_LOCKED_SLOTS;
            this.hasJoinedBefore = false;
        }

        public PlayerSlotData(int lockedSlots, boolean hasJoinedBefore) {
            this.lockedSlots = lockedSlots;
            this.hasJoinedBefore = hasJoinedBefore;
        }
    }

    public static PlayerSlotData getOrCreatePlayerData(UUID playerId) {
        return playerData.computeIfAbsent(playerId, k -> new PlayerSlotData());
    }

    public static boolean hasJoinedBefore(UUID playerId) {
        PlayerSlotData data = playerData.get(playerId);
        return data != null && data.hasJoinedBefore;
    }

    public static void markAsJoined(UUID playerId) {
        PlayerSlotData data = getOrCreatePlayerData(playerId);
        data.hasJoinedBefore = true;
    }

    public static int getLockedSlots(UUID playerId) {
        return getOrCreatePlayerData(playerId).lockedSlots;
    }

    public static void setLockedSlots(UUID playerId, int slots) {
        PlayerSlotData data = getOrCreatePlayerData(playerId);
        data.lockedSlots = Math.max(0, slots);
    }

    public static void addLockedSlot(UUID playerId) {
        PlayerSlotData data = getOrCreatePlayerData(playerId);
        data.lockedSlots++;
    }

    public static boolean removeLockedSlot(UUID playerId) {
        PlayerSlotData data = getOrCreatePlayerData(playerId);
        if (data.lockedSlots > 0) {
            data.lockedSlots--;
            return true;
        }
        return false;
    }

    public static boolean wouldBeBanned(UUID playerId, int additionalSlots) {
        int currentLocked = getLockedSlots(playerId);
        return (currentLocked + additionalSlots) >= SlotSteal.TOTAL_INVENTORY_SLOTS;
    }

    public static void loadData(MinecraftServer server) {
        Path savePath = server.getSavePath(WorldSavePath.ROOT).resolve(DATA_FILE);

        if (Files.exists(savePath)) {
            try (Reader reader = Files.newBufferedReader(savePath)) {
                Type type = new TypeToken<Map<UUID, PlayerSlotData>>() {}.getType();
                Map<UUID, PlayerSlotData> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    playerData = loaded;
                    SlotSteal.LOGGER.info("Loaded Slot Steal data for {} players", playerData.size());
                }
            } catch (Exception e) {
                SlotSteal.LOGGER.error("Failed to load Slot Steal data", e);
            }
        }
    }

    public static void saveData(MinecraftServer server) {
        Path savePath = server.getSavePath(WorldSavePath.ROOT).resolve(DATA_FILE);

        try (Writer writer = Files.newBufferedWriter(savePath)) {
            GSON.toJson(playerData, writer);
            SlotSteal.LOGGER.info("Saved Slot Steal data for {} players", playerData.size());
        } catch (Exception e) {
            SlotSteal.LOGGER.error("Failed to save Slot Steal data", e);
        }
    }
}
