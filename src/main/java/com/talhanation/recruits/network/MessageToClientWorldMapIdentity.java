package com.talhanation.recruits.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.gui.worldmap.storage.WorldMapCacheManager;
import com.talhanation.recruits.client.gui.worldmap.storage.WorldMapStorageId;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import com.talhanation.recruits.network.compat.RecruitsNetworkContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;

import java.util.UUID;

public class MessageToClientWorldMapIdentity implements RecruitsMessage<MessageToClientWorldMapIdentity> {
    private UUID worldId;

    public MessageToClientWorldMapIdentity() {
    }

    public MessageToClientWorldMapIdentity(UUID worldId) {
        this.worldId = worldId;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return PacketFlow.CLIENTBOUND;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void executeClientSide(RecruitsNetworkContext context) {
        if (worldId == null) return;
        WorldMapStorageId.setServerWorldId(worldId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            WorldMapCacheManager.getInstance().initialize(mc.level);
        }
        ClientManager.loadRoutes();
        ClientManager.loadSpecialStates();
    }

    @Override
    public MessageToClientWorldMapIdentity fromBytes(FriendlyByteBuf buf) {
        this.worldId = buf.readUUID();
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(this.worldId);
    }
}
