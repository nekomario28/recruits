package com.talhanation.recruits.mixin;

import com.talhanation.recruits.entities.AbstractRecruitEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), remap = false)
    private void recruits$targetAttackerWhenMountIsHurt(
            DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if ((Object) this instanceof Animal animal
                && animal.isAlive()
                && animal.isVehicle()
                && animal.getControllingPassenger()
                instanceof AbstractRecruitEntity recruit
                && source.getEntity() instanceof LivingEntity attacker
                && recruit.canAttack(attacker)) {
            recruit.setTarget(attacker);
        }
    }
}
