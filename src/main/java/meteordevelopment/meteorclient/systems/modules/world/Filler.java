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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Filler extends Module
{
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<FillerMode> mode = sgGeneral.add(
        new EnumSetting.Builder<FillerMode>()
            .name("mode")
            .description("What mode to use.")
            .defaultValue(FillerMode.Below)
            .build()
    );

    private final Setting<Integer> range = sgGeneral.add(
        new IntSetting.Builder()
            .name("block-range")
            .description("How far to place blocks")
            .defaultValue(5)
            .min(2)
            .sliderMax(6)
            .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(
        new BlockListSetting
            .Builder()
            .name("blocks")
            .description("Which blocks to use.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(() -> mode.get() != FillerMode.Litematica)
            .build()
    );

    private final Setting<HorizontalDirection> horizontalDirection = sgGeneral.add(
        new EnumSetting
            .Builder<HorizontalDirection>().name("horizontal-direction")
            .description("What direction to fill in horizontally.")
            .defaultValue(HorizontalDirection.East)
            .visible(() -> mode.get() == FillerMode.Horizontal || mode.get() == FillerMode.HorizontalSwim)
            .build()
    );

    private final Setting<PlaneDirection> planeDirection = sgGeneral.add(
        new EnumSetting
            .Builder<PlaneDirection>()
            .name("plane-direction")
            .description("What axis to put the plane on.")
            .defaultValue(PlaneDirection.X)
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Integer> planeValue = sgGeneral.add(
        new IntSetting
            .Builder()
            .name("plane-value")
            .description("The value for the axis on the plane. Think Direction = X, value = -39 to mean place on X = -39.")
            .defaultValue(-39).noSlider()
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Integer> planeThickness = sgGeneral.add(
        new IntSetting.Builder()
            .name("plane-thickness")
            .description("How thick to build the plane. Useful for building walls.")
            .min(1)
            .sliderMax(4)
            .defaultValue(1)
            .visible(() -> mode.get() == FillerMode.Plane)
            .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(
        new DoubleSetting.Builder()
            .name("fade-time")
            .description("How many seconds it takes to fade.")
            .defaultValue(0.2)
            .min(0)
            .sliderMax(1.0)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting
            .Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting
            .Builder()
            .name("side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(85, 0, 255, 40))
            .visible(() -> shapeMode.get() != ShapeMode.Lines)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting
            .Builder()
            .name("line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 255, 255, 60))
            .visible(() -> shapeMode.get() != ShapeMode.Sides)
            .build()
    );

    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

    private final Map<BlockPos, Long> renderLastPlacedBlock = new HashMap<>();

    public Filler()
    {
        super(Categories.World, "filler", "NSO Filler");
    }

    @EventHandler
    private void onTick(TickEvent.Post event)
    {
        long currentTime = System.currentTimeMillis();

        if (mc.player.isUsingItem())
        {
            return;
        }

        if (mode.get() != FillerMode.Litematica)
        {
            List<BlockPos> placePoses = getBlockPoses();

            boolean canMove = true;

            for (Map.Entry<BlockPos, Long> entry : renderLastPlacedBlock.entrySet())
            {
                if (currentTime - entry.getValue() > fadeTime.get() * 1000)
                {
                    continue;
                }

                canMove = false;
            }

            if (!canMove)
            {
                mc.player.input.movementForward = 0;
                mc.player.input.movementSideways = 0;
            }

            placePoses.sort((x, y) ->
            {
                return Double.compare(x.getSquaredDistance(mc.player.getPos()),
                    y.getSquaredDistance(mc.player.getPos()));
            });

            Item useItem = findUseItem();

            if (!MeteorClient.BLOCK.beginPlacement(placePoses, useItem))
            {
                return;
            }

            placePoses.forEach(blockPos ->
            {
                if (MeteorClient.BLOCK.placeBlock(useItem, blockPos))
                {
                    renderLastPlacedBlock.put(blockPos, currentTime);
                }
            });

            MeteorClient.BLOCK.endPlacement();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event)
    {
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<BlockPos, Long> entry : renderLastPlacedBlock.entrySet())
        {
            long placedTime = entry.getValue();

            if (currentTime - placedTime > fadeTime.get() * 1000)
            {
                continue;
            }

            double time = (currentTime - placedTime) / 1000.0;
            double timeCompletion = time / fadeTime.get();

            Color fadedSideColor =
                sideColor.get().copy().a((int) (sideColor.get().a * (1 - timeCompletion)));
            Color fadedLineColor =
                lineColor.get().copy().a((int) (lineColor.get().a * (1 - timeCompletion)));

            BlockPos pos = entry.getKey();

            for (Direction dir : Direction.values())
            {
                if (!isSharedFace(pos, dir))
                {  // Only render if not shared
                    event.renderer.face(pos, dir, fadedSideColor, fadedLineColor, shapeMode.get());
                }
            }


        }
    }

    private boolean isSharedFace(BlockPos pos, Direction direction)
    {
        BlockPos adjacentPos = pos.offset(direction);

        // Check if the adjacent block is also a rendered block and is still valid
        if (renderLastPlacedBlock.containsKey(adjacentPos))
        {
            long adjacentPlacedTime = renderLastPlacedBlock.get(adjacentPos);
            long currentTime = System.currentTimeMillis();

            // If the adjacent block is still within fade time, skip rendering the face
            if (currentTime - adjacentPlacedTime <= fadeTime.get() * 1000)
            {
                return true;  // The face should not be rendered
            }
        }
        return false; // The face should be rendered
    }


    private List<BlockPos> getBlockPoses()
    {
        List<BlockPos> placePoses = new ArrayList<>();

        int r = range.get();
        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());

        int ex = eyePos.getX();
        int ey = eyePos.getY();
        int ez = eyePos.getZ();

        for (int x = -r; x <= r; x++)
        {
            for (int y = -r; y <= r; y++)
            {
                for (int z = -r; z <= r; z++)
                {
                    BlockPos pos = mutablePos.set(ex + x, ey + y, ez + z);

                    switch (mode.get())
                    {
                        case Below ->
                        {
                            if (pos.getY() >= mc.player.getBlockY())
                            {
                                continue;
                            }
                        }
                        case Horizontal ->
                        {
                            if (!directionCheck(pos))
                            {
                                continue;
                            }
                        }
                        case HorizontalSwim ->
                        {
                            if (pos.getY() == mc.player.getBlockY() && !directionCheck(pos))
                            {
                                continue;
                            }
                        }
                        case Plane ->
                        {
                            if (!planeCheck(pos))
                            {
                                continue;
                            }
                        }
                        default ->
                        {

                        }
                    }

                    BlockState state = mc.world.getBlockState(pos);

                    if (MeteorClient.BLOCK.checkPlacement(Items.OBSIDIAN, pos, state)
                        && inPlaceRange(pos))
                    {
                        placePoses.add(new BlockPos(pos));
                    }
                }
            }
        }

        return placePoses;
    }

    private boolean directionCheck(BlockPos blockPos)
    {
        switch (horizontalDirection.get())
        {
            case East ->
            {
                if (blockPos.getX() <= mc.player.getBlockX())
                {
                    return false;
                }
            }
            case West ->
            {
                if (blockPos.getX() >= mc.player.getBlockX())
                {
                    return false;
                }
            }

            case South ->
            {
                if (blockPos.getZ() <= mc.player.getBlockZ())
                {
                    return false;
                }
            }
            case North ->
            {
                if (blockPos.getZ() >= mc.player.getBlockZ())
                {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean planeCheck(BlockPos blockPos)
    {
        int blockValue = 0;

        switch (planeDirection.get())
        {
            case X -> blockValue = blockPos.getX();
            case Y -> blockValue = blockPos.getY();
            case Z -> blockValue = blockPos.getZ();
        }

        // -1 becuase if planeValue = 32 and blockValue = 32, 32 - 32 = 0, so 0 difference
        return Math.abs(planeValue.get() - blockValue) <= (planeThickness.get() - 1);
    }

    private Item findUseItem()
    {
        FindItemResult result = InvUtils.find(itemStack ->
        {
            for (Block blocks : blocks.get())
            {
                if (blocks.asItem() == itemStack.getItem())
                {
                    return true;
                }
            }

            return false;
        });

        if (!result.found())
        {
            return null;
        }

        return mc.player.getInventory().getStack(result.slot()).getItem();
    }

    private boolean inPlaceRange(BlockPos blockPos)
    {
        Vec3d from = mc.player.getEyePos();

        return blockPos.toCenterPos().distanceTo(from) <= 5.1;
    }

    private enum FillerMode
    {
        Below, Horizontal, HorizontalSwim, Plane, Litematica,
    }

    public enum HorizontalDirection
    {
        North, South, East, West
    }

    public enum PlaneDirection
    {
        X, Y, Z
    }
}
