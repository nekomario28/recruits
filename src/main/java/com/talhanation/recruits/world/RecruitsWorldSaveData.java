package com.talhanation.recruits.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

public class RecruitsWorldSaveData extends SavedData {
    private static final String FILE_ID = "recruitsWorldId";
    private static final Factory<RecruitsWorldSaveData> FACTORY = new Factory<>(
            RecruitsWorldSaveData::new,
            RecruitsWorldSaveData::load,
            DataFixTypes.LEVEL);

    private UUID worldId;

    public RecruitsWorldSaveData() {
        this.worldId = UUID.randomUUID();
        this.setDirty();
    }

    private RecruitsWorldSaveData(UUID worldId) {
        this.worldId = worldId == null ? UUID.randomUUID() : worldId;
        if (worldId == null) this.setDirty();
    }

    public static RecruitsWorldSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static RecruitsWorldSaveData load(CompoundTag nbt, HolderLookup.Provider registries) {
        UUID worldId = nbt.hasUUID("WorldId") ? nbt.getUUID("WorldId") : null;
        return new RecruitsWorldSaveData(worldId);
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putUUID("WorldId", worldId);
        return nbt;
    }

    public UUID getWorldId() {
        return worldId;
    }
}
