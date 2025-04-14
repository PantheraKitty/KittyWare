package meteordevelopment.meteorclient.systems.modules.combat;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableDouble;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.managers.SwapManager.SwapMode;
import meteordevelopment.meteorclient.systems.managers.TargetManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class SwordAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> silentSwapOverrideDelay = sgGeneral.add(new BoolSetting.Builder()
            .name("silent-swap-override-delay")
            .description(
                    "Whether or not to use the held items delay when attacking with silent swap")
            .defaultValue(true).visible(() -> MeteorClient.SWAP.getItemSwapMode() != SwapMode.None)
            .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder().name("rotate")
            .description("Whether or not to rotate to the entity to attack it.").defaultValue(true)
            .build());

    private final Setting<Boolean> snapRotation = sgGeneral.add(new BoolSetting.Builder().name("snap-rotate")
            .description("Instantly rotates to the targeted entity.").defaultValue(true).visible(() -> rotate.get())
            .build());

    private final Setting<Boolean> forcePauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("force-pause-on-eat").description("Does not attack while using an item.")
            .defaultValue(false).build());

    private final Setting<Boolean> pauseInAir = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-in-air").description("Does not attack while jumping or falling")
            .defaultValue(false).build());

    private final Setting<Boolean> pauseInventoryOepn = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-on-inventory")
            .description(
                    "Does not attack when the inventory is open. Disabling this may cause unhappiness.")
            .defaultValue(true).build());

    private final Setting<Boolean> wallCrits = sgGeneral.add(new BoolSetting.Builder()
            .name("wall-crits")
            .description("Grimv3 crits, but only in walls")
            .defaultValue(true).build());

    private final Setting<Boolean> wallCritsPauseOnMove = sgGeneral.add(new BoolSetting.Builder()
            .name("wall-crits-pause-on-move")
            .description("Grimv3 crits, but only in walls, but only when you're not moving")
            .defaultValue(true).visible(() -> wallCrits.get()).build());

    private final Setting<Boolean> wallCritsOnlyOnSword = sgGeneral.add(new BoolSetting.Builder()
            .name("wall-crits-only-on-sword")
            .description("Grimv3 crits, but only in walls, but when you're holding a sword")
            .defaultValue(true).visible(() -> wallCrits.get()).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Whether or not to render attacks").defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).visible(() -> render.get()).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the rendering.")
            .defaultValue(new SettingColor(160, 0, 225, 35)).visible(() -> shapeMode.get().sides())
            .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> render.get() && shapeMode.get().lines()).build());

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("fade-time").description("How long to fade the bounding box render.").min(0)
            .sliderMax(2.0).defaultValue(0.8).build());

    private final TargetManager targetManager = new TargetManager(this, true);

    private long lastAttackTime = 0;
    private List<Entity> targets = null;
    private Entity lastAttackedEntity = null;
    private int targetIndex = 0;

    public SwordAura() {
        super(Categories.Combat, "sword-aura", "Automatically attacks entities with your sword");
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player.isDead() || mc.player.isSpectator()) {
            return;
        }

        if (forcePauseEat.get() && mc.player.isUsingItem()
                && mc.player.getActiveHand() == Hand.MAIN_HAND) {
            return;
        }

        if (pauseInAir.get() && !mc.player.isOnGround()) {
            return;
        }

        // Priorizie finding a netherite sword
        FindItemResult result = MeteorClient.SWAP.getSlot(Items.NETHERITE_SWORD);
        if (!result.found()) {
            result = MeteorClient.SWAP.getSlot(Items.DIAMOND_SWORD);
        }

        if (!result.found()) {
            return;
        }

        targets = targetManager.getEntityTargets();

        if (targets.isEmpty()) {
            return;
        }

        Entity target = targets.get(targetIndex % targets.size());

        int delayCheckSlot = result.slot();

        if (silentSwapOverrideDelay.get()) {
            delayCheckSlot = mc.player.getInventory().selectedSlot;
        }

        if (delayCheck(delayCheckSlot)) {
            if (pauseInventoryOepn.get() && (mc.currentScreen instanceof AbstractInventoryScreen
                    || mc.currentScreen instanceof GenericContainerScreen)) {
                return;
            }

            if (rotate.get()) {
                Vec3d point = getClosestPointOnBox(target.getBoundingBox(), mc.player.getEyePos());

                if (snapRotation.get()) {
                    MeteorClient.ROTATION.snapAt(point);
                }

                MeteorClient.ROTATION.requestRotation(point, 9);

                if (!MeteorClient.ROTATION.lookingAt(target.getBoundingBox())) {
                    return;
                }
            }

            boolean isHolding = result.isMainHand();

            if (MeteorClient.SWAP.beginSwap(result, true)) {
                attack(target, !isHolding);

                MeteorClient.SWAP.endSwap(true);
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || lastAttackedEntity == null) {
            return;
        }

        double secondsSinceAttack = (System.currentTimeMillis() - lastAttackTime) / 1000.0;

        if (secondsSinceAttack > fadeTime.get()) {
            return;
        }

        double alpha = 1 - (secondsSinceAttack / fadeTime.get());

        // Bounding box interpolation
        double x = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderX,
                lastAttackedEntity.getX()) - lastAttackedEntity.getX();
        double y = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderY,
                lastAttackedEntity.getY()) - lastAttackedEntity.getY();
        double z = MathHelper.lerp(event.tickDelta, lastAttackedEntity.lastRenderZ,
                lastAttackedEntity.getZ()) - lastAttackedEntity.getZ();

        Box box = lastAttackedEntity.getBoundingBox();

        event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY,
                z + box.maxZ, sideColor.get().copy().a((int) (sideColor.get().a * alpha)),
                lineColor.get().copy().a((int) (lineColor.get().a * alpha)), shapeMode.get(), 0);
    }

    public void attack(Entity target, boolean didSwap) {
        if (wallCrits.get()) {
            sendCrits(didSwap);
        }

        mc.getNetworkHandler()
                .sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackedEntity = target;
        lastAttackTime = System.currentTimeMillis();
        targetIndex++;
    }

    private boolean delayCheck(int slot) {
        PlayerInventory inventory = mc.player.getInventory();
        ItemStack itemStack = inventory.getStack(slot);

        MutableDouble attackSpeed = new MutableDouble(
                mc.player.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_SPEED));

        AttributeModifiersComponent attributeModifiers = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributeModifiers != null) {
            attributeModifiers.applyModifiers(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                if (entry == EntityAttributes.GENERIC_ATTACK_SPEED) {
                    attackSpeed.add(modifier.value());
                }
            });
        }

        double attackCooldownTicks = 1.0 / attackSpeed.getValue() * 20.0;

        long currentTime = System.currentTimeMillis();

        // 50 ms in a tick
        if ((currentTime - lastAttackTime) / 50.0 > attackCooldownTicks) {
            return true;
        }

        return false;
    }

    private void sendCrits(boolean didSwap) {
        boolean isMoving = mc.player.input.movementForward > 1e-5f || mc.player.input.movementSideways > 1e-5;

        if (!PlayerUtils.isPlayerPhased()) {
            return;
        }

        if (!RotationManager.lastGround) {
            return;
        }

        // If we're moving, don't send
        if (wallCritsPauseOnMove.get() && isMoving) {
            return;
        }

        // If we swapped, we're not holding our sword, don't crit
        if (wallCritsOnlyOnSword.get() && didSwap) {
            return;
        }

        Vec3d pos = new Vec3d(MeteorClient.ROTATION.lastX, MeteorClient.ROTATION.lastY, MeteorClient.ROTATION.lastZ);

        // Normal
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y, pos.z,
                MeteorClient.ROTATION.lastYaw, MeteorClient.ROTATION.lastPitch, true));

        // Up
        // Literally from Meteor criticals
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y + 0.0625, pos.z,
                MeteorClient.ROTATION.lastYaw, MeteorClient.ROTATION.lastPitch, false));

        // Down a little
        // Literally form Meteor criticals
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(pos.x, pos.y + 0.0450, pos.z,
                MeteorClient.ROTATION.lastYaw, MeteorClient.ROTATION.lastPitch, false));
    }

    public Vec3d getClosestPointOnBox(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }

    public enum SwitchMode {
        None, SilentHotbar, SilentSwap, Auto
    }
}
