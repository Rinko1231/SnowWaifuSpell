package com.rinko1231.SnowWaifuSpell.entity;

import com.rinko1231.SnowWaifuSpell.ai.FlyingFollowOwnerGoal;
import com.rinko1231.SnowWaifuSpell.ai.NewHoverBeamGoal;
import com.rinko1231.SnowWaifuSpell.ai.NewSitWhenOrderedToGoal;
import com.rinko1231.SnowWaifuSpell.config.SnowWaifuConfig;
import com.rinko1231.SnowWaifuSpell.init.ModEntityRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.goals.*;
import io.redspace.ironsspellbooks.entity.spells.cone_of_cold.ConeOfColdProjectile;
import io.redspace.ironsspellbooks.entity.spells.ray_of_frost.RayOfFrostVisualEntity;
import io.redspace.ironsspellbooks.entity.spells.snowball.Snowball;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import twilightforest.init.TFParticleType;
import twilightforest.init.TFSounds;

import javax.annotation.Nullable;
import java.util.List;

public class SummonedSnowQueen extends TamableMob implements IMagicSummon {
    private static final EntityDataAccessor<Boolean> BEAM_FLAG;
    private static final EntityDataAccessor<Integer> QUEEN_LEVEL =
            SynchedEntityData.defineId(SummonedSnowQueen.class, EntityDataSerializers.INT);
    private static final int SNOWBALL_INTERVAL = SnowWaifuConfig.snowBallInterval.get(); // 5 秒
    private static final int ICE_RAY_INTERVAL = SnowWaifuConfig.iceRayInterval.get(); // 8 秒

    static {
        BEAM_FLAG = SynchedEntityData.defineId(SummonedSnowQueen.class, EntityDataSerializers.BOOLEAN);
    }



    private int snowballCooldown = 0;
    private int iceRayCooldown = 0;

    public SummonedSnowQueen(EntityType<? extends SummonedSnowQueen> type, Level level) {
        super(type, level);
        this.xpReward = 0;

        //this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public SummonedSnowQueen(Level level, LivingEntity owner) {
        this(ModEntityRegistry.SUMMONED_SNOW_QUEEN.get(), level);
        this.setSummoner(owner);
        this.setOwnerUUID(owner.getUUID());
        //this.setNoGravity(true);
        this.moveControl = new FlyingMoveControl(this, 10, true);
    }

    public int getQueenLevel() {
        return this.entityData.get(QUEEN_LEVEL);
    }

    public void setQueenLevel(int level) {
        this.entityData.set(QUEEN_LEVEL, level);
    }

    public boolean isAlliedTo(Entity pEntity) {
        return super.isAlliedTo(pEntity) || this.isAlliedHelper(pEntity) || pEntity == this.getSummoner();
    }

    public void setSummoner(@Nullable LivingEntity owner) {
        if (owner != null) {
            SummonManager.setOwner(this, owner);
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource pSource, float pAmount) {
        return !this.shouldIgnoreDamage(pSource) && super.hurt(pSource, pAmount);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {

        ItemStack heldItem = player.getItemInHand(hand);
        Level level = player.level();

        // 仅主人可操作
        if (!this.isOwnedBy(player)) {
            return super.mobInteract(player, hand);
        }

        // 优先处理特殊物品交互
        if (!heldItem.isEmpty()) {
            if (heldItem.is(Items.BUCKET) && !this.isBaby()) {
                // 挤奶音效同步
                this.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                ItemStack filled = ItemUtils.createFilledResult(
                        heldItem, player, Items.MILK_BUCKET.getDefaultInstance()
                );
                player.setItemInHand(hand, filled);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            // TODO
        }

        // 空手交互逻辑
        boolean isSneaking = player.isShiftKeyDown();
        if (!isSneaking) {
            boolean sitting = this.isOrderedToSit();

            // 切换状态
            this.setOrderedToSit(!sitting);
            this.setInSittingPose(!sitting);

            // 停止行为
            this.setTarget(null);
            this.getNavigation().stop();

            // 坐下禁用移动
            if (this.isOrderedToSit()) {
                this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
            } else {
                this.goalSelector.enableControlFlag(Goal.Flag.LOOK);
                this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            }

            // 提示信息
            player.displayClientMessage(
                    Component.translatable(sitting
                            ? "message.snowwaifuspell.stand"
                            : "message.snowwaifuspell.sit"),
                    true
            );
        } else {
            // 潜行右击：传送并坐下
            BlockPos groundPos = player.blockPosition();
            this.teleportTo(groundPos.getX() + 0.5, groundPos.getY(), groundPos.getZ() + 0.5);
            this.setOrderedToSit(true);
            this.setInSittingPose(true);

            this.setTarget(null);
            this.getNavigation().stop();
            this.goalSelector.disableControlFlag(Goal.Flag.MOVE);

            player.displayClientMessage(
                    Component.translatable("message.snowwaifuspell.teleport_and_sit"),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TFSounds.SNOW_QUEEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TFSounds.SNOW_QUEEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TFSounds.SNOW_QUEEN_DEATH.get();
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        super.setOrderedToSit(sit);
        if (sit) {
            // 坐下时立刻停止攻击
            this.isBreathingPhase = false;
            this.setBreathing(false);
            // 冷却字段
            // 喷雾冷却计时
            this.snowballCooldown = 0;
            this.iceRayCooldown = 0;
            this.setTarget(null);
            forceStopBreath();
            this.getNavigation().stop();
            // 清除场上冰风
            this.level().getEntitiesOfClass(
                    ConeOfColdProjectile.class,
                    this.getBoundingBox().inflate(5.0), // 范围可调
                    p -> p.getOwner() == this
            ).forEach(Entity::discard);
        }
    }

    @Override
    public void tick() {
        super.tick();


        if (this.deathTime > 0) {
            for (int i = 0; i < 5; ++i) {
                double d = this.getRandom().nextGaussian() * 0.02;
                double d1 = this.getRandom().nextGaussian() * 0.02;
                double d2 = this.getRandom().nextGaussian() * 0.02;
                this.level().addParticle(
                        this.getRandom().nextBoolean() ? ParticleTypes.EXPLOSION : ParticleTypes.POOF,
                        this.getX() + (this.getRandom().nextFloat() * this.getBbWidth() * 2.0F) - this.getBbWidth(),
                        this.getY() + (this.getRandom().nextFloat() * this.getBbHeight()),
                        this.getZ() + (this.getRandom().nextFloat() * this.getBbWidth() * 2.0F) - this.getBbWidth(),
                        d, d1, d2
                );
            }
        }
    }


    // 常量定义
    private static final int BREATH_DURATION = SnowWaifuConfig.breathConeDuration.get(); // 3秒
    private static final int BREATH_COOLDOWN = SnowWaifuConfig.breathConeInterval.get();

    // 状态计数
    private int breathPhaseTimer = 0;
    private boolean isBreathingPhase = false;

    private void forceStopBreath() {
        this.isBreathingPhase = false;
        this.setBreathing(false);
        this.breathPhaseTimer = 0;
        // 清理锥体
        this.level().getEntitiesOfClass(
                ConeOfColdProjectile.class,
                this.getBoundingBox().inflate(6.0),
                p -> p.getOwner() == this
        ).forEach(Entity::discard);
    }

    private void handleCombatAI() {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (!hasTarget) {
            forceStopBreath();
            snowballCooldown = 0;
            iceRayCooldown = 0;
            return;
        }

        // === 相位计时 ===
        if (--breathPhaseTimer <= 0) {
            // 切换相位
            isBreathingPhase = !isBreathingPhase;
            setBreathing(isBreathingPhase);
            if (isBreathingPhase) {
                breathPhaseTimer = BREATH_DURATION; // 切换到开 → 持续喷雾
            } else {
                breathPhaseTimer = BREATH_COOLDOWN; // 切换到关 → 冷却期
                // 立刻清锥体
                this.level().getEntitiesOfClass(
                        ConeOfColdProjectile.class,
                        this.getBoundingBox().inflate(6.0),
                        p -> p.getOwner() == this
                ).forEach(Entity::discard);
            }
        }

        if (isBreathingPhase) {
            // 喷雾期间：持续伤害
            doBreathAttack();
        } else {
            // 冷却期：放雪球和冰射线
            if (snowballCooldown > 0) snowballCooldown--;
            if (iceRayCooldown > 0) iceRayCooldown--;

            if (snowballCooldown == 0) {
                castSnowball(target);
                snowballCooldown = SNOWBALL_INTERVAL;
            }

            if (iceRayCooldown == 0) {
                castIceRay();
                iceRayCooldown = ICE_RAY_INTERVAL;
            }
        }
    }
    @Override
    public void aiStep() {
        super.aiStep();

        if (this.isInSittingPose()) {
            forceStopBreath();
            return;
        }

        // 慢速下落与落地缓冲
        if (!this.onGround() && this.getDeltaMovement().y < 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
        }
        if (this.onGround() && Math.abs(this.getDeltaMovement().y) < 0.05D) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
        }

        if (!level().isClientSide) {
            handleCombatAI();
        }
        if (this.level().isClientSide()) {
            this.spawnParticles();
        }
    }


    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new NewSitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new NewHoverBeamGoal(this, 20));
        this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
        //this.goalSelector.addGoal(7, new GenericFollowOwnerGoal(this, this::getSummoner, (double) 1.5F, 15.0F, 4.0F, true, 25.0F));
        this.goalSelector.addGoal(7, new FlyingFollowOwnerGoal(this, this::getSummoner,
                1.5, 15.0F, 4.0F, 30.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        this.targetSelector.addGoal(1, new GenericOwnerHurtByTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(2, new GenericOwnerHurtTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(3, new GenericCopyOwnerTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(4, (new GenericHurtByTargetGoal(this, (entity) -> entity == this.getSummoner())).setAlertOthers());
        this.targetSelector.addGoal(5, new GenericProtectOwnerTargetGoal(this, this::getSummoner));

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BEAM_FLAG, false);
        this.entityData.define(QUEEN_LEVEL, 1); // 默认1级

    }

    @Override
    protected int calculateFallDamage(float p_21237_, float p_21238_) {
        return 0;
    }

    public void doBreathAttack() {
        if (!this.level().isClientSide) {
            List<ConeOfColdProjectile> existing = this.level().getEntitiesOfClass(
                    ConeOfColdProjectile.class,
                    this.getBoundingBox().inflate(2.0),
                    p -> p.getOwner() == this
            );
            if (!existing.isEmpty()) {
                existing.forEach(ConeOfColdProjectile::setDealDamageActive);
                return;
            }

            ConeOfColdProjectile cone = new ConeOfColdProjectile(this.level(), this);
            cone.setPos(this.getX(), this.getEyeY() * 0.7 + this.getY(), this.getZ());
            cone.setDamage(SnowWaifuConfig.getBreathDamage(this.getQueenLevel()));
            this.level().addFreshEntity(cone);
        }
    }

    private void spawnParticles() {
        // 雪花常驻粒子
        for (int i = 0; i < 3; ++i) {
            float px = (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.3F;
            float py = this.getEyeHeight() + (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.5F;
            float pz = (this.getRandom().nextFloat() - this.getRandom().nextFloat()) * 0.3F;
            this.level().addParticle(TFParticleType.SNOW_GUARDIAN.get(),
                    this.xOld + px, this.yOld + py, this.zOld + pz,
                    0.0, 0.0, 0.0);
        }


    }

    public boolean isBreathing() {
        return this.getEntityData().get(BEAM_FLAG);
    }

    public void setBreathing(boolean flag) {
        this.getEntityData().set(BEAM_FLAG, flag);
    }

    public void castSnowball(Entity target) {
        if (!(this.getSummoner() instanceof ServerPlayer)) return;
        Level level = this.level();

        Snowball orb = new Snowball(level, this); // 从女王位置发射
        orb.setOwner(this);
        orb.setPos(this.getX(), this.getEyeY() - orb.getBoundingBox().getYsize() * 0.5F, this.getZ());

        Vec3 direction = target.position().subtract(this.position()).normalize();
        orb.shoot(direction.x, direction.y + 0.1, direction.z, 1.2F, 0.5F);

        orb.setExplosionRadius(2.0F + this.getQueenLevel());
        orb.setDamage(40F);

        level.addFreshEntity(orb);
    }


    @Override
    public void onUnSummon() {
        if (!this.level().isClientSide) {
            MagicManager.spawnParticles(this.level(), ParticleTypes.POOF,
                    this.getX(), this.getY(), this.getZ(),
                    25, 0.4, 0.8, 0.4, 0.03, false);
            this.setRemoved(RemovalReason.DISCARDED);
        }
    }


    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(true);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target == owner) return false;
        return !(target instanceof SummonedSnowQueen);
    }

    @Override
    public void onRemovedFromWorld() {
        this.onRemovedHelper(this);
        super.onRemovedFromWorld();
    }

    private void castIceRay() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;

        Level level = this.level();
        int queenLevel = this.getQueenLevel();

        // 范围与数值
        final float range = 30.0F;
        final float damage = (float) SnowWaifuConfig.getRayDamage(queenLevel);
        final int freezeTime = (int) (queenLevel * 10.0F); // ticks

        // ======= 显式瞄准：从女王眼睛 → 目标的“瞄准点” =======
        // 选用目标“眼睛”更不容易穿地；对于超高实体也可换成 getBoundingBox().getCenter().y 或 0.6 ~ 0.8 的插值
        Vec3 start = this.getEyePosition();
        Vec3 aimAt = target.getEyePosition(); // 或 new Vec3(target.getX(), target.getY(0.6D), target.getZ())
        Vec3 dir = aimAt.subtract(start).normalize();
        Vec3 end = start.add(dir.scale(range));

        // 做实体/方块的综合碰撞检测；适度 Inflate 提升命中大体型/高速目标的稳定性
        HitResult hitResult = Utils.raycastForEntity(
                level,
                this,
                start,
                end,
                /*checkForBlocks*/ true,
                /*bbInflation*/ 0.2F,
                e -> e.isPickable() && e != this && e != this.getOwner()
        );

        // 视觉实体沿同一条线（避免特效与命中不一致）
        level.addFreshEntity(new RayOfFrostVisualEntity(level, start, hitResult.getLocation(), this));

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity hitEntity = ((EntityHitResult) hitResult).getEntity();

            // 用注册表拿 Ray of Frost，并用工厂方法造带冻结的 SpellDamageSource
            AbstractSpell frostSpell = SpellRegistry.RAY_OF_FROST_SPELL.get();
            SpellDamageSource frostSource = SpellDamageSource
                    .source(this, this, frostSpell)
                    .setFreezeTicks(freezeTime);

            hitEntity.hurt(frostSource, damage);

            MagicManager.spawnParticles(
                    level, ParticleHelper.ICY_FOG,
                    hitResult.getLocation().x, hitEntity.getY(), hitResult.getLocation().z,
                    4, 0, 0, 0, 0.3, true
            );
        } else if (hitResult.getType() == HitResult.Type.BLOCK) {
            MagicManager.spawnParticles(
                    level, ParticleHelper.ICY_FOG,
                    hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z,
                    4, 0, 0, 0, 0.3, true
            );
        }

        // 雪花收尾
        MagicManager.spawnParticles(
                level, ParticleHelper.SNOWFLAKE,
                hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z,
                50, 0, 0, 0, 0.3, false
        );
    }



}

