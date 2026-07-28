package com.talhanation.recruits.network;

import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.network.compat.RecruitsMessage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.PacketFlow;
import com.talhanation.recruits.network.compat.RecruitsNetworkContext;

import java.util.UUID;


public class MessageDeleteClaim implements RecruitsMessage<MessageDeleteClaim> {

    private UUID claimId;

    public MessageDeleteClaim(){

    }

    public MessageDeleteClaim(RecruitsClaim claim) {
        this.claimId = claim == null ? null : claim.getUUID();
    }

    public PacketFlow getExecutingSide() {
        return PacketFlow.SERVERBOUND;
    }

    public void executeServerSide(RecruitsNetworkContext context){
        ServerPlayer player = context.getSender();
        if (player == null || this.claimNBT == null || this.claimNBT.isEmpty()) return;
        RecruitsClaim requestedClaim = ClaimNetworkAuthority.readClaim(this.claimNBT);
        if (requestedClaim == null) return;
        RecruitsClaim claim = ClaimNetworkAuthority.claimByUuid(requestedClaim.getUUID());
        if (claim == null) return;
        if (!ClaimNetworkAuthority.isCreativeAdmin(player)) return;

        ClaimEvents.recruitsClaimManager.removeClaim(claim);
        ClaimEvents.recruitsClaimManager.broadcastClaimsToAll((ServerLevel) player.getCommandSenderWorld());
    }

    public MessageDeleteClaim fromBytes(FriendlyByteBuf buf) {
        this.claimId = buf.readBoolean() ? buf.readUUID() : null;
        return this;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.claimId != null);
        if (this.claimId != null) {
            buf.writeUUID(this.claimId);
        }
    }
}
