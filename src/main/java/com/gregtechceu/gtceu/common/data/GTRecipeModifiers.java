package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.PowerDistributionConfig;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData;
import com.gregtechceu.gtceu.common.machine.trait.PowerDistributionTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.gregtechceu.gtceu.api.recipe.OverclockingLogic.*;

public class GTRecipeModifiers {

    /**
     * Given an {@link OverclockingLogic}, creates a {@link RecipeModifier} designed for an {@link IOverclockMachine}
     */
    public static final Function<OverclockingLogic, RecipeModifier> ELECTRIC_OVERCLOCK = Util
            .memoize(logic -> (machine, recipe) -> {
                if (!(machine instanceof IOverclockMachine overclockMachine)) return ModifierFunction.IDENTITY;
                if (RecipeHelper.getRecipeEUtTier(recipe) > overclockMachine.getMaxOverclockTier()) {
                    return ModifierFunction
                            .cancel(Component.translatable("gtceu.recipe_modifier.insufficient_voltage"));
                }
                return logic.getModifier(machine, recipe, overclockMachine.getOverclockVoltage());
            });

    // Shortcuts for common OC logics
    public static final RecipeModifier OC_PERFECT = ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK);
    public static final RecipeModifier OC_NON_PERFECT = ELECTRIC_OVERCLOCK.apply(NON_PERFECT_OVERCLOCK);
    public static final RecipeModifier OC_PERFECT_SUBTICK = ELECTRIC_OVERCLOCK.apply(PERFECT_OVERCLOCK_SUBTICK);
    public static final RecipeModifier OC_NON_PERFECT_SUBTICK = ELECTRIC_OVERCLOCK.apply(NON_PERFECT_OVERCLOCK_SUBTICK);

    public static final RecipeModifier PD_AWARE_OC = (machine, recipe) -> {
        if (!(machine instanceof IOverclockMachine overclockMachine)) return ModifierFunction.IDENTITY;
        if (!(machine instanceof SimpleTieredMachine tieredMachine)) {
            return RecipeModifier.nullWrongType(SimpleTieredMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > overclockMachine.getMaxOverclockTier()) {
            return ModifierFunction.cancel(Component.translatable("gtceu.recipe_modifier.insufficient_voltage"));
        }

        PowerDistributionConfig pd = tieredMachine.getTrait(PowerDistributionTrait.TYPE).getPowerDistribution();
        int machineTier = tieredMachine.getTier();

        // The configurator UI already blocks over-budget dial changes; this is a defense-in-depth guard for
        // configs that could otherwise reach an invalid state (e.g. stale save data). A recipe only runs at
        // exactly-spent budget — under-spend is a valid UI transit state, but not a runnable one.
        if (!pd.isExactSpend(machineTier)) {
            return ModifierFunction
                    .cancel(Component.translatable("gtceu.recipe_modifier.power_distribution_budget_not_exact"));
        }

        boolean recipeHasOutputs = recipe.outputs.values().stream().anyMatch(list -> !list.isEmpty());
        if (recipeHasOutputs && !pd.hasActiveOutput()) {
            return ModifierFunction
                    .cancel(Component.translatable("gtceu.recipe_modifier.power_distribution_no_active_output"));
        }

        double durMult = pd.durationMultiplier(recipe.duration);
        double euMult = pd.euMultiplier(recipe.duration);

        long baseEUt = RecipeHelper.getRealEUt(recipe).getTotalEU();
        if (baseEUt * euMult > GTValues.VA[machineTier]) {
            return ModifierFunction.cancel(Component.translatable("gtceu.recipe_modifier.insufficient_voltage"));
        }

        ModifierFunction ocModifier = ModifierFunction.builder()
                .durationMultiplier(durMult)
                .eutMultiplier(euMult)
                .build();

        int primaryBonusPct = recipe.data.contains("primary_bonus_pct") ? recipe.data.getInt("primary_bonus_pct") :
                recipe.recipeType.getPrimaryBonusPercentPerPU();
        double primaryMult = pd.primaryMultiplier(primaryBonusPct);
        ModifierFunction result = ocModifier;
        if (primaryMult != 1.0) {
            result = result.andThen(r -> applyPrimaryBonus(r, primaryMult));
        }

        // Symmetric like the Speed/Power Draw curve: PU below 2 reduces yield below the recipe's base chance
        // instead of being a free budget refund. Hard floor at 0 PU (matching Primary Output's own floor at
        // primaryPU <= 0) — guaranteed zero byproducts, not just a reduced chance that could still be positive
        // on a recipe whose base chance exceeds the linear reduction.
        int byproductPU = pd.getByproductPU();
        int byproductDelta = byproductPU - 2;
        if (byproductPU <= 0) {
            result = result.andThen(GTRecipeModifiers::removeChancedOutputs);
        } else if (byproductDelta != 0) {
            result = result.andThen(r -> applyYieldBoost(r, byproductDelta));
        }
        return result;
    };

    private static GTRecipe applyYieldBoost(GTRecipe recipe, int yieldSlots) {
        Map<RecipeCapability<?>, List<Content>> newOutputs = new HashMap<>();
        boolean changed = false;
        for (var entry : recipe.outputs.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Content> newList = new ArrayList<>();
            for (Content c : entry.getValue()) {
                if (c.tierChanceBoost() > 0 && c.chance() < c.maxChance()) {
                    changed = true;
                    int newChance = c.chance() + c.tierChanceBoost() * yieldSlots;
                    while (newChance >= c.maxChance()) {
                        newList.add(new Content(cap.copyContent(c.content()), c.maxChance(), c.maxChance(), 0));
                        newChance -= c.maxChance();
                    }
                    if (newChance > 0) {
                        newList.add(new Content(cap.copyContent(c.content()), newChance, c.maxChance(), 0));
                    }
                } else {
                    newList.add(c);
                }
            }
            newOutputs.put(cap, newList);
        }
        if (!changed) return recipe;
        recipe.outputs.clear();
        recipe.outputs.putAll(newOutputs);
        return recipe;
    }

    /** Byproduct hard floor (byproductPU &lt;= 0): unconditionally omits every chanced output, guaranteed zero. */
    private static GTRecipe removeChancedOutputs(GTRecipe recipe) {
        Map<RecipeCapability<?>, List<Content>> newOutputs = new HashMap<>();
        boolean changed = false;
        for (var entry : recipe.outputs.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Content> newList = new ArrayList<>();
            for (Content c : entry.getValue()) {
                if (c.tierChanceBoost() > 0 && c.chance() < c.maxChance()) {
                    changed = true;
                } else {
                    newList.add(c);
                }
            }
            newOutputs.put(cap, newList);
        }
        if (!changed) return recipe;
        recipe.outputs.clear();
        recipe.outputs.putAll(newOutputs);
        return recipe;
    }

    /**
     * Scales a recipe's guaranteed (non-chanced) outputs by {@code multiplier}, distributing any fractional
     * remainder as a chance for exactly one more unit — e.g. amount 3 at multiplier 0.5 gives 1 guaranteed
     * plus a 50% chance of a 2nd, not a 50% chance of another full 3-unit stack.
     * <p>
     * Only touches content that is not already chanced ({@code tierChanceBoost == 0 && !isChanced()}) and
     * whose underlying ingredient is a {@link SizedIngredient} or {@link FluidIngredient} (a fixed, known
     * amount) — anything else (already-chanced/yield content, or an unrecognized/range-based ingredient) is
     * left unchanged.
     */
    private static GTRecipe applyPrimaryBonus(GTRecipe recipe, double multiplier) {
        Map<RecipeCapability<?>, List<Content>> newOutputs = new HashMap<>();
        boolean changed = false;
        for (var entry : recipe.outputs.entrySet()) {
            RecipeCapability<?> cap = entry.getKey();
            List<Content> newList = new ArrayList<>();
            for (Content c : entry.getValue()) {
                boolean eligible = c.tierChanceBoost() == 0 && !c.isChanced();
                int amount = eligible ? getGuaranteedAmount(c.content()) : 0;
                if (eligible && amount > 0) {
                    changed = true;
                    double target = amount * multiplier;
                    int guaranteed = ContentModifier.multiplier(multiplier).apply(amount);
                    double remainder = target - guaranteed;

                    if (guaranteed > 0) {
                        newList.add(c.copy(cap, ContentModifier.multiplier(multiplier)));
                    }
                    if (remainder > 1e-6) {
                        Object oneUnit = cap.copyContent(c.content(), new ContentModifier(0, 1));
                        int chance = (int) Math.round(remainder * ChanceLogic.getMaxChancedValue());
                        newList.add(new Content(oneUnit, chance, ChanceLogic.getMaxChancedValue(), 0));
                    }
                    // if guaranteed == 0 and remainder <= 1e-6 (multiplier == 0): add nothing, output omitted
                } else {
                    newList.add(c);
                }
            }
            newOutputs.put(cap, newList);
        }
        if (!changed) return recipe;
        recipe.outputs.clear();
        recipe.outputs.putAll(newOutputs);
        return recipe;
    }

    private static int getGuaranteedAmount(Object content) {
        if (content instanceof SizedIngredient sized) return sized.getAmount();
        if (content instanceof FluidIngredient fluid) return fluid.getAmount();
        return 0;
    }

    public static final BiFunction<MedicalCondition, Integer, RecipeModifier> ENVIRONMENT_REQUIREMENT = Util
            .memoize((condition, maxAllowedStrength) -> (machine, recipe) -> {
                if (!ConfigHolder.INSTANCE.gameplay.environmentalHazards) return ModifierFunction.IDENTITY;
                if (!(machine.getLevel() instanceof ServerLevel serverLevel)) return ModifierFunction.NULL;

                EnvironmentalHazardSavedData data = EnvironmentalHazardSavedData.getOrCreate(serverLevel);
                BlockPos machinePos = machine.getBlockPos();
                var zone = data.getZoneByContainedPosAndCondition(machinePos, condition);
                if (zone == null) return ModifierFunction.IDENTITY;

                float strength = zone.strength();
                if (strength > maxAllowedStrength) return ModifierFunction.NULL;

                int multiplier = (1 + (int) (strength * 5 / maxAllowedStrength));
                if (multiplier > 5) return ModifierFunction.NULL;

                return ModifierFunction.builder()
                        .durationMultiplier(multiplier)
                        .build();
            });

    public static final RecipeModifier DEFAULT_ENVIRONMENT_REQUIREMENT = ENVIRONMENT_REQUIREMENT
            .apply(GTMedicalConditions.CARBON_MONOXIDE_POISONING, 1000);

    public static final RecipeModifier PARALLEL_HATCH = GTRecipeModifiers::hatchParallel;
    public static final RecipeModifier BATCH_MODE = GTRecipeModifiers::batchMode;

    /**
     * Recipe Modifier for <b>Parallel Multiblock Machines</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Looks for the Parallel Hatch on a Multiblock and attempts to parallelize the recipe up to the set amount
     * </p>
     *
     * @param machine a {@link MultiblockControllerMachine} machine
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Parallel Multiblock
     */
    public static @NotNull ModifierFunction hatchParallel(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof MultiblockControllerMachine controller && controller.isFormed()) {
            int parallels = controller.getParallelHatch()
                    .map(hatch -> ParallelLogic.getParallelAmount(machine, recipe, hatch.getCurrentParallel()))
                    .orElse(1);

            if (parallels == 1) return ModifierFunction.IDENTITY;
            return ModifierFunction.builder()
                    .modifyAllContents(ContentModifier.multiplier(parallels))
                    .eutMultiplier(parallels)
                    .parallels(parallels)
                    .build();
        }
        return ModifierFunction.IDENTITY;
    }

    public static @NotNull ModifierFunction batchMode(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof MultiblockControllerMachine controller && controller.isFormed() &&
                controller.isBatchEnabled()) {
            if (recipe.duration < ConfigHolder.INSTANCE.machines.batchDuration) {
                int parallel = ConfigHolder.INSTANCE.machines.batchDuration / recipe.duration;
                parallel = ParallelLogic.getParallelAmountWithoutEU(machine, recipe, parallel);

                if (parallel == 0) return ModifierFunction.NULL;
                if (parallel == 1) return ModifierFunction.IDENTITY;

                return ModifierFunction.builder()
                        .inputModifier(ContentModifier.multiplier(parallel))
                        .outputModifier(ContentModifier.multiplier(parallel))
                        .durationMultiplier(parallel)
                        .batchParallels(parallel)
                        .build();
            }
        }
        return ModifierFunction.IDENTITY;
    }

    /**
     * Recipe Modifier for <b>Cracker Multiblocks</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is OC'd via {@link OverclockingLogic#NON_PERFECT_OVERCLOCK_SUBTICK}.
     * Then, EUt is multiplied by {@code 1 - (0.1 × coilTier)}
     * </p>
     *
     * @param machine a {@link CoilWorkableElectricMultiblockMachine} used for Cracking
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Cracker
     */
    public static @NotNull ModifierFunction crackerOverclock(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coilMachine)) {
            return RecipeModifier.nullWrongType(CoilWorkableElectricMultiblockMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > coilMachine.getTier()) return ModifierFunction.NULL;

        var oc = OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, recipe,
                coilMachine.getOverclockVoltage());
        if (coilMachine.getCoilTier() > 0) {
            var coilModifier = ModifierFunction.builder()
                    .eutMultiplier(1.0 - coilMachine.getCoilTier() * 0.1)
                    .build();
            oc = oc.andThen(coilModifier);
        }
        return oc;
    }

    /**
     * Recipe Modifier for <b>Blast Furnace Multiblocks</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe is rejected if the required temperature is higher than the blast furnace's working temperature.
     * This working temperature is equal to {@code coilTemp + (100K × (voltageTier - MV))} for energy tiers over MV.
     * </p>
     * <p>
     * Recipe is OC'd via {@link OverclockingLogic#heatingCoilOC}.<br>
     * Then, EUt is multiplied by {@code 0.95×} for every {@code 900K} over the required temperature.
     * </p>
     *
     * @param machine a {@link CoilWorkableElectricMultiblockMachine} used for Blasting
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Blast Furnace
     */
    public static @NotNull ModifierFunction ebfOverclock(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coilMachine)) {
            return RecipeModifier.nullWrongType(CoilWorkableElectricMultiblockMachine.class, machine);
        }

        int blastFurnaceTemperature = coilMachine.getCoilType().getCoilTemperature() +
                (100 * Math.max(0, coilMachine.getTier() - GTValues.MV));
        int recipeTemp = recipe.data.getInt("ebf_temp");
        if (!recipe.data.contains("ebf_temp") || recipeTemp > blastFurnaceTemperature) {
            return ModifierFunction.cancel(Component.translatable("gtceu.recipe_modifier.coil_temperature_too_low"));
        }

        if (RecipeHelper.getRecipeEUtTier(recipe) > coilMachine.getTier()) {
            return ModifierFunction.cancel(Component.translatable("gtceu.recipe_modifier.insufficient_voltage"));
        }

        var discount = ModifierFunction.builder()
                .eutMultiplier(getCoilEUtDiscount(recipeTemp, blastFurnaceTemperature))
                .build();

        OverclockingLogic logic = (p, v) -> OverclockingLogic.heatingCoilOC(p, v, recipeTemp, blastFurnaceTemperature);
        var oc = logic.getModifier(machine, recipe, coilMachine.getOverclockVoltage());

        return oc.compose(discount);
    }

    /**
     * Recipe Modifier for <b>Pyrolyse Oven Multiblocks</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Recipe duration is multiplied by {@code 1.333×} for Cupronickel Coils
     * or {@code 2 / (tier + 1)} for higher tiercoils.<br>
     * Then, Recipe is OC'd via {@link OverclockingLogic#NON_PERFECT_OVERCLOCK_SUBTICK}.
     * </p>
     *
     * @param machine a {@link CoilWorkableElectricMultiblockMachine} used for Pyrolysis
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Pyrolyse Oven
     */
    public static @NotNull ModifierFunction pyrolyseOvenOverclock(@NotNull MetaMachine machine,
                                                                  @NotNull GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coilMachine)) {
            return RecipeModifier.nullWrongType(CoilWorkableElectricMultiblockMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > coilMachine.getTier()) return ModifierFunction.NULL;

        int tier = coilMachine.getCoilTier();
        double durationMultiplier = (tier == 0) ? (4.0 / 3.0) : (2.0 / (tier + 1)); // 75% speed with cupro coils
        var durationModifier = ModifierFunction.builder()
                .durationMultiplier(durationMultiplier)
                .build();

        var oc = NON_PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, recipe, coilMachine.getOverclockVoltage());
        return durationModifier.andThen(oc);
    }

    /**
     * Recipe Modifier for <b>Multi Smelters</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * Modifies the recipe in the following order:
     * <ol>
     * <li>Calculates the maximum parallels as {@code 32 × coilLevel}</li>
     * <li>Finds the actual parallel amount that the smelter can do</li>
     * <li>Sets the recipe duration to {@code 128 × 2 × parallels / maxParallels}</li>
     * <li>Sets the recipe EUt to {@code (4 × maxParallels / (8 × coilDiscount))}</li>
     * <li>Applies {@link OverclockingLogic#NON_PERFECT_OVERCLOCK} to this modified recipe</li>
     * <li>Multiplies the recipe contents by the parallel amount</li>
     * </ol>
     * </p>
     *
     * @param machine a {@link CoilWorkableElectricMultiblockMachine} used for parallel smelting
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Multi Smelter
     */
    public static @NotNull ModifierFunction multiSmelterParallel(@NotNull MetaMachine machine,
                                                                 @NotNull GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coilMachine)) {
            return RecipeModifier.nullWrongType(CoilWorkableElectricMultiblockMachine.class, machine);
        }

        int maxParallel = 32 * coilMachine.getCoilType().getLevel();
        int parallels = ParallelLogic.getParallelAmount(machine, recipe, maxParallel);
        if (parallels == 0) return ModifierFunction.NULL;

        int duration = (int) (128 * 2.0 * parallels / maxParallel);
        long eut = (long) (4 * maxParallel / (8.0 * coilMachine.getCoilType().getEnergyDiscount()));
        ModifierFunction baseModifier = r -> {
            var copy = r.copy();
            EURecipeCapability.putEUContent(copy.tickInputs, new EnergyStack(Math.max(1, eut)));
            copy.duration = Math.max(1, duration);
            return copy;
        };

        GTRecipe copy = baseModifier.apply(recipe);
        var ocModifier = NON_PERFECT_OVERCLOCK.getModifier(machine, copy, coilMachine.getOverclockVoltage());
        var parallelModifier = ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .parallels(parallels)
                .build();

        return baseModifier.andThen(ocModifier).andThen(parallelModifier);
    }
}
