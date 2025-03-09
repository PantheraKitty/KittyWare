package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public final class Filler extends Module
{
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<FillerMode> mode = sgGeneral.add(
        new EnumSetting.Builder<FillerMode>()
            .name("mode")
            .description("What mode to use.")
            .defaultValue(FillerMode.Below)
            .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(
        new BlockListSetting.Builder()
            .name("blocks")
            .description("Which blocks to use.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(() -> mode.get() != FillerMode.Litematica)
            .build()
    );

    private final Setting<HorizontalDirection> horizontalDirection = sgGeneral.add(
        new EnumSetting.Builder<HorizontalDirection>()
            .name("horizontal-direction")
            .description("What direction to fill in horizontally.")
            .defaultValue(HorizontalDirection.East)
            .visible(() -> mode.get() == FillerMode.Horizontal || mode.get() == FillerMode.HorizontalSwim)
            .build()
    );

    private final Setting<PlaneDirection> planeDirection = sgGeneral.add(
        new EnumSetting.Builder<PlaneDirection>()
            .name("plane-direction")
            .description("What axis to put the plane on.")
            .defaultValue(PlaneDirection.X)
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Integer> planeValue = sgGeneral.add(
        new IntSetting.Builder()
            .name("plane-value")
            .description("The value for the axis on the plane.")
            .defaultValue(-39)
            .noSlider()
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Integer> planeThickness = sgGeneral.add(
        new IntSetting.Builder()
            .name("plane-thickness")
            .description("How thick to build the plane.")
            .min(1)
            .sliderMax(4)
            .defaultValue(1)
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Double> fadeTime = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("fade-time")
            .description("How many seconds it takes to fade.")
            .defaultValue(0.2D)
            .min(0.0D)
            .sliderMax(1.0D)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(85, 0, 255, 40))
            .visible(() -> shapeMode.get() != ShapeMode.Lines)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 255, 255, 60))
            .visible(() -> shapeMode.get() != ShapeMode.Sides)
            .build()
    );

    private final Mutable mutablePos = new Mutable();
    private final Map<BlockPos, Long> renderLastPlacedBlock = new HashMap<>();

    public Filler()
    {
        super(Categories.World, "filler", "NSO Filler");
    }

    @EventHandler
    private void onTick(final TickEvent.Post event)
    {
        final long currentTime = System.currentTimeMillis();
        if (!mc.player.isUsingItem())
        {
            if (mode.get() != FillerMode.Litematica)
            {
                final List<BlockPos> placePoses = getBlockPoses();
                boolean canMove = true;

                for (final Map.Entry<BlockPos, Long> entry : renderLastPlacedBlock.entrySet())
                {
                    if (!((double) (currentTime - entry.getValue()) > fadeTime.get() * 1000.0))
                    {
                        canMove = false;
                    }
                }

                if (!canMove)
                {
                    mc.player.input.movementForward = 0.0f;
                    mc.player.input.movementSideways = 0.0f;
                }

                placePoses.sort((x, y) -> Double.compare(x.getSquaredDistance(mc.player.getPos()), y.getSquaredDistance(mc.player.getPos())));
                final Item useItem = findUseItem();
                if (!MeteorClient.BLOCK.beginPlacement(placePoses, useItem))
                {
                    return;
                }

                placePoses.forEach(blockPos ->
                {
                    if (MeteorClient.BLOCK.placeBlock(blockPos))
                    {
                        renderLastPlacedBlock.put(blockPos, currentTime);
                    }
                });
                MeteorClient.BLOCK.endPlacement();
            }
        }
    }

    @EventHandler
    private void onRender3D(final Render3DEvent event)
    {
        final long currentTime = System.currentTimeMillis();
        final Iterator<Map.Entry<BlockPos, Long>> iterator = renderLastPlacedBlock.entrySet().iterator();

        while (iterator.hasNext())
        {
            final Map.Entry<BlockPos, Long> entry = iterator.next();
            if (!((double) (currentTime - entry.getValue()) > fadeTime.get() * 1000.0))
            {
                final double time = (double) (currentTime - entry.getValue()) / 1000.0;
                final double timeCompletion = time / fadeTime.get();
                final Color fadedSideColor = (sideColor.get()).copy()
                    .a((int) ((sideColor.get()).a * (1.0 - timeCompletion)));
                final Color fadedLineColor = (lineColor.get()).copy()
                    .a((int) ((lineColor.get()).a * (1.0 - timeCompletion)));

                event.renderer.box(entry.getKey(), fadedSideColor, fadedLineColor,
                    shapeMode.get(), 0);
            }
        }
    }

    private List<BlockPos> getBlockPoses()
    {
        final List<BlockPos> placePoses = new ArrayList<>();
        final int r = 5;
        final BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        final int ex = eyePos.getX();
        final int ey = eyePos.getY();
        final int ez = eyePos.getZ();

        for (int x = -r; x <= r; ++x)
        {
            for (int y = -r; y <= r; ++y)
            {
                for (int z = -r; z <= r; ++z)
                {
                    final BlockPos pos = mutablePos.set(ex + x, ey + y, ez + z);
                    switch ((mode.get()).ordinal())
                    {
                        case 0:
                            if (pos.getY() >= mc.player.getBlockY())
                            {
                                continue;
                            }
                            break;
                        case 1:
                            if (!directionCheck(pos))
                            {
                                continue;
                            }
                            break;
                        case 2:
                            if (pos.getY() == mc.player.getBlockY() && !directionCheck(pos))
                            {
                                continue;
                            }
                            break;
                        case 3:
                            if (!planeCheck(pos))
                            {
                                continue;
                            }
                    }

                    if (MeteorClient.BLOCK.checkPlacement(pos) && inPlaceRange(pos))
                    {
                        placePoses.add(new BlockPos(pos));
                    }
                }
            }
        }
        return placePoses;
    }


    private boolean directionCheck(final BlockPos blockPos)
    {
        switch ((horizontalDirection.get()).ordinal())
        {
            case 0:
                if (blockPos.getZ() <= mc.player.getZ())
                {
                    return false;
                }
                break;
            case 1:
                if (blockPos.getZ() >= mc.player.getZ())
                {
                    return false;
                }
                break;
            case 2:
                if (blockPos.getX() >= mc.player.getX())
                {
                    return false;
                }
                break;
            case 3:
                if (blockPos.getX() <= mc.player.getX())
                {
                    return false;
                }
        }
        return true;
    }

    private boolean planeCheck(final BlockPos blockPos)
    {
        int blockValue = 0;
        switch ((planeDirection.get()).ordinal())
        {
            case 0:
                blockValue = blockPos.getX();
                break;
            case 2:
                blockValue = blockPos.getY();
                break;
            case 3:
                blockValue = blockPos.getZ();
        }
        return Math.abs(planeValue.get() - blockValue) <= planeThickness.get() - 1;
    }


    private Item findUseItem()
    {
        final FindItemResult result = InvUtils.find(itemStack ->
        {
            for (final Block blocks : blocks.get())
            {
                if (blocks.asItem() == itemStack.getItem())
                {
                    return true;
                }
            }
            return false;
        });
        return !result.found() ? null : mc.player.getInventory().getStack(result.slot()).getItem();
    }

    private boolean inPlaceRange(final BlockPos blockPos)
    {
        final Vec3d from = mc.player.getPos();
        return blockPos.toCenterPos().distanceTo(from) <= 5.1D;
    }

    private enum FillerMode
    {
        Below,
        Horizontal,
        HorizontalSwim,
        Plane,
        Litematica;
    }

    private enum HorizontalDirection
    {
        North,
        South,
        East,
        West;
    }

    private enum PlaneDirection
    {
        X,
        Y,
        Z;
    }
}




