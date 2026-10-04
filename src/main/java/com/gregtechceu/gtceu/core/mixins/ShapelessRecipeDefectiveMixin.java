package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries the "defective" flag from crafting-grid ingredients onto the crafted result. Hooking
 * {@code assemble} (not the player's crafted-item event) means the flag is already on the result stack in the result
 * slot, so shift-clicking and anything else that assembles the recipe is covered.
 */
@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeDefectiveMixin {

    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private void gtceu$propagateDefective(CraftingContainer container, RegistryAccess access,
                                          CallbackInfoReturnable<ItemStack> cir) {
        DefectiveFlag.propagateFromGrid(container, cir.getReturnValue());
    }
}
