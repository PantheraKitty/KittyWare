/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render.blockesp;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.Utils.getRenderDistance;

public class ESPChunk
{

    private final int x, z;
    public Long2ObjectMap<ESPBlock> blocks;

    public ESPChunk(int x, int z)
    {
        this.x = x;
        this.z = z;
    }

    public static ESPChunk searchChunk(Chunk chunk, List<Block> blocks, boolean activatedSpawners)
    {
        ESPChunk schunk = new ESPChunk(chunk.getPos().x, chunk.getPos().z);
        if (schunk.shouldBeDeleted()) return schunk;

        BlockPos.Mutable blockPos = new BlockPos.Mutable();

        for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++)
        {
            for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++)
            {
                int height = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE).get(x - chunk.getPos().getStartX(), z - chunk.getPos().getStartZ());

                for (int y = mc.world.getBottomY(); y < height; y++)
                {
                    blockPos.set(x, y, z);
                    BlockState bs = chunk.getBlockState(blockPos);

                    if (blocks.contains(bs.getBlock()))
                    {
                        if (activatedSpawners && bs.isOf(Blocks.SPAWNER) && chunk.getBlockEntity(blockPos) instanceof MobSpawnerBlockEntity spawner)
                        {
                            if (spawner.getLogic().spawnDelay != 20 && isChestNearSpawner(blockPos))
                            {
                                schunk.add(blockPos, false);
                            }
                        } else
                        {
                            schunk.add(blockPos, false);
                        }
                    }
                }
            }
        }

        return schunk;
    }

    // Why the fuck is World::isChunkLoaded deprecated?
    @SuppressWarnings("deprecation")
    private static boolean isChestNearSpawner(BlockPos spawnerPos)
    {
        // Search 3x3 radius
        // NOTE: Maybe increase to be the actual bounding box of the spawner?
        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dy = -3; dy <= 3; dy++)
            {
                for (int dz = -3; dz <= 3; dz++)
                {
                    BlockPos checkPos = spawnerPos.add(dx, dy, dz);

                    if (!mc.world.isChunkLoaded(checkPos))
                    {
                        continue;
                    }

                    BlockEntity blockEntity = mc.world.getBlockEntity(checkPos);
                    if (blockEntity != null && blockEntity instanceof LootableContainerBlockEntity)
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public ESPBlock get(int x, int y, int z)
    {
        return blocks == null ? null : blocks.get(ESPBlock.getKey(x, y, z));
    }

    public void add(BlockPos blockPos, boolean update)
    {
        ESPBlock block = new ESPBlock(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        if (blocks == null) blocks = new Long2ObjectOpenHashMap<>(64);
        blocks.put(ESPBlock.getKey(blockPos), block);

        if (update) block.update();
    }

    public void add(BlockPos blockPos)
    {
        add(blockPos, true);
    }

    public void remove(BlockPos blockPos)
    {
        if (blocks != null)
        {
            ESPBlock block = blocks.remove(ESPBlock.getKey(blockPos));
            if (block != null) block.group.remove(block);
        }
    }

    public void update()
    {
        if (blocks != null)
        {
            for (ESPBlock block : blocks.values()) block.update();
        }
    }

    public void update(int x, int y, int z)
    {
        if (blocks != null)
        {
            ESPBlock block = blocks.get(ESPBlock.getKey(x, y, z));
            if (block != null) block.update();
        }
    }

    public int size()
    {
        return blocks == null ? 0 : blocks.size();
    }

    public boolean shouldBeDeleted()
    {
        int viewDist = getRenderDistance() + 1;
        int chunkX = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getZ());

        return x > chunkX + viewDist || x < chunkX - viewDist || z > chunkZ + viewDist || z < chunkZ - viewDist;
    }

    public void render(Render3DEvent event)
    {
        if (blocks != null)
        {
            for (ESPBlock block : blocks.values()) block.render(event);
        }
    }
}
