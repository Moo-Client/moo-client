package com.mooclient.util;

/**
 * Utility class for responsive HUD widget positioning, anchor management,
 * and boundary clamping across different screen sizes and GUI scales.
 */
public class MooHudPositionHelper {

    public enum HudAnchorX {
        LEFT,
        CENTER,
        RIGHT
    }

    public enum HudAnchorY {
        TOP,
        CENTER,
        BOTTOM
    }

    /**
     * Calculates the safe, clamped X coordinate for rendering a HUD widget.
     *
     * @param storedX       Configured X position (-1 for default anchor)
     * @param widgetWidth   Scaled visual width of the widget
     * @param screenWidth   Current scaled window width
     * @param defaultAnchor Default horizontal anchor
     * @param defaultMargin Margin in pixels from the anchor edge
     * @return Clamped X screen coordinate
     */
    public static int calculateRenderX(int storedX, int widgetWidth, int screenWidth, HudAnchorX defaultAnchor, int defaultMargin) {
        if (storedX < 0) {
            return switch (defaultAnchor) {
                case RIGHT -> Math.max(2, screenWidth - widgetWidth - defaultMargin);
                case CENTER -> Math.max(2, (screenWidth - widgetWidth) / 2 + defaultMargin);
                default -> Math.max(2, defaultMargin);
            };
        }
        int maxAllowed = Math.max(2, screenWidth - widgetWidth - 2);
        return Math.max(2, Math.min(maxAllowed, storedX));
    }

    /**
     * Calculates the safe, clamped Y coordinate for rendering a HUD widget.
     *
     * @param storedY        Configured Y position (-1 for default anchor)
     * @param widgetHeight   Scaled visual height of the widget
     * @param screenHeight   Current scaled window height
     * @param defaultAnchor  Default vertical anchor
     * @param defaultMargin  Margin or offset in pixels
     * @return Clamped Y screen coordinate
     */
    public static int calculateRenderY(int storedY, int widgetHeight, int screenHeight, HudAnchorY defaultAnchor, int defaultMargin) {
        if (storedY < 0) {
            return switch (defaultAnchor) {
                case BOTTOM -> Math.max(2, screenHeight - widgetHeight - defaultMargin);
                case CENTER -> Math.max(2, (screenHeight - widgetHeight) / 2 + defaultMargin);
                default -> Math.max(2, defaultMargin);
            };
        }
        int maxAllowed = Math.max(2, screenHeight - widgetHeight - 2);
        return Math.max(2, Math.min(maxAllowed, storedY));
    }

    /**
     * Helper to clamp and snap new coordinates during drag & drop.
     */
    public static int snapAndClampX(int rawX, int widgetWidth, int screenWidth, boolean snapping) {
        int x = rawX;
        if (snapping) {
            int snapDist = 12;
            int rightSnap = screenWidth - widgetWidth - 10;
            int centerSnap = (screenWidth - widgetWidth) / 2;

            if (Math.abs(x - 10) < snapDist) {
                x = 10;
            } else if (Math.abs(x - rightSnap) < snapDist) {
                x = rightSnap;
            } else if (Math.abs(x - centerSnap) < snapDist) {
                x = centerSnap;
            }
        }
        int maxAllowed = Math.max(2, screenWidth - widgetWidth - 2);
        return Math.max(2, Math.min(maxAllowed, x));
    }

    /**
     * Helper to clamp and snap new Y coordinates during drag & drop.
     */
    public static int snapAndClampY(int rawY, int widgetHeight, int screenHeight, boolean snapping) {
        int y = rawY;
        if (snapping) {
            int snapDist = 12;
            int bottomSnap = screenHeight - widgetHeight - 10;
            int centerSnap = (screenHeight - widgetHeight) / 2;

            if (Math.abs(y - 10) < snapDist) {
                y = 10;
            } else if (Math.abs(y - bottomSnap) < snapDist) {
                y = bottomSnap;
            } else if (Math.abs(y - centerSnap) < snapDist) {
                y = centerSnap;
            }
        }
        int maxAllowed = Math.max(2, screenHeight - widgetHeight - 2);
        return Math.max(2, Math.min(maxAllowed, y));
    }
}
