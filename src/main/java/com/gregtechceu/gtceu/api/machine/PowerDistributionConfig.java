package com.gregtechceu.gtceu.api.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PowerDistributionConfig implements INBTSerializable<CompoundTag> {

    private static final String NBT_TUNING = "tuningPU";
    private static final String NBT_SPEED = "speedPU";
    private static final String NBT_PRIMARY = "primaryPU";
    private static final String NBT_BYPRODUCT = "byproductPU";

    /** Speed/Tuning curve factors: each Speed step past baseline cuts duration by this fraction. */
    private static final double DURATION_CUT = 0.75;
    /** Floors the EU/t multiplier at 2^EU_DELTA_FLOOR (12.5% of base at the default -3). */
    private static final double EU_DELTA_FLOOR = -3;
    /** 1 tick @ 20 tps — duration can never be reduced below this regardless of Speed. */
    private static final long MIN_DURATION_TICKS = 1;

    private int tuningPU;
    private int speedPU;
    private int primaryPU;
    private int byproductPU;

    public static PowerDistributionConfig defaults(int machineTier) {
        int tierDefault = tierDefault(machineTier);
        return new PowerDistributionConfig(tierDefault, tierDefault, 2, 2);
    }

    public static int tierDefault(int machineTier) {
        return 2 * machineTier;
    }

    public static int budget(int machineTier) {
        return 8 + 4 * (machineTier - 1);
    }

    public int spent() {
        return tuningPU + speedPU + primaryPU + byproductPU;
    }

    public boolean isOverBudget(int machineTier) {
        return spent() > budget(machineTier);
    }

    /**
     * Required for a recipe to actually run — under-spend is allowed by the UI as a reallocation transit state, but not
     * runnable.
     */
    public boolean isExactSpend(int machineTier) {
        return spent() == budget(machineTier);
    }

    public boolean hasActiveOutput() {
        return Math.max(primaryPU, byproductPU) >= 1;
    }

    /**
     * Duration multiplier from Speed alone: each PU above the 2-PU baseline cuts duration by
     * {@link #DURATION_CUT}, each PU below stretches it by the same factor in reverse. Floored so the
     * final duration never drops below {@link #MIN_DURATION_TICKS}; the returned ratio reflects that
     * floor exactly, so callers combining this with {@link #euMultiplier} stay consistent with it.
     */
    public double durationMultiplier(long baseDurationTicks) {
        double rawDuration = baseDurationTicks * Math.pow(DURATION_CUT, speedPU - 2);
        long duration = Math.max(MIN_DURATION_TICKS, Math.round(rawDuration));
        return duration / (double) baseDurationTicks;
    }

    /**
     * EU/t multiplier: a "matched" component that exactly offsets {@link #durationMultiplier} (so a
     * balanced Speed=Tuning climb leaves total EU per craft unchanged) times an "imbalance" component
     * that doubles per point of {@code speedPU - tuningPU}, floored at {@link #EU_DELTA_FLOOR}.
     */
    public double euMultiplier(long baseDurationTicks) {
        double matchedEuMultiplier = 1.0 / durationMultiplier(baseDurationTicks);
        double effectiveEuDelta = Math.max(speedPU - tuningPU, EU_DELTA_FLOOR);
        double imbalanceEuMultiplier = Math.pow(2, effectiveEuDelta);
        return matchedEuMultiplier * imbalanceEuMultiplier;
    }

    /**
     * Primary Output curve: below the 2-PU baseline this is a flat reduction (1 PU = 50%, 0 PU = 0%);
     * at/above baseline it's {@code 1.0 + (pu - 2) * (bonusPercentPerPU / 100.0)} — a per-recipe/per-machine-type
     * configurable slope, since a >100% guaranteed output can be a material-duplication exploit on recipes
     * that combine/split materials (composition/decomposition).
     */
    public double primaryMultiplier(int bonusPercentPerPU) {
        if (primaryPU <= 0) return 0.0;
        if (primaryPU == 1) return 0.5;
        return 1.0 + (primaryPU - 2) * (bonusPercentPerPU / 100.0);
    }

    public CompoundTag writeToNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_TUNING, tuningPU);
        tag.putInt(NBT_SPEED, speedPU);
        tag.putInt(NBT_PRIMARY, primaryPU);
        tag.putInt(NBT_BYPRODUCT, byproductPU);
        return tag;
    }

    public static PowerDistributionConfig readFromNbt(CompoundTag tag) {
        return new PowerDistributionConfig(
                tag.getInt(NBT_TUNING),
                tag.getInt(NBT_SPEED),
                tag.getInt(NBT_PRIMARY),
                tag.getInt(NBT_BYPRODUCT));
    }

    /** Required for {@code @SaveField}/{@code @SyncToClient} — the sync system mutates the existing field value. */
    @Override
    public CompoundTag serializeNBT() {
        return writeToNbt();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.tuningPU = tag.getInt(NBT_TUNING);
        this.speedPU = tag.getInt(NBT_SPEED);
        this.primaryPU = tag.getInt(NBT_PRIMARY);
        this.byproductPU = tag.getInt(NBT_BYPRODUCT);
    }
}
