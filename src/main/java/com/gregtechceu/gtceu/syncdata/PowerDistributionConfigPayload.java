package com.gregtechceu.gtceu.syncdata;

import com.gregtechceu.gtceu.api.machine.PowerDistributionConfig;

import com.lowdragmc.lowdraglib.syncdata.payload.ObjectTypedPayload;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.Nullable;

public class PowerDistributionConfigPayload extends ObjectTypedPayload<PowerDistributionConfig> {

    @Override
    public @Nullable Tag serializeNBT() {
        return payload.writeToNbt();
    }

    @Override
    public void deserializeNBT(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            payload = PowerDistributionConfig.readFromNbt(compoundTag);
        }
    }
}
