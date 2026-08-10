package com.gregtechceu.gtceu.api.machine;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PowerDistributionConfig implements INBTSerializable<CompoundTag> {

    private static final String NBT_TUNING = "tuningPU";
    private static final String NBT_SPEED = "speedPU";
    private static final String NBT_PRIMARY = "primaryPU";
    private static final String NBT_BYPRODUCT = "byproductPU";

    /**
     * Speed/Tuning curve factor for the "matched" portion of Speed — PU up to tuningPU, i.e. the free/balanced
     * tier climb. Two steps land per tier (tierDefault = 2×tier), so this compounds to {@code DURATION_CUT^2}
     * duration per tier at the default balanced climb — sqrt(0.88) makes that per-tier figure exactly 88% (was
     * ~56.25% at the old 0.75). Also governs PU below the 2-PU baseline (stretch, in reverse) regardless of
     * Tuning. See {@link #OC_CUT} for PU pushed past tuningPU (true overclock).
     */
    private static final double DURATION_CUT = Math.sqrt(0.88);
    /**
     * Duration curve factor for the "excess" portion of Speed — PU pushed past tuningPU, i.e. deliberate
     * overclock rather than the free climb. {@code 1/sqrt(2)} so that 2 excess PU exactly halves duration —
     * matching vanilla GT's own single-OC-step duration factor exactly, since a vanilla OC step is the anchor
     * this lane is built to reproduce. See {@link #euMultiplier} for the matching EU-side relationship
     * ({@code 1/excessFactor^2}, also vanilla's own OC relationship) that pairs with this.
     */
    private static final double OC_CUT = 1.0 / Math.sqrt(2);
    /**
     * Floors the efficiency lane's (Tuning ahead of Speed) EU/t discount at 2^EU_DELTA_FLOOR (12.5% of base at
     * the default -3). Vanilla has no equivalent mechanic to anchor this lane to, so it's independent of
     * {@link #OC_CUT}/{@link #DURATION_CUT} and untouched by the vanilla-matching rework of the overclock lane.
     */
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

    /** Unfloored duration factor from the matched (Speed≤Tuning) portion of Speed alone. */
    private double matchedDurationFactor() {
        return Math.pow(DURATION_CUT, Math.min(speedPU, tuningPU) - 2);
    }

    /** Unfloored duration factor from the excess (Speed&gt;Tuning, true overclock) portion of Speed alone. */
    private double excessDurationFactor() {
        return Math.pow(OC_CUT, Math.max(0, speedPU - tuningPU));
    }

    /**
     * Duration multiplier from Speed alone: PU up to tuningPU (the "matched" portion, including all PU below
     * the 2-PU baseline, which stretch duration in reverse the same way regardless of Tuning) cut duration by
     * {@link #DURATION_CUT} each; PU past tuningPU (true overclock) cut it by the steeper {@link #OC_CUT}
     * instead. Floored so the final duration never drops below {@link #MIN_DURATION_TICKS}. {@link #euMultiplier}
     * computes its own cancellation from the unfloored per-lane factors above rather than from this floored
     * ratio, so the two can diverge slightly right at the floor — not exact in that edge case, same tolerance
     * the tick-rounding already accepted before this change.
     */
    public double durationMultiplier(long baseDurationTicks) {
        double rawDuration = baseDurationTicks * matchedDurationFactor() * excessDurationFactor();
        long duration = Math.max(MIN_DURATION_TICKS, Math.round(rawDuration));
        return duration / (double) baseDurationTicks;
    }

    /**
     * Continuous (unfloored, no {@link #MIN_DURATION_TICKS} clamp) duration multiplier — same formula as
     * {@link #durationMultiplier(long)} minus the per-recipe integer-tick rounding, for callers with no specific
     * recipe/base-duration in hand (e.g. a GUI tooltip hovering a dial with no recipe context to floor against).
     */
    public double idealizedDurationMultiplier() {
        return matchedDurationFactor() * excessDurationFactor();
    }

    /**
     * EU/t multiplier, built from two independent lanes:
     * <ul>
     * <li>Matched (Speed≤Tuning): EU-neutral — cancels {@link #matchedDurationFactor()} exactly, so a balanced
     * Speed=Tuning climb leaves total EU per craft unchanged, same as always.</li>
     * <li>Speed &gt; Tuning (true overclock): vanilla-style and lossy — {@code 1/excessDurationFactor()^2}, the
     * same EU-to-duration relationship every other OC step in this mod already uses (duration half → EU
     * quadruple), so 2 excess PU reproduces a single vanilla OC step exactly.</li>
     * <li>Speed &lt; Tuning (efficiency): unaffected by the above — the separate, pre-existing EU discount with
     * no duration effect, floored at {@link #EU_DELTA_FLOOR}. Vanilla has no equivalent mechanic to anchor
     * this lane to, so it's left exactly as it was before this rework.</li>
     * </ul>
     */
    public double euMultiplier() {
        double matchedEuMultiplier = 1.0 / matchedDurationFactor();
        if (speedPU >= tuningPU) {
            double excessDurationFactor = excessDurationFactor();
            return matchedEuMultiplier / (excessDurationFactor * excessDurationFactor);
        }
        double effectiveEuDelta = Math.max(speedPU - tuningPU, EU_DELTA_FLOOR);
        return matchedEuMultiplier * Math.pow(2, effectiveEuDelta);
    }

    /**
     * Total EU per craft, as a multiplier against the recipe's unmodified total ({@code baseEUt × baseDuration}):
     * {@code euMultiplier() × idealizedDurationMultiplier()}. Not attributable to either dial alone — it's a
     * joint consequence of wherever Speed and Tuning currently sit — but it's the number that actually answers
     * "how much does this config cost me overall," which neither {@link #euMultiplier()} (EU/t alone) nor
     * {@link #idealizedDurationMultiplier()} (duration alone) answers by itself outside the matched-only case.
     * Collapses to exactly {@code 1} when balanced (Speed=Tuning, EU-neutral), to the efficiency lane's own
     * discount term alone when Tuning leads Speed (independent of whatever the matched climb is doing), and to
     * {@code 1/excessDurationFactor()} when Speed leads Tuning (matching the vanilla-anchored "2 excess PU =
     * total ×2" property). Continuous/unfloored, same caveat as {@link #idealizedDurationMultiplier()} — for
     * GUI display, not per-recipe application.
     */
    public double idealizedTotalEuMultiplier() {
        return euMultiplier() * idealizedDurationMultiplier();
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
