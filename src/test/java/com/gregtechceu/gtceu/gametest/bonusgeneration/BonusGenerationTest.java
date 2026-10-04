package com.gregtechceu.gtceu.gametest.bonusgeneration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.DefectiveBonus;
import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.LoopRecipeList;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.machine.trait.PowerDistributionTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks for the defective bonus: how it is recorded on a loop recipe and how it is delivered. */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BonusGenerationTest {

    private static final String BATCH = "BonusGeneration";
    private static final BlockPos POS = new BlockPos(0, 1, 0);
    private static final ResourceLocation STEEL_WIRE = GTCEu.id("wiremill/mill_steel_wire");

    private static GTRecipe recipe(GameTestHelper helper, ResourceLocation id) {
        var found = helper.getLevel().getServer().getRecipeManager().byKey(id);
        helper.assertTrue(found.isPresent() && found.get() instanceof GTRecipe, "test premise: recipe " + id);
        return (GTRecipe) found.get();
    }

    /** A copy the test may write to without touching the registry recipe. */
    private static GTRecipe copyOf(GTRecipe recipe) {
        GTRecipe copy = recipe.copy();
        copy.data = copy.data.copy();
        return copy;
    }

    private static SimpleTieredMachine place(GameTestHelper helper, MachineDefinition definition) {
        return (SimpleTieredMachine) TestUtils.setMachine(helper, POS, definition);
    }

    /** Sets the dials; the HV budget is 16 and must be spent exactly. */
    private static void dials(SimpleTieredMachine machine, int tuning, int speed, int primary, int byproduct) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tuningPU", tuning);
        tag.putInt("speedPU", speed);
        tag.putInt("primaryPU", primary);
        tag.putInt("byproductPU", byproduct);
        machine.getTrait(PowerDistributionTrait.class).getPowerDistribution().deserializeNBT(tag);
    }

    private static int itemOutputAmount(GTRecipe recipe) {
        for (Content c : recipe.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (c.content() instanceof SizedIngredient s && !c.isChanced()) return s.getAmount();
        }
        return -1;
    }

    private static ListTag bonusOf(GTRecipe recipe) {
        return recipe.data.getList(DefectiveBonus.KEY, Tag.TAG_COMPOUND);
    }

    private static ItemStack wire(int count, boolean defective) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.wireGtSingle, GTMaterials.Steel, count);
        return defective ? DefectiveFlag.mark(stack) : stack;
    }

    // ---- data isolation and the loop decision ----

    @GameTest(template = "empty", batch = BATCH)
    public static void modifiedRecipeDoesNotShareDataWithTheOriginal(GameTestHelper helper) {
        GTRecipe original = recipe(helper, STEEL_WIRE);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.HV]);
        dials(machine, 7, 2, 7, 0);
        GTRecipe modified = machine.fullModifyRecipe(original.copy());
        helper.assertTrue(modified != null, "the recipe was refused by the modifier");
        helper.assertTrue(modified.data.contains(DefectiveBonus.KEY), "no bonus was recorded at a multiplier of 1.25");
        helper.assertFalse(original.data.contains(DefectiveBonus.KEY), "the bonus leaked into the original recipe");
        GTRecipe second = machine.fullModifyRecipe(original.copy());
        helper.assertTrue(second != null && second.data != modified.data, "two modified copies share their data");
        helper.assertFalse(original.data.contains(DefectiveBonus.KEY), "the bonus leaked into the original recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void loopMembershipDecidesTheBonusPath(GameTestHelper helper) {
        GTRecipe wire = recipe(helper, STEEL_WIRE);
        helper.assertTrue(LoopRecipeList.isLoop(wire.recipeType, wire.id), "steel wire is a loop recipe");
        GTRecipe slab = recipe(helper, GTCEu.id("cutter/oak_slab"));
        helper.assertFalse(LoopRecipeList.isLoop(slab.recipeType, slab.id), "test premise: the oak slab is not a loop");
        SimpleTieredMachine cutter = place(helper, GTMachines.CUTTER[GTValues.HV]);
        dials(cutter, 7, 2, 7, 0);
        GTRecipe modified = cutter.fullModifyRecipe(slab.copy());
        helper.assertTrue(modified != null, "the cutter recipe was refused");
        helper.assertFalse(modified.data.contains(DefectiveBonus.KEY), "a non-loop recipe got a defective bonus");
        helper.succeed();
    }

    // ---- the split ----

    @GameTest(template = "empty", batch = BATCH)
    public static void splitRecordsTheAboveBaselinePart(GameTestHelper helper) {
        GTRecipe base = recipe(helper, STEEL_WIRE);
        helper.assertTrue(itemOutputAmount(base) == 2, "test premise: the steel wire recipe makes 2 wires");

        GTRecipe a = GTRecipeModifiers.applyLoopBonus(copyOf(base), 1.5);
        helper.assertTrue(itemOutputAmount(a) == 2, "normal output changed at 1.5");
        CompoundTag e1 = bonusOf(a).getCompound(0);
        helper.assertTrue(bonusOf(a).size() == 1 && e1.getInt("guaranteed") == 1 && e1.getInt("chance") == 0,
                "at 1.5 expected 1 guaranteed and no chance entry, got " + e1);

        GTRecipe b = GTRecipeModifiers.applyLoopBonus(copyOf(base), 1.25);
        helper.assertTrue(itemOutputAmount(b) == 2, "normal output changed at 1.25");
        CompoundTag e2 = bonusOf(b).getCompound(0);
        helper.assertTrue(e2.getInt("guaranteed") == 0 && e2.getInt("chance") == 5000,
                "at 1.25 expected 0 guaranteed and a 50% unit, got " + e2);

        GTRecipe c = GTRecipeModifiers.applyLoopBonus(copyOf(base), 1.0);
        helper.assertFalse(c.data.contains(DefectiveBonus.KEY), "a bonus was recorded at 1.0");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void reductionsStayAsBefore(GameTestHelper helper) {
        GTRecipe base = recipe(helper, STEEL_WIRE);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.HV]);
        dials(machine, 7, 2, 1, 6); // Primary 1 halves the guaranteed output
        GTRecipe modified = machine.fullModifyRecipe(base.copy());
        helper.assertTrue(modified != null, "the recipe was refused");
        helper.assertTrue(itemOutputAmount(modified) == 1, "Primary 1 should give 1 wire, got " +
                itemOutputAmount(modified));
        helper.assertFalse(modified.data.contains(DefectiveBonus.KEY), "a reduction recorded a bonus");
        helper.succeed();
    }

    // ---- fluids, and recipes not on the list ----

    @GameTest(template = "empty", batch = BATCH)
    public static void fluidOutputsOfLoopRecipesStayAtBaseline(GameTestHelper helper) {
        GTRecipe base = recipe(helper, GTCEu.id("extractor/extract_stellite_100_block"));
        int before = 0;
        for (Content c : base.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of())) {
            before += ((FluidIngredient) c.content()).getAmount();
        }
        helper.assertTrue(before > 0, "test premise: the recipe has a fluid output");
        GTRecipe result = GTRecipeModifiers.applyLoopBonus(copyOf(base), 1.5);
        int after = 0;
        for (Content c : result.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of())) {
            after += ((FluidIngredient) c.content()).getAmount();
        }
        helper.assertTrue(after == before, "a fluid output was scaled: " + before + " to " + after);
        helper.assertFalse(result.data.contains(DefectiveBonus.KEY), "a fluid-only recipe recorded an item bonus");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void unlistedRecipesKeepTheOldFormula(GameTestHelper helper) {
        GTRecipe base = recipe(helper, GTCEu.id("cutter/oak_slab"));
        SimpleTieredMachine cutter = place(helper, GTMachines.CUTTER[GTValues.HV]);
        dials(cutter, 7, 2, 7, 0); // multiplier 1 + 5 * 5% = 1.25
        GTRecipe modified = cutter.fullModifyRecipe(base.copy());
        helper.assertTrue(modified != null, "the recipe was refused");
        double m = 1.25;
        int expectedGuaranteed = 0;
        double expectedChanceUnits = 0;
        for (Content c : base.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (c.tierChanceBoost() != 0 || c.isChanced() || !(c.content() instanceof SizedIngredient s)) continue;
            double target = s.getAmount() * m;
            int g = (int) Math.floor(target);
            expectedGuaranteed += g;
            double remainder = target - g;
            if (remainder > 1e-6) expectedChanceUnits += Math.round(remainder * 10000) / 10000.0;
        }
        int guaranteed = 0;
        double chanceUnits = 0;
        for (Content c : modified.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            if (!(c.content() instanceof SizedIngredient s)) continue;
            if (c.isChanced()) chanceUnits += s.getAmount() * (double) c.chance() / c.maxChance();
            else guaranteed += s.getAmount();
        }
        helper.assertTrue(guaranteed == expectedGuaranteed,
                "guaranteed output " + guaranteed + ", the old formula gives " + expectedGuaranteed);
        helper.assertTrue(Math.abs(chanceUnits - expectedChanceUnits) < 0.001,
                "chanced units " + chanceUnits + ", the old formula gives " + expectedChanceUnits);
        helper.succeed();
    }

    // ---- delivery ----

    @GameTest(template = "empty", batch = BATCH)
    public static void deliveryPutsFlaggedBonusInTheExtraSlot(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeModifiers.applyLoopBonus(copyOf(recipe(helper, STEEL_WIRE)), 1.5);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.LV]);
        machine.exportItems.setStackInSlot(0, wire(2, false));
        DefectiveBonus.deliver(machine, recipe, false, RandomSource.create(1));
        ItemStack extra = machine.exportItems.getStackInSlot(1);
        helper.assertTrue(extra.getCount() == 1 && DefectiveFlag.isDefective(extra),
                "expected 1 flagged wire in the extra slot, got " + extra);
        ItemStack normal = machine.exportItems.getStackInSlot(0);
        helper.assertTrue(normal.getCount() == 2 && !DefectiveFlag.isDefective(normal), "the normal slot changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void aRunThatConsumedADefectiveItemEarnsNoBonus(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeModifiers.applyLoopBonus(copyOf(recipe(helper, STEEL_WIRE)), 1.5);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.LV]);
        DefectiveBonus.deliver(machine, recipe, true, RandomSource.create(1));
        helper.assertTrue(machine.exportItems.getStackInSlot(0).isEmpty() &&
                machine.exportItems.getStackInSlot(1).isEmpty(), "a bonus was delivered for a defective-input run");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void deliveryWithFullSlotsReturnsNormally(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeModifiers.applyLoopBonus(copyOf(recipe(helper, STEEL_WIRE)), 1.5);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.LV]);
        machine.exportItems.setStackInSlot(0, wire(64, false));
        machine.exportItems.setStackInSlot(1, wire(64, false));
        DefectiveBonus.deliver(machine, recipe, false, RandomSource.create(1));
        helper.assertTrue(machine.exportItems.getStackInSlot(0).getCount() == 64 &&
                machine.exportItems.getStackInSlot(1).getCount() == 64 &&
                !DefectiveFlag.isDefective(machine.exportItems.getStackInSlot(1)), "full slots changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void remainderRollMatchesItsChance(GameTestHelper helper) {
        GTRecipe recipe = GTRecipeModifiers.applyLoopBonus(copyOf(recipe(helper, STEEL_WIRE)), 1.25);
        CompoundTag entry = bonusOf(recipe).getCompound(0);
        RandomSource random = RandomSource.create(42);
        int total = 0;
        for (int i = 0; i < 1000; i++) total += DefectiveBonus.roll(entry, random);
        helper.assertTrue(total >= 400 && total <= 600, "1,000 rolls at 50% gave " + total);
        helper.succeed();
    }

    // ---- the whole list, and conservation ----

    @GameTest(template = "empty", batch = BATCH)
    public static void sweepOverTheWholeLoopList(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        List<String> failures = new ArrayList<>();
        int checked = 0, notGt = 0, noEligibleOutput = 0;
        for (double m : new double[] { 2.0, 1.37 }) {
            for (Map.Entry<String, Set<String>> entry : LoopRecipeList.snapshot().entrySet()) {
                for (String id : entry.getValue()) {
                    var found = manager.byKey(ResourceLocation.tryParse(id));
                    if (found.isEmpty() || !(found.get() instanceof GTRecipe base)) {
                        if (m == 2.0) notGt++;
                        continue;
                    }
                    GTRecipe copy = copyOf(base);
                    int itemsBefore = totalAmount(copy, true), fluidsBefore = totalAmount(copy, false);
                    GTRecipeModifiers.applyLoopBonus(copy, m);
                    if (totalAmount(copy, true) != itemsBefore || totalAmount(copy, false) != fluidsBefore) {
                        failures.add(id + ": a normal output changed");
                        continue;
                    }
                    double expected = 0;
                    for (Content c : base.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
                        if (c.tierChanceBoost() == 0 && !c.isChanced() && c.content() instanceof SizedIngredient s &&
                                s.getAmount() > 0 && s.getItems().length > 0 && !s.getItems()[0].isEmpty()) {
                            expected += s.getAmount() * (m - 1.0);
                        }
                    }
                    double recorded = 0;
                    for (Tag t : bonusOf(copy)) {
                        CompoundTag e = (CompoundTag) t;
                        recorded += e.getInt("guaranteed") +
                                e.getInt("chance") / (double) ChanceLogic.getMaxChancedValue();
                    }
                    if (m == 2.0) {
                        checked++;
                        if (expected == 0) noEligibleOutput++;
                    }
                    if (Math.abs(recorded - expected) > 0.001 * Math.max(1, expected)) {
                        failures.add(id + " at " + m + ": expected bonus " + expected + ", recorded " + recorded);
                    }
                }
            }
        }
        GTCEu.LOGGER.info("Loop bonus sweep: {} GT recipes checked, {} list entries skipped (not a GT recipe), " +
                "{} recipes with no eligible item output (left unchanged)", checked, notGt, noEligibleOutput);
        helper.assertTrue(checked > 0, "the sweep checked nothing; is the loop list empty?");
        helper.assertTrue(failures.isEmpty(), failures.size() + " recipes failed, first: " +
                failures.subList(0, Math.min(5, failures.size())));
        helper.succeed();
    }

    private static int totalAmount(GTRecipe recipe, boolean items) {
        int total = 0;
        var contents = recipe.outputs.getOrDefault(items ? ItemRecipeCapability.CAP : FluidRecipeCapability.CAP,
                List.of());
        for (Content c : contents) {
            if (items && c.content() instanceof SizedIngredient s) total += s.getAmount();
            else if (!items && c.content() instanceof FluidIngredient f) {
                for (var stack : f.getStacks()) total += stack.getAmount();
            }
        }
        return total;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bonusMatchesTheExpectationOverManyRuns(GameTestHelper helper) {
        GTRecipe base = recipe(helper, STEEL_WIRE);
        RandomSource random = RandomSource.create(7);
        for (double m : new double[] { 1.05, 1.10, 1.50 }) {
            GTRecipe copy = GTRecipeModifiers.applyLoopBonus(copyOf(base), m);
            helper.assertTrue(itemOutputAmount(copy) == 2, "the normal output was not exactly 2 at " + m);
            int total = 0;
            for (int run = 0; run < 2000; run++) {
                for (Tag t : bonusOf(copy)) total += DefectiveBonus.roll((CompoundTag) t, random);
            }
            double expected = 2000 * 2 * (m - 1.0);
            helper.assertTrue(Math.abs(total - expected) <= 0.15 * expected,
                    "at " + m + " the bonus over 2,000 runs was " + total + ", expected about " + expected);
        }
        helper.succeed();
    }
    // ---- upstream pre-rolls ----

    /**
     * {@code RecipeLogic.setupRecipe} now runs the recipe through {@code RecipeHelper.doPrerolls} and keeps that copy
     * as the running (and later finishing) recipe. The bonus record and the kind marker live in the recipe's data, so
     * they must survive it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void prerollKeepsBonusData(GameTestHelper helper) {
        GTRecipe original = recipe(helper, STEEL_WIRE);
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.HV]);
        dials(machine, 7, 2, 7, 0);
        GTRecipe modified = machine.fullModifyRecipe(original.copy());
        helper.assertTrue(modified != null, "the recipe was refused by the modifier");
        helper.assertTrue(modified.data.contains(DefectiveBonus.KEY), "test premise: the bonus was recorded");
        String kind = modified.data.getString(DefectiveBonus.KIND_KEY);
        helper.assertTrue(!kind.isEmpty(), "test premise: the kind marker was recorded");
        ListTag bonus = bonusOf(modified).copy();

        GTRecipe rolled = RecipeHelper.doPrerolls(modified, new IdentityHashMap<>());
        helper.assertTrue(rolled != modified, "test premise: doPrerolls returns a copy");
        helper.assertTrue(rolled.data.contains(DefectiveBonus.KEY), "the bonus record was lost by doPrerolls");
        helper.assertTrue(bonusOf(rolled).equals(bonus), "the bonus record changed: " + bonusOf(rolled));
        helper.assertTrue(rolled.data.getString(DefectiveBonus.KIND_KEY).equals(kind),
                "the kind marker changed or was lost by doPrerolls");
        helper.assertTrue(itemOutputAmount(rolled) == itemOutputAmount(modified),
                "doPrerolls changed the guaranteed output");
        helper.succeed();
    }
}
