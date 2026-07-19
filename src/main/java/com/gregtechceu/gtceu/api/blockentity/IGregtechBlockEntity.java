package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.extensions.IForgeBlockEntity;

public interface IGregtechBlockEntity extends ISyncManaged, ITickSubscription, IForgeBlockEntity {

    // Deliberately not named getLevel(): that name collides with vanilla BlockEntity's own getLevel()
    // method, and this project's SRG reobfuscation has shown non-deterministic behavior deciding whether
    // to remap this interface's abstract method to match the vanilla one, causing intermittent
    // AbstractMethodError at runtime in the packaged (non-dev) jar. A name with no vanilla SRG mapping
    // table entry can never be a rename candidate, so this is immune to that bug regardless of build shape.
    Level getGtLevel();

    BlockPos getBlockPos();

    BlockState getBlockState();

    long getOffsetTimer();

    boolean isRemoved();

    /**
     * Called to notify neighboring blocks that this block has changed.
     */
    default void notifyBlockUpdate() {
        if (getGtLevel() != null) {
            getGtLevel().updateNeighborsAt(getBlockPos(), getGtLevel().getBlockState(getBlockPos()).getBlock());
        }
    }

    default void scheduleNeighborShapeUpdate() {
        Level level = getGtLevel();
        BlockPos pos = getBlockPos();

        if (level == null || pos == null)
            return;

        level.getBlockState(pos).updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
    }

    default boolean isRemote() {
        return getGtLevel() == null ? GTCEu.isClientThread() : getGtLevel().isClientSide;
    }

    default void scheduleRenderUpdate() {
        var pos = getBlockPos();
        var level = getGtLevel();
        if (level != null) {
            var state = getGtLevel().getBlockState(pos);
            if (level.isClientSide) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
                requestModelDataUpdate();
            } else {
                level.blockEvent(pos, state.getBlock(), 1, 0);
            }
        }
    }

    default BlockEntity getNeighbor(Direction direction) {
        return getGtLevel().getBlockEntity(getBlockPos().relative(direction));
    }
}
