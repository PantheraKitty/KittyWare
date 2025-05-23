/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.DropItemsEvent;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerJumpEvent;
import meteordevelopment.meteorclient.events.entity.player.PlayerTravelEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.SoundBlocker;
import meteordevelopment.meteorclient.systems.modules.movement.*;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import meteordevelopment.meteorclient.systems.modules.player.SpeedMine;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity
{
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    @Shadow
    public abstract PlayerAbilities getAbilities();

    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    protected void clipAtLedge(CallbackInfoReturnable<Boolean> info)
    {
        if (!getWorld().isClient)
            return;

        ClipAtLedgeEvent event = MeteorClient.EVENT_BUS.post(ClipAtLedgeEvent.get());
        if (event.isSet())
            info.setReturnValue(event.isClip());
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
        at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack stack, boolean bl, boolean bl2,
                            CallbackInfoReturnable<ItemEntity> info)
    {
        if (getWorld().isClient && !stack.isEmpty())
        {
            if (MeteorClient.EVENT_BUS.post(DropItemsEvent.get(stack)).isCancelled())
                info.cancel();
        }
    }

    @ModifyReturnValue(method = "getBlockBreakingSpeed", at = @At(value = "RETURN"))
    public float onGetBlockBreakingSpeed(float breakSpeed, BlockState block)
    {
        if (!getWorld().isClient)
            return breakSpeed;

        SpeedMine speedMine = Modules.get().get(SpeedMine.class);
        if (!speedMine.isActive() || speedMine.mode.get() != SpeedMine.Mode.Normal
            || !speedMine.filter(block.getBlock()))
            return breakSpeed;

        float breakSpeedMod = (float) (breakSpeed * speedMine.modifier.get());

        if (mc.crosshairTarget instanceof BlockHitResult bhr)
        {
            BlockPos pos = bhr.getBlockPos();
            if (speedMine.modifier.get() < 1 || (BlockUtils.canInstaBreak(pos,
                breakSpeed) == BlockUtils.canInstaBreak(pos, breakSpeedMod)))
            {
                return breakSpeedMod;
            } else
            {
                return 0.9f / BlockUtils.calcBlockBreakingDelta2(pos, 1);
            }
        }

        return breakSpeed;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    public void dontJump(CallbackInfo info)
    {
        if (!getWorld().isClient)
            return;

        Anchor module = Modules.get().get(Anchor.class);
        if (module.isActive() && module.cancelJump)
            info.cancel();
        else if (Modules.get().get(Scaffold.class).towering())
            info.cancel();
    }

    @ModifyReturnValue(method = "getMovementSpeed", at = @At("RETURN"))
    private float onGetMovementSpeed(float original)
    {
        if (!getWorld().isClient)
            return original;
        if (!Modules.get().get(NoSlow.class).slowness())
            return original;

        float walkSpeed = getAbilities().getWalkSpeed();

        if (original < walkSpeed)
        {
            if (isSprinting())
                return (float) (walkSpeed * 1.30000001192092896);
            else
                return walkSpeed;
        }

        return original;
    }

    @Inject(method = "getOffGroundSpeed", at = @At("HEAD"), cancellable = true)
    private void onGetOffGroundSpeed(CallbackInfoReturnable<Float> info)
    {
        if (!getWorld().isClient)
            return;

        float speed = Modules.get().get(Flight.class).getOffGroundSpeed();
        if (speed != -1)
            info.setReturnValue(speed);
    }

    @WrapWithCondition(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/player/PlayerEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private boolean keepSprint$setVelocity(PlayerEntity instance, Vec3d vec3d)
    {
        return Modules.get().get(Sprint.class).stopSprinting();
    }

    @WrapWithCondition(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/player/PlayerEntity;setSprinting(Z)V"))
    private boolean keepSprint$setSprinting(PlayerEntity instance, boolean b)
    {
        return Modules.get().get(Sprint.class).stopSprinting();
    }

    @ModifyReturnValue(method = "getBlockInteractionRange", at = @At("RETURN"))
    private double modifyBlockInteractionRange(double original)
    {
        return Math.max(0, original + Modules.get().get(Reach.class).blockReach());
    }

    @ModifyReturnValue(method = "getEntityInteractionRange", at = @At("RETURN"))
    private double modifyEntityInteractionRange(double original)
    {
        return Math.max(0, original + Modules.get().get(Reach.class).entityReach());
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void onJumpPre(CallbackInfo ci)
    {
        MeteorClient.EVENT_BUS.post(new PlayerJumpEvent.Pre());
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void onJumpPost(CallbackInfo ci)
    {
        MeteorClient.EVENT_BUS.post(new PlayerJumpEvent.Post());
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3d movementInput, CallbackInfo ci)
    {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player != mc.player)
            return;

        PlayerTravelEvent.Pre event = new PlayerTravelEvent.Pre();
        MeteorClient.EVENT_BUS.post(event);
        if (event.isCancelled())
        {
            ci.cancel();
            PlayerTravelEvent.Post forcedPostEvent = new PlayerTravelEvent.Post();
            MeteorClient.EVENT_BUS.post(forcedPostEvent);
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3d movementInput, CallbackInfo ci)
    {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player != mc.player)
            return;

        PlayerTravelEvent.Post event = new PlayerTravelEvent.Post();
        MeteorClient.EVENT_BUS.post(event);
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V"))
    private void poseNotCollide(World instance, PlayerEntity except, double x, double y, double z, SoundEvent sound, SoundCategory category, float volume, float pitch)
    {
        SoundBlocker soundBlocker = Modules.get().get(SoundBlocker.class);

        if (soundBlocker.isActive())
        {
            instance.playSound(except, x, y, z, sound, category,
                (float) (volume * soundBlocker.getCrystalHitVolume()), pitch);
            return;
        }

        instance.playSound(except, x, y, z, sound, category, volume, pitch);
    }
}
