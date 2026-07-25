package com.talhanation.recruits;

import com.talhanation.recruits.entities.*;
import com.talhanation.recruits.init.ModEntityTypes;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import java.util.Random;

public class AttributeEvent {
    protected final Random random = new Random();

    public static void entityAttributeEvent(final EntityAttributeCreationEvent event) {
        //event.put(ModEntityTypes.ASSASSIN.get(), AssassinEntity.setAttributes().build());
        //event.put(ModEntityTypes.ASSASSIN_LEADER.get(), AssassinLeaderEntity.setAttributes().build());
        event.put(ModEntityTypes.BOWMAN.get(), BowmanEntity.setAttributes().build());
        event.put(ModEntityTypes.CROSSBOWMAN.get(), CrossBowmanEntity.setAttributes().build());
        event.put(ModEntityTypes.NOMAD.get(), NomadEntity.setAttributes().build());
        event.put(ModEntityTypes.RECRUIT.get(), RecruitEntity.setAttributes().build());
        event.put(ModEntityTypes.RECRUIT_SHIELDMAN.get(), RecruitShieldmanEntity.setAttributes().build());
        event.put(ModEntityTypes.HORSEMAN.get(), HorsemanEntity.setAttributes().build());
        event.put(ModEntityTypes.MESSENGER.get(), MessengerEntity.setAttributes().build());
        event.put(ModEntityTypes.PATROL_LEADER.get(), CommanderEntity.setAttributes().build());
        event.put(ModEntityTypes.CAPTAIN.get(), CaptainEntity.setAttributes().build());
        event.put(ModEntityTypes.SCOUT.get(), ScoutEntity.setAttributes().build());
        event.put(ModEntityTypes.VILLAGER_NOBLE.get(), VillagerNobleEntity.setAttributes().build());
        event.put(ModEntityTypes.SIEGE_ENGINEER.get(), SiegeEngineerEntity.setAttributes().build());

    }
}
