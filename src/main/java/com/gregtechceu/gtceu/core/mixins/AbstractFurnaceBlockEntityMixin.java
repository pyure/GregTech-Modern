package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.recipe.DefectiveFlag;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smelting a defective item yields a defective item, and defective and clean results never merge into
 * one output stack (vanilla only compares the item, so a furnace would otherwise stack them together).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "canBurn", at = @At("RETURN"), cancellable = true)
    private void gtceu$keepDefectiveSeparate(RegistryAccess access, @Nullable Recipe<?> recipe,
                                             NonNullList<ItemStack> items, int maxStackSize,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        ItemStack output = items.get(2);
        if (!output.isEmpty() && DefectiveFlag.isDefective(items.get(0)) != DefectiveFlag.isDefective(output)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "burn", at = @At("HEAD"))
    private void gtceu$noteInput(RegistryAccess access, @Nullable Recipe<?> recipe,
                                 NonNullList<ItemStack> items, int maxStackSize,
                                 CallbackInfoReturnable<Boolean> cir) {
        DefectiveFlag.setFurnaceInput(DefectiveFlag.isDefective(items.get(0)));
    }

    @Inject(method = "burn", at = @At("RETURN"))
    private void gtceu$flagOutput(RegistryAccess access, @Nullable Recipe<?> recipe,
                                  NonNullList<ItemStack> items, int maxStackSize,
                                  CallbackInfoReturnable<Boolean> cir) {
        boolean flagged = DefectiveFlag.takeFurnaceInput();
        if (flagged && cir.getReturnValueZ() && !items.get(2).isEmpty()) DefectiveFlag.mark(items.get(2));
    }
}
