package com.talhanation.recruits.client.events;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.entities.ICanTradeEmbargo;
import com.talhanation.recruits.world.RecruitsHireTradesRegistry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ClientVillagerEvents {
    @SubscribeEvent
    public void onLocalPlayerJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer) {
            RecruitsHireTradesRegistry.registerTrades();
        }
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) return;

        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (player == null || target == null) return;

        Team targetTeam = target.getTeam();
        String teamID = null;
        if (target instanceof ICanTradeEmbargo embargoTarget) {
            teamID = embargoTarget.getEmbargoTeamID();
        } else if (target instanceof Villager && targetTeam != null) {
            teamID = targetTeam.getName();
        }

        if (teamID == null || teamID.isEmpty()) return;
        String embargoed = ClientManager.embargoMap.getOrDefault(player.getUUID(), "");
        if (embargoed.contains(teamID)) {
            event.setCanceled(true);
        }
    }
}
