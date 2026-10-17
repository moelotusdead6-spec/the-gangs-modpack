package com.elysium.goldclaimmap;

import java.awt.geom.Point2D;
import java.util.List;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.PolygonHelper;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JourneyMapPlugin(apiVersion = "2.0.0")
public class GoldClaimJourneyMapPlugin implements IClientPlugin {
    public static final String MOD_ID = "goldclaimmap";

    private static final int GOLD = 0xFFD700;
    private static final int AMBER = 0xFFAA00;
    private static final int LABEL_TEXT = 0xFFF2B3;
    private static final int POLYGON_Y = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile IClientAPI api;
    private static volatile List<ClaimRegion> claims = List.of();

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        api = clientApi;
        refresh();
    }

    static void setClaims(List<ClaimRegion> updated) {
        claims = List.copyOf(updated);
        refresh();
    }

    private static synchronized void refresh() {
        IClientAPI current = api;
        if (current == null) {
            return;
        }
        current.removeAll(MOD_ID);
        for (ClaimRegion claim : claims) {
            try {
                current.show(createClaimOverlay(claim));
            } catch (Exception e) {
                LOGGER.warn("Could not show claim overlay {}", claim, e);
            }
        }
    }

    public static PolygonOverlay createClaimOverlay(ClaimRegion claim) {
        // Claim bounds are inclusive block coordinates; the polygon traces block edges.
        MapPolygon area = PolygonHelper.createBlockRect(
                new BlockPos(claim.minX(), POLYGON_Y, claim.minZ()),
                new BlockPos(claim.maxX() + 1, POLYGON_Y, claim.maxZ() + 1));

        ShapeProperties shape = new ShapeProperties()
                .setStrokeColor(GOLD)
                .setStrokeOpacity(0.8f)
                .setStrokeWidth(1.5f)
                .setFillColor(AMBER)
                .setFillOpacity(0.12f);

        TextProperties text = new TextProperties()
                .setActiveUIs(Context.UI.Fullscreen)
                .setColor(LABEL_TEXT)
                .setOpacity(0.7f)
                .setBackgroundColor(0x000000)
                .setBackgroundOpacity(0.3f)
                .setFontShadow(true);

        PolygonOverlay overlay = new PolygonOverlay(MOD_ID, claim.dimension(), shape, area);
        overlay.setActiveUIs(Context.UI.Fullscreen);
        overlay.setTextProperties(text);
        overlay.setOverlayListener(new HoverLabel(overlay, labelFor(claim)));
        return overlay;
    }

    private static String labelFor(ClaimRegion claim) {
        if (claim.admin()) {
            return "Server claim";
        }
        return claim.ownerName().isBlank() ? "Claimed" : claim.ownerName();
    }

    /** Shows the owner label only while the cursor is over the claim. */
    private record HoverLabel(PolygonOverlay overlay, String label) implements IOverlayListener {
        @Override
        public void onMouseMove(UIState state, Point2D.Double mousePosition, BlockPos blockPosition) {
            if (!this.label.equals(this.overlay.getLabel())) {
                this.overlay.setLabel(this.label);
                this.overlay.flagForRerender();
            }
        }

        @Override
        public void onMouseOut(UIState state, Point2D.Double mousePosition, BlockPos blockPosition) {
            this.hide();
        }

        @Override
        public void onDeactivate(UIState state) {
            this.hide();
        }

        private void hide() {
            if (this.overlay.getLabel() != null) {
                this.overlay.setLabel(null);
                this.overlay.flagForRerender();
            }
        }
    }
}
