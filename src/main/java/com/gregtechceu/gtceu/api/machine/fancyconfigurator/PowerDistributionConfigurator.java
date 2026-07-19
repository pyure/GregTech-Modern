package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.ColorBlockWidget;
import com.gregtechceu.gtceu.api.gui.widget.FillBarWidget;
import com.gregtechceu.gtceu.api.machine.PowerDistributionConfig;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Four-dial "Power Distribution" panel — replaces the old per-slot Upgrade Slots panel.
 * <p>
 * Sync pattern mirrors the old per-slot Upgrade Slots panel: this configurator keeps a client-side
 * cached copy of the config, refreshed via {@link #writeInitialData} / {@link #readInitialData}
 * on GUI open and {@link #detectAndSendChange} / {@link #readUpdateInfo} while it's open. Buttons
 * mutate the real {@code machine.powerDistribution} field directly on the server and rely on that
 * same detection loop to push the change back to the client.
 */
public class PowerDistributionConfigurator implements IFancyConfigurator {

    private static final int UPDATE_CONFIG = 0;
    private static final int DIAL_TUNING = 0;
    private static final int DIAL_SPEED = 1;
    private static final int DIAL_PRIMARY = 2;
    private static final int DIAL_BYPRODUCT = 3;

    // Display order (Speed, Tuning, Output, Byprod) is separate from the dial identity constants above,
    // which stay stable since they index into PowerDistributionConfig's fields via the switches below.
    private static final int[] DISPLAY_ORDER = { DIAL_SPEED, DIAL_TUNING, DIAL_PRIMARY, DIAL_BYPRODUCT };

    private static final String[] DIAL_LANG_KEYS = { "tuning", "speed", "primary", "byproduct" };

    private static final int PANEL_WIDTH = 190;
    private static final int MARGIN = 4;
    private static final int BAR_HEIGHT = 12;
    private static final int ROW_HEIGHT = 22;
    private static final int MINI_BAR_X = 92;
    private static final int MINI_BAR_WIDTH = 40;
    private static final int MINI_BAR_HEIGHT = 8;
    private static final int VALUE_X = 138;
    private static final int MINUS_BUTTON_X = 160;
    private static final int PLUS_BUTTON_X = 174;
    private static final int BUTTON_SIZE = 9;

    // GTCEu Teal palette (ARGB, full opacity) — see specs/power-distribution-ui-redesign.md.
    // Only the subset actually used by this phase's flat-content elements; border/panel/header/dim are
    // part of the mock's full-flat-background variant, unused under the hybrid background decision.
    private static final int COLOR_HEADER_TEXT = 0xFFE4F2F0;
    private static final int COLOR_TEXT = 0xFFCFE4E1;
    private static final int COLOR_ACCENT = 0xFF2FB8AD;
    private static final int COLOR_HOT = 0xFFD9704A;
    private static final int COLOR_COOL = 0xFF3AA0D9;
    private static final int COLOR_TRACK = 0xFF33474A;
    private static final int COLOR_BAR_TRACK = 0xFF101C1D;
    private static final int COLOR_SUCCESS = 0xFF4FBF7A;
    private static final int COLOR_DANGER = 0xFFD9534F;
    private static final int COLOR_NOT_READY = 0xFF5C716F;

    // Idealized (non-floored, non-rounded) mirror of PowerDistributionConfig's own DURATION_CUT/EU_DELTA_FLOOR —
    // deliberately duplicated, not shared: tooltips show the continuous formula since no recipe/base-duration is
    // necessarily known at hover time, while the real modifier applies the per-recipe integer-tick floor
    // (durationMultiplier(baseDurationTicks)/euMultiplier(baseDurationTicks)). Keep in sync if those change.
    private static final double TOOLTIP_DURATION_CUT = 0.75;
    private static final double TOOLTIP_EU_DELTA_FLOOR = -3;

    private final SimpleTieredMachine machine;
    // client-side display copy; updated via writeInitialData / readUpdateInfo
    private PowerDistributionConfig clientConfig;

    public PowerDistributionConfigurator(SimpleTieredMachine machine) {
        this.machine = machine;
        this.clientConfig = machine.getPowerDistribution();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.power_distribution.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return new ResourceTexture("gtceu:textures/gui/icon/power_distribution_capacitor.png");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(getTitle());
    }

    @Override
    public Widget createConfigurator() {
        int rowsStartY = MARGIN + BAR_HEIGHT + MARGIN;
        int footerY = rowsStartY + DISPLAY_ORDER.length * ROW_HEIGHT + MARGIN;
        int totalHeight = footerY + MINI_BAR_HEIGHT + MARGIN;

        var group = new WidgetGroup(0, 0, PANEL_WIDTH, totalHeight);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);

        addAllocationBar(group);

        for (int row = 0; row < DISPLAY_ORDER.length; row++) {
            addDialRow(group, DISPLAY_ORDER[row], rowsStartY + row * ROW_HEIGHT);
        }

        addFooter(group, footerY);

        return group;
    }

    private void addAllocationBar(WidgetGroup group) {
        int barWidth = PANEL_WIDTH - MARGIN * 2;
        var bar = new FillBarWidget(MARGIN, MARGIN, barWidth, BAR_HEIGHT, COLOR_BAR_TRACK,
                this::getSpentFraction, this::getBarColor)
                .setTooltipSupplier(this::getBarTooltip);
        group.addWidget(bar);
        group.addWidget(new LabelWidget(MARGIN + 2, MARGIN + 2, this::getBarLabelText)
                .setTextColor(COLOR_HEADER_TEXT));
    }

    private void addDialRow(WidgetGroup group, int dial, int y) {
        group.addWidget(new LabelWidget(MARGIN, y + 4,
                Component.translatable("gtceu.power_distribution." + DIAL_LANG_KEYS[dial]).getString())
                .setTextColor(COLOR_TEXT));

        var miniBar = new FillBarWidget(MINI_BAR_X, y + (ROW_HEIGHT - MINI_BAR_HEIGHT) / 2, MINI_BAR_WIDTH,
                MINI_BAR_HEIGHT, COLOR_TRACK, () -> getRowFraction(dial), () -> COLOR_ACCENT)
                .setTooltipSupplier(() -> getLiveDialTooltip(dial));
        group.addWidget(miniBar);

        group.addWidget(new LabelWidget(VALUE_X, y + 4, () -> String.valueOf(getClientValue(dial)))
                .setTextColor(COLOR_ACCENT));

        ButtonWidget minusButton = new ButtonWidget(MINUS_BUTTON_X, y + (ROW_HEIGHT - BUTTON_SIZE) / 2, BUTTON_SIZE,
                BUTTON_SIZE, GuiTextures.BUTTON_INT_CIRCUIT_MINUS,
                clickData -> {
                    if (!clickData.isRemote) adjust(dial, -1);
                });
        group.addWidget(minusButton);

        ButtonWidget plusButton = new ButtonWidget(PLUS_BUTTON_X, y + (ROW_HEIGHT - BUTTON_SIZE) / 2, BUTTON_SIZE,
                BUTTON_SIZE, GuiTextures.BUTTON_INT_CIRCUIT_PLUS,
                clickData -> {
                    if (!clickData.isRemote) adjust(dial, 1);
                });
        group.addWidget(plusButton);
    }

    private void addFooter(WidgetGroup group, int y) {
        var dot = new ColorBlockWidget(MARGIN, y + (MINI_BAR_HEIGHT - 8) / 2, 8, 8)
                .setColorSupplier(this::getFooterDotColor);
        // Static content (unlike the bar/row tooltips) — the 3-line explainer doesn't depend on current state,
        // so it's set once here rather than recomputed every updateScreen().
        dot.setHoverTooltips(getFooterTooltip());
        group.addWidget(dot);
        group.addWidget(new LabelWidget(MARGIN + 12, y + 1, this::getBudgetStatusText).setTextColor(COLOR_TEXT));
    }

    private List<Component> getLiveDialTooltip(int dial) {
        return switch (dial) {
            case DIAL_TUNING -> getTuningTooltip();
            case DIAL_SPEED -> getSpeedTooltip();
            case DIAL_PRIMARY -> getPrimaryTooltip();
            default -> getByproductTooltip();
        };
    }

    private List<Component> getSpeedTooltip() {
        int pu = clientConfig.getSpeedPU();
        double durationMultiplier = Math.pow(TOOLTIP_DURATION_CUT, pu - 2);
        return List.of(
                Component.translatable("gtceu.power_distribution.speed"),
                Component.translatable("gtceu.power_distribution.speed.tooltip_live", pu, format2(durationMultiplier)));
    }

    private List<Component> getTuningTooltip() {
        int pu = clientConfig.getTuningPU();
        double idealizedDurationMultiplier = Math.pow(TOOLTIP_DURATION_CUT, clientConfig.getSpeedPU() - 2);
        double effectiveEuDelta = Math.max(clientConfig.getSpeedPU() - pu, TOOLTIP_EU_DELTA_FLOOR);
        double euMultiplier = (1.0 / idealizedDurationMultiplier) * Math.pow(2, effectiveEuDelta);
        return List.of(
                Component.translatable("gtceu.power_distribution.tuning"),
                Component.translatable("gtceu.power_distribution.tuning.tooltip_live", pu, format2(euMultiplier)));
    }

    private List<Component> getPrimaryTooltip() {
        int pu = clientConfig.getPrimaryPU();
        int bonusPercentPerPU = machine.getRecipeType().getPrimaryBonusPercentPerPU();
        Component name = Component.translatable("gtceu.power_distribution.primary");
        Component effect;
        if (bonusPercentPerPU == 0) {
            effect = Component.translatable("gtceu.power_distribution.primary.tooltip_live_no_bonus");
        } else if (pu <= 0) {
            effect = Component.translatable("gtceu.power_distribution.primary.tooltip_live_voided");
        } else if (pu == 1) {
            effect = Component.translatable("gtceu.power_distribution.primary.tooltip_live_chance", pu);
        } else {
            int bonusPercent = (pu - 2) * bonusPercentPerPU;
            effect = Component.translatable("gtceu.power_distribution.primary.tooltip_live_bonus", pu, bonusPercent);
        }
        return List.of(name, effect);
    }

    private List<Component> getByproductTooltip() {
        return List.of(
                Component.translatable("gtceu.power_distribution.byproduct"),
                Component.translatable("gtceu.power_distribution.byproduct.tooltip"));
    }

    private List<Component> getBarTooltip() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        int spent = clientConfig.spent();
        return List.of(
                Component.translatable("gtceu.power_distribution.bar_tooltip.line1", spent, budget),
                Component.translatable("gtceu.power_distribution.bar_tooltip.line2"));
    }

    private static List<Component> getFooterTooltip() {
        return List.of(
                Component.translatable("gtceu.power_distribution.footer_tooltip.ready"),
                Component.translatable("gtceu.power_distribution.footer_tooltip.not_ready"),
                Component.translatable("gtceu.power_distribution.footer_tooltip.invalid"));
    }

    private static String format2(double value) {
        return String.format("%.2f", value);
    }

    private int getClientValue(int dial) {
        return switch (dial) {
            case DIAL_TUNING -> clientConfig.getTuningPU();
            case DIAL_SPEED -> clientConfig.getSpeedPU();
            case DIAL_PRIMARY -> clientConfig.getPrimaryPU();
            default -> clientConfig.getByproductPU();
        };
    }

    private double getRowFraction(int dial) {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        if (budget <= 0) return 0.0;
        return (double) getClientValue(dial) / budget;
    }

    private double getSpentFraction() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        if (budget <= 0) return 0.0;
        return (double) clientConfig.spent() / budget;
    }

    private int getBarColor() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        if (clientConfig.spent() != budget) return COLOR_NOT_READY;
        if (clientConfig.getSpeedPU() > clientConfig.getTuningPU()) return COLOR_HOT;
        if (clientConfig.getSpeedPU() < clientConfig.getTuningPU()) return COLOR_COOL;
        return COLOR_ACCENT;
    }

    private String getBarLabelText() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        return clientConfig.spent() + "/" + budget + " PU";
    }

    private int getFooterDotColor() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        int spent = clientConfig.spent();
        if (spent == budget) return COLOR_SUCCESS;
        if (spent > budget) return COLOR_DANGER;
        return COLOR_NOT_READY;
    }

    private int getMin(int dial) {
        return switch (dial) {
            case DIAL_SPEED -> 1;
            default -> 0;
        };
    }

    private int getMax(int dial) {
        return Integer.MAX_VALUE;
    }

    private void adjust(int dial, int delta) {
        PowerDistributionConfig pd = machine.getPowerDistribution();
        int current = switch (dial) {
            case DIAL_TUNING -> pd.getTuningPU();
            case DIAL_SPEED -> pd.getSpeedPU();
            case DIAL_PRIMARY -> pd.getPrimaryPU();
            default -> pd.getByproductPU();
        };
        int next = Mth.clamp(current + delta, getMin(dial), getMax(dial));
        if (next == current) return;

        PowerDistributionConfig candidate = switch (dial) {
            case DIAL_TUNING -> new PowerDistributionConfig(next, pd.getSpeedPU(), pd.getPrimaryPU(),
                    pd.getByproductPU());
            case DIAL_SPEED -> new PowerDistributionConfig(pd.getTuningPU(), next, pd.getPrimaryPU(),
                    pd.getByproductPU());
            case DIAL_PRIMARY -> new PowerDistributionConfig(pd.getTuningPU(), pd.getSpeedPU(), next,
                    pd.getByproductPU());
            default -> new PowerDistributionConfig(pd.getTuningPU(), pd.getSpeedPU(), pd.getPrimaryPU(), next);
        };
        // Under-budget (unspent) is fine; over-budget is not — reject moves that would cross into it.
        // This blocks both raising a sink past the available budget and lowering Tuning below current spend.
        if (candidate.isOverBudget(machine.getTier())) return;

        machine.powerDistribution = candidate;
    }

    private String getBudgetStatusText() {
        int budget = PowerDistributionConfig.budget(machine.getTier());
        int spent = clientConfig.spent();
        String state;
        if (spent > budget) {
            state = Component.translatable("gtceu.power_distribution.status.over_budget").getString();
        } else if (spent < budget) {
            state = Component.translatable("gtceu.power_distribution.status.unspent").getString();
        } else {
            state = Component.translatable("gtceu.power_distribution.status.ready").getString();
        }
        return Component.translatable("gtceu.power_distribution.status", spent, budget, state).getString();
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        writeConfig(buffer, machine.getPowerDistribution());
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        clientConfig = readConfig(buffer);
    }

    @Override
    public void detectAndSendChange(BiConsumer<Integer, Consumer<FriendlyByteBuf>> sender) {
        PowerDistributionConfig current = machine.getPowerDistribution();
        if (!configEquals(current, clientConfig)) {
            clientConfig = current;
            sender.accept(UPDATE_CONFIG, buf -> writeConfig(buf, current));
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buf) {
        if (id == UPDATE_CONFIG) {
            clientConfig = readConfig(buf);
        }
    }

    private static void writeConfig(FriendlyByteBuf buf, PowerDistributionConfig config) {
        buf.writeVarInt(config.getTuningPU());
        buf.writeVarInt(config.getSpeedPU());
        buf.writeVarInt(config.getPrimaryPU());
        buf.writeVarInt(config.getByproductPU());
    }

    private static PowerDistributionConfig readConfig(FriendlyByteBuf buf) {
        return new PowerDistributionConfig(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private static boolean configEquals(PowerDistributionConfig a, PowerDistributionConfig b) {
        return a.getTuningPU() == b.getTuningPU() && a.getSpeedPU() == b.getSpeedPU() &&
                a.getPrimaryPU() == b.getPrimaryPU() && a.getByproductPU() == b.getByproductPU();
    }
}
