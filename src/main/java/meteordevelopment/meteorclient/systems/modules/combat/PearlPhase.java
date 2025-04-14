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
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> phaseBind = sgGeneral.add(new KeybindSetting.Builder()
            .name("key-bind").description("Phase on keybind press").build());

    private final Setting<RotateMode> rotateMode =
            sgGeneral.add(new EnumSetting.Builder<RotateMode>().name("rotate-mode")
                    .description("Which method of rotating should be used.")
                    .defaultValue(RotateMode.DelayedInstantWebOnly).build());

    private final Setting<Boolean> burrow =
            sgGeneral.add(new BoolSetting.Builder().name("borrow")
                    .description("Places a block where you phase.").defaultValue(true).build());

    private final Setting<Boolean> antiPearlFail = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-pearl-fail").description("Hits entites below you when you phase.")
            .defaultValue(true).build());

    private final Setting<Boolean> antiPearlFailStrict =
            sgGeneral.add(new BoolSetting.Builder().name("anti-pearl-fail-strict")
                    .description("Waits for the entity to disapear before phasing.")
                    .defaultValue(false).build());

    private boolean active = false;
    private boolean keyUnpressed = false;
    private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();

    public PearlPhase() {
        super(Categories.Combat, "pearl-phase", "Phases into walls using pearls");
    }

    private void activate() {
        active = true;

        if (mc.player == null || mc.world == null)
            return;

        update();
    }

    private void deactivate(boolean phased) {
        active = false;

        if (phased) {
            info("Phased");
        }
    }

    private void update() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!active) {
            return;
        }

        Box boundingBox = mc.player.getBoundingBox().shrink(0.05, 0.1, 0.05);
        double feetY = mc.player.getY();

        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        // Can't phase if we're already phased
        if (BlockPos.stream(feetBox).anyMatch(blockPos -> {
            return mc.world.getBlockState(blockPos).isSolidBlock(mc.world, blockPos);
        })) {
            deactivate(false);
        }

        if (!MeteorClient.SWAP.canSwap(Items.ENDER_PEARL)) {
            deactivate(false);
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL)) {
            deactivate(false);
            return;
        }

        // Can't phase while sneaking/crawling
        if (mc.options.sneakKey.isPressed() || mc.player.isCrawling()) {
            deactivate(false);
            return;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!active) {
            return;
        }

        Vec3d targetPos = calculateTargetPos();
        float[] angle = MeteorClient.ROTATION.getRotation(targetPos);

        // Rotation Modes:
        // Movement: Requests a rotation from the RotationManager and waits for it to be fulfilled
        // Instant: Instantly sends a movement packet with the rotation
        // DelayedInstant: Requests a rotation from the RotationManager and waits for it to be
        // fulfilled, then sends a movement packet with the rotation
        // DelayedInstantWebOnly: Same as DelayedInstant, but only sends a movement packet when in
        // webs

        // Movement fails in webs on Grim,
        // instant is a bit iffy since it doesn't work when you rubberband

        // DelayedInstantWebOnly should work best for grim?
        switch (rotateMode.get()) {
            case Movement -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    throwPearl(angle[0], angle[1]);
                }
            }
            case Instant -> {
                if (mc.player.isOnGround()) {
                    MeteorClient.ROTATION.snapAt(targetPos);

                    throwPearl(angle[0], angle[1]);
                }
            }
            case DelayedInstant -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    MeteorClient.ROTATION.snapAt(targetPos);

                    throwPearl(angle[0], angle[1]);
                }
            }
            case DelayedInstantWebOnly -> {
                MeteorClient.ROTATION.requestRotation(targetPos, 1000f);

                if (MeteorClient.ROTATION.lookingAt(Box.of(targetPos, 0.05, 0.05, 0.05))) {
                    if (MovementFix.inWebs) {
                        MeteorClient.ROTATION.snapAt(targetPos);
                    }

                    throwPearl(angle[0], angle[1]);
                }
            }
        }
    }

    private void throwPearl(float yaw, float pitch) {
        if (antiPearlFail.get()) {
            HitResult hitResult = getEnderPearlHitResult();

            if (hitResult != null) {
                if (hitResult.getType() == HitResult.Type.ENTITY) {
                    Entity hitEntity = ((EntityHitResult) hitResult).getEntity();

                    if (hitEntity instanceof EndCrystalEntity
                            || hitEntity instanceof ItemFrameEntity) {
                        // Forcibly request breaking the entity if it's thjere

                        MeteorClient.ROTATION.requestRotation(hitEntity.getPos(), 11);

                        if (!MeteorClient.ROTATION.lookingAt(hitEntity.getBoundingBox())
                                && RotationManager.lastGround) {
                            MeteorClient.ROTATION.snapAt(hitEntity.getPos());
                        }

                        if (MeteorClient.ROTATION.lookingAt(hitEntity.getBoundingBox())) {
                            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket
                                    .attack(hitEntity, mc.player.isSneaking()));
                        }
                    }

                    if (antiPearlFailStrict.get() && hitEntity != null) {
                        return;
                    }
                }
            }


            // If we're in a scaffold, break it cause it causes unhappiness
            if (mc.world.getBlockState(mc.player.getBlockPos()).isOf(Blocks.SCAFFOLDING)) {
                // Since we insta-break scaffolding, we can just send the
                // START_DESTROY_BLOCK packet
                // See Paper-Server ServerPlayerGameMode.java:278
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, mc.player.getBlockPos(), Direction.UP,
                        mc.world.getPendingUpdateManager().incrementSequence().getSequence()));

                if (antiPearlFailStrict.get()) {
                    return;
                }
            }
        }

        if (burrow.get() && !mc.player.isUsingItem()) {
            Vec3d targetPos = calculateTargetPos();
            Box newHitbox = mc.player.getBoundingBox()
                    .offset(targetPos.x - mc.player.getX(), 0, targetPos.z - mc.player.getZ())
                    .expand(0.05);

            List<BlockPos> placePoses = new ArrayList<>();

            // Calculate the corners of the bounding box at the feet level
            int minX = (int) Math.floor(newHitbox.minX);
            int maxX = (int) Math.floor(newHitbox.maxX);
            int minZ = (int) Math.floor(newHitbox.minZ);
            int maxZ = (int) Math.floor(newHitbox.maxZ);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos feetPos = new BlockPos(x, mc.player.getBlockPos().getY(), z);

                    placePoses.add(feetPos);
                }
            }

            if (MeteorClient.BLOCK.beginPlacement(placePoses, Items.OBSIDIAN)) {
                placePoses.forEach(blockPos -> {
                    MeteorClient.BLOCK.placeBlock(Items.OBSIDIAN, blockPos);
                });

                MeteorClient.BLOCK.endPlacement();
            }
        }

        if (MeteorClient.SWAP.beginSwap(Items.ENDER_PEARL, true)) {
            int sequence = mc.world.getPendingUpdateManager().incrementSequence().getSequence();

            mc.getNetworkHandler().sendPacket(
                    new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));

            deactivate(true);

            MeteorClient.SWAP.endSwap(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        // Custom keypress implementation because.... I don't like binding modules like this? idk
        if (!phaseBind.get().isPressed()) {
            keyUnpressed = true;
        }

        if (phaseBind.get().isPressed() && keyUnpressed
                && !(mc.currentScreen instanceof ChatScreen)) {
            activate();
            keyUnpressed = false;
        }

        update();
    }

    private HitResult getEnderPearlHitResult() {
        if (!simulator.set(mc.player, Items.ENDER_PEARL.getDefaultStack(), 0, false, 1f)) {
            return null;
        }

        for (int i = 0; i < 256; i++) {
            HitResult result = simulator.tick();

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private Vec3d calculateTargetPos() {
        final double X_OFFSET = Math.PI / 13;
        final double Z_OFFSET = Math.PI / 4;

        // cache pos
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();

        // Calculate position based on the x and z offets
        double x = playerX + MathHelper.clamp(
                toClosest(playerX, Math.floor(playerX) + X_OFFSET, Math.floor(playerX) + Z_OFFSET)
                        - playerX,
                -0.2, 0.2);

        double z = playerZ + MathHelper.clamp(
                toClosest(playerZ, Math.floor(playerZ) + X_OFFSET, Math.floor(playerZ) + Z_OFFSET)
                        - playerZ,
                -0.2, 0.2);

        return new Vec3d(x, mc.player.getY() - 0.5, z);
    }

    private double toClosest(double num, double min, double max) {
        double dmin = num - min;
        double dmax = max - num;

        if (dmax > dmin) {
            return min;
        } else {
            return max;
        }
    }

    public enum SwitchMode {
        SilentHotbar, SilentSwap
    }

    public enum RotateMode {
        Movement, Instant, DelayedInstant, DelayedInstantWebOnly
    }
}
