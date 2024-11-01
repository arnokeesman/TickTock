package me.techchrism.ticktock;

import me.techchrism.ticktock.mixin.ChunkTicketManagerInvoker;
import me.techchrism.ticktock.mixin.ChunkTicketTypeAccessor;
import me.techchrism.ticktock.mixin.ServerChunkLoadingManagerInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.GameRules;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public class Ticktock implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        //todo check if this should happen at the start or end of the world tick
        ServerTickEvents.START_WORLD_TICK.register(this::tick);
    }

    private void tick(ServerWorld world)
    {
        ServerChunkLoadingManagerInvoker storage = (ServerChunkLoadingManagerInvoker) world.getChunkManager().chunkLoadingManager;
        ChunkTicketManagerInvoker ticketManager = (ChunkTicketManagerInvoker) storage.ticktock_invokeGetTicketManager();
        int randomTickSpeed = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        if(randomTickSpeed == 0)
        {
            return;
        }
        storage.ticktock_invokeEntryIterator().forEach(chunkHolder ->
        {
            // Ensure the chunk is force loaded rather than a "regular" chunk outside the 128-block radius
            boolean forced = ticketManager.ticktock_invokeGetTicketSet(chunkHolder.getPos().toLong()).stream().anyMatch(chunkTicket ->
                    ((ChunkTicketTypeAccessor) chunkTicket.getType()).ticktock_getName().equals("forced"));
            if(!forced)
            {
                return;
            }

            OptionalChunk<WorldChunk> optionalWorldChunk = chunkHolder.getTickingFuture().getNow(ChunkHolder.UNLOADED_WORLD_CHUNK);
            optionalWorldChunk.ifPresent(chunk -> {
                // Make sure it's too far to get regular random ticks
                if (!storage.ticktock_invokeShouldTick(chunkHolder.getPos())) {
                    Profiler profiler = world.getProfiler();
                    int startX = chunk.getPos().getStartX();
                    int startZ = chunk.getPos().getStartZ();
                    int bottomY = chunk.getBottomY();
                    ChunkSection[] chunkSections = chunk.getSectionArray();
                    for (int i = 0; i < chunkSections.length; i++) {
                        ChunkSection chunkSection = chunkSections[i];
                        if (chunkSection != null && chunkSection.hasRandomTicks()) {
                            int yOffset = bottomY + i * 16;
                            for (int m = 0; m < randomTickSpeed; m++) {
                                BlockPos randomPosInChunk = world.getRandomPosInChunk(startX, yOffset, startZ, 15);
                                profiler.push("randomTick");
                                BlockState blockState = chunkSection.getBlockState(randomPosInChunk.getX() - startX,
                                        randomPosInChunk.getY() - yOffset,
                                        randomPosInChunk.getZ() - startZ);
                                if (blockState.hasRandomTicks()) {
                                    blockState.randomTick(world, randomPosInChunk, world.random);
                                }
                                FluidState fluidState = blockState.getFluidState();
                                if (fluidState.hasRandomTicks()) {
                                    fluidState.onRandomTick(world, randomPosInChunk, world.random);
                                }
                                profiler.pop();
                            }
                        }
                    }
                }
            });
        });
    }
}
