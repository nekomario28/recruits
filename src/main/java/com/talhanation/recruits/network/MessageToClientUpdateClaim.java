package com.talhanation.recruits.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.api.ClientClaimEvent;
import com.talhanation.recruits.client.gui.worldmap.claim.WorldMapClaimIndex;
import com.talhanation.recruits.network.codec.ClaimNetworkCodec;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import com.talhanation.recruits.network.compat.RecruitsNetworkContext;
import com.talhanation.recruits.world.RecruitsClaim;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.common.NeoForge;

public class MessageToClientUpdateClaim implements RecruitsMessage<MessageToClientUpdateClaim> {
    private RecruitsClaim claim;

    public MessageToClientUpdateClaim() {
    }

    public MessageToClientUpdateClaim(RecruitsClaim claim) {
        this.claim = claim;
    }

    @Override
    public PacketFlow getExecutingSide() {
        return PacketFlow.CLIENTBOUND;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void executeClientSide(RecruitsNetworkContext context) {
        updateOrAddClaim(this.claim);
    }

    @OnlyIn(Dist.CLIENT)
    private void updateOrAddClaim(RecruitsClaim newClaim) {
        if (newClaim == null) return;
        if (newClaim.isRemoved) {
            removeClaim(newClaim);
            return;
        }

        for (int i = 0; i < ClientManager.recruitsClaims.size(); i++) {
            RecruitsClaim existing = ClientManager.recruitsClaims.get(i);
            if (existing.getUUID().equals(newClaim.getUUID())) {
                ClientManager.recruitsClaims.set(i, newClaim);
                WorldMapClaimIndex.invalidate();
                boolean isCurrentClaim = ClientManager.currentClaim != null
                        && ClientManager.currentClaim.getUUID().equals(newClaim.getUUID());
                if (isCurrentClaim) ClientManager.currentClaim = newClaim;
                ClientManager.updateActiveSiege(newClaim);
                NeoForge.EVENT_BUS.post(new ClientClaimEvent.DataUpdated(newClaim, isCurrentClaim));
                return;
            }
        }

        ClientManager.recruitsClaims.add(newClaim);
        WorldMapClaimIndex.invalidate();
        ClientManager.updateActiveSiege(newClaim);
        NeoForge.EVENT_BUS.post(new ClientClaimEvent.DataUpdated(newClaim, false));
    }

    @OnlyIn(Dist.CLIENT)
    private void removeClaim(RecruitsClaim removedClaim) {
        boolean wasCurrentClaim = ClientManager.currentClaim != null
                && ClientManager.currentClaim.getUUID().equals(removedClaim.getUUID());
        ClientManager.recruitsClaims.removeIf(
                claim -> claim != null && claim.getUUID().equals(removedClaim.getUUID()));
        ClientManager.activeSiegeClaims.remove(removedClaim.getUUID());
        if (wasCurrentClaim) ClientManager.currentClaim = null;
        WorldMapClaimIndex.invalidate();
        NeoForge.EVENT_BUS.post(new ClientClaimEvent.DataUpdated(removedClaim, wasCurrentClaim));
    }

    @Override
    public MessageToClientUpdateClaim fromBytes(FriendlyByteBuf buf) {
        this.claim = ClaimNetworkCodec.readNullableClaim(buf);
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        ClaimNetworkCodec.writeNullableClaim(buf, this.claim);
    }
}
