/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.meteor.SilentMineFinishedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.managers.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

public class SilentMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral
            .add(new DoubleSetting.Builder().name("range").description("Range to activate use at")
                    .defaultValue(5.14).min(0.0).sliderMax(7.0).build());

    public final Setting<Boolean> antiRubberband = sgGeneral.add(new BoolSetting.Builder()
            .name("strict-anti-rubberband")
            .description(
                    "Attempts to prevent you from rubberbanding extra hard. May result in kicks.")
            .defaultValue(true).build());

    public final Setting<Boolean> preSwitchSinglebreak = sgGeneral.add(new BoolSetting.Builder()
            .name("pre-switch-single-break")
            .description(
                    "Pre-switches to your pickaxe when the singlebreak block is almost done, for more responsive breaking.")
            .defaultValue(true).build());

    private final Setting<Integer> singleBreakFailTicks = sgGeneral.add(new IntSetting.Builder()
            .name("single-break-fail-ticks")
            .description("Number of ticks to wait before retrying a singlebreak in case of fail.")
            .defaultValue(20).min(5).sliderMax(50).build());

    public final Setting<Boolean> rebreakSetBlockBroken = sgGeneral.add(new BoolSetting.Builder()
            .name("set-rebreak-block-broken")
            .description("Breaks the rebreak client side instantly.").defaultValue(true).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("do-render")
            .description("Renders the blocks in queue to be broken.").defaultValue(true).build());

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
            .name("render-block").description("Whether to render the block being broken.")
            .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).visible(renderBlock::get).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the rendering.")
            .defaultValue(new SettingColor(255, 180, 255, 15))
            .visible(() -> renderBlock.get() && shapeMode.get().sides()).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(255, 255, 255, 60))
            .visible(() -> renderBlock.get() && shapeMode.get().lines()).build());

    private final Setting<Boolean> debugRenderPrimary =
            sgRender.add(new BoolSetting.Builder().name("debug-render-primary")
                    .description("Render the primary block differently for debugging.")
                    .defaultValue(false).build());

    private SilentMineBlock rebreakBlock = null;
    private SilentMineBlock delayedDestroyBlock = null;
    private BlockPos lastDelayedDestroyBlockPos = null;

    private double currentGameTickCalculated = 0;

    private boolean needDelayedDestroySwapBack = false;
    private boolean needRebreakSwapBack = false;

    public SilentMine() {
        super(Categories.Player, "silent-mine",
                "Allows you to mine blocks without holding a pickaxe");

        currentGameTickCalculated = RenderUtils.getCurrentGameTickCalculated();
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        currentGameTickCalculated = RenderUtils.getCurrentGameTickCalculated();

        if (hasDelayedDestroy()) {
            lastDelayedDestroyBlockPos = delayedDestroyBlock.blockPos;
        } else {
            lastDelayedDestroyBlockPos = null;
        }

        if (hasDelayedDestroy() && (mc.world.getBlockState(delayedDestroyBlock.blockPos).isAir()
                || !BlockUtils.canBreak(delayedDestroyBlock.blockPos))) {
            MeteorClient.EVENT_BUS
                    .post(new SilentMineFinishedEvent.Post(delayedDestroyBlock.blockPos, false));

            delayedDestroyBlock = null;
        }

        if (rebreakBlock != null && (mc.world.getBlockState(rebreakBlock.blockPos).isAir()
                || !BlockUtils.canBreak(rebreakBlock.blockPos))) {
            rebreakBlock.beenAir = true;
        }

        if (hasRebreakBlock() && rebreakBlock.timesSendBreakPacket > 10
                && !canRebreakRebreakBlock()) {
            rebreakBlock.cancelBreaking();
            rebreakBlock = null;
        }

        // Update our doublemine block
        if (hasDelayedDestroy()
                && delayedDestroyBlock.ticksHeldPickaxe <= singleBreakFailTicks.get()) {
            BlockState blockState = mc.world.getBlockState(delayedDestroyBlock.blockPos);

            if (delayedDestroyBlock.isReady()) {
                FindItemResult result = InvUtils.findFastestTool(blockState);

                if (result.found() && mc.player.getInventory().selectedSlot != result.slot()) {
                    if (MeteorClient.SWAP.beginSwap(result, false)) {
                        needDelayedDestroySwapBack = true;
                    }
                }

                if (!result.found() || mc.player.getInventory().selectedSlot == result.slot()) {
                    delayedDestroyBlock.ticksHeldPickaxe++;
                }
            }
        }

        // Update our primary mine block
        if (rebreakBlock != null) {
            BlockState blockState = mc.world.getBlockState(rebreakBlock.blockPos);

            if (rebreakBlock.isReady()) {
                if (inBreakRange(rebreakBlock.blockPos)) {
                    FindItemResult result = InvUtils.findFastestTool(blockState);

                    MeteorClient.EVENT_BUS
                            .post(new SilentMineFinishedEvent.Pre(rebreakBlock.blockPos, true));

                    if (result.found() && mc.player.getInventory().selectedSlot != result.slot()) {
                        if (MeteorClient.SWAP.beginSwap(result, true)) {
                            needRebreakSwapBack = true;
                        }
                    }

                    rebreakBlock.tryBreak();

                    if (needRebreakSwapBack) {
                        MeteorClient.SWAP.endSwap(true);
                    }

                    if (rebreakSetBlockBroken.get() && canRebreakRebreakBlock()) {
                        mc.world.setBlockState(rebreakBlock.blockPos, Blocks.AIR.getDefaultState());
                    }
                } else {
                    rebreakBlock = null;
                }
            }
        }

        if (hasDelayedDestroy()
                && delayedDestroyBlock.ticksHeldPickaxe > singleBreakFailTicks.get()) {
            if (inBreakRange(delayedDestroyBlock.blockPos)) {
                delayedDestroyBlock.startBreaking(true);
            } else {
                delayedDestroyBlock.cancelBreaking();
                delayedDestroyBlock = null;
            }
        }

        boolean delayedDestroyFinished = !(hasDelayedDestroy() && delayedDestroyBlock.isReady());

        if (needDelayedDestroySwapBack && delayedDestroyFinished) {
            MeteorClient.SWAP.endSwap(false);
            needDelayedDestroySwapBack = false;
        }
    }

    public void silentBreakBlock(BlockPos pos, double priority) {
        silentBreakBlock(pos, Direction.UP, priority);
    }

    public void silentBreakBlock(BlockPos blockPos, Direction direction, double priority) {
        if (!isActive()) {
            return;
        }

        if (blockPos == null || alreadyBreaking(blockPos)) {
            return;
        }

        // Can't break it
        if (!BlockUtils.canBreak(blockPos, mc.world.getBlockState(blockPos))) {
            return;
        }

        // Reach check
        if (!inBreakRange(blockPos)) {
            return;
        }

        if (!hasDelayedDestroy()) {
            boolean willResetPrimary = rebreakBlock != null && !canRebreakRebreakBlock();

            if (willResetPrimary && rebreakBlock.priority < priority) {
                return;
            }

            // Little leeway
            currentGameTickCalculated -= 0.1;
            delayedDestroyBlock = new SilentMineBlock(blockPos, direction, priority, false);

            delayedDestroyBlock.startBreaking(true);

            if (willResetPrimary) {
                rebreakBlock.startBreaking(false);
            }
        }

        if (alreadyBreaking(blockPos)) {
            return;
        }

        if (rebreakBlock != null && delayedDestroyBlock != null
                && (priority >= rebreakBlock.priority || canRebreakRebreakBlock())) {
            // Don't reset rebreak block when were pretty close to finished
            if (delayedDestroyBlock.getBreakProgress() <= 0.8) {
                rebreakBlock = null;
            }
        }

        if (rebreakBlock == null) {
            rebreakBlock = new SilentMineBlock(blockPos, direction, priority, true);

            rebreakBlock.startBreaking(false);
        }
    }

    @EventHandler
    public void onStartBreakingBlock(StartBreakingBlockEvent event) {
        event.cancel();

        silentBreakBlock(event.blockPos, event.direction, 100f);
    }

    public boolean canSwapBack() {
        boolean result = needDelayedDestroySwapBack;

        if (hasDelayedDestroy() && delayedDestroyBlock.isReady()) {
            result = false;
        }

        return result;
    }

    public boolean hasDelayedDestroy() {
        return delayedDestroyBlock != null;
    }

    public boolean hasRebreakBlock() {
        return rebreakBlock != null && !rebreakBlock.beenAir;
    }

    public BlockPos getDelayedDestroyBlockPos() {
        if (delayedDestroyBlock == null) {
            return null;
        }

        return delayedDestroyBlock.blockPos;
    }

    // Returns the last ticks delayed destroy block position if there was one, otherwise returns
    // null
    // Useful for something like knowing if we should place a surround block there to dig down
    public BlockPos getLastDelayedDestroyBlockPos() {
        return lastDelayedDestroyBlockPos;
    }

    public double getDelayedDestroyProgress() {
        if (delayedDestroyBlock == null) {
            return 0;
        }

        return delayedDestroyBlock.getBreakProgress();
    }

    public BlockPos getRebreakBlockPos() {
        if (rebreakBlock == null) {
            return null;
        }

        return rebreakBlock.blockPos;
    }

    public double getRebreakBlockProgress() {
        if (rebreakBlock == null) {
            return 0;
        }

        return rebreakBlock.getBreakProgress();
    }

    public boolean canRebreakRebreakBlock() {
        if (rebreakBlock == null) {
            return false;
        }

        return rebreakBlock.beenAir;
    }

    public boolean inBreakRange(BlockPos blockPos) {
        if ((new Box(blockPos)).squaredMagnitude(mc.player.getEyePos()) > range.get()
                * range.get()) {
            return false;
        }

        return true;
    }

    public boolean alreadyBreaking(BlockPos blockPos) {
        if ((rebreakBlock != null && blockPos.equals(rebreakBlock.blockPos))
                || (delayedDestroyBlock != null && blockPos.equals(delayedDestroyBlock.blockPos))) {
            return true;
        }

        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get()) {
            double calculatedDrawGameTick = RenderUtils.getCurrentGameTickCalculated();

            if (rebreakBlock != null) {
                rebreakBlock.render(event, calculatedDrawGameTick, true);
            }

            if (delayedDestroyBlock != null) {
                delayedDestroyBlock.render(event, calculatedDrawGameTick, false);
            }
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerActionC2SPacket packet
                && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                && antiRubberband.get() && (packet.getPos().equals(getRebreakBlockPos())
                        || packet.getPos().equals(getDelayedDestroyBlockPos()))) {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, packet.getPos(),
                            packet.getDirection()));
        }
    }

    private int getSeq() {
        return mc.world.getPendingUpdateManager().incrementSequence().getSequence();
    }

    class SilentMineBlock {
        public BlockPos blockPos;

        public Direction direction;

        public boolean started = false;

        public int timesSendBreakPacket = 0;

        public int ticksHeldPickaxe = 0;

        public boolean beenAir = false;

        private double destroyProgressStart = 0;

        private double priority = 0;

        private boolean isRebreak;

        public SilentMineBlock(BlockPos blockPos, Direction direction, double priority,
                boolean isRebreak) {
            this.blockPos = blockPos;

            this.direction = direction;

            this.priority = priority;

            this.isRebreak = isRebreak;
        }

        public boolean isReady() {
            if (!BlockUtils.canBreak(blockPos)) {
                return false;
            }

            double breakProgressSingleTick = getBreakProgressSingleTick();
            double threshold = isRebreak ? 0.7
                    : 1.0 - (preSwitchSinglebreak.get() ? (breakProgressSingleTick / 2.0) : 0.0);

            return getBreakProgress() >= threshold || timesSendBreakPacket > 0;
        }

        public void startBreaking(boolean isDelayedDestroy) {
            ticksHeldPickaxe = 0;
            timesSendBreakPacket = 0;
            this.destroyProgressStart = currentGameTickCalculated;

            if (isDelayedDestroy && canRebreakRebreakBlock()) {
                rebreakBlock = null;
            }

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));


            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            if (!antiRubberband.get()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));

                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
            }

            started = true;
        }

        public void tryBreak() {
            mc.getNetworkHandler()
                    .sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, blockPos, direction,
                            getSeq()));

            if (!antiRubberband.get()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
            }

            timesSendBreakPacket++;
        }

        public void cancelBreaking() {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, blockPos, direction));
        }

        public double getBreakProgress() {
            return getBreakProgress(currentGameTickCalculated);
        }

        public double getBreakProgress(double gameTick) {
            BlockState state = mc.world.getBlockState(blockPos);

            FindItemResult slot = InvUtils.findFastestToolHotbar(mc.world.getBlockState(blockPos));


            Box boundingBox = mc.player.getBoundingBox();

            double playerFeetY = boundingBox.minY;

            Box groundBox = new Box(boundingBox.minX, playerFeetY - 0.2, boundingBox.minZ,
                    boundingBox.maxX, playerFeetY, boundingBox.maxZ);

            boolean willBeOnGround = false;

            for (BlockPos pos : BlockUtils.iterate(groundBox)) {
                BlockState blockState = mc.world.getBlockState(pos);

                // Skip air or non-solid blocks
                if (!blockState.isSolidBlock(mc.world, pos)) {
                    continue;
                }

                double blockTopY = pos.getY() + 1.0;
                double distanceToBlock = playerFeetY - blockTopY;

                if (distanceToBlock >= 0 && distanceToBlock < Math.abs(mc.player.getVelocity().y * 2)) {
                    willBeOnGround = true;
                }
            }

            double breakingSpeed = BlockUtils.getBlockBreakingSpeed(
                    slot.found() ? slot.slot() : mc.player.getInventory().selectedSlot, state,
                    RotationManager.lastGround || (willBeOnGround && !isRebreak));

            return Math.min(BlockUtils.getBreakDelta(breakingSpeed, state)
                    * (double) (gameTick - destroyProgressStart), 1.0);
        }

        public double getBreakProgressSingleTick() {
            return getBreakProgress(destroyProgressStart + 1);
        }

        public double getPriority() {
            return priority;
        }

        public void render(Render3DEvent event, double renderTick, boolean isPrimary) {
            VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);
            if (shape == null || shape.isEmpty()) {
                event.renderer.box(blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                return;
            }

            Box orig = shape.getBoundingBox();

            // The primary block can be broken at 0.7 completion, so speed up the visual by the
            // reciprical
            double shrinkFactor =
                    1d - Math.clamp(isPrimary ? getBreakProgress(renderTick) * (1 / 0.7)
                            : getBreakProgress(renderTick), 0, 1);
            BlockPos pos = blockPos;


            Box box = orig.shrink(orig.getLengthX() * shrinkFactor,
                    orig.getLengthY() * shrinkFactor, orig.getLengthZ() * shrinkFactor);

            double xShrink = (orig.getLengthX() * shrinkFactor) / 2;
            double yShrink = (orig.getLengthY() * shrinkFactor) / 2;
            double zShrink = (orig.getLengthZ() * shrinkFactor) / 2;

            double x1 = pos.getX() + box.minX + xShrink;
            double y1 = pos.getY() + box.minY + yShrink;
            double z1 = pos.getZ() + box.minZ + zShrink;
            double x2 = pos.getX() + box.maxX + xShrink;
            double y2 = pos.getY() + box.maxY + yShrink;
            double z2 = pos.getZ() + box.maxZ + zShrink;

            Color color = sideColor.get();

            if (debugRenderPrimary.get() && isPrimary) {
                color = Color.ORANGE.a(40);
            }

            event.renderer.box(x1, y1, z1, x2, y2, z2, color, lineColor.get(), shapeMode.get(), 0);
        }
    }
}
