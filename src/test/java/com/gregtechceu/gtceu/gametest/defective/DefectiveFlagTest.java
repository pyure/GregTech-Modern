package com.gregtechceu.gtceu.gametest.defective;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Checks for the "defective" item flag (see {@link DefectiveFlag}), run against the real wiremill and
 * macerator-recycling recipes in a multiblock harness.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DefectiveFlagTest {

    private static final String BATCH = "DefectiveFlag";

    private static MetaMachine getMetaMachine(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return (MetaMachine) entity;
    }

    private record Rig(WorkableMultiblockMachine controller, ItemBusPartMachine in1, ItemBusPartMachine out1) {

        RecipeLogic logic() {
            return controller.getRecipeLogic();
        }

        NotifiableItemStackHandler in() {
            return in1.getInventory();
        }

        NotifiableItemStackHandler out() {
            return out1.getInventory();
        }
    }

    private static Rig rig(GameTestHelper helper, GTRecipeType type) {
        WorkableMultiblockMachine controller = (WorkableMultiblockMachine) getMetaMachine(
                helper.getBlockEntity(new BlockPos(1, 2, 0)));
        TestUtils.formMultiblock(helper, controller);
        controller.setRecipeType(type);
        return new Rig(controller,
                (ItemBusPartMachine) getMetaMachine(helper.getBlockEntity(new BlockPos(2, 1, 0))),
                (ItemBusPartMachine) getMetaMachine(helper.getBlockEntity(new BlockPos(0, 1, 0))));
    }

    private static ItemStack copperIngot() {
        return ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Copper);
    }

    private static ItemStack copperWire(int count) {
        return ChemicalHelper.get(TagPrefix.wireGtSingle, GTMaterials.Copper, count);
    }

    /** Runs the wiremill on one copper ingot (+circuit 1) and returns the rig after the output appeared. */
    private static Rig runWiremill(GameTestHelper helper, ItemStack ingot) {
        Rig rig = rig(helper, GTRecipeTypes.WIREMILL_RECIPES);
        helper.assertTrue(rig.controller.isFormed(), "multiblock did not form");
        rig.in().setStackInSlot(0, ingot);
        rig.in().setStackInSlot(1, IntCircuitBehaviour.stack(1));
        rig.logic().findAndHandleRecipe();
        helper.assertTrue(rig.logic().isActive(), "wiremill recipe did not start");
        for (int i = 0; i < 600 && rig.out().getStackInSlot(0).isEmpty(); i++) {
            rig.logic().serverTick();
        }
        helper.assertFalse(rig.out().getStackInSlot(0).isEmpty(), "wiremill produced nothing");
        return rig;
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void defectiveIngotMakesDefectiveWire(GameTestHelper helper) {
        Rig rig = runWiremill(helper, DefectiveFlag.mark(copperIngot()));
        ItemStack wire = rig.out().getStackInSlot(0);
        helper.assertTrue(wire.getItem() == copperWire(1).getItem(), "wrong output item: " + wire);
        helper.assertTrue(DefectiveFlag.isDefective(wire), "output of a defective input was not flagged: " + wire);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void cleanIngotMakesCleanWire(GameTestHelper helper) {
        Rig rig = runWiremill(helper, copperIngot());
        ItemStack wire = rig.out().getStackInSlot(0);
        helper.assertFalse(DefectiveFlag.isDefective(wire), "clean input produced a flagged output: " + wire);
        helper.assertTrue(wire.getTag() == null, "clean output has stray NBT: " + wire.getTag());
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void macerateRecyclingRejectsDefectiveWire(GameTestHelper helper) {
        Rig rig = rig(helper, GTRecipeTypes.MACERATOR_RECIPES);
        rig.in().setStackInSlot(0, DefectiveFlag.mark(copperWire(2)));
        rig.logic().findAndHandleRecipe();
        helper.assertFalse(rig.logic().isActive(), "macerator accepted a defective wire (recycling not blocked)");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void macerateRecyclingStillAcceptsCleanWire(GameTestHelper helper) {
        Rig rig = rig(helper, GTRecipeTypes.MACERATOR_RECIPES);
        rig.in().setStackInSlot(0, copperWire(2));
        rig.logic().findAndHandleRecipe();
        helper.assertTrue(rig.logic().isActive(), "macerator no longer accepts a clean wire");
        ItemStack left = rig.in().getStackInSlot(0);
        helper.assertTrue(left.getCount() == 1, "expected 1 wire left, got " + left);
        helper.assertTrue(left.getTag() == null,
                "NBT-predicate matching left stray NBT on a clean input stack: " + left.getTag());
        helper.succeed();
    }

    // ---------------- crafting table ----------------

    private static net.minecraft.world.inventory.CraftingContainer grid(
                                                                        net.minecraft.world.item.crafting.Recipe<?> recipe,
                                                                        boolean flagged) {
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {

            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        };
        var container = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        int slot = 0;
        for (var ingredient : recipe.getIngredients()) {
            if (!ingredient.isEmpty() && ingredient.getItems().length > 0 && slot < 9) {
                ItemStack stack = ingredient.getItems()[0].copy();
                container.setItem(slot, flagged ? DefectiveFlag.mark(stack) : stack);
            }
            slot++;
        }
        return container;
    }

    private static net.minecraft.world.item.crafting.CraftingRecipe firstCraftingRecipe(GameTestHelper helper,
                                                                                        Class<?> exactClass) {
        for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (recipe.getClass() == exactClass &&
                    recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe c &&
                    !c.getIngredients().isEmpty() && c.getIngredients().size() <= 9) {
                return c;
            }
        }
        throw new IllegalStateException("no " + exactClass.getSimpleName() + " found");
    }

    private static void craftingCase(GameTestHelper helper, Class<?> recipeClass) {
        var recipe = firstCraftingRecipe(helper, recipeClass);
        var access = helper.getLevel().registryAccess();
        ItemStack dirty = recipe.assemble(grid(recipe, true), access);
        helper.assertTrue(DefectiveFlag.isDefective(dirty), recipeClass.getSimpleName() + " " + recipe.getId() +
                ": defective ingredient did not flag the result: " + dirty);
        ItemStack clean = recipe.assemble(grid(recipe, false), access);
        helper.assertFalse(DefectiveFlag.isDefective(clean),
                recipeClass.getSimpleName() + " " + recipe.getId() + ": clean ingredients produced a flagged result");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shapedCraftingPropagates(GameTestHelper helper) {
        craftingCase(helper, net.minecraft.world.item.crafting.ShapedRecipe.class);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shapelessCraftingPropagates(GameTestHelper helper) {
        craftingCase(helper, net.minecraft.world.item.crafting.ShapelessRecipe.class);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void gtEnergyTransferCraftingPropagates(GameTestHelper helper) {
        craftingCase(helper, com.gregtechceu.gtceu.api.recipe.ShapedEnergyTransferRecipe.class);
    }

    // ---------------- vanilla furnace ----------------

    private static final BlockPos FURNACE_POS = new BlockPos(0, 1, 0);

    private static net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace(GameTestHelper helper,
                                                                                             ItemStack input) {
        helper.setBlock(FURNACE_POS, net.minecraft.world.level.block.Blocks.FURNACE);
        var be = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper
                .getBlockEntity(FURNACE_POS);
        be.setItem(0, input);
        be.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL));
        return be;
    }

    private static void smelt(GameTestHelper helper,
                              net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) {
            net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.serverTick(helper.getLevel(),
                    be.getBlockPos(), be.getBlockState(), be);
        }
    }

    private static ItemStack copperDust() {
        return ChemicalHelper.get(TagPrefix.dust, GTMaterials.Copper);
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void furnaceFlagsDefectiveOutput(GameTestHelper helper) {
        var be = furnace(helper, DefectiveFlag.mark(copperDust()));
        smelt(helper, be, 260);
        ItemStack out = be.getItem(2);
        helper.assertFalse(out.isEmpty(), "furnace produced nothing from copper dust (no smelting recipe?)");
        helper.assertTrue(DefectiveFlag.isDefective(out), "furnace output of a defective input is not flagged: " + out);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void furnaceKeepsCleanOutputClean(GameTestHelper helper) {
        var be = furnace(helper, copperDust());
        smelt(helper, be, 260);
        ItemStack out = be.getItem(2);
        helper.assertFalse(out.isEmpty(), "furnace produced nothing from copper dust");
        helper.assertFalse(DefectiveFlag.isDefective(out), "clean input produced a flagged output");
        helper.assertTrue(out.getTag() == null, "clean furnace output has stray NBT: " + out.getTag());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void furnaceRefusesToMergeDefectiveIntoCleanStack(GameTestHelper helper) {
        var probe = furnace(helper, copperDust());
        smelt(helper, probe, 260);
        ItemStack cleanIngot = probe.getItem(2).copy();
        helper.assertFalse(cleanIngot.isEmpty(), "probe furnace produced nothing");
        var be = furnace(helper, DefectiveFlag.mark(copperDust()));
        be.setItem(2, cleanIngot.copy());
        smelt(helper, be, 260);
        ItemStack out = be.getItem(2);
        helper.assertTrue(out.getCount() == cleanIngot.getCount() && !DefectiveFlag.isDefective(out),
                "defective result merged into a clean stack: " + out);
        helper.assertTrue(DefectiveFlag.isDefective(be.getItem(0)), "defective input should still be waiting");
        helper.succeed();
    }

    // ---------------- GT electric furnace (proxied vanilla smelting recipes) ----------------

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void electricFurnaceFlagsDefectiveOutput(GameTestHelper helper) {
        Rig rig = rig(helper, GTRecipeTypes.FURNACE_RECIPES);
        rig.in().setStackInSlot(0, DefectiveFlag.mark(copperDust()));
        rig.logic().findAndHandleRecipe();
        helper.assertTrue(rig.logic().isActive(), "electric furnace did not pick up a smelting recipe for copper dust");
        for (int i = 0; i < 600 && rig.out().getStackInSlot(0).isEmpty(); i++) {
            rig.logic().serverTick();
        }
        ItemStack out = rig.out().getStackInSlot(0);
        helper.assertFalse(out.isEmpty(), "electric furnace produced nothing");
        helper.assertTrue(DefectiveFlag.isDefective(out), "electric furnace output not flagged: " + out);
        helper.succeed();
    }

    // ---------------- tool-head replacement and tool repair ----------------

    private static ItemStack electricDrill() {
        return com.gregtechceu.gtceu.common.data.GTMaterialItems.TOOL_ITEMS
                .get(GTMaterials.Steel, com.gregtechceu.gtceu.api.item.tool.GTToolType.DRILL_LV).get()
                .get(100_000L, 100_000L);
    }

    private static net.minecraft.world.inventory.CraftingContainer gridOf(ItemStack... stacks) {
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {

            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                return true;
            }
        };
        var container = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < stacks.length; i++) container.setItem(i, stacks[i]);
        return container;
    }

    private static net.minecraft.world.item.crafting.CraftingRecipe recipeOfClass(GameTestHelper helper,
                                                                                  Class<?> exactClass) {
        for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
            if (recipe.getClass() == exactClass &&
                    recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe c) {
                return c;
            }
        }
        throw new IllegalStateException("no recipe of class " + exactClass.getSimpleName());
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void toolHeadReplacementPropagates(GameTestHelper helper) {
        var recipe = recipeOfClass(helper, com.gregtechceu.gtceu.api.recipe.ToolHeadReplaceRecipe.class);
        var access = helper.getLevel().registryAccess();
        ItemStack head = ChemicalHelper.get(TagPrefix.toolHeadDrill, GTMaterials.Iron);

        ItemStack dirty = recipe.assemble(gridOf(electricDrill(), DefectiveFlag.mark(head.copy())), access);
        helper.assertFalse(dirty.isEmpty(), "tool head replacement produced nothing; check the test's stacks");
        helper.assertTrue(DefectiveFlag.isDefective(dirty), "defective head did not flag the new tool: " + dirty);

        ItemStack dirtyTool = recipe.assemble(gridOf(DefectiveFlag.mark(electricDrill()), head.copy()), access);
        helper.assertTrue(DefectiveFlag.isDefective(dirtyTool), "defective tool did not flag the new tool");

        ItemStack clean = recipe.assemble(gridOf(electricDrill(), head.copy()), access);
        helper.assertFalse(clean.isEmpty(), "clean tool head replacement produced nothing");
        helper.assertFalse(DefectiveFlag.isDefective(clean), "clean inputs produced a flagged tool");

        // A grid with no result (only one stack) returns empty without throwing and flags nothing.
        ItemStack none = recipe.assemble(gridOf(DefectiveFlag.mark(electricDrill())), access);
        helper.assertTrue(none.isEmpty(), "a grid with no result should be empty, got " + none);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void toolRepairPropagates(GameTestHelper helper) {
        var recipe = recipeOfClass(helper, net.minecraft.world.item.crafting.RepairItemRecipe.class);
        var access = helper.getLevel().registryAccess();

        ItemStack a = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        a.setDamageValue(100);
        ItemStack b = a.copy();

        ItemStack dirty = recipe.assemble(gridOf(DefectiveFlag.mark(a.copy()), b.copy()), access);
        helper.assertFalse(dirty.isEmpty(), "repair produced nothing; check the test's stacks");
        helper.assertTrue(DefectiveFlag.isDefective(dirty), "repair with a defective tool was not flagged: " + dirty);

        ItemStack clean = recipe.assemble(gridOf(a.copy(), b.copy()), access);
        helper.assertFalse(clean.isEmpty(), "clean repair produced nothing");
        helper.assertFalse(DefectiveFlag.isDefective(clean), "clean repair produced a flagged tool");

        // Existing rule unchanged: two electric tools cannot be repaired together.
        helper.assertFalse(recipe.matches(gridOf(electricDrill(), electricDrill()), helper.getLevel()),
                "electric tools can now be repaired together");
        helper.succeed();
    }

    // ---------------- fluid refusal rule ----------------

    private static GTRecipeType FLUID_RULE_TYPE;

    @net.minecraft.gametest.framework.BeforeBatch(batch = BATCH)
    public static void prepareFluidRuleRecipes(net.minecraft.server.level.ServerLevel level) {
        FLUID_RULE_TYPE = TestUtils.createRecipeType("defective_fluid_rule", 2, 2, 2, 2);
        long eut = com.gregtechceu.gtceu.api.GTValues.VA[com.gregtechceu.gtceu.api.GTValues.HV];
        FLUID_RULE_TYPE.getAdditionHandler().beginStaging();
        // copper dust -> molten copper (solid in liquid form)
        FLUID_RULE_TYPE.getAdditionHandler().addStaging(FLUID_RULE_TYPE
                .recipeBuilder(GTCEu.id("defective_rule_solid"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Copper))
                .outputFluids(GTMaterials.Copper.getFluid(144))
                .EUt(eut).duration(1).buildRawRecipe());
        // tin dust -> iron ingot + oxygen (gas: allowed)
        FLUID_RULE_TYPE.getAdditionHandler().addStaging(FLUID_RULE_TYPE
                .recipeBuilder(GTCEu.id("defective_rule_gas"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Tin))
                .outputItems(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Iron))
                .outputFluids(GTMaterials.Oxygen.getFluid(1000))
                .EUt(eut).duration(1).buildRawRecipe());
        // nickel dust -> ranged molten copper (ranged output must not throw)
        FLUID_RULE_TYPE.getAdditionHandler().addStaging(FLUID_RULE_TYPE
                .recipeBuilder(GTCEu.id("defective_rule_ranged"))
                .inputItems(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Nickel))
                .outputFluidsRanged(GTMaterials.Copper.getFluid(144),
                        net.minecraft.util.valueproviders.UniformInt.of(100, 200))
                .EUt(eut).duration(1).buildRawRecipe());
        FLUID_RULE_TYPE.getAdditionHandler().completeStaging();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void solidInLiquidFormClassification(GameTestHelper helper) {
        var plasma = GTMaterials.Iron.getFluid(com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys.PLASMA);
        helper.assertTrue(DefectiveFlag.isSolidInLiquidForm(GTMaterials.Copper.getFluid()), "molten copper");
        helper.assertTrue(DefectiveFlag.isSolidInLiquidForm(GTMaterials.Cupronickel.getFluid()), "cupronickel liquid");
        helper.assertFalse(DefectiveFlag.isSolidInLiquidForm(GTMaterials.Water.getFluid()), "water");
        helper.assertFalse(DefectiveFlag.isSolidInLiquidForm(GTMaterials.Oxygen.getFluid()), "oxygen");
        helper.assertFalse(DefectiveFlag.isSolidInLiquidForm(GTMaterials.HydrochloricAcid.getFluid()),
                "hydrochloric acid");
        helper.assertFalse(DefectiveFlag.isSolidInLiquidForm(GTMaterials.Helium.getFluid()), "helium");
        helper.assertTrue(plasma != null, "test premise: iron has a plasma fluid");
        helper.assertFalse(DefectiveFlag.isSolidInLiquidForm(plasma), "iron plasma");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void defectiveItemRefusedWhenRecipeMakesMoltenFluid(GameTestHelper helper) {
        Rig rig = rig(helper, FLUID_RULE_TYPE);
        rig.in().setStackInSlot(0, DefectiveFlag.mark(copperDust()));
        rig.logic().findAndHandleRecipe();
        helper.assertFalse(rig.logic().isActive(), "a defective item was accepted by a recipe making molten fluid");
        helper.assertTrue(rig.in().getStackInSlot(0).getCount() == 1, "the refused stack was touched");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void cleanItemStillMakesMoltenFluid(GameTestHelper helper) {
        Rig rig = rig(helper, FLUID_RULE_TYPE);
        rig.in().setStackInSlot(0, copperDust());
        rig.logic().findAndHandleRecipe();
        helper.assertTrue(rig.logic().isActive(), "a clean item no longer runs the molten fluid recipe");
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void defectiveItemAllowedWhenRecipeMakesGas(GameTestHelper helper) {
        Rig rig = rig(helper, FLUID_RULE_TYPE);
        rig.in().setStackInSlot(0, DefectiveFlag.mark(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Tin)));
        rig.logic().findAndHandleRecipe();
        helper.assertTrue(rig.logic().isActive(), "a defective item was refused by a recipe making only gas");
        for (int i = 0; i < 10 && rig.out().getStackInSlot(0).isEmpty(); i++) rig.logic().serverTick();
        ItemStack out = rig.out().getStackInSlot(0);
        helper.assertFalse(out.isEmpty(), "the gas recipe produced no item");
        helper.assertTrue(DefectiveFlag.isDefective(out),
                "the item output of a defective input is not flagged: " + out);
        helper.succeed();
    }

    @GameTest(template = "lcr_input_separation", batch = BATCH)
    public static void rangedFluidOutputIsHandledWithoutThrowing(GameTestHelper helper) {
        Rig dirty = rig(helper, FLUID_RULE_TYPE);
        dirty.in().setStackInSlot(0, DefectiveFlag.mark(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Nickel)));
        dirty.logic().findAndHandleRecipe();
        helper.assertFalse(dirty.logic().isActive(), "a defective item was accepted by a ranged molten fluid recipe");
        dirty.in().setStackInSlot(0, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Nickel));
        dirty.logic().findAndHandleRecipe();
        helper.assertTrue(dirty.logic().isActive(), "a clean item no longer runs the ranged fluid recipe");
        helper.succeed();
    }

    // ---------------- other callers of the shared item handler ----------------

    /**
     * The static item handler is also used by the miner's {@code ItemRecipeHandler}. A recipe without the
     * recycling flag or a solid-fluid output must treat a defective stack exactly like a clean one there.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void sharedHandlerCallersIgnoreTheFlag(GameTestHelper helper) {
        var recipe = GTRecipeTypes.WIREMILL_RECIPES.recipeBuilder(GTCEu.id("defective_shared_handler"))
                .inputItems(copperIngot())
                .outputItems(copperWire(2))
                .EUt(com.gregtechceu.gtceu.api.GTValues.VA[com.gregtechceu.gtceu.api.GTValues.LV]).duration(1)
                .buildRawRecipe();
        for (boolean defective : new boolean[] { false, true }) {
            var handler = new com.gregtechceu.gtceu.api.misc.ItemRecipeHandler(
                    com.gregtechceu.gtceu.api.capability.recipe.IO.IN, 1, null);
            handler.storage.setStackInSlot(0, defective ? DefectiveFlag.mark(copperIngot()) : copperIngot());
            var left = new java.util.ArrayList<net.minecraft.world.item.crafting.Ingredient>();
            for (var content : recipe.inputs
                    .get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)) {
                left.add(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP.of(content.content()));
            }
            var simulated = handler.handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO.IN, recipe,
                    new java.util.ArrayList<>(left), true);
            helper.assertTrue(simulated.isEmpty(), (defective ? "defective" : "clean") +
                    " stack was not accepted by the shared handler in simulation");
            var executed = handler.handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO.IN, recipe,
                    new java.util.ArrayList<>(left), false);
            helper.assertTrue(executed.isEmpty() && handler.storage.getStackInSlot(0).isEmpty(),
                    (defective ? "defective" : "clean") + " stack was not consumed by the shared handler");
        }
        helper.succeed();
    }
    // ---------------- upstream interactions ----------------

    /**
     * Upstream's item handler accepts a null recipe (callers that only move items). A null recipe cannot be a
     * recycling recipe, so a defective stack must pass the handler's input path and the refusal helpers must not
     * throw on it.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void nullRecipeIsNeverRefused(GameTestHelper helper) {
        ItemStack defective = DefectiveFlag.mark(copperIngot());
        helper.assertFalse(DefectiveFlag.rejects(null, defective), "a null recipe refused a defective stack");
        helper.assertFalse(DefectiveFlag.hasSolidInLiquidFormOutput(null), "a null recipe has a solid-fluid output");

        var recipe = GTRecipeTypes.WIREMILL_RECIPES.recipeBuilder(GTCEu.id("defective_null_recipe"))
                .inputItems(copperIngot())
                .outputItems(copperWire(2))
                .EUt(com.gregtechceu.gtceu.api.GTValues.VA[com.gregtechceu.gtceu.api.GTValues.LV]).duration(1)
                .buildRawRecipe();
        var handler = new com.gregtechceu.gtceu.api.misc.ItemRecipeHandler(
                com.gregtechceu.gtceu.api.capability.recipe.IO.IN, 1, null);
        handler.storage.setStackInSlot(0, defective);
        var left = new java.util.ArrayList<net.minecraft.world.item.crafting.Ingredient>();
        for (var content : recipe.inputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)) {
            left.add(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP.of(content.content()));
        }
        var simulated = handler.handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO.IN, null,
                new java.util.ArrayList<>(left), true);
        helper.assertTrue(simulated.isEmpty(), "the handler refused a defective stack when given a null recipe");
        var executed = handler.handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO.IN, null,
                new java.util.ArrayList<>(left), false);
        helper.assertTrue(executed.isEmpty() && handler.storage.getStackInSlot(0).isEmpty(),
                "the defective stack was not consumed when given a null recipe");
        helper.succeed();
    }

    /**
     * Spoilage updates run over item handlers and write their own state onto stacks. A flagged stack must come out
     * of {@code SpoilUtils.updateHandler} with its count and flag intact (a plain material stack with no other NBT at
     * all), and two flagged stacks must still merge afterwards, also for a spoilable item that does carry spoilage
     * state.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void spoilageUpdateLeavesFlaggedStacksAlone(GameTestHelper helper) {
        var level = helper.getLevel();
        var handler = new com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler(3);
        ItemStack ingots = DefectiveFlag.mark(copperIngot());
        ingots.setCount(3);
        handler.setStackInSlot(0, ingots);
        ItemStack spoilable = DefectiveFlag.mark(
                com.gregtechceu.gtceu.common.data.GTItems.SPOILABLE_1.get().getDefaultInstance().copyWithCount(4));
        handler.setStackInSlot(1, spoilable);

        com.gregtechceu.gtceu.api.item.component.SpoilUtils.updateHandler(handler, level, null, null);

        ItemStack after = handler.getStackInSlot(0);
        helper.assertTrue(after.getCount() == 3 && DefectiveFlag.isDefective(after),
                "update changed a flagged material stack: " + after);
        helper.assertTrue(after.getTag() != null && after.getTag().size() == 1,
                "update added NBT to a flagged material stack: " + after.getTag());
        ItemStack afterSpoilable = handler.getStackInSlot(1);
        helper.assertTrue(afterSpoilable.getCount() == 4 && DefectiveFlag.isDefective(afterSpoilable) &&
                afterSpoilable.getItem() == spoilable.getItem(),
                "update changed or spoiled a flagged spoilable stack: " + afterSpoilable);

        ItemStack more = DefectiveFlag.mark(copperIngot());
        helper.assertTrue(handler.insertItem(0, more, false).isEmpty(), "second flagged stack did not merge");
        helper.assertTrue(handler.getStackInSlot(0).getCount() == 4, "merged count is wrong after the update");
        ItemStack moreSpoilable = DefectiveFlag.mark(
                com.gregtechceu.gtceu.common.data.GTItems.SPOILABLE_1.get().getDefaultInstance());
        com.gregtechceu.gtceu.api.item.component.SpoilUtils.updateHandler(
                new com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler(
                        net.minecraft.core.NonNullList.of(ItemStack.EMPTY, moreSpoilable)),
                level, null, null);
        helper.assertTrue(
                net.minecraftforge.items.ItemHandlerHelper.canItemStacksStack(handler.getStackInSlot(1),
                        moreSpoilable),
                "two flagged spoilable stacks no longer stack after the spoilage update");
        helper.succeed();
    }
}
