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
        PowerDistributionConfig pd = machine.getTrait(PowerDistributionTrait.TYPE).getPowerDistribution();
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
        // NOT a no-op above LV: speedDelta = 6-2 = 4, so rawDuration = 16 * 0.75^4 = 5.0625, floored/rounded to 5
        // ticks (1t to turn on, 5t to run). Since the default is balanced (speedPU==tuningPU), total EU per craft
        // is preserved exactly: eut = 120 * (16/5) = 384, chargeUsed = 384*5 = 1920 = 120*16, matching
        // powerDistributionBaselineTest's 1920 via the same flat-total-EU property.
        // Originally used test_overclock_logic_4 (EUt=V[HV]=512) and asserted unmodified duration/EU/t at "default"
        // — both wrong once Power Distribution existed: (1) 512 > VA[HV]=480 violates PD_AWARE_OC's voltage
        // ceiling even at an unmodified multiplier, and (2) the machine's default is not the formula's neutral
        // baseline for any tier above LV. Switched to test_overclock_logic_7 (EUt=VA[MV]=120) to fix both.
        helper.succeedOnTickWhen(6, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(
                    itemOut.getStackInSlot(0),
                    new ItemStack(Blocks.STONE, 1)),
                    "Singleblock CR didn't run recipe in correct time");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.VA[GTValues.MV] * 16L;
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
     * Verified property: a balanced Speed=Tuning climb preserves total EU per craft exactly. speedPU=tuningPU=7
     * on a 16-tick/VA[MV]-EUt recipe gives duration=4 (round(16 * 0.75^5) = round(3.796875) = 4) and
     * eut=480 (matchedEuMultiplier = 16/4 = 4 exactly, imbalance = 1 since balanced; 120*4=480=VA[HV] exactly,
     * at the ceiling, not over it) — chosen specifically because both land on exact powers of 2, so
     * 4 * 480 == 16 * 120 with zero floating-point risk. primaryPU is kept at 2 (not 1) deliberately —
     * {@code PowerDistributionConfig.primaryMultiplier} halves a primaryPU=1 guaranteed output into a 50%-chance
     * output (from the separate Primary/Byproduct output-split feature), which would make this test's item-stack
     * assertion flaky for a reason unrelated to what it's actually verifying.
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
        // 1t to turn on, 4t to run the recipe (duration floored/rounded down from 16 to 4)
        helper.succeedOnTickWhen(5, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "Balanced climb didn't cut recipe duration to 4 ticks");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.VA[GTValues.MV] * 16L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "Balanced climb didn't preserve total EU, instead of " + chargeNeeded + " it used " +
                            chargeUsed);
        });
    }

    /**
     * Verified property: each point of imbalance (speedPU - tuningPU) exactly doubles total EU. Holding
     * speedPU at the literal baseline of 2 (so duration stays unmodified at 16 ticks, matchedEuMultiplier = 1
     * exactly) and dropping tuningPU to 0 gives euDelta = 2, so imbalanceEuMultiplier = 2^2 = 4 exactly —
     * eut = 480 (= VA[HV], at the ceiling not over it), four times {@link #powerDistributionBaselineTest}'s
     * 120, with zero floating-point risk since duration never changes.
     */
    @GameTest(template = "singleblock_charged_cr", batch = "OverclockLogic")
    public static void powerDistributionImbalanceDoublingTest(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) helper.getBlockEntity(new BlockPos(0, 1, 0));
        assert machine != null;
        machine.setRecipeType(CR_RECIPE_TYPE);
        setPowerDistribution(machine, 0, 2, 7, 7);
        NotifiableEnergyContainer energyContainer = (NotifiableEnergyContainer) machine
                .getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemIn = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP).get(0);
        NotifiableItemStackHandler itemOut = (NotifiableItemStackHandler) machine
                .getCapabilitiesFlat(IO.OUT, ItemRecipeCapability.CAP).get(0);

        long originalCharge = GTValues.V[GTValues.HV] * 64L;
        itemIn.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        // 1t to turn on, 16t to run the recipe (duration unmodified, only EU/t changes)
        helper.succeedOnTickWhen(17, () -> {
            helper.assertTrue(TestUtils.isItemStackEqual(itemOut.getStackInSlot(0), new ItemStack(Blocks.STONE, 1)),
                    "2 points of imbalance changed recipe duration, should only affect EU/t");
            long chargeUsed = originalCharge - energyContainer.getEnergyStored();
            long chargeNeeded = GTValues.VA[GTValues.MV] * 16L * 4L;
            helper.assertTrue(chargeUsed == chargeNeeded,
                    "2 points of imbalance didn't quadruple total EU, instead of " + chargeNeeded + " it used " +
                            chargeUsed);
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
