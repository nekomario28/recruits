package com.talhanation.recruits.world;

import com.talhanation.recruits.ClaimEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.talhanation.recruits.Main;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.config.RecruitsServerConfig;
import com.talhanation.recruits.network.MessageToClientUpdateClaim;
import com.talhanation.recruits.network.MessageToClientUpdateClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import com.talhanation.recruits.network.compat.RecruitsPacketDistributor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BooleanSupplier;
public class RecruitsClaimManager {
    private static final int CLAIMS_PER_SYNC_PACKET = 256;
    private static final int CLAIM_CHUNKS_PER_SYNC_PACKET = 8192;
    private final Map<ChunkPos, RecruitsClaim> claims = new HashMap<>();
    private final Map<UUID, RecruitsClaim> activeSieges = new HashMap<>();

    public void load(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        this.claims.clear();
        this.activeSieges.clear();
        for (RecruitsClaim claim : data.getAllClaims()) {
            for (ChunkPos pos : claim.getClaimedChunks()) {
                this.claims.put(pos, claim);
            }
            if (claim.isUnderSiege) {
                this.activeSieges.put(claim.getUUID(), claim);
            }
        }
    }

    public void save(ServerLevel level) {
        RecruitsClaimSaveData data = RecruitsClaimSaveData.get(level);
        data.setAllClaims(new ArrayList<>(new HashSet<>(this.claims.values())));
        data.setDirty();
    }

    public void addOrUpdateClaim(ServerLevel level, RecruitsClaim claim) {
        tryAddOrUpdateClaim(level, claim, () -> true);
    }

    public boolean tryAddOrUpdateClaim(ServerLevel level, RecruitsClaim claim, BooleanSupplier beforeCommit) {
        if (claim == null) return false;

        // ClaimEvent.Updated feuern – cancelable
        boolean isNew = claims.values().stream().noneMatch(c -> c.getUUID().equals(claim.getUUID()));
        ClaimEvent.Updated updateEvent = new ClaimEvent.Updated(claim, level, isNew);
        NeoForge.EVENT_BUS.post(updateEvent);
        if (updateEvent.isCanceled()) return false;
        if (!beforeCommit.getAsBoolean()) return false;

        claims.entrySet().removeIf(entry -> entry.getValue().getUUID().equals(claim.getUUID()));

        if(!claim.isRemoved){
            for (ChunkPos pos : claim.getClaimedChunks()) {
                this.claims.put(pos, claim);
            }
        }

        this.broadcastClaimsToAll(level);
        return true;
    }

    public void removeClaim(RecruitsClaim claim) {
        if (claim != null) {
            // ClaimEvent.Removed feuern
            ServerLevel level = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
            NeoForge.EVENT_BUS.post(new ClaimEvent.Removed(claim, level));

            claims.entrySet().removeIf(entry -> entry.getValue().equals(claim));
            activeSieges.remove(claim.getUUID());
        }
    }

    public void addActiveSiege(RecruitsClaim claim) {
        if (claim != null) {
            activeSieges.put(claim.getUUID(), claim);
        }
    }

    public void removeActiveSiege(RecruitsClaim claim) {
        if (claim != null) {
            activeSieges.remove(claim.getUUID());
        }
    }

    public Collection<RecruitsClaim> getActiveSieges() {
        return activeSieges.values();
    }

    public boolean isActiveSiege(RecruitsClaim claim) {
        return claim != null && activeSieges.containsKey(claim.getUUID());
    }

    // -------------------------------------------------------------------------

    @Nullable
    public RecruitsClaim getClaim(ChunkPos chunkPos) {
        return this.claims.get(chunkPos);
    }

    @Nullable
    public RecruitsClaim getClaim(int chunkX, int chunkZ) {
        return this.getClaim(new ChunkPos(chunkX, chunkZ));
    }

    @Nullable
    public RecruitsClaim getClaim(UUID claimId) {
        if (claimId == null) return null;
        for (RecruitsClaim claim : new HashSet<>(this.claims.values())) {
            if (claimId.equals(claim.getUUID())) return claim;
        }
        return null;
    }

    public List<RecruitsClaim> getAllClaims() {
        return new ArrayList<>(new HashSet<>(this.claims.values()));
    }

    public boolean claimExists(RecruitsClaim claim, List<ChunkPos> allPos) {
        for (ChunkPos pos : allPos) {
            if (claims.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    public static RecruitsClaim getClaimAt(ChunkPos pos, List<RecruitsClaim> allClaims) {
        for (RecruitsClaim claim : allClaims) {
            if (claim.containsChunk(pos)) {
                return claim;
            }
        }
        return null;
    }

    public void broadcastClaimsToAll(ServerLevel level) {
        List<RecruitsClaim> allClaims = this.getAllClaims();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            this.sendClaimsTo(player, allClaims);
        }
    }

    public void sendClaimsTo(ServerPlayer player) {
        if (player == null) return;
        this.sendClaimsTo(player, this.getAllClaims());
    }

    private void sendClaimsTo(ServerPlayer player, List<RecruitsClaim> allClaims) {
        if (allClaims == null || allClaims.isEmpty()) {
            sendClaimBatch(player, List.of(), true, true);
            return;
        }
        boolean resetClaims = true;
        int batchChunkCount = 0;
        List<RecruitsClaim> batch = new ArrayList<>();
        for (RecruitsClaim claim : allClaims) {
            int claimChunkCount = claim == null || claim.getClaimedChunks() == null ? 0 : claim.getClaimedChunks().size();
            boolean batchFull = batch.size() >= CLAIMS_PER_SYNC_PACKET;
            boolean chunkBudgetFull = !batch.isEmpty()
                    && batchChunkCount + claimChunkCount > CLAIM_CHUNKS_PER_SYNC_PACKET;
            if (batchFull || chunkBudgetFull) {
                sendClaimBatch(player, batch, resetClaims, false);
                resetClaims = false;
                batch = new ArrayList<>();
                batchChunkCount = 0;
            }
            batch.add(claim);
            batchChunkCount += claimChunkCount;
        }
        if (!batch.isEmpty()) sendClaimBatch(player, batch, resetClaims, true);
    }

    private void sendClaimBatch(ServerPlayer player, List<RecruitsClaim> batch,
                                boolean resetClaims, boolean syncComplete) {
        Main.SIMPLE_CHANNEL.send(RecruitsPacketDistributor.PLAYER.with(() -> player),
                new MessageToClientUpdateClaims(
                        batch,
                        RecruitsServerConfig.ClaimingCost.get(),
                        RecruitsServerConfig.ChunkCost.get(),
                        RecruitsServerConfig.MaxClaimChunks.get(),
                        RecruitsServerConfig.CascadeThePriceOfClaims.get(),
                        RecruitsServerConfig.AllowClaiming.get(),
                        RecruitsServerConfig.FogOfWarEnabled.get(),
                        FactionEvents.getCurrency(),
                        resetClaims,
                        syncComplete));
    }

    public void broadcastClaimUpdateTo(RecruitsClaim claim, List<ServerPlayer> players) {
        if (claim == null || players == null || players.isEmpty()) return;

        for (ServerPlayer player : players) {
            Main.SIMPLE_CHANNEL.send(RecruitsPacketDistributor.PLAYER.with(() -> player),
                    new MessageToClientUpdateClaim(claim));
        }
    }

    public void broadcastClaimUpdateToAll(ServerLevel level, RecruitsClaim claim) {
        if (level == null || claim == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Main.SIMPLE_CHANNEL.send(RecruitsPacketDistributor.PLAYER.with(() -> player),
                    new MessageToClientUpdateClaim(claim));
        }
    }
}
