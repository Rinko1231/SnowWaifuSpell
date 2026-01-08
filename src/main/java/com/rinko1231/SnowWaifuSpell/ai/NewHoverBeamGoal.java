package com.rinko1231.SnowWaifuSpell.ai;

import com.rinko1231.SnowWaifuSpell.entity.SummonedSnowQueen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import twilightforest.entity.ai.goal.HoverBaseGoal;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class NewHoverBeamGoal extends HoverBaseGoal<SummonedSnowQueen> {
    public NewHoverBeamGoal(SummonedSnowQueen snowQueen, int hoverTime) {
        super(snowQueen, 4.0F, hoverTime);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.attacker.isOrderedToSit()) return false;
        LivingEntity target = this.attacker.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (this.attacker.isOrderedToSit()) return false;
        LivingEntity target = this.attacker.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void stop() {
        this.attacker.getNavigation().stop();
        // 不要改喷雾状态，不要开火
    }

    @Override
    public void tick() {
        LivingEntity target = this.attacker.getTarget();
        if (target == null) {
            this.attacker.getNavigation().stop();
            return;
        }

        updateHoverPosition(target);

        this.attacker.lookAt(target, 30.0F, 30.0F);
        this.attacker.getLookControl().setLookAt(target);

        double distanceSq = this.attacker.distanceToSqr(this.hoverPosX, this.hoverPosY, this.hoverPosZ);
        if (distanceSq > 4.0) {
            this.attacker.getNavigation().moveTo(this.hoverPosX, this.hoverPosY, this.hoverPosZ, 1.8);
        } else {
            this.attacker.getNavigation().stop();
        }
    }

    private void updateHoverPosition(LivingEntity target) {
        final double horizontalOffset = 5.0 + this.attacker.getRandom().nextDouble() * 3.0;
        final double verticalOffset   = 4.0 + this.attacker.getRandom().nextDouble() * 2.0;

        Vec3 toTarget = target.position().subtract(this.attacker.position()).normalize();
        Vec3 hoverOffset = new Vec3(-toTarget.z, 0, toTarget.x).scale(horizontalOffset);

        this.hoverPosX = target.getX() + hoverOffset.x;

        // 主人在地面时降低高度；否则最多离目标“脚下”+4 格
        double baseY = target.getY();
        if (target == this.attacker.getOwner() && target.onGround()) {
            this.hoverPosY = baseY + 2.0;
        } else {
            double desiredY = baseY + verticalOffset;
            this.hoverPosY = Math.min(desiredY, baseY + 4.0);
        }

        this.hoverPosZ = target.getZ() + hoverOffset.z;
    }
}
