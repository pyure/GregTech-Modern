package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * A "defective" item is an ordinary item carrying {@link #KEY} in its NBT. Every recipe that accepts the
 * normal item accepts it too (ingredient matching ignores NBT), except recipes tagged with
 * {@link #NO_DEFECTIVE_INPUT} (recycling), whose input handling refuses defective stacks. Any machine recipe that
 * consumes a defective item flags all of its item outputs.
 * <p>
 * Consumption and output are observed through two server-thread scoped trackers, set up by
 * {@link com.gregtechceu.gtceu.api.machine.trait.RecipeLogic} around its input and output handling, and read by
 * {@link com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler}.
 * <p>
 * Recycling exclusion is deliberately a recipe data flag, not an ingredient: {@code NBTPredicateIngredient} cannot
 * be used for it because the recipe lookup only indexes it for stacks that already carry NBT, and its
 * {@code test} calls {@code getOrCreateTag()} on the machine's real input stacks.
 */
public final class DefectiveFlag {

    public static final String KEY = "gtceu_defective";
    /** Recipe data key: the recipe refuses defective item inputs. */
    public static final String NO_DEFECTIVE_INPUT = "no_defective_input";

    private DefectiveFlag() {}

    public static boolean isDefective(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(KEY);
    }

    public static ItemStack mark(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(KEY, true);
        return stack;
    }

    /**
     * @return whether {@code recipe} must not consume {@code stack} because the stack is defective: the recipe is
     *         marked {@link #NO_DEFECTIVE_INPUT} (recycling), or it would turn the item into a fluid that is a solid in
     *         liquid form, which cannot carry the flag.
     */
    public static boolean rejects(GTRecipe recipe, ItemStack stack) {
        if (!isDefective(stack)) return false;
        return (recipe.data != null && recipe.data.getBoolean(NO_DEFECTIVE_INPUT)) ||
                hasSolidInLiquidFormOutput(recipe);
    }

    /**
     * A fluid counts as a "solid in liquid form" when it is the liquid or molten fluid of a material that has both a
     * fluid and a dust form (molten metals and similar). Gases, plasma, and acids or solutions with no dust form do
     * not count. This is the same test the loop audit uses to price fluids at 144 mB per unit.
     */
    public static boolean isSolidInLiquidForm(Fluid fluid) {
        Material material = ChemicalHelper.getMaterial(fluid);
        if (material == null || !material.hasProperty(PropertyKey.FLUID) ||
                !material.hasProperty(PropertyKey.DUST)) {
            return false;
        }
        FluidProperty property = material.getProperty(PropertyKey.FLUID);
        return property.get(FluidStorageKeys.LIQUID) == fluid || property.get(FluidStorageKeys.MOLTEN) == fluid;
    }

    /** @return whether any fluid output of {@code recipe}, guaranteed, chanced or ranged, is a solid in liquid form. */
    public static boolean hasSolidInLiquidFormOutput(GTRecipe recipe) {
        var contents = recipe.outputs.get(FluidRecipeCapability.CAP);
        if (contents == null) return false;
        for (Content content : contents) {
            // getStacks, not getAmount: ranged ingredients throw on getAmount
            for (FluidStack stack : FluidRecipeCapability.CAP.of(content.content()).getStacks()) {
                if (isSolidInLiquidForm(stack.getFluid())) return true;
            }
        }
        return false;
    }

    /** @return whether any stack in {@code container} is defective. */
    public static boolean anyDefective(net.minecraft.world.Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (isDefective(container.getItem(i))) return true;
        }
        return false;
    }

    /** Flags {@code result} if any ingredient in the crafting grid is defective. */
    public static void propagateFromGrid(net.minecraft.world.Container grid, ItemStack result) {
        if (!result.isEmpty() && anyDefective(grid)) mark(result);
    }

    // ---- furnace input (vanilla furnace burn: input is shrunk before we see the output) ----

    private static final ThreadLocal<Boolean> FURNACE_INPUT = new ThreadLocal<>();

    public static void setFurnaceInput(boolean defective) {
        FURNACE_INPUT.set(defective);
    }

    public static boolean takeFurnaceInput() {
        boolean v = Boolean.TRUE.equals(FURNACE_INPUT.get());
        FURNACE_INPUT.remove();
        return v;
    }

    // ---- consumption tracking (recipe start) ----

    private static final ThreadLocal<boolean[]> CONSUMED = new ThreadLocal<>();

    public static void beginConsume() {
        CONSUMED.set(new boolean[1]);
    }

    /** Called for every stack actually removed from an input inventory. */
    public static void noteConsumed(ItemStack extracted) {
        boolean[] seen = CONSUMED.get();
        if (seen != null && isDefective(extracted)) seen[0] = true;
    }

    /** @return whether any defective item was consumed since {@link #beginConsume()}. */
    public static boolean endConsume() {
        boolean[] seen = CONSUMED.get();
        CONSUMED.remove();
        return seen != null && seen[0];
    }

    // ---- refusal tracking (recipe search) ----

    /** Shown in the machine's problem callout when a recipe failed because a defective stack was refused. */
    public static final String REFUSAL_LANG_KEY = "gtceu.recipe_logic.refuses_defective_input";
    public static final String LABEL_DEFECTIVE_LANG_KEY = "gtceu.defective.label.defective";
    public static final String NO_RECYCLE_LANG_KEY = "gtceu.defective.no_recycle";

    private static final ThreadLocal<boolean[]> REFUSED = new ThreadLocal<>();

    public static void beginRefusalWatch() {
        REFUSED.set(new boolean[1]);
    }

    /** Called when an input stack matched an ingredient of a recipe but was refused because it is defective. */
    public static void noteRefusal() {
        boolean[] seen = REFUSED.get();
        if (seen != null) seen[0] = true;
    }

    /** @return whether a refusal happened since {@link #beginRefusalWatch()}. */
    public static boolean endRefusalWatch() {
        boolean[] seen = REFUSED.get();
        REFUSED.remove();
        return seen != null && seen[0];
    }

    // ---- display ----

    /** @return the translation key of the label for a defective stack, or null if the stack is not defective. */
    public static String labelKey(ItemStack stack) {
        if (!isDefective(stack)) return null;
        return LABEL_DEFECTIVE_LANG_KEY;
    }

    // ---- output marking (recipe finish) ----

    private static final ThreadLocal<Boolean> MARK_OUTPUTS = new ThreadLocal<>();

    public static void beginMarkOutputs(boolean mark) {
        MARK_OUTPUTS.set(mark);
    }

    public static void endMarkOutputs() {
        MARK_OUTPUTS.remove();
    }

    public static boolean shouldMarkOutputs() {
        return Boolean.TRUE.equals(MARK_OUTPUTS.get());
    }
}
