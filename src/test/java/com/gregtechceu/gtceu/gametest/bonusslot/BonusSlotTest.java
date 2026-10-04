package com.gregtechceu.gtceu.gametest.bonusslot;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Checks for the extra output slot (see {@link SimpleTieredMachine#insertBonusOutput}). */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class BonusSlotTest {

    private static final String BATCH = "BonusSlot";
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private static Map<String, MachineDefinition[]> withExtraSlot() {
        Map<String, MachineDefinition[]> m = new LinkedHashMap<>();
        m.put("extruder", GTMachines.EXTRUDER);
        m.put("fluid_solidifier", GTMachines.FLUID_SOLIDIFIER);
        m.put("alloy_smelter", GTMachines.ALLOY_SMELTER);
        m.put("bender", GTMachines.BENDER);
        m.put("compressor", GTMachines.COMPRESSOR);
        m.put("wiremill", GTMachines.WIREMILL);
        m.put("laser_engraver", GTMachines.LASER_ENGRAVER);
        m.put("forge_hammer", GTMachines.FORGE_HAMMER);
        m.put("electric_furnace", GTMachines.ELECTRIC_FURNACE);
        m.put("assembler", GTMachines.ASSEMBLER);
        m.put("forming_press", GTMachines.FORMING_PRESS);
        m.put("polarizer", GTMachines.POLARIZER);
        m.put("mixer", GTMachines.MIXER);
        m.put("distillery", GTMachines.DISTILLERY);
        return m;
    }

    private static Map<String, MachineDefinition[]> withoutExtraSlot() {
        Map<String, MachineDefinition[]> m = new LinkedHashMap<>();
        m.put("macerator", GTMachines.MACERATOR);
        m.put("packer", GTMachines.PACKER);
        m.put("lathe", GTMachines.LATHE);
        m.put("centrifuge", GTMachines.CENTRIFUGE);
        m.put("electrolyzer", GTMachines.ELECTROLYZER);
        m.put("extractor", GTMachines.EXTRACTOR);
        m.put("chemical_reactor", GTMachines.CHEMICAL_REACTOR);
        return m;
    }

    private static SimpleTieredMachine place(GameTestHelper helper, MachineDefinition definition) {
        return (SimpleTieredMachine) TestUtils.setMachine(helper, POS, definition);
    }

    private static SimpleTieredMachine lvWiremill(GameTestHelper helper) {
        return place(helper, GTMachines.WIREMILL[GTValues.LV]);
    }

    private static ItemStack wire(int count, boolean defective) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.wireGtSingle, GTMaterials.Steel, count);
        return defective ? DefectiveFlag.mark(stack) : stack;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void slotCountsMatchTheApprovedList(GameTestHelper helper) {
        for (var entry : withExtraSlot().entrySet()) {
            for (MachineDefinition def : entry.getValue()) {
                if (def == null) continue;
                SimpleTieredMachine machine = place(helper, def);
                int typeMax = machine.getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP);
                helper.assertTrue(typeMax == 1,
                        entry.getKey() + " recipe type now declares " + typeMax + " item outputs");
                helper.assertTrue(machine.exportItems.getSlots() == typeMax + 1,
                        entry.getKey() + " tier " + machine.getTier() + " has " + machine.exportItems.getSlots() +
                                " output slots, expected " + (typeMax + 1));
            }
        }
        for (var entry : withoutExtraSlot().entrySet()) {
            for (MachineDefinition def : entry.getValue()) {
                if (def == null) continue;
                SimpleTieredMachine machine = place(helper, def);
                int typeMax = machine.getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP);
                helper.assertTrue(machine.exportItems.getSlots() == typeMax,
                        entry.getKey() + " tier " + machine.getTier() + " has " + machine.exportItems.getSlots() +
                                " output slots, expected " + typeMax + " (no extra slot)");
            }
        }
        // steam variants share the recipe types but are a different class: unchanged
        for (var def : new MachineDefinition[] { GTMachines.STEAM_MACERATOR.left(),
                GTMachines.STEAM_EXTRACTOR.left() }) {
            var machine = (com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine) TestUtils.setMachine(helper,
                    POS, def);
            int typeMax = machine.getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP);
            helper.assertTrue(machine.exportItems.getSlots() == typeMax, "a steam machine gained an output slot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bonusLandsInTheExtraSlot(GameTestHelper helper) {
        SimpleTieredMachine machine = lvWiremill(helper);
        machine.exportItems.setStackInSlot(0, wire(10, false));
        helper.assertTrue(machine.insertBonusOutput(wire(2, true)).isEmpty(), "bonus did not fit");
        helper.assertTrue(machine.exportItems.getStackInSlot(1).getCount() == 2 &&
                DefectiveFlag.isDefective(machine.exportItems.getStackInSlot(1)), "bonus not in the extra slot");
        helper.assertTrue(machine.insertBonusOutput(wire(3, true)).isEmpty(), "second bonus did not fit");
        helper.assertTrue(machine.exportItems.getStackInSlot(1).getCount() == 5, "bonus stacks did not merge");
        ItemStack first = machine.exportItems.getStackInSlot(0);
        helper.assertTrue(first.getCount() == 10 && !DefectiveFlag.isDefective(first), "the normal slot changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void fullSlotsAreDiscardedWithoutBlocking(GameTestHelper helper) {
        SimpleTieredMachine machine = lvWiremill(helper);
        machine.exportItems.setStackInSlot(0, wire(64, false));
        machine.exportItems.setStackInSlot(1, wire(64, false));
        var statusBefore = machine.getRecipeLogic().getStatus();
        ItemStack discarded = machine.insertBonusOutput(wire(4, true));
        helper.assertTrue(discarded.getCount() == 4, "the unplaced bonus should be reported as discarded");
        helper.assertTrue(machine.exportItems.getStackInSlot(0).getCount() == 64 &&
                machine.exportItems.getStackInSlot(1).getCount() == 64, "a full inventory changed");
        helper.assertFalse(DefectiveFlag.isDefective(machine.exportItems.getStackInSlot(1)),
                "a defective stack merged into a clean one");
        helper.assertTrue(machine.getRecipeLogic().getStatus() == statusBefore, "the recipe logic status changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void defectiveAndCleanNeverMerge(GameTestHelper helper) {
        SimpleTieredMachine machine = lvWiremill(helper);
        machine.exportItems.setStackInSlot(0, wire(5, true));
        helper.assertTrue(machine.insertBonusOutput(wire(3, false)).isEmpty(), "clean stack did not fit");
        helper.assertTrue(machine.exportItems.getStackInSlot(0).getCount() == 5 &&
                DefectiveFlag.isDefective(machine.exportItems.getStackInSlot(0)), "a clean stack merged into slot 0");
        ItemStack second = machine.exportItems.getStackInSlot(1);
        helper.assertTrue(second.getCount() == 3 && !DefectiveFlag.isDefective(second), "clean stack not in slot 1");
        helper.assertTrue(machine.insertBonusOutput(wire(2, true)).isEmpty(), "defective stack did not fit");
        helper.assertTrue(machine.exportItems.getStackInSlot(0).getCount() == 7, "defective stacks did not merge");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void bonusUsesAnyFreeSlotOnAFourSlotMachine(GameTestHelper helper) {
        SimpleTieredMachine machine = place(helper, GTMachines.MACERATOR[GTValues.LV]);
        helper.assertTrue(machine.exportItems.getSlots() == 4, "the macerator should have four output slots");
        machine.exportItems.setStackInSlot(0, ChemicalHelper.get(TagPrefix.dust, GTMaterials.Copper, 8));
        ItemStack bonus = DefectiveFlag.mark(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Copper, 2));
        helper.assertTrue(machine.insertBonusOutput(bonus).isEmpty(), "bonus did not fit a free slot");
        int flagged = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = machine.exportItems.getStackInSlot(i);
            if (DefectiveFlag.isDefective(s)) flagged += s.getCount();
        }
        helper.assertTrue(flagged == 2, "expected 2 defective dust in the outputs, found " + flagged);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void autoOutputIncludesTheExtraSlot(GameTestHelper helper) {
        SimpleTieredMachine machine = lvWiremill(helper);
        BlockPos chestPos = POS.above();
        helper.setBlock(chestPos, Blocks.CHEST);
        machine.exportItems.insertItemInternal(1, wire(3, true), false); // only the extra slot holds anything
        machine.exportItems.exportToNearby(Direction.UP);
        var chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        int moved = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && DefectiveFlag.isDefective(s)) moved += s.getCount();
        }
        helper.assertTrue(moved == 3, "auto-output moved " + moved + " of the 3 items in the extra slot");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void machineSavedWithTheOldSizeGainsTheExtraSlot(GameTestHelper helper) {
        SimpleTieredMachine machine = lvWiremill(helper);
        // simulate a machine loaded from a save made before the extra slot existed: one slot, with contents
        machine.exportItems.storage.setSize(1);
        machine.exportItems.storage.setStackInSlot(0, wire(7, false));
        helper.assertTrue(machine.exportItems.getSlots() == 1, "test premise: the inventory shrank to one slot");
        machine.ensureExportSlotCount();
        helper.assertTrue(machine.exportItems.getSlots() == 2, "the inventory did not grow to two slots");
        ItemStack first = machine.exportItems.getStackInSlot(0);
        helper.assertTrue(first.getCount() == 7 && !DefectiveFlag.isDefective(first), "the old contents changed");
        helper.assertTrue(machine.exportItems.getStackInSlot(1).isEmpty(), "the new slot should start empty");
        helper.assertTrue(machine.insertBonusOutput(wire(2, true)).isEmpty(), "bonus did not fit");
        helper.assertTrue(machine.exportItems.getStackInSlot(0).getCount() == 7, "the bonus went into the old slot");
        helper.succeed();
    }
}
