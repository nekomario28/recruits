package com.talhanation.recruits.network;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import com.talhanation.recruits.network.compat.RecruitsNetworkContext;
import com.talhanation.recruits.world.RecruitsClaim;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class MessageDeleteClaim implements RecruitsMessage<MessageDeleteClaim> {
    private UUID claimId;

    public MessageDeleteClaim() {
    }

    public MessageDeleteClaim(RecruitsClaim claim) {
        this.claimId = claim == null ? null : claim.getUUID();
    }

    @Override
    public PacketFlow getExecutingSide() {
        return PacketFlow.SERVERBOUND;
    }

    @Override
    public void executeServerSide(RecruitsNetworkContext context) {
        ServerPlayer player = context.getSender();
        if (player == null || this.claimId == null) return;
        RecruitsClaim claim = ClaimNetworkAuthority.claimByUuid(this.claimId);
        if (claim == null || !ClaimNetworkAuthority.isCreativeAdmin(player)) return;

        ClaimEvents.recruitsClaimManager.removeClaim(claim);
        ClaimEvents.recruitsClaimManager.broadcastClaimsToAll((ServerLevel) player.getCommandSenderWorld());
    }

    @Override
    public MessageDeleteClaim fromBytes(FriendlyByteBuf buf) {
        this.claimId = buf.readBoolean() ? buf.readUUID() : null;
        return this;
    }

    @Override
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.claimId != null);
        if (this.claimId != null) buf.writeUUID(this.claimId);
    }
}
