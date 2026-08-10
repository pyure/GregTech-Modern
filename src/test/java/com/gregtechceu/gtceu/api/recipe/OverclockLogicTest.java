package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.PowerDistributionConfig;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.PowerDistributionTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.gregtechceu.gtceu.api.recipe.OverclockingLogic.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeModifiers.*;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.LARGE_CHEMICAL_RECIPES;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class OverclockLogicTest {

    private static GTRecipeType LCR_RECIPE_TYPE;
    private static GTRecipeType CR_RECIPE_TYPE;

    @BeforeBatch(batch = "OverclockLogic")
    public static void prepare(ServerLevel level) {
        LCR_RECIPE_TYPE = TestUtils.createRecipeType("overclock_logic_lcr_tests", GTRecipeTypes.LARGE_CHEMICAL_RECIPES);
        CR_RECIPE_TYPE = TestUtils.createRecipeType("overclock_logic_cr_tests", GTRecipeTypes.CHEMICAL_RECIPES);

        LCR_RECIPE_TYPE.getAdditionHandler().beginStaging();
        CR_RECIPE_TYPE.getAdditionHandler().beginStaging();
        LCR_RECIPE_TYPE.getAdditionHandler().addStaging(LCR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic"))
                .inputItems(new ItemStack(Items.RED_BED))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.HV])
                .duration(20)
                // NBT has a schematic in it with an HV energy input hatch
                .buildRawRecipe());
        LCR_RECIPE_TYPE.getAdditionHandler().addStaging(LCR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_2"))
                .inputItems(new ItemStack(Items.STICK))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.LV])
                .duration(1)
                // NBT has a schematic in it with an HV energy input hatch
                .buildRawRecipe());
        LCR_RECIPE_TYPE.getAdditionHandler().addStaging(LCR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_3"))
                .inputItems(new ItemStack(Items.BROWN_BED))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.EV])
                .duration(1)
                // NBT has a schematic in it with an HV energy input hatch
                .buildRawRecipe());
        CR_RECIPE_TYPE.getAdditionHandler().addStaging(CR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_4"))
                .inputItems(new ItemStack(Items.RED_BED))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.HV])
                .duration(16)
                // NBT has a schematic in it with an HV charged singleblock CR in it
                .buildRawRecipe());
        CR_RECIPE_TYPE.getAdditionHandler().addStaging(CR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_5"))
                .inputItems(new ItemStack(Items.BROWN_BED))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.MV])
                .duration(16)
                // NBT has a schematic in it with an HV charged singleblock CR in it
                .buildRawRecipe());
        CR_RECIPE_TYPE.getAdditionHandler().addStaging(CR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_7"))
                .inputItems(new ItemStack(Items.IRON_INGOT))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV])
                .duration(16)
                // NBT has a schematic in it with an HV charged singleblock CR in it. EUt = VA[MV] (not V[HV] like
                // test_overclock_logic_4) deliberately: PD_AWARE_OC's voltage ceiling check is
                // baseEUt * euMultiplier > VA[machineTier], and the Power Distribution tests below apply up to a
                // 4x EU multiplier — VA[MV] * 4 == VA[HV] exactly, staying at (not over) the ceiling in every case.
                .buildRawRecipe());
        CR_RECIPE_TYPE.getAdditionHandler().addStaging(CR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_6"))
                .inputItems(new ItemStack(Items.GOLD_INGOT))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.V[GTValues.LV])
                .duration(1)
                // NBT has a schematic in it with an HV charged singleblock CR in it; 1t duration for the Power
                // Distribution duration-floor tests
                .buildRawRecipe());
        CR_RECIPE_TYPE.getAdditionHandler().addStaging(CR_RECIPE_TYPE
                .recipeBuilder(GTCEu.id("test_overclock_logic_8"))
                .inputItems(new ItemStack(Items.EMERALD))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(20)
                .duration(16)
                // NBT has a schematic in it with an HV charged singleblock CR in it. Deliberately a *low* EUt
                // (unlike test_overclock_logic_7, which was tuned to land exactly on VA[HV]=480 at a 4x
                // multiplier) — powerDistributionImbalanceDoublingTest's 4x multiplier is now computed via
                // Math.pow(1/sqrt(2), 2) rather than the old flat Math.pow(2, 2), and unlike the old integer
                // exponent, that's not guaranteed to land on exactly 4.0 (tiny floating-point drift either way
                // is possible) — landing exactly on a hard ceiling is a real risk now, not just a tidy number,
                // confirmed by this recipe's predecessor silently refusing to run at all under
                // PD_AWARE_OC's voltage check once tried. 20 EUt gives real margin (20*4=80, nowhere near any
                // real tier's VA[]) instead of gambling on an exact boundary.
                .buildRawRecipe());
        LCR_RECIPE_TYPE.getAdditionHandler().completeStaging();
        CR_RECIPE_TYPE.getAdditionHandler().completeStaging();
    }

    private record BusHolder(ItemBusPartMachine inputBus1, ItemBusPartMachine inputBus2, ItemBusPartMachine outputBus1,
                             WorkableMultiblockMachine controller) {}

    /**
     * Retrieves the busses for this specific template and force a multiblock structure check
     *
     * @param helper the GameTestHelper
     * @return the busses, in the BusHolder record.
     */
    private static BusHolder getBussesAndForm(GameTestHelper helper) {
        WorkableMultiblockMachine controller = (WorkableMultiblockMachine) helper.getBlockEntity(new BlockPos(1, 2, 0));
        assert controller != null;
        TestUtils.formMultiblock(helper, controller);
        controller.setRecipeType(LCR_RECIPE_TYPE);
        ItemBusPartMachine inputBus1 = (ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 1, 0));
        ItemBusPartMachine inputBus2 = (ItemBusPartMachine) helper.getBlockEntity(new BlockPos(2, 2, 0));
        ItemBusPartMachine outputBus1 = (ItemBusPartMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        return new BusHolder(inputBus1, inputBus2, outputBus1, controller);
    }

    /** {@code powerDistribution} now lives on {@link PowerDistributionTrait}, not directly on the machine. */
    private static void setPowerDistribution(SimpleTieredMachine machine, int tuningPU, int speedPU, int primaryPU,
                                             int byproductPU) {
        PowerDistributionConfig pd = machine.getTrait(PowerDistributionTrait.class).getPowerDistribution();
        pd.setTuningPU(tuningPU);
        pd.setSpeedPU(speedPU);
        pd.setPrimaryPU(primaryPU);
        pd.setByproductPU(byproductPU);
    }

    // Test for running HV recipe at HV
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic", setupTicks = 40, timeoutTicks = 200)
    public static void overclockLogicOnTierNothingChanges(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Items.RED_BED));
        // One tick to start, 20 for the recipe to run
        helper.succeedOnTickWhen(21, () -> {
            helper.assertTrue(
                    TestUtils.isItemStackEqual(busHolder.outputBus1.getInventory().getStackInSlot(0),
                            new ItemStack(Blocks.STONE)),
                    "Item didn't craft at the right tick with an on-tier recipe" +
                            busHolder.outputBus1.getInventory().getStackInSlot(0).getDisplayName());
        });
    }

    // Test for running LV 1t recipe at HV
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic", setupTicks = 40, timeoutTicks = 200)
    public static void overclockLogicTwoTiersAbove16Parallels(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Items.STICK, 64));
        // One tick to start, 4 for the recipe to run (16/t from ULV recipe to HV)
        helper.succeedOnTickWhen(5, () -> {
            helper.assertTrue(
                    TestUtils.isItemStackEqual(busHolder.outputBus1.getInventory().getStackInSlot(0),
                            new ItemStack(Blocks.STONE, 64)),
                    "Item didn't craft at the right tick with an on-tier recipe" +
                            busHolder.outputBus1.getInventory().getStackInSlot(0).getDisplayName());
        });
    }

    // Test for running EV recipe at HV
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic", setupTicks = 40, timeoutTicks = 200)
    public static void overclockLogicOverTierNothingHappens(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Items.BROWN_BED));
        helper.onEachTick(() -> {
            helper.assertFalse(
                    busHolder.outputBus1.getInventory().getStackInSlot(0).getItem().equals(Blocks.STONE.asItem()),
                    "Item crafted at one tier over when it shouldn't have");
        });
        TestUtils.succeedAfterTest(helper, 200);
    }

    // Test for code wise calculating perfect OC
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicApplyPerfectOverclockTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-input-separation"))
                .id(GTCEu.id("test-multiblock-input-separation"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.ACACIA_WOOD))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV]).duration(100)
                .buildRawRecipe();

        GTRecipe newRecipe = OC_PERFECT.applyModifier(busHolder.controller, recipeBeforeModifiers);
        helper.assertTrue(newRecipe != null, "Could not apply overclock to recipe");
        helper.assertTrue(newRecipe.duration == (recipeBeforeModifiers.duration / PERFECT_DURATION_FACTOR_INV),
                "Perfect perfect overclock didn't cut recipe time by 4");
        helper.assertTrue(
                newRecipe.getInputEUt().getTotalEU() ==
                        (recipeBeforeModifiers.getInputEUt().getTotalEU() * STD_VOLTAGE_FACTOR),
                "Non perfect overclock didn't multiply EU by 4");
        helper.succeed();
    }

    // Test for code wise calculating non-perfect OC
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicApplyNonPerfectOverclockTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-overclock-test-npo"))
                .id(GTCEu.id("test-multiblock-overclock-test-npo"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.ACACIA_WOOD))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV]).duration(100)
                .buildRawRecipe();

        GTRecipe newRecipe = OC_NON_PERFECT.applyModifier(busHolder.controller, recipeBeforeModifiers);
        helper.assertTrue(newRecipe != null, "Could not apply overclock to recipe");
        helper.assertTrue(newRecipe.duration == (recipeBeforeModifiers.duration / STD_DURATION_FACTOR_INV),
                "Non perfect overclock didn't cut recipe time by 2");
        helper.assertTrue(
                newRecipe.getInputEUt().getTotalEU() ==
                        (recipeBeforeModifiers.getInputEUt().getTotalEU() * STD_VOLTAGE_FACTOR),
                "Non perfect overclock didn't multiply EU by 4");
        helper.succeed();
    }

    // Test for code wise calculating subtick perfect OC
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicApplyPerfectParallelOverclockTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-overclock-test-psto"))
                .id(GTCEu.id("test-multiblock-overclock-test-psto"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV]).duration(1)
                .buildRawRecipe();
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));

        GTRecipe newRecipe = OC_PERFECT_SUBTICK.applyModifier(busHolder.controller, recipeBeforeModifiers);

        helper.assertTrue(newRecipe != null, "Could not apply overclock to recipe");
        helper.assertTrue(newRecipe.subtickParallels == PERFECT_DURATION_FACTOR_INV,
                "Perfect subtick overclock didn't multiply parallels by 4");
        helper.assertTrue(
                newRecipe.getInputEUt().getTotalEU() ==
                        (recipeBeforeModifiers.getInputEUt().getTotalEU() * STD_VOLTAGE_FACTOR),
                "Perfect subtick overclock didn't multiply EU by 4");
        helper.succeed();
    }

    // Test for code wise calculating subtick non-perfect OC
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicApplyNonPerfectParallelOverclockTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-overclock-test-npsto"))
                .id(GTCEu.id("test-multiblock-overclock-test-npsto"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV]).duration(1)
                .buildRawRecipe();
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));

        GTRecipe newRecipe = OC_NON_PERFECT_SUBTICK.applyModifier(busHolder.controller, recipeBeforeModifiers);

        helper.assertTrue(newRecipe != null, "Could not apply overclock to recipe");
        helper.assertTrue(newRecipe.subtickParallels == STD_DURATION_FACTOR_INV,
                "Non-Perfect subtick overclock didn't multiply parallels by 2");
        helper.assertTrue(
                newRecipe.getInputEUt().getTotalEU() ==
                        (recipeBeforeModifiers.getInputEUt().getTotalEU() * STD_VOLTAGE_FACTOR),
                "Non-Perfect subtick overclock didn't multiply EU by 4");
        helper.succeed();
    }

    // Test for code wise calculating non-subtick non-perfect OC on a 1t recipe
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicApplyNonPerfectNonParallel1tOverclockTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-overclock-test-npsto"))
                .id(GTCEu.id("test-multiblock-overclock-test-npsto"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.MV]).duration(1)
                .buildRawRecipe();
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));

        GTRecipe newRecipe = OC_NON_PERFECT.applyModifier(busHolder.controller, recipeBeforeModifiers);

        helper.assertTrue(newRecipe != null, "Could not apply overclock to recipe");
        helper.assertTrue(newRecipe.subtickParallels == 1,
                "Non-Perfect Non-subtick overclock overclocked when it shouldn't have");
        helper.assertTrue(
                newRecipe.getInputEUt().getTotalEU() == recipeBeforeModifiers.getInputEUt().getTotalEU(),
                "Non-Perfect Non-subtick overclock at 1t changed EU");
        helper.succeed();
    }

    // Test for code wise calculating an overclock on a recipe that can't be run
    @GameTest(template = "lcr_input_separation", batch = "OverclockLogic")
    public static void overclockLogicEVRecipeHVMachineTest(GameTestHelper helper) {
        BusHolder busHolder = getBussesAndForm(helper);
        // An HV LCR can overclock an MV recipe once
        // We pass the controller because it is used to fetch .getMaxVoltageTier() and check input ingredients for
        // parallel
        GTRecipe recipeBeforeModifiers = LARGE_CHEMICAL_RECIPES
                .recipeBuilder(GTCEu.id("test-multiblock-overclock-test-ev-hv"))
                .id(GTCEu.id("test-multiblock-overclock-test-ev-hv"))
                .inputItems(new ItemStack(Blocks.COBBLESTONE))
                .outputItems(new ItemStack(Blocks.STONE))
                .EUt(GTValues.VA[GTValues.EV]).duration(1)
                .buildRawRecipe();
        busHolder.inputBus1.getInventory().setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));

        GTRecipe newRecipe = OC_NON_PERFECT.applyModifier(busHolder.controller, recipeBeforeModifiers);

        helper.assertTrue(newRecipe == null, "Applied EV overclock to HV recipe when it shouldn't have");

        helper.succeed();
    }

    // Test for charge usage of a singleblock HV chemical reactor running an HV recipe
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void overclockLogicHVPowerTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));

        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        helper.assertTrue(energyContainer.getEnergyStored() == originalCharge,
                "Singleblock charged CR NBT changed, machine not fully charged anymore");

        itemIn.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        // Machine runs at its actual default Power Distribution config (speedPU=tuningPU=6, primaryPU=byproductPU=2
        // for HV — untouched here, this test intentionally exercises the out-of-the-box default). That default is
        // NOT a no-op above LV, and PowerDistributionConfig.euMultiplier() no longer cancels the *rounded* duration
        // ratio (it did under the old single-curve formula, which is where a since-corrected first draft of these
        // numbers came from) — it cancels the *unfloored* matchedDurationFactor(), so the two must be computed
        // separately rather than derived from one another:
        //   matchedPU = 6-2 = 4 (balanced, so this is the whole curve — excessPU = 0)
        //   matchedDurationFactor = DURATION_CUT^4 = 0.88^2 = 0.7744
        //   rawDuration = 16 * 0.7744 = 12.3904, round() -> 12 ticks (1t to turn on, 12t to run)
        //   matchedEuMultiplier = 1/0.7744 = 1.291322...
        //   eut = truncate(120 * 1.291322...) = truncate(154.9587) = 154 (ContentModifier.apply truncates via an
        //     (int)/(long) cast, it does not round — confirmed against a live in-game truncation artifact elsewhere)
        //   chargeUsed = 154 * 12 = 1848 — NOT 120*16=1920; the "balanced climb preserves total EU" property only
        //     holds against the *unrounded* raw duration (12.3904, not the rounded 12), and on a short 16-tick
        //     recipe like this one the gap between them is a real ~3.7%, not negligible tick-rounding noise.
        // Originally used test_overclock_logic_4 (EUt=V[HV]=512) and asserted unmodified duration/EU/t at "default"
        // — both wrong once Power Distribution existed: (1) 512 > VA[HV]=480 violates PD_AWARE_OC's voltage
        // ceiling even at an unmodified multiplier, and (2) the machine's default is not the formula's neutral
        // baseline for any tier above LV. Switched to test_overclock_logic_7 (EUt=VA[MV]=120) to fix both.
        helper.succeedOnTickWhen(13, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(
                    itemOut.getStackInSlot(0),
                    new ItemStack(Blocks.STONE, 1)),
                    "Singleblock CR didn't run recipe in correct time");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = 1848L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Recipe didn't consume right amount, instead of " + chargeNeeded + " it used " + chargeUsed);
        });
    }

    /**
     * Verified property: at the formula's literal baseline (speedPU = tuningPU = 2 — NOT the tier default,
     * which for HV is 6/6), a recipe runs completely unmodified. This is distinct from what a fresh machine
     * actually defaults to; see {@link #powerDistributionBalancedClimbFlatEuTest} for that.
     * <p>
     * Uses {@code test_overclock_logic_7} (EUt = VA[MV] = 120, not {@code test_overclock_logic_4}'s
     * V[HV] = 512) deliberately: {@code PD_AWARE_OC}'s voltage ceiling check is
     * {@code baseEUt * euMultiplier > VA[machineTier]}, and 512 already exceeds VA[HV] = 480 even at an
     * unmodified 1.0x multiplier — {@code test_overclock_logic_4} silently violates the ceiling on its own,
     * independent of any PD math, which is also why the pre-existing {@link #overclockLogicHVPowerTest} fails.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionBaselineTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 2, 2, 6, 6);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        // 1t to turn on, 16t to run the unmodified recipe
        helper.succeedOnTickWhen(17, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "Baseline PD config didn't run the recipe in the unmodified duration");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.VA[GTValues.MV] * 16L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Baseline PD config changed EU/t, instead of " + chargeNeeded + " it used " + chargeUsed);
        });
    }

    /**
     * Verified property: a balanced Speed=Tuning climb is EU-neutral against the *unrounded* raw duration —
     * speedPU=tuningPU=7 on a 16-tick/VA[MV]-EUt recipe gives:
     * <ul>
     * <li>matchedPU = 7-2 = 5 (balanced, excessPU = 0, so this is the whole curve)</li>
     * <li>matchedDurationFactor = DURATION_CUT^5 ≈ 0.726452, rawDuration = 16*0.726452 ≈ 11.623, round() -> 12
     * ticks (1t to turn on, 12t to run)</li>
     * <li>matchedEuMultiplier = 1/0.726452 ≈ 1.376545, eut = truncate(120*1.376545) = truncate(165.185) = 165
     * ({@code ContentModifier.apply} truncates via an int/long cast, does not round)</li>
     * <li>chargeUsed = 165*12 = 1980 — not 120*16=1920. Does <b>not</b> land on VA[HV]=480 or any other clean
     * number the way the old 0.75-constant version of this test did; that was incidental to the old constant,
     * not a load-bearing property. The "flat total EU" property this test verifies only holds exactly against
     * the unrounded raw duration (11.623), not the rounded tick count (12) — the two differ by a small but, on
     * a short 16-tick recipe, non-negligible amount (~3%), same caveat as {@link #overclockLogicHVPowerTest}.</li>
     * </ul>
     * primaryPU is kept at 2 (not 1) deliberately — {@code PowerDistributionConfig.primaryMultiplier} halves a
     * primaryPU=1 guaranteed output into a 50%-chance output (from the separate Primary/Byproduct output-split
     * feature), which would make this test's item-stack assertion flaky for a reason unrelated to what it's
     * actually verifying.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionBalancedClimbFlatEuTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 7, 7, 2, 0);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        // 1t to turn on, 12t to run the recipe (duration rounded from 16 to 12 — see the derivation in this
        // method's doc comment)
        helper.succeedOnTickWhen(13, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "Balanced climb didn't cut recipe duration to 12 ticks");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = 1980L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Balanced climb didn't consume the expected total EU, instead of " + chargeNeeded +
                            " it used " + chargeUsed);
        });
    }

    /**
     * Verified property (rewritten — the old premise below no longer holds, see the note at the bottom):
     * pushing Speed 2 PU past Tuning, with Tuning pinned at the literal 2-baseline (so {@code matchedPU=0} and
     * the matched lane contributes nothing at all), reproduces a single vanilla GT overclock step <b>exactly</b>:
     * duration ×0.5, EU/t ×4, total energy ×2 — not ×4; {@code duration½ × EU×4 = ×2 total}, easy to mis-derive
     * as ×4 if only the EU/t number is considered. On {@code test_overclock_logic_8} (EUt=20, duration=16):
     * {@code excessDurationFactor = OC_CUT²} — theoretically exactly 0.5 (and {@code duration = 16×0.5 = 8}
     * theoretically exact, since {@code matchedPU=0} means {@code matchedDurationFactor=1} exactly), but
     * {@code OC_CUT = 1/sqrt(2)} is an irrational value's nearest double, so unlike the old formula's integer
     * {@code Math.pow(2, delta)} exponents, nothing here is actually guaranteed bit-exact — deliberately using
     * a low EUt (not {@code test_overclock_logic_7}'s {@code VA[MV]=120}, which lands the resulting eut exactly
     * on {@code VA[HV]=480}) so a few ULPs of drift either way can't push the result over the voltage ceiling
     * and silently refuse the recipe, which is exactly what happened when this test first tried reusing
     * {@code test_overclock_logic_7} here — confirmed by the recipe simply never completing, not a wrong
     * number.
     * <p>
     * <b>Old premise (pre-OC_CUT-rework), no longer true, kept here for context</b>: this test used to hold
     * Speed at the literal baseline (2) and only lower Tuning, asserting duration stayed at 16 ticks and only
     * EU/t changed, doubling per point of imbalance. Both halves of that are now false — Tuning falling below
     * Speed's own baseline is itself a form of overclock and does affect duration (explicitly confirmed
     * intentional via live user testing, see {@code plans/state.md}), and doubling now happens per 2-PU step
     * to match vanilla, not per single PU.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionImbalanceDoublingTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 2, 4, 6, 4);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.EMERALD));
        // 1t to turn on, 8t to run the recipe (duration exactly halved, zero rounding — see doc comment).
        // tuning=2, speed=4, primary=6, byproduct=4 -> spent=16=budget(HV) exactly (HV's budget is 16, not
        // MV's 12 — the machine here is HV, per singleblock_charged_cr/originalCharge below).
        helper.succeedOnTickWhen(9, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "2 excess PU didn't cut recipe duration to 8 ticks");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = 640L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "2 excess PU didn't reproduce a single vanilla OC step's total EU, instead of " + chargeNeeded +
                            " it used " + chargeUsed);
        });
    }

    /**
     * Verified property: past the duration floor, further Speed does nothing at all — duration and EU/t both
     * freeze rather than becoming a further penalty or bonus. Uses the 1-tick {@code test_overclock_logic_6}
     * recipe, where any speedPU >= 3 floors duration to 1 tick unconditionally (0.75^1 = 0.75 already rounds
     * to 1, and every higher speedPU only pushes the raw value further below 1). Compare this test's result
     * (speedPU=6) against {@link #powerDistributionDurationFloorHigherSpeedTest} (speedPU=7, still balanced) —
     * identical duration and EU/t on both proves nothing further changes once floored.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionDurationFloorTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 6, 6, 2, 2);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        // 1t to turn on, 1t to run the recipe (floored — raw duration would be under 1 tick)
        helper.succeedOnTickWhen(2, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "1-tick recipe didn't floor to 1 tick at speedPU=6");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.V[GTValues.LV];
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Floored duration changed EU/t, instead of " + chargeNeeded + " it used " + chargeUsed);
        });
    }

    /**
     * See {@link #powerDistributionDurationFloorTest} — same recipe, higher speedPU, must match exactly.
     * primaryPU is kept at 2 (not 1) deliberately — see {@link #powerDistributionBalancedClimbFlatEuTest}'s
     * note on why primaryPU=1 would make the item-stack assertion flaky for an unrelated reason.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionDurationFloorHigherSpeedTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 7, 7, 2, 0);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        // 1t to turn on, 1t to run the recipe (still floored — higher speedPU than
        // powerDistributionDurationFloorTest, must produce the identical result)
        helper.succeedOnTickWhen(2, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "1-tick recipe didn't floor to 1 tick at speedPU=7");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.V[GTValues.LV];
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Pushing speedPU past the floor changed EU/t, instead of " + chargeNeeded + " it used " +
                            chargeUsed);
        });
    }
}
