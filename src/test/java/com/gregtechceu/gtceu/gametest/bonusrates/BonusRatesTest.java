package com.gregtechceu.gtceu.gametest.bonusrates;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.DefectiveBonus;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.LoopRecipeList;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phase 6 rate table: every power-distribution machine type from the old whitelist gives 5% per PU, and only the
 * extractor's loop recipes are exempt. The table below is this test's own copy, deliberately not read from the code.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BonusRatesTest {

    private static final String BATCH = "BonusRates";
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    /** Recipe type paths of the 37 types in the spec's rate table (rock crusher's type is "rock_breaker"). */
    private static final List<String> TABLE = List.of(
            "centrifuge", "electrolyzer", "chemical_reactor", "mixer", "alloy_smelter", "arc_furnace", "packer",
            "brewery", "canner", "distillery", "fluid_solidifier", "polarizer", "laser_engraver", "scanner",
            "electric_furnace", "assembler", "circuit_assembler", "bender", "cutter", "extruder", "lathe",
            "compressor", "forge_hammer", "forming_press", "wiremill", "autoclave", "chemical_bath", "extractor",
            "fermenter", "fluid_heater", "macerator", "rock_breaker", "air_scrubber",
            "thermal_centrifuge", "sifter", "ore_washer", "gas_collector");

    private static final int RATE = 5;

    /** Types that were 0% before Phase 6. */
    private static final List<String> OLD_ZERO = List.of("centrifuge", "electrolyzer", "chemical_reactor", "mixer",
            "alloy_smelter", "arc_furnace", "packer", "brewery", "canner", "distillery", "fluid_solidifier",
            "polarizer", "laser_engraver", "scanner");

    private static GTRecipeType type(GameTestHelper helper, String path) {
        GTRecipeType type = GTRegistries.RECIPE_TYPES.get(GTCEu.id(path));
        helper.assertTrue(type != null, "test premise: recipe type " + path);
        return type;
    }

    private static SimpleTieredMachine wiremill(GameTestHelper helper) {
        return (SimpleTieredMachine) TestUtils.setMachine(helper, POS, GTMachines.WIREMILL[GTValues.HV]);
    }

    /** The HV budget of 16 must be spent exactly. */
    private static void dials(SimpleTieredMachine machine, int tuning, int speed, int primary, int byproduct) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tuningPU", tuning);
        tag.putInt("speedPU", speed);
        tag.putInt("primaryPU", primary);
        tag.putInt("byproductPU", byproduct);
        machine.getPowerDistribution().deserializeNBT(tag);
    }

    /** Guaranteed items plus chanced items weighted by their chance, for the outputs the Primary dial scales. */
    private static double itemUnits(GTRecipe recipe) {
        double total = 0;
        for (Content c : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (c.tierChanceBoost != 0 || !(c.content instanceof SizedIngredient s)) continue;
            if (c.isChanced()) total += s.getAmount() * (double) c.chance / c.maxChance;
            else total += s.getAmount();
        }
        return total;
    }

    /** Only the guaranteed outputs are scaled by the Primary dial. */
    private static double guaranteedUnits(GTRecipe recipe) {
        double total = 0;
        for (Content c : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (c.tierChanceBoost == 0 && !c.isChanced() && c.content instanceof SizedIngredient s) {
                total += s.getAmount();
            }
        }
        return total;
    }

    private static boolean scalable(GTRecipe recipe) {
        for (Content c : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (c.tierChanceBoost == 0 && !c.isChanced() && c.content instanceof SizedIngredient s &&
                    s.getAmount() > 0 && s.getItems().length > 0 && !s.getItems()[0].isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static GTRecipe copyOf(GTRecipe recipe) {
        GTRecipe copy = recipe.copy();
        copy.data = copy.data.copy();
        return copy;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void everyTableTypeGivesFivePercent(GameTestHelper helper) {
        helper.assertTrue(TABLE.size() == 37, "the table copy should have 37 rows, has " + TABLE.size());
        List<String> wrong = new ArrayList<>();
        for (String path : TABLE) {
            GTRecipeType type = type(helper, path);
            if (type.getPrimaryBonusPercentPerPU() != RATE) {
                wrong.add(path + "=" + type.getPrimaryBonusPercentPerPU());
            }
            if (type.isLoopBonusExempt() != path.equals("extractor")) wrong.add(path + " exemption flag");
        }
        helper.assertTrue(wrong.isEmpty(), "wrong rate or exemption: " + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void extractorLoopRecipesGetNoBonus(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        SimpleTieredMachine machine = (SimpleTieredMachine) TestUtils.setMachine(helper, POS,
                GTMachines.EXTRACTOR[GTValues.HV]);
        dials(machine, 7, 2, 7, 0); // multiplier 1.25

        var clay = manager.byKey(GTCEu.id("extractor/clay_extraction"));
        helper.assertTrue(clay.isPresent() && clay.get() instanceof GTRecipe, "test premise: clay_extraction");
        GTRecipe loop = (GTRecipe) clay.get();
        helper.assertTrue(LoopRecipeList.isLoop(loop.recipeType, loop.id), "test premise: clay_extraction is a loop");
        double before = itemUnits(loop);
        GTRecipe modified = machine.fullModifyRecipe(loop.copy());
        helper.assertTrue(modified != null, "the extractor recipe was refused");
        helper.assertFalse(modified.data.contains(DefectiveBonus.KEY), "an exempt loop recipe recorded a bonus");
        helper.assertTrue(itemUnits(modified) == before, "an exempt loop recipe's output changed: " + before + " to " +
                itemUnits(modified));

        GTRecipe scalableNonLoop = null;
        for (GTRecipe r : manager.getAllRecipesFor(loop.recipeType)) {
            if (!LoopRecipeList.isLoop(r.recipeType, r.id) && scalable(r)) {
                scalableNonLoop = r;
                break;
            }
        }
        helper.assertTrue(scalableNonLoop != null, "test premise: a non-loop extractor recipe with a scalable item " +
                "output");
        double base = itemUnits(scalableNonLoop);
        GTRecipe boosted = machine.fullModifyRecipe(scalableNonLoop.copy());
        helper.assertTrue(boosted != null, "the non-loop extractor recipe was refused");
        double units = itemUnits(boosted);
        helper.assertTrue(Math.abs(units - (base + guaranteedUnits(scalableNonLoop) * 0.25)) < 0.01 * Math.max(1, base),
                scalableNonLoop.id + ": expected " + (base + guaranteedUnits(scalableNonLoop) * 0.25) +
                        " item units at 1.25, got " + units);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void everyTableTypeGivesTheBonusItPredicts(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        SimpleTieredMachine machine = wiremill(helper);
        List<String> failures = new ArrayList<>();
        List<String> noLoop = new ArrayList<>();
        List<String> noNonLoop = new ArrayList<>();
        for (int primary : new int[] { 4, 7 }) {
            dials(machine, 7, 2, primary, 16 - 7 - 2 - primary);
            double m = 1.0 + (primary - 2) * RATE / 100.0;
            helper.assertTrue(Math.abs(machine.getPowerDistribution().primaryMultiplier(RATE) - m) < 1e-9,
                    "the dials give a multiplier of " + machine.getPowerDistribution().primaryMultiplier(RATE) +
                            ", expected " + m);
            for (String path : TABLE) {
                GTRecipeType type = type(helper, path);
                GTRecipe loop = null, nonLoop = null;
                int loops = 0, nonLoops = 0;
                for (GTRecipe r : manager.getAllRecipesFor(type)) {
                    if (!scalable(r)) continue;
                    if (primary == 4) {
                        if (LoopRecipeList.isLoop(r.recipeType, r.id)) loops++;
                        else nonLoops++;
                    }
                    if (LoopRecipeList.isLoop(r.recipeType, r.id)) {
                        if (loop == null) loop = r;
                    } else if (nonLoop == null) nonLoop = r;
                }
                if (primary == 4) {
                    GTCEu.LOGGER.info("Balance: {} {} loop and {} non-loop recipes with a scalable output{}", path,
                            loops, nonLoops, OLD_ZERO.contains(path) ? " (was 0%)" : "");
                }
                if (loop == null) {
                    if (primary == 4) noLoop.add(path);
                } else if (!type.isLoopBonusExempt()) {
                    GTRecipe copy = GTRecipeModifiers.applyLoopBonus(copyOf(loop), m);
                    double recorded = 0;
                    for (var t : copy.data.getList(DefectiveBonus.KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                        CompoundTag e = (CompoundTag) t;
                        recorded += e.getInt("guaranteed") +
                                e.getInt("chance") / (double) ChanceLogic.getMaxChancedValue();
                    }
                    double expected = guaranteedUnits(loop) * (m - 1.0);
                    if (Math.abs(recorded - expected) > 0.01 * Math.max(1, expected)) {
                        failures.add(path + " loop " + loop.id + " at primary " + primary + ": bonus " + recorded +
                                ", expected " + expected);
                    }
                }
                if (nonLoop == null) {
                    if (primary == 4) noNonLoop.add(path);
                } else {
                    GTRecipe copy = GTRecipeModifiers.applyPrimaryBonus(copyOf(nonLoop), m);
                    double expected = itemUnits(nonLoop) + guaranteedUnits(nonLoop) * (m - 1.0);
                    if (Math.abs(itemUnits(copy) - expected) > 0.01 * Math.max(1, expected)) {
                        failures.add(path + " non-loop " + nonLoop.id + " at primary " + primary + ": units " +
                                itemUnits(copy) + ", expected " + expected);
                    }
                }
            }
        }
        GTCEu.LOGGER.info("Rate check: no scalable loop recipe for {}", noLoop);
        GTCEu.LOGGER.info("Rate check: no scalable non-loop recipe for {}", noNonLoop);
        helper.assertTrue(failures.isEmpty(), failures.size() + " mismatches, first: " +
                failures.subList(0, Math.min(5, failures.size())));
        helper.succeed();
    }
}
