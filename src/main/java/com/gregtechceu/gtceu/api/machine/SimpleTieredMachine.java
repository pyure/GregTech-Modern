package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.machine.trait.BatterySlotTrait;
import com.gregtechceu.gtceu.common.machine.trait.PowerDistributionTrait;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;

import java.util.*;

/**
 * All simple single machines are implemented here.
 */
public class SimpleTieredMachine extends WorkableTieredMachine {

    public SimpleTieredMachine(BlockEntityCreationInfo info, int tier, RecipeLogic recipeLogic,
                               Int2IntFunction tankScalingFunction) {
        super(info, tier, false, recipeLogic, tankScalingFunction);

        attachPersistentTrait("autoOutput", new AutoOutputTrait(List.of(exportItems), List.of(exportFluids)));
        attachPersistentTrait("circuit", new ProgrammableCircuitSlotTrait());
        attachPersistentTrait("batterySlot", new BatterySlotTrait(energyContainer));
        attachPersistentTrait("powerDistribution", new PowerDistributionTrait(tier));
    }

    public SimpleTieredMachine(BlockEntityCreationInfo info, int tier, Int2IntFunction tankScalingFunction) {
        this(info, tier, new RecipeLogic(), tankScalingFunction);
    }

    public SimpleTieredMachine(BlockEntityCreationInfo info, int tier) {
        this(info, tier, GTMachineUtils.defaultTankSizeFunction);
    }

    /**
     * Recipe types whose electric machines get one extra item output slot, where the defective bonus is delivered.
     * A fixed list on purpose: the slot count must be stable when a machine is built, whatever the loop list says.
     * Steam machines are a different class and are not affected.
     */
    private static final Set<String> BONUS_SLOT_RECIPE_TYPES = Set.of("extruder", "fluid_solidifier",
            "alloy_smelter", "bender", "compressor", "wiremill", "laser_engraver", "forge_hammer",
            "electric_furnace", "assembler", "forming_press", "polarizer", "mixer", "distillery");

    public static boolean hasBonusSlot(GTRecipeType type) {
        return BONUS_SLOT_RECIPE_TYPES.contains(type.registryName.getPath());
    }

    public boolean hasBonusSlot() {
        return hasBonusSlot(getRecipeType());
    }

    /**
     * Called from the superclass constructor, so it reads only the machine definition and recipe types, which are
     * assigned by then, and none of this class's own state.
     */
    @Override
    protected int getItemOutputSlots() {
        return super.getItemOutputSlots() + (hasBonusSlot() ? 1 : 0);
    }

    /**
     * Puts a bonus stack into any output slot that accepts it (a defective stack only merges with defective
     * stacks) and discards whatever does not fit. It never blocks or fails the recipe logic.
     *
     * @return the part that did not fit and was discarded
     */
    public ItemStack insertBonusOutput(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < exportItems.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = exportItems.insertItemInternal(slot, remaining, false);
        }
        return remaining;
    }

    @Override
    public long getDisplayRecipeVoltage() {
        return GTValues.V[this.tier];
    }
}
