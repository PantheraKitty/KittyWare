package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.movement.MovementFix;
import meteordevelopment.meteorclient.utils.entity.ProjectileEntitySimulator;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.data.server.advancement.AdvancementProvider;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;

public class PearlPhase extends Module {
    private final SettingGroup sgGeneral;
    private final Setting<Keybind> phaseBind;
    private final Setting<PearlPhase.RotateMode> rotateMode;
    private final Setting<Boolean> burrow;
    private final Setting<Boolean> antiPearlFail;
    private final Setting<Boolean> antiPearlFailStrict;
    private boolean active;
    private boolean keyUnpressed;
    private final ProjectileEntitySimulator simulator;

    public PearlPhase() {
        super(Categories.Combat, "auto-pearl-phase", "Phases into walls using pearls");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.phaseBind = this.sgGeneral.add(((KeybindSetting.Builder)((KeybindSetting.Builder)(new KeybindSetting.Builder()).name("key-bind")).description("Phase on keybind press")).build());
        this.rotateMode = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("rotate-mode")).description("Which method of rotating should be used.")).defaultValue(PearlPhase.RotateMode.DelayedInstantWebOnly)).build());
        this.burrow = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("borrow")).description("Places a block where you phase.")).defaultValue(true)).build());
        this.antiPearlFail = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("anti-pearl-fail")).description("Hits entites below you when you phase.")).defaultValue(true)).build());
        this.antiPearlFailStrict = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("anti-pearl-fail-strict")).description("Waits for the entity to disapear before phasing.")).defaultValue(false)).build());
        this.active = false;
        this.keyUnpressed = false;
        this.simulator = new ProjectileEntitySimulator();
    }

    private void activate() {
        this.active = true;
        if (this.mc.player != null && this.mc.world != null) {
            this.update();
        }
    }

    private void deactivate(boolean phased) {
        this.active = false;
        if (phased) {
            this.info("Phased", new Object[0]);
        }

    }

    private void update() {
        if (this.mc.player != null && this.mc.world != null) {
            if (this.active) {
                Box boundingBox = this.mc.player.getBoundingBox().shrink(0.05D, 0.1D, 0.05D);
                double feetY = this.mc.player.getY();
                Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX, feetY + 0.1D, boundingBox.maxZ);
                if (BlockPos.stream(feetBox).anyMatch((blockPos) -> {
                    return this.mc.world.getBlockState(blockPos).isSolidBlock(this.mc.world, blockPos);
                })) {
                    this.deactivate(false);
                }

                if (!MeteorClient.SWAP.canSwap(Items.ENDER_PEARL)) {
                    this.deactivate(false);
                } else if (this.mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
                    this.deactivate(false);
                } else if (this.mc.options.sneakKey.isPressed() || this.mc.player.isCrawling()) {
                    this.deactivate(false);
                }
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.active) {
            Vec3d targetPos = this.calculateTargetPos();
            float[] angle = MeteorClient.ROTATION.getRotation(targetPos);
            switch(((PearlPhase.RotateMode)this.rotateMode.get()).ordinal()) {
                case 0:
                    MeteorClient.ROTATION.requestRotation(targetPos, 1000.0D);
                    if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05D, 0.05D, 0.05D))) {
                        this.throwPearl(angle[0], angle[1]);
                    }
                    break;
                case 1:
                    if (this.mc.player.isOnGround()) {
                        MeteorClient.ROTATION.snapAt(targetPos);
                        this.throwPearl(angle[0], angle[1]);
                    }
                    break;
                case 2:
                    MeteorClient.ROTATION.requestRotation(targetPos, 1000.0D);
                    if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05D, 0.05D, 0.05D))) {
                        MeteorClient.ROTATION.snapAt(targetPos);
                        this.throwPearl(angle[0], angle[1]);
                    }
                    break;
                case 3:
                    MeteorClient.ROTATION.requestRotation(targetPos, 1000.0D);
                    if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05D, 0.05D, 0.05D))) {
                        if (MovementFix.inWebs) {
                            MeteorClient.ROTATION.snapAt(targetPos);
                        }

                        this.throwPearl(angle[0], angle[1]);
                    }
            }

        }
    }

    private void throwPearl(float yaw, float pitch) {
        if ((Boolean)this.antiPearlFail.get()) {
            HitResult hitResult = this.getEnderPearlHitResult();
            if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                Entity hitEntity = ((EntityHitResult)hitResult).getEntity();
                if (hitEntity instanceof EndCrystalEntity || hitEntity instanceof ItemFrameEntity) {
                    MeteorClient.ROTATION.requestRotation(hitEntity.getPos(), 11.0D);
                    if (!MeteorClient.ROTATION.lookingAt(hitEntity.getBoundingBox()) && RotationManager.lastGround) {
                        MeteorClient.ROTATION.snapAt(hitEntity.getPos());
                    }

                    if (MeteorClient.ROTATION.lookingAt(hitEntity.getBoundingBox())) {
                        this.mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(hitEntity, this.mc.player.isSneaking()));
                    }
                }

                if ((Boolean)this.antiPearlFailStrict.get() && hitEntity != null) {
                    return;
                }
            }

            if (this.mc.world.getBlockState(this.mc.player.getBlockPos()).isOf(Blocks.SCAFFOLDING)) {
                this.mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, this.mc.player.getBlockPos(), Direction.UP, this.mc.world.getPendingUpdateManager().incrementSequence().getSequence()));
                if ((Boolean)this.antiPearlFailStrict.get()) {
                    return;
                }
            }
        }

        if ((Boolean)this.burrow.get() && !this.mc.player.isUsingItem()) {
            Vec3d targetPos = this.calculateTargetPos();
            Box newHitbox = this.mc.player.getBoundingBox().offset(targetPos.x - this.mc.player.getX(), 0.0D, targetPos.z - this.mc.player.getZ()).expand(0.05D);
            List<BlockPos> placePoses = new ArrayList();
            int minX = (int)Math.floor(newHitbox.minX);
            int maxX = (int)Math.floor(newHitbox.maxX);
            int minZ = (int)Math.floor(newHitbox.minZ);
            int maxZ = (int)Math.floor(newHitbox.maxZ);

            for(int x = minX; x <= maxX; ++x) {
                for(int z = minZ; z <= maxZ; ++z) {
                    BlockPos feetPos = new BlockPos(x, this.mc.player.getBlockPos().getY(), z);
                    placePoses.add(feetPos);
                }
            }

            if (MeteorClient.BLOCK.beginPlacement(placePoses, Items.OBSIDIAN)) {
                placePoses.forEach((blockPos) -> {
                    MeteorClient.BLOCK.placeBlock(Items.OBSIDIAN, blockPos);
                });
                MeteorClient.BLOCK.endPlacement();
            }
        }

        if (MeteorClient.SWAP.beginSwap(Items.ENDER_PEARL, true)) {
            int sequence = this.mc.world.getPendingUpdateManager().incrementSequence().getSequence();
            this.mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));
            this.deactivate(true);
            MeteorClient.SWAP.endSwap(true);
        }

    }

    @EventHandler(
        priority = 200
    )
    private void onRender(Render3DEvent event) {
        if (!((Keybind)this.phaseBind.get()).isPressed()) {
            this.keyUnpressed = true;
        }

        if (((Keybind)this.phaseBind.get()).isPressed() && this.keyUnpressed && !(this.mc.currentScreen instanceof ChatScreen)) {
            this.activate();
            this.keyUnpressed = false;
        }

        this.update();
    }

    private HitResult getEnderPearlHitResult() {
        if (!this.simulator.set(this.mc.player, Items.ENDER_PEARL.getDefaultStack(), 0.0D, false, 1.0F)) {
            return null;
        } else {
            for(int i = 0; i < 256; ++i) {
                HitResult result = this.simulator.tick();
                if (result != null) {
                    return result;
                }
            }

            return null;
        }
    }

    private Vec3d calculateTargetPos() {
        double X_OFFSET = 0.241660973353061D;
        double Z_OFFSET = 0.7853981633974483D;
        double playerX = this.mc.player.getX();
        double playerZ = this.mc.player.getZ();
        double x = playerX + MathHelper.clamp(this.toClosest(playerX, Math.floor(playerX) + 0.241660973353061D, Math.floor(playerX) + 0.7853981633974483D) - playerX, -0.2D, 0.2D);
        double z = playerZ + MathHelper.clamp(this.toClosest(playerZ, Math.floor(playerZ) + 0.241660973353061D, Math.floor(playerZ) + 0.7853981633974483D) - playerZ, -0.2D, 0.2D);
        return new Vec3d(x, this.mc.player.getY() - 0.5D, z);
    }

    private double toClosest(double num, double min, double max) {
        double dmin = num - min;
        double dmax = max - num;
        return dmax > dmin ? min : max;
    }

    public static enum RotateMode {
        Movement,
        Instant,
        DelayedInstant,
        DelayedInstantWebOnly;

        // $FF: synthetic method
        private static PearlPhase.RotateMode[] $values() {
            return new PearlPhase.RotateMode[]{Movement, Instant, DelayedInstant, DelayedInstantWebOnly};
        }
    }

    public static enum SwitchMode {
        SilentHotbar,
        SilentSwap;

        // $FF: synthetic method
        private static PearlPhase.SwitchMode[] $values() {
            return new PearlPhase.SwitchMode[]{SilentHotbar, SilentSwap};
        }
    }
}
