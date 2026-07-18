package com.gregtechceu.gtceu.api.gui.widget;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A flat-color percentage-fill bar: a fixed track plus a fill segment that is resized against
 * {@code fractionSupplier} every {@link #updateScreen()}. Unlike {@link DualProgressWidget}, this is a single
 * dynamically-colored fill, not a two-texture split at a fixed point.
 */
public class FillBarWidget extends WidgetGroup {

    private final int width;
    private final int height;
    private final DoubleSupplier fractionSupplier;
    private final ColorBlockWidget track;
    private final ColorBlockWidget fill;
    private Supplier<List<Component>> tooltipSupplier;

    public FillBarWidget(int x, int y, int width, int height, int trackColor, DoubleSupplier fractionSupplier,
                         IntSupplier fillColorSupplier) {
        super(x, y, width, height);
        this.width = width;
        this.height = height;
        this.fractionSupplier = fractionSupplier;

        this.track = new ColorBlockWidget(0, 0, width, height).setColorSupplier(() -> trackColor);
        this.fill = new ColorBlockWidget(0, 0, 0, height).setColorSupplier(fillColorSupplier);
        // Starts hidden — at width 0, ColorBlockWidget's 1px border inset would compute a negative draw
        // width. updateScreen() reveals it once a nonzero fraction is known, so no glitch frame is possible
        // even if a draw happens before the first updateScreen() call.
        this.fill.setVisible(false);
        addWidget(track);
        addWidget(fill);
    }

    /**
     * Recomputed every {@link #updateScreen()}, matching how {@code TankWidget} refreshes live tooltip text.
     * <p>
     * Applied to both leaf children ({@link #track} and {@link #fill}), not to this {@code WidgetGroup} itself —
     * {@code Widget.drawTooltipTexts()} only fires when {@code getHoverElement(mouseX, mouseY) == this}, and
     * {@code WidgetGroup.getHoverElement()} always delegates to whichever child claims the position, so a
     * WidgetGroup's own tooltip list is never actually drawn. Since {@code track} spans the full bar and
     * {@code fill} sits on top of it once visible, whichever one wins the hover check needs the same content.
     */
    public FillBarWidget setTooltipSupplier(Supplier<List<Component>> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tooltipSupplier != null) {
            List<Component> tooltips = tooltipSupplier.get();
            track.setHoverTooltips(tooltips);
            fill.setHoverTooltips(tooltips);
        }

        double fraction = Math.max(0.0, Math.min(1.0, fractionSupplier.getAsDouble()));
        int fillWidth = (int) Math.round(width * fraction);
        if (fillWidth <= 0) {
            fill.setVisible(false);
            return;
        }
        fill.setVisible(true);
        if (fill.getSize().width != fillWidth) {
            fill.setSize(new Size(fillWidth, height));
        }
    }
}
