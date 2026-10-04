package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.EditableUI;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.widget.GhostCircuitSlotWidget;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.PowerDistributionConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputBoth;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.ISubscription;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.common.collect.Tables;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * All simple single machines are implemented here.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SimpleTieredMachine extends WorkableTieredMachine
                                 implements IAutoOutputBoth, IFancyUIMachine, IHasCircuitSlot {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(SimpleTieredMachine.class,
            WorkableTieredMachine.MANAGED_FIELD_HOLDER);

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

    @Override
    protected NotifiableItemStackHandler createExportItemHandler(Object... args) {
        int slots = getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP) + (hasBonusSlot() ? 1 : 0);
        return new NotifiableItemStackHandler(this, slots, IO.OUT);
    }

    /**
     * A machine saved before the extra slot existed loads with its old, smaller output inventory (the item handler
     * adopts the size saved in NBT), so its extra slot widget would point at an existing slot and mirror it. Grows the
     * inventory to the expected size, keeping every stack in place.
     */
    public void ensureExportSlotCount() {
        int expected = getRecipeType().getMaxOutputs(ItemRecipeCapability.CAP) + (hasBonusSlot() ? 1 : 0);
        var storage = exportItems.storage;
        if (storage.getSlots() >= expected) return;
        ItemStack[] old = new ItemStack[storage.getSlots()];
        for (int i = 0; i < old.length; i++) old[i] = storage.getStackInSlot(i).copy();
        storage.setSize(expected);
        for (int i = 0; i < old.length; i++) storage.setStackInSlot(i, old[i]);
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

    @Persisted
    @DescSynced
    @RequireRerender
    protected Direction outputFacingItems;
    @Persisted
    @DescSynced
    @RequireRerender
    protected Direction outputFacingFluids;
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean autoOutputItems;
    @Getter
    @Persisted
    @DescSynced
    @RequireRerender
    protected boolean autoOutputFluids;
    @Getter
    @Setter
    @Persisted
    protected boolean allowInputFromOutputSideItems;
    @Getter
    @Setter
    @Persisted
    protected boolean allowInputFromOutputSideFluids;
    @Getter
    @Persisted
    protected final CustomItemStackHandler chargerInventory;
    @Getter
    @Persisted
    protected final NotifiableItemStackHandler circuitInventory;
    @Nullable
    protected TickableSubscription autoOutputSubs, batterySubs;
    @Nullable
    protected ISubscription exportItemSubs, exportFluidSubs, energySubs;
    @Getter
    @Persisted
    @DescSynced
    public PowerDistributionConfig powerDistribution;

    public SimpleTieredMachine(IMachineBlockEntity holder, int tier, Int2IntFunction tankScalingFunction,
                               Object... args) {
        super(holder, tier, tankScalingFunction, args);
        this.outputFacingItems = hasFrontFacing() ? getFrontFacing().getOpposite() : Direction.UP;
        this.outputFacingFluids = outputFacingItems;
        this.chargerInventory = createChargerItemHandler(args);
        this.circuitInventory = createCircuitItemHandler(args);
        this.powerDistribution = PowerDistributionConfig.defaults(tier);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    protected CustomItemStackHandler createChargerItemHandler(Object... args) {
        var handler = new CustomItemStackHandler() {

            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        handler.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));
        return handler;
    }

    protected NotifiableItemStackHandler createCircuitItemHandler(Object... args) {
        return new NotifiableItemStackHandler(this, 1, IO.IN, IO.NONE)
                .setFilter(IntCircuitBehaviour::isIntegratedCircuit);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////
    @Override
    public void onLoad() {
        super.onLoad();
        ensureExportSlotCount();
        if (!isRemote()) {
            if (getLevel() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));
            }
            updateBatterySubscription();
            exportItemSubs = exportItems.addChangedListener(this::updateAutoOutputSubscription);
            exportFluidSubs = exportFluids.addChangedListener(this::updateAutoOutputSubscription);
            energySubs = energyContainer.addChangedListener(this::updateBatterySubscription);
            chargerInventory.setOnContentsChanged(this::updateBatterySubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportItemSubs != null) {
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }

        if (exportFluidSubs != null) {
            exportFluidSubs.unsubscribe();
            exportFluidSubs = null;
        }

        if (energySubs != null) {
            energySubs.unsubscribe();
            energySubs = null;
        }
    }

    //////////////////////////////////////
    // ******* Auto Output *******//
    //////////////////////////////////////

    @Override
    public boolean hasAutoOutputFluid() {
        return exportFluids.getTanks() > 0;
    }

    @Override
    public boolean hasAutoOutputItem() {
        return exportItems.getSlots() > 0;
    }

    @Override
    public @Nullable Direction getOutputFacingFluids() {
        if (hasAutoOutputFluid()) {
            return outputFacingFluids;
        }
        return null;
    }

    @Override
    public @Nullable Direction getOutputFacingItems() {
        if (hasAutoOutputItem()) {
            return outputFacingItems;
        }
        return null;
    }

    @Override
    public void setAutoOutputItems(boolean allow) {
        if (hasAutoOutputItem()) {
            this.autoOutputItems = allow;
            updateAutoOutputSubscription();
        }
    }

    @Override
    public void setAutoOutputFluids(boolean allow) {
        if (hasAutoOutputFluid()) {
            this.autoOutputFluids = allow;
            updateAutoOutputSubscription();
        }
    }

    @Override
    public void setOutputFacingFluids(@Nullable Direction outputFacing) {
        if (hasAutoOutputFluid()) {
            this.outputFacingFluids = outputFacing;
            updateAutoOutputSubscription();
        }
    }

    @Override
    public void setOutputFacingItems(@Nullable Direction outputFacing) {
        if (hasAutoOutputItem()) {
            this.outputFacingItems = outputFacing;
            updateAutoOutputSubscription();
        }
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }

    protected void updateAutoOutputSubscription() {
        var outputFacingItems = getOutputFacingItems();
        var outputFacingFluids = getOutputFacingFluids();
        if ((isAutoOutputItems() && !exportItems.isEmpty() && outputFacingItems != null &&
                GTTransferUtils.hasAdjacentItemHandler(getLevel(), getPos(), outputFacingItems)) ||
                (isAutoOutputFluids() && !exportFluids.isEmpty() && outputFacingFluids != null &&
                        GTTransferUtils.hasAdjacentFluidHandler(getLevel(), getPos(), outputFacingFluids))) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true)) {
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        } else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false)) {
            updateBatterySubscription();
        }
    }

    protected void autoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            if (isAutoOutputFluids() && getOutputFacingFluids() != null) {
                exportFluids.exportToNearby(getOutputFacingFluids());
            }
            if (isAutoOutputItems() && getOutputFacingItems() != null) {
                exportItems.exportToNearby(getOutputFacingItems());
            }
        }
        updateAutoOutputSubscription();
    }

    @Override
    public boolean isFacingValid(Direction facing) {
        if (facing == getOutputFacingItems() || facing == getOutputFacingFluids()) {
            return false;
        }
        return super.isFacingValid(facing);
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////
    @Override
    public void onMachineRemoved() {
        super.onMachineRemoved();
        clearInventory(chargerInventory);
        if (!ConfigHolder.INSTANCE.machines.ghostCircuit) {
            clearInventory(circuitInventory.storage);
        }
    }

    /// //////////////////////////////////
    // ****** RECIPE LOGIC *******//
    /// //////////////////////////////////

    @Override
    public long getDisplayRecipeVoltage() {
        return GTValues.V[this.tier];
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);

        if (hasAutoOutputFluid()) {
            configuratorPanel.attachConfigurators(createAutoOutputFluidConfigurator());
        }
        if (hasAutoOutputItem()) {
            configuratorPanel.attachConfigurators(createAutoOutputItemConfigurator());
        }

        if (isCircuitSlotEnabled()) {
            configuratorPanel.attachConfigurators(new CircuitFancyConfigurator(circuitInventory.storage));
        }

        configuratorPanel.attachConfigurators(new PowerDistributionConfigurator(this));
    }

    private IFancyConfigurator createAutoOutputFluidConfigurator() {
        return createAutoOutputConfigurator(
                GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON,
                "gtceu.gui.fluid_auto_output",
                this::isAutoOutputFluids,
                (cd, nextState) -> this.setAutoOutputFluids(nextState));
    }

    private IFancyConfigurator createAutoOutputItemConfigurator() {
        return createAutoOutputConfigurator(
                GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON,
                "gtceu.gui.item_auto_output",
                this::isAutoOutputItems,
                (cd, nextState) -> this.setAutoOutputItems(nextState));
    }

    private IFancyConfigurator createAutoOutputConfigurator(ResourceTexture modesButtonTexture,
                                                            String tooltipBaseLangKey,
                                                            BooleanSupplier stateSupplier,
                                                            BiConsumer<ClickData, Boolean> onToggle) {
        var toggle = new IFancyConfiguratorButton.Toggle(
                new GuiTextureGroup(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5),
                        modesButtonTexture.getSubTexture(0, 1 / 3f, 1, 1 / 3f)),
                new GuiTextureGroup(
                        GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5),
                        modesButtonTexture.getSubTexture(0, 2 / 3f, 1, 1 / 3f)),
                stateSupplier,
                onToggle);

        toggle.setTooltipsSupplier(enabled -> {
            var key = tooltipBaseLangKey + '.' + (enabled ? "enabled" : "disabled");
            return List.of(Component.translatable(key));
        });

        return toggle;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static BiFunction<ResourceLocation, GTRecipeType, EditableMachineUI> EDITABLE_UI_CREATOR = Util
            .memoize((path, recipeType) -> new EditableMachineUI("simple", path, () -> {
                WidgetGroup template = recipeType.getRecipeUI().createEditableUITemplate(false, false).createDefault();
                SlotWidget batterySlot = createBatterySlot().createDefault();
                WidgetGroup group = new WidgetGroup(0, 0, template.getSize().width,
                        Math.max(template.getSize().height, 78));
                template.setSelfPosition(new Position(0, (group.getSize().height - template.getSize().height) / 2));
                batterySlot.setSelfPosition(new Position(group.getSize().width / 2 - 9, group.getSize().height - 18));
                group.addWidget(batterySlot);
                group.addWidget(template);
                if (hasBonusSlot(recipeType)) {
                    // below the rightmost slot of the recipe layout, which is the output slot
                    Position outputSlot = null;
                    for (var widget : template.getContainedWidgets(true)) {
                        if (widget instanceof com.lowdragmc.lowdraglib.gui.widget.SlotWidget &&
                                (outputSlot == null || widget.getPosition().x > outputSlot.x)) {
                            outputSlot = widget.getPosition();
                        }
                    }
                    if (outputSlot != null) {
                        SlotWidget bonusSlot = createBonusSlot().createDefault();
                        // below the output slot, else to its right (widening the panel), else left, else above;
                        // never on top of another widget (the distillery has a fluid slot under its item slot)
                        int[][] offsets = { { 0, 20 }, { 20, 0 }, { -20, 0 }, { 0, -20 } };
                        for (int[] offset : offsets) {
                            int x = outputSlot.x + offset[0];
                            int y = outputSlot.y + offset[1];
                            if (x < 0 || y < 0 || y + 18 > group.getSize().height) continue;
                            if (overlapsAnyWidget(group, x, y, 18, 18)) continue;
                            bonusSlot.setSelfPosition(new Position(x, y));
                            group.addWidget(bonusSlot);
                            if (x + 18 > group.getSize().width) {
                                group.setSize(x + 18, group.getSize().height);
                            }
                            break;
                        }
                    }
                }

                // TODO fix this.
                // if (ConfigHolder.INSTANCE.machines.ghostCircuit) {
                // SlotWidget circuitSlot = createCircuitConfigurator().createDefault();
                // circuitSlot.setSelfPosition(new Position(120, 62));
                // group.addWidget(circuitSlot);
                // }

                return group;
            }, (template, machine) -> {
                if (machine instanceof SimpleTieredMachine tieredMachine) {
                    var storages = Tables.newCustomTable(new EnumMap<>(IO.class),
                            LinkedHashMap<RecipeCapability<?>, Object>::new);
                    storages.put(IO.IN, ItemRecipeCapability.CAP, tieredMachine.importItems.storage);
                    storages.put(IO.OUT, ItemRecipeCapability.CAP, tieredMachine.exportItems.storage);
                    storages.put(IO.IN, FluidRecipeCapability.CAP, tieredMachine.importFluids);
                    storages.put(IO.OUT, FluidRecipeCapability.CAP, tieredMachine.exportFluids);
                    storages.put(IO.IN, CWURecipeCapability.CAP, tieredMachine.importComputation);
                    storages.put(IO.OUT, CWURecipeCapability.CAP, tieredMachine.exportComputation);

                    tieredMachine.getRecipeType().getRecipeUI().createEditableUITemplate(false, false).setupUI(template,
                            new GTRecipeTypeUI.RecipeHolder(tieredMachine.recipeLogic::getProgressPercent,
                                    storages,
                                    new CompoundTag(),
                                    Collections.emptyList(),
                                    false, false));
                    createBatterySlot().setupUI(template, tieredMachine);
                    if (tieredMachine.hasBonusSlot()) {
                        createBonusSlot().setupUI(template, tieredMachine);
                    }
                    // createCircuitConfigurator().setupUI(template, tieredMachine);
                }
            }));

    /** @return whether the given rectangle overlaps any leaf widget (not a container) inside {@code group}. */
    private static boolean overlapsAnyWidget(WidgetGroup group, int x, int y, int width, int height) {
        for (var widget : group.getContainedWidgets(true)) {
            if (widget instanceof WidgetGroup) continue;
            int wx = widget.getPosition().x, wy = widget.getPosition().y;
            int ww = widget.getSize().width, wh = widget.getSize().height;
            if (x < wx + ww && wx < x + width && y < wy + wh && wy < y + height) return true;
        }
        return false;
    }

    /** The extra output slot where the defective bonus is delivered. */
    protected static EditableUI<SlotWidget, SimpleTieredMachine> createBonusSlot() {
        return new EditableUI<>("bonus_output_slot", SlotWidget.class, () -> {
            var slotWidget = new SlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setHandlerSlot(machine.exportItems.storage, machine.exportItems.getSlots() - 1);
            slotWidget.setCanPutItems(false);
            slotWidget.setCanTakeItems(true);
            slotWidget.setHoverTooltips(Component.translatable("gtceu.machine.bonus_output_slot.tooltip"));
        });
    }

    /**
     * Create a battery slot widget.
     */
    protected static EditableUI<SlotWidget, SimpleTieredMachine> createBatterySlot() {
        return new EditableUI<>("battery_slot", SlotWidget.class, () -> {
            var slotWidget = new SlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setHandlerSlot(machine.chargerInventory, 0);
            slotWidget.setCanPutItems(true);
            slotWidget.setCanTakeItems(true);
            slotWidget.setHoverTooltips(LangHandler.getMultiLang("gtceu.gui.charger_slot.tooltip",
                    GTValues.VNF[machine.getTier()], GTValues.VNF[machine.getTier()]).toArray(Component[]::new));
        });
    }

    /**
     * Create a ghost circuit slot widget.
     */
    protected static EditableUI<GhostCircuitSlotWidget, SimpleTieredMachine> createCircuitConfigurator() {
        return new EditableUI<>("circuit_configurator", GhostCircuitSlotWidget.class, () -> {
            var slotWidget = new GhostCircuitSlotWidget();
            slotWidget.setBackground(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
            return slotWidget;
        }, (slotWidget, machine) -> {
            slotWidget.setCircuitInventory(machine.circuitInventory);
            slotWidget.setCanPutItems(false);
            slotWidget.setCanTakeItems(false);
            slotWidget.setHoverTooltips(
                    LangHandler.getMultiLang("gtceu.gui.configurator_slot.tooltip").toArray(Component[]::new));
        });
    }

    // Method provided to override
    protected IGuiTexture getCircuitSlotOverlay() {
        return GuiTextures.INT_CIRCUIT_OVERLAY;
    }

    //////////////////////////////////////
    // ******* Rendering ********//
    //////////////////////////////////////
    @Override
    public @Nullable ResourceTexture sideTips(Player player, BlockPos pos, BlockState state, Set<GTToolType> toolTypes,
                                              Direction side) {
        if (toolTypes.contains(GTToolType.WRENCH)) {
            if (!player.isShiftKeyDown()) {
                if (!hasFrontFacing() || side != getFrontFacing()) {
                    return GuiTextures.TOOL_IO_FACING_ROTATION;
                }
            }
        }
        if (toolTypes.contains(GTToolType.SCREWDRIVER)) {
            if (side == getOutputFacingItems() || side == getOutputFacingFluids()) {
                return GuiTextures.TOOL_ALLOW_INPUT;
            }
        }
        return super.sideTips(player, pos, state, toolTypes, side);
    }
}
