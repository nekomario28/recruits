package com.talhanation.recruits.compat.workers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public interface IVillagerWorker {
   ItemStack getCustomProfessionItem();
   //For SCREEN + CONTAINER
   void openSpecialGUI(ServerPlayer player);
   //SET IF SCREEN OR SCREEN+CONTAINER
   boolean hasOnlyScreen();
}