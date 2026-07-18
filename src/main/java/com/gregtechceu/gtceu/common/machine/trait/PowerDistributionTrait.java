package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.PowerDistributionConfig;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitType;
import com.gregtechceu.gtceu.api.machine.trait.feature.IAttachConfiguratorsTrait;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.DynamicDrawable;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.drawable.progress.ProgressDrawable;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.DoubleSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.Dialog;
import brachy.modularui.widgets.ProgressWidget;
import brachy.modularui.widgets.layout.Flow;
import lombok.Getter;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Machine trait which attaches the four-dial "Power Distribution" panel (Speed/Tuning/Primary Output/Byproduct)
 * to the side of a machine's GUI. Ported from the old {@code PowerDistributionConfigurator}
 * ({@code IFancyConfigurator}) to MUI2, modeled on {@link ProgrammableCircuitSlotTrait} — the same
 * button-opens-a-popup-Dialog pattern used for the circuit slot.
 * <p>
 * Layout and colors are a direct pixel-for-pixel port of the "GTCEu Teal" theme from the design handoff
 * (Claude Design artifact referenced in {@code specs/power-distribution-ui-redesign.md} — see its embedded
 * {@code THEMES} array, id {@code 2b}), translated from the mockup's absolute-CSS-position layout to MUI2's
 * {@code IPositioned.pos(x, y)}/{@code size(w, h)} absolute positioning (verified against the actual
 * {@code modularui} jar via javap/decompile, not guessed — {@code Flow}'s own flex layout would have fought
 * these fixed coordinates, so {@link ParentWidget} — which imposes no layout of its own — hosts every
 * absolutely-positioned element instead).
 */
public class PowerDistributionTrait extends MachineTrait implements IAttachConfiguratorsTrait {

    public static final MachineTraitType<PowerDistributionTrait> TYPE = new MachineTraitType<>(
            PowerDistributionTrait.class);

    // GTCEu Teal palette — exact values from the design handoff's THEMES['2b'] entry.
    private static final int COLOR_BORDER = 0xFF0C1416;
    private static final int COLOR_PANEL = 0xFF1E2B2D;
    private static final int COLOR_HEADER = 0xFF243638;
    private static final int COLOR_HEADER_TEXT = 0xFFE4F2F0;
    private static final int COLOR_TEXT = 0xFFCFE4E1;
    private static final int COLOR_DIM = 0xFF6E8987;
    private static final int COLOR_ACCENT = 0xFF2FB8AD;
    private static final int COLOR_HOT = 0xFFD9704A;
    private static final int COLOR_COOL = 0xFF3AA0D9;
    private static final int COLOR_TRACK = 0xFF33474A;
    private static final int COLOR_BAR_TRACK = 0xFF101C1D;
    private static final int COLOR_SUCCESS = 0xFF4FBF7A;
    private static final int COLOR_DANGER = 0xFFD9534F;
    private static final int COLOR_NOT_READY = 0xFF5C716F;

    // Panel geometry — exact pixel coordinates from the design handoff mockup (208x154 outer, 4px border).
    private static final int PANEL_W = 208;
    private static final int PANEL_H = 154;
    private static final int BODY_X = 4;
    private static final int BODY_Y = 4;
    private static final int BODY_W = 200;
    private static final int BODY_H = 146;
    private static final int HEADER_H = 16;
    private static final int BAR_X = 6;
    private static final int BAR_Y = 18;
    private static final int BAR_W = 188;
    private static final int BAR_H = 12;
    private static final int ROW_X = 6;
    private static final int ROW_W = 194;
    private static final int ROW_H = 20;
    private static final int ROW_STRIDE = 23;
    private static final int ROWS_TOP = 33;
    private static final int MINI_BAR_X = 60;
    private static final int MINI_BAR_Y = 6;
    private static final int MINI_BAR_W = 40;
    private static final int MINI_BAR_H = 8;
    private static final int VALUE_X = 106;
    private static final int VALUE_Y = 3;
    private static final int VALUE_W = 26;
    private static final int MINUS_X = 136;
    private static final int PLUS_X = 156;
    // 9px = native resolution of button_circuit_minus/plus.png (avoids upscale blur) and about half an
    // 18px inventory slot — the mockup's own 16x16 read as "same size as a slot" once actually in-game.
    private static final int BUTTON_SIZE = 9;
    private static final int BUTTON_Y = (ROW_H - BUTTON_SIZE) / 2;
    private static final int FOOTER_Y = 128;
    private static final int FOOTER_DOT_SIZE = 7;

    @Getter
    @SaveField
    public final PowerDistributionConfig powerDistribution;

    private final int tier;

    public PowerDistributionTrait(int tier) {
        this.tier = tier;
        this.powerDistribution = PowerDistributionConfig.defaults(tier);
    }

    @Override
    public MachineTraitType<PowerDistributionTrait> getTraitType() {
        return TYPE;
    }

    @Override
    public void attachLeftConfigurators(Flow flow, ModularPanel<?> panel, PanelSyncManager syncManager) {
        flow.child(createButton(panel, syncManager));
    }

    private ButtonWidget<?> createButton(ModularPanel<?> panel, PanelSyncManager syncManager) {
        IPanelHandler panelHandler = syncManager.syncedPanel("power_distribution_panel", false,
                (sm, sh) -> buildDialog(sm).relative(panel).leftRel(0.0f, -4, 1f));

        return new ButtonWidget<>()
                .size(18)
                .overlay(UITexture.fullImage(GTCEu.MOD_ID, "textures/gui/icon/speedometer.png"))
                .onMousePressed((context, button) -> {
                    panelHandler.togglePanel();
                    return true;
                })
                .tooltipBuilder(t -> t.addLine(Component.translatable("gtceu.power_distribution.title")));
    }

    private Dialog<?, ?> buildDialog(PanelSyncManager syncManager) {
        DoubleSyncValue spentFraction = new DoubleSyncValue(
                () -> powerDistribution.spent() / (double) PowerDistributionConfig.budget(tier));

        ParentWidget<?> body = new ParentWidget<>()
                .pos(BODY_X, BODY_Y)
                .size(BODY_W, BODY_H)
                .background(new Rectangle().color(COLOR_PANEL).solid());

        body.child(rect(COLOR_HEADER).pos(0, 0).size(BODY_W, HEADER_H));
        body.child(coloredText(() -> Component.translatable("gtceu.power_distribution.title"), COLOR_HEADER_TEXT)
                .pos(6, 4).size(150, 10));

        body.child(new ProgressWidget()
                .texture(new Rectangle().color(COLOR_BAR_TRACK).solid(),
                        new DynamicDrawable(() -> new Rectangle().color(allocationBarColor()).solid()),
                        ProgressDrawable.Direction.RIGHT)
                .value(spentFraction)
                .pos(BAR_X, BAR_Y)
                .size(BAR_W, BAR_H)
                .tooltipBuilder(t -> t
                        .addLine(Component.translatable("gtceu.power_distribution.bar_tooltip.line1",
                                powerDistribution.spent(), PowerDistributionConfig.budget(tier)))
                        .addLine(Component.translatable("gtceu.power_distribution.bar_tooltip.line2")))
                .tooltipAutoUpdate(true));
        // Centered text overlay on the bar — approximated with a fixed offset rather than true text-centering
        // (unverified in this MUI2 build), close enough for a short "N/N PU" string.
        body.child(coloredText(this::barLabelText, COLOR_HEADER_TEXT).pos(BAR_X + 64, BAR_Y + 2).size(60, 10));

        body.child(createDialRow(syncManager, "speed", 1, 0,
                powerDistribution::getSpeedPU, powerDistribution::setSpeedPU,
                () -> Component.translatable("gtceu.power_distribution.speed.tooltip_live",
                        powerDistribution.getSpeedPU(),
                        String.format("%.2f", Math.pow(0.75, powerDistribution.getSpeedPU() - 2)))));
        body.child(createDialRow(syncManager, "tuning", 0, 1,
                powerDistribution::getTuningPU, powerDistribution::setTuningPU,
                () -> Component.translatable("gtceu.power_distribution.tuning.tooltip_live",
                        powerDistribution.getTuningPU(), String.format("%.2f", idealizedEuMultiplier()))));
        body.child(createDialRow(syncManager, "primary", 0, 2,
                powerDistribution::getPrimaryPU, powerDistribution::setPrimaryPU, this::primaryTooltip));
        body.child(createDialRow(syncManager, "byproduct", 0, 3,
                powerDistribution::getByproductPU, powerDistribution::setByproductPU,
                () -> Component.translatable("gtceu.power_distribution.byproduct.tooltip")));

        body.child(dynamicRect(() -> footerDotColor())
                .pos(ROW_X, FOOTER_Y + (BAR_H - FOOTER_DOT_SIZE) / 2).size(FOOTER_DOT_SIZE, FOOTER_DOT_SIZE));
        body.child(coloredText(this::footerText, COLOR_TEXT)
                .pos(ROW_X + FOOTER_DOT_SIZE + 6, FOOTER_Y + 1).size(150, 10));

        return new Dialog<>("power_distribution")
                .disablePanelsBelow(false)
                .draggable(true)
                .closeOnOutOfBoundsClick(true)
                .size(PANEL_W, PANEL_H)
                .background(new Rectangle().color(COLOR_BORDER).solid())
                .child(body);
    }

    private ParentWidget<?> createDialRow(PanelSyncManager syncManager, String key, int minValue, int rowIndex,
                                          IntSupplier getter, IntConsumer setter,
                                          Supplier<Component> tooltipSupplier) {
        IntSyncValue dial = new IntSyncValue(getter, setter).allowC2S();
        syncManager.syncValue("pd_" + key, dial);

        int rowTop = ROWS_TOP + rowIndex * ROW_STRIDE;
        ParentWidget<?> row = new ParentWidget<>().pos(ROW_X, rowTop).size(ROW_W, ROW_H);

        row.child(coloredText(() -> Component.translatable("gtceu.power_distribution." + key + ".short"), COLOR_TEXT)
                .pos(0, 4).size(MINI_BAR_X - 4, 10));

        ProgressWidget miniBar = new ProgressWidget()
                .texture(new Rectangle().color(COLOR_TRACK).solid(),
                        new DynamicDrawable(() -> new Rectangle().color(COLOR_ACCENT).solid()),
                        ProgressDrawable.Direction.RIGHT)
                .value(new DoubleSyncValue(() -> dial.getIntValue() / (double) PowerDistributionConfig.budget(tier)))
                .pos(MINI_BAR_X, MINI_BAR_Y)
                .size(MINI_BAR_W, MINI_BAR_H)
                .tooltipAutoUpdate(true);
        miniBar.tooltipBuilder(t -> t.addLine(tooltipSupplier.get()));
        row.child(miniBar);

        row.child(coloredText(() -> Component.literal(Integer.toString(dial.getIntValue())),
                () -> dial.getIntValue() > 0 ? COLOR_ACCENT : COLOR_DIM)
                .pos(VALUE_X, VALUE_Y).size(VALUE_W, 10));

        row.child(new ButtonWidget<>()
                .pos(MINUS_X, BUTTON_Y).size(BUTTON_SIZE, BUTTON_SIZE)
                .background(new Rectangle().color(COLOR_TRACK).solid())
                .overlay(UITexture.fullImage(GTCEu.MOD_ID, "textures/gui/widget/button_circuit_minus.png"))
                .onMousePressed((context, button) -> {
                    // Matches the old PowerDistributionConfigurator.adjust()'s per-dial floor: Speed
                    // can never go below 1 (a machine can't run at 0 Speed), the other three floor at 0.
                    if (dial.getIntValue() - 1 >= minValue) {
                        dial.setIntValue(dial.getIntValue() - 1);
                    }
                    return true;
                }));

        row.child(new ButtonWidget<>()
                .pos(PLUS_X, BUTTON_Y).size(BUTTON_SIZE, BUTTON_SIZE)
                .background(new Rectangle().color(COLOR_TRACK).solid())
                .overlay(UITexture.fullImage(GTCEu.MOD_ID, "textures/gui/widget/button_circuit_plus.png"))
                .onMousePressed((context, button) -> {
                    // Matches the old PowerDistributionConfigurator.adjust()'s over-budget guard: the move
                    // itself is blocked here, on top of PD_AWARE_OC separately refusing to run an
                    // over-budget recipe as defense-in-depth (e.g. for stale save data).
                    if (powerDistribution.spent() + 1 <= PowerDistributionConfig.budget(tier)) {
                        dial.setIntValue(dial.getIntValue() + 1);
                    }
                    return true;
                }));

        return row;
    }

    /**
     * Static-colored translatable/literal text, built via a {@link DynamicDrawable}-backed widget for consistency
     * with the dynamically-colored labels below (only one code path to trust, rather than two).
     */
    private static ButtonWidget<?> coloredText(Supplier<Component> textSupplier, int color) {
        return coloredText(textSupplier, () -> color);
    }

    private static ButtonWidget<?> coloredText(Supplier<Component> textSupplier, IntSupplier colorSupplier) {
        // ButtonWidget with no background/overlay/click behavior is used purely as a positionable text host —
        // Text.dynamic(...).asWidget() (used elsewhere in this file) returns a type that doesn't accept .pos()/
        // .size() calls the same way; this sidesteps that by piggybacking on the already-proven ButtonWidget
        // positioning API and just not wiring up any interaction.
        return new ButtonWidget<>()
                .background(new Rectangle().color(0x00000000).solid())
                .overlay(Text.dynamic(() -> styled(textSupplier.get(), colorSupplier.getAsInt())));
    }

    private static Component styled(Component base, int color) {
        return base.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF)));
    }

    private static ButtonWidget<?> rect(int color) {
        return dynamicRect(() -> color);
    }

    private static ButtonWidget<?> dynamicRect(IntSupplier colorSupplier) {
        return new ButtonWidget<>()
                .background(new DynamicDrawable(() -> new Rectangle().color(colorSupplier.getAsInt()).solid()));
    }

    /** Priority: not-ready (spend mismatch) > hot (speed ahead) > cool (tuning ahead) > accent (balanced). */
    private int allocationBarColor() {
        if (powerDistribution.spent() != PowerDistributionConfig.budget(tier)) return COLOR_NOT_READY;
        if (powerDistribution.getSpeedPU() > powerDistribution.getTuningPU()) return COLOR_HOT;
        if (powerDistribution.getSpeedPU() < powerDistribution.getTuningPU()) return COLOR_COOL;
        return COLOR_ACCENT;
    }

    private int footerDotColor() {
        int spent = powerDistribution.spent();
        int budget = PowerDistributionConfig.budget(tier);
        if (spent == budget) return COLOR_SUCCESS;
        if (spent < budget) return COLOR_NOT_READY;
        return COLOR_DANGER;
    }

    private double idealizedEuMultiplier() {
        double euDelta = Math.max(powerDistribution.getSpeedPU() - powerDistribution.getTuningPU(), -3);
        return Math.pow(2, euDelta);
    }

    private Component primaryTooltip() {
        int primaryPU = powerDistribution.getPrimaryPU();
        int bonusPerPU = ((IRecipeLogicMachine) getMachine()).getRecipeType().getPrimaryBonusPercentPerPU();
        if (primaryPU <= 0) {
            return Component.translatable("gtceu.power_distribution.primary.tooltip_live_voided");
        }
        if (primaryPU == 1) {
            return Component.translatable("gtceu.power_distribution.primary.tooltip_live_chance");
        }
        if (bonusPerPU == 0) {
            return Component.translatable("gtceu.power_distribution.primary.tooltip_live_no_bonus");
        }
        return Component.translatable("gtceu.power_distribution.primary.tooltip_live_bonus", primaryPU,
                (primaryPU - 2) * bonusPerPU);
    }

    private Component barLabelText() {
        return Component.literal(powerDistribution.spent() + "/" + PowerDistributionConfig.budget(tier) + " PU");
    }

    private Component footerText() {
        int spent = powerDistribution.spent();
        int budget = PowerDistributionConfig.budget(tier);
        String stateKey = spent == budget ? "status.ready" : spent < budget ? "status.unspent" : "status.over_budget";
        return Component.translatable("gtceu.power_distribution.status", spent, budget,
                Component.translatable("gtceu.power_distribution." + stateKey));
    }
}
