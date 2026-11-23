package com.girlfriend;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.UUID;

public class GirlfriendEntity extends PathAwareEntity {
    private UUID ownerUuid;
    private boolean staying;

    public GirlfriendEntity(EntityType<? extends GirlfriendEntity> type, World world) {
        super(type, world);
        this.setInvulnerable(true);   // she can't be hurt (creative players / void can still remove her)
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new FollowOwnerGoal(this));
        goalSelector.add(2, new WanderAroundFarGoal(this, 0.8) {
            @Override public boolean canStart() { return !isStaying() && super.canStart(); }
        });
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || staying;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwner(UUID uuid) { this.ownerUuid = uuid; }
    public boolean isStaying() { return staying; }
    public void setStaying(boolean s) { this.staying = s; if (s) getNavigation().stop(); }

    public PlayerEntity getOwner() {
        return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
    }

    public String displayName() {
        return getName().getString();
    }

    /** Right-click = stay/follow. Sneak + right-click = kiss on the cheek. */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        if (ownerUuid == null) {
            ownerUuid = player.getUuid();
            sp.sendMessage(Text.literal(displayName() + " is now yours \u2665"), true);
            return ActionResult.SUCCESS;
        }
        if (!player.getUuid().equals(ownerUuid)) return ActionResult.PASS;

        if (player.isSneaking()) {
            kiss(sp);
        } else {
            setStaying(!staying);
            sp.sendMessage(Text.literal(displayName() + (staying ? " will stay here." : " will follow you.")), true);
        }
        return ActionResult.SUCCESS;
    }

    public void kiss(ServerPlayerEntity player) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        getLookControl().lookAt(player, 30f, 30f);
        sw.spawnParticles(ParticleTypes.HEART, getX(), getY() + 1.9, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.4f);
        player.sendMessage(Text.literal(displayName() + " blushes and kisses you on the cheek \u2665"), true);
        GirlfriendAI.talk(this, player, "*gives you a sweet kiss on the cheek*");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) nbt.putUuid("GfOwner", ownerUuid);
        nbt.putBoolean("GfStaying", staying);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("GfOwner")) ownerUuid = nbt.getUuid("GfOwner");
        staying = nbt.getBoolean("GfStaying");
    }

    /** Walks toward her owner and teleports if she gets left far behind. */
    static class FollowOwnerGoal extends Goal {
        private final GirlfriendEntity gf;
        private int cooldown;

        FollowOwnerGoal(GirlfriendEntity gf) {
            this.gf = gf;
            setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override public boolean canStart() {
            PlayerEntity o = gf.getOwner();
            return o != null && !gf.isStaying() && gf.squaredDistanceTo(o) > 36;
        }

        @Override public boolean shouldContinue() {
            PlayerEntity o = gf.getOwner();
            return o != null && !gf.isStaying() && gf.squaredDistanceTo(o) > 9;
        }

        @Override public void tick() {
            PlayerEntity o = gf.getOwner();
            if (o == null || --cooldown > 0) return;
            cooldown = 10;
            if (gf.squaredDistanceTo(o) > 400) {
                gf.refreshPositionAndAngles(o.getX(), o.getY(), o.getZ(), gf.getYaw(), gf.getPitch());
                gf.getNavigation().stop();
            } else {
                gf.getNavigation().startMovingTo(o, 1.2);
            }
        }

        @Override public void stop() { gf.getNavigation().stop(); }
    }
}
