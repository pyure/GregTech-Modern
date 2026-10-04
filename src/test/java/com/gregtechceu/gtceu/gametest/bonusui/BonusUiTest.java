package com.gregtechceu.gtceu.gametest.bonusui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.DefectiveBonus;
import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BonusUiTest {

    private static final String BATCH = "BonusUi";
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private static SimpleTieredMachine place(GameTestHelper helper, MachineDefinition definition) {
        return (SimpleTieredMachine) TestUtils.setMachine(helper, POS, definition);
    }

    private static void dials(SimpleTieredMachine machine, int tuning, int speed, int primary, int byproduct) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tuningPU", tuning);
        tag.putInt("speedPU", speed);
        tag.putInt("primaryPU", primary);
        tag.putInt("byproductPU", byproduct);
        machine.getPowerDistribution().deserializeNBT(tag);
    }

    private static GTRecipe recipe(GameTestHelper helper, String id) {
        var found = helper.getLevel().getServer().getRecipeManager().byKey(GTCEu.id(id));
        helper.assertTrue(found.isPresent() && found.get() instanceof GTRecipe, "test premise: recipe " + id);
        return (GTRecipe) found.get();
    }

    private static ItemStack flagged(TagPrefix prefix, Material material) {
        return DefectiveFlag.mark(ChemicalHelper.get(prefix, material, 1));
    }

    private static boolean hasRefusalReason(SimpleTieredMachine machine) {
        for (Component reason : machine.getRecipeLogic().getFailureReasonMap().values()) {
            if (reason.getContents() instanceof TranslatableContents t &&
                    t.getKey().equals(DefectiveFlag.REFUSAL_LANG_KEY)) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void everyDefectiveItemReadsDefective(GameTestHelper helper) {
        for (TagPrefix prefix : new TagPrefix[] { TagPrefix.dust, TagPrefix.dustSmall, TagPrefix.dustTiny,
                TagPrefix.ingot, TagPrefix.plate, TagPrefix.wireGtSingle,
                TagPrefix.rod, TagPrefix.gear }) {
            helper.assertTrue(DefectiveFlag.LABEL_DEFECTIVE_LANG_KEY.equals(
                    DefectiveFlag.labelKey(flagged(prefix, GTMaterials.Steel))),
                    prefix.name + " should read defective");
        }
        helper.assertTrue(DefectiveFlag.labelKey(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Steel, 1)) == null,
                "a clean ingot got a label");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void refusalReasonAppearsOnlyForARefusedStack(GameTestHelper helper) {
        GTRecipe macerate = null;
        ItemStack wire = ChemicalHelper.get(TagPrefix.wireGtSingle, GTMaterials.Steel, 1);
        for (GTRecipe r : helper.getLevel().getServer().getRecipeManager()
                .getAllRecipesFor(GTRecipeTypes.MACERATOR_RECIPES)) {
            if (r.data != null && r.data.getBoolean(DefectiveFlag.NO_DEFECTIVE_INPUT) &&
                    r.id.getPath().equals("macerator/macerate_steel_single_wire")) {
                macerate = r;
                break;
            }
        }
        helper.assertTrue(macerate != null, "test premise: a recycling recipe for the steel wire");

        SimpleTieredMachine machine = place(helper, GTMachines.MACERATOR[GTValues.HV]);
        dials(machine, 7, 2, 7, 0);
        machine.importItems.setStackInSlot(0, DefectiveFlag.mark(wire.copy()));
        machine.getRecipeLogic().checkMatchedRecipeAvailable(macerate);
        helper.assertTrue(hasRefusalReason(machine), "a refused defective wire left no refusal reason; reasons: " +
                machine.getRecipeLogic().getFailureReasonMap().values());

        machine = place(helper, GTMachines.MACERATOR[GTValues.HV]);
        dials(machine, 7, 2, 7, 0);
        machine.importItems.setStackInSlot(0, wire.copy());
        machine.getRecipeLogic().checkMatchedRecipeAvailable(macerate);
        helper.assertFalse(hasRefusalReason(machine), "a clean wire produced a refusal reason");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void otherFailuresKeepTheirOwnReason(GameTestHelper helper) {
        // clean steel ingots in a wiremill with no programmed circuit: the recipe fails, but not because of a refusal
        GTRecipe wire = recipe(helper, "wiremill/mill_steel_wire");
        SimpleTieredMachine machine = place(helper, GTMachines.WIREMILL[GTValues.HV]);
        dials(machine, 7, 2, 7, 0);
        machine.importItems.setStackInSlot(0, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Steel, 4));
        machine.getRecipeLogic().checkMatchedRecipeAvailable(wire);
        helper.assertFalse(hasRefusalReason(machine), "an unrelated failure was reported as a refusal");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void modifiedRecipeCarriesItsBonusKind(GameTestHelper helper) {
        GTRecipe wire = recipe(helper, "wiremill/mill_steel_wire");
        SimpleTieredMachine wiremill = place(helper, GTMachines.WIREMILL[GTValues.HV]);
        dials(wiremill, 7, 2, 7, 0);
        GTRecipe modified = wiremill.fullModifyRecipe(wire.copy());
        helper.assertTrue(modified != null && DefectiveBonus.KIND_DEFECTIVE.equals(
                modified.data.getString(DefectiveBonus.KIND_KEY)), "the wiremill recipe should carry 'defective'");

        GTRecipe clay = recipe(helper, "extractor/clay_extraction");
        SimpleTieredMachine extractor = place(helper, GTMachines.EXTRACTOR[GTValues.HV]);
        dials(extractor, 7, 2, 7, 0);
        modified = extractor.fullModifyRecipe(clay.copy());
        helper.assertTrue(modified != null && DefectiveBonus.KIND_NONE.equals(
                modified.data.getString(DefectiveBonus.KIND_KEY)), "clay extraction should carry 'none'");

        GTRecipe slab = recipe(helper, "cutter/oak_slab");
        SimpleTieredMachine cutter = place(helper, GTMachines.CUTTER[GTValues.HV]);
        dials(cutter, 7, 2, 7, 0);
        modified = cutter.fullModifyRecipe(slab.copy());
        helper.assertTrue(modified != null && DefectiveBonus.KIND_CLEAN.equals(
                modified.data.getString(DefectiveBonus.KIND_KEY)), "the cutter slab recipe should carry 'clean'");

        for (GTRecipe original : new GTRecipe[] { wire, clay, slab }) {
            helper.assertFalse(original.data.contains(DefectiveBonus.KIND_KEY),
                    original.id + " (registry recipe) was marked");
        }
        helper.succeed();
    }
}
