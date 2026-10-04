package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The defective bonus of a loop recipe. The Power Distribution modifier takes the part of a guaranteed item output
 * that the Primary dial adds above the baseline out of the normal outputs and records it on the running recipe's data
 * under {@link #KEY}, one entry per output: the item, a guaranteed count, and a chance (out of
 * {@link ChanceLogic#getMaxChancedValue()}) for one more unit. When the recipe finishes, {@link #deliver} creates the
 * flagged items and puts them in the machine's output slots, discarding what does not fit. A run that consumed a
 * defective item earns no bonus.
 */
public final class DefectiveBonus {

    public static final String KEY = "defective_bonus";

    /** Written by the Power Distribution modifier: what kind of Primary bonus the running recipe carries. */
    public static final String KIND_KEY = "bonus_kind";
    public static final String KIND_DEFECTIVE = "defective";
    public static final String KIND_NONE = "none";
    public static final String KIND_CLEAN = "clean";

    private DefectiveBonus() {}

    public static CompoundTag entry(ItemStack unit, int guaranteed, int chance) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", unit.copyWithCount(1).save(new CompoundTag()));
        tag.putInt("guaranteed", guaranteed);
        tag.putInt("chance", chance);
        return tag;
    }

    /**
     * @return how many items this entry produces on one finish: the guaranteed count, plus one on a successful roll.
     */
    public static int roll(CompoundTag entry, RandomSource random) {
        int count = entry.getInt("guaranteed");
        int chance = entry.getInt("chance");
        if (chance > 0 && random.nextInt(ChanceLogic.getMaxChancedValue()) < chance) count++;
        return count;
    }

    /**
     * Creates and inserts the recorded bonus. Does nothing when the run consumed a defective item or the recipe
     * carries no bonus. Never blocks: what does not fit is discarded.
     */
    public static void deliver(SimpleTieredMachine machine, GTRecipe recipe, boolean consumedDefective,
                               RandomSource random) {
        if (consumedDefective || recipe.data == null || !recipe.data.contains(KEY, Tag.TAG_LIST)) return;
        for (Tag tag : recipe.data.getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) tag;
            int count = roll(entry, random);
            if (count <= 0) continue;
            ItemStack unit = ItemStack.of(entry.getCompound("item"));
            if (unit.isEmpty()) continue;
            machine.insertBonusOutput(DefectiveFlag.mark(unit.copyWithCount(count)));
        }
    }
}
