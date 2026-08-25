package com.mooclient.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for responsive HUD widget positioning, anchor management,
 * smart magnetic snapping, and alignment guidelines.
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
     * Visual alignment guideline rendered during HUD drag & drop.
     */
    public static class GuideLine {
        public int x1, y1, x2, y2;
        public int color;

        public GuideLine(int x1, int y1, int x2, int y2, int color) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
        }
    }

    /**
     * Screen bounding box of a HUD widget used for multi-widget snapping.
     */
    public static class WidgetRect {
        public String id;
        public int x, y, width, height;

        public WidgetRect(String id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Result of a smart snap calculation containing new coordinates and active guidelines.
     */
    public static class SnapResult {
        public int snappedX;
        public int snappedY;
        public List<GuideLine> guideLines = new ArrayList<>();

        public SnapResult(int snappedX, int snappedY) {
            this.snappedX = snappedX;
            this.snappedY = snappedY;
        }
    }

    /**
     * Represents a responsive widget position anchored to screen edges or center.
     */
    public static class WidgetPosition {
        public HudAnchorX anchorX = HudAnchorX.LEFT;
        public HudAnchorY anchorY = HudAnchorY.TOP;
        public int offsetX = 6;
        public int offsetY = 6;

        public WidgetPosition(HudAnchorX anchorX, HudAnchorY anchorY, int offsetX, int offsetY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public int calculateX(int widgetWidth, int screenWidth) {
            int x = switch (anchorX) {
                case LEFT -> offsetX;
                case CENTER -> (screenWidth - widgetWidth) / 2 + offsetX;
                case RIGHT -> screenWidth - widgetWidth - offsetX;
            };
            int maxAllowed = Math.max(2, screenWidth - widgetWidth - 2);
            return Math.max(2, Math.min(maxAllowed, x));
        }

        public int calculateY(int widgetHeight, int screenHeight) {
            int y = switch (anchorY) {
                case TOP -> offsetY;
                case CENTER -> (screenHeight - widgetHeight) / 2 + offsetY;
                case BOTTOM -> screenHeight - widgetHeight - offsetY;
            };
            int maxAllowed = Math.max(2, screenHeight - widgetHeight - 2);
            return Math.max(2, Math.min(maxAllowed, y));
        }

        public void setFromScreenCoords(int x, int y, int widgetWidth, int widgetHeight, int screenWidth, int screenHeight) {
            if (screenWidth <= 0 || screenHeight <= 0) return;

            // Determine best horizontal anchor
            int centerX = x + widgetWidth / 2;
            if (centerX < screenWidth * 0.35f) {
                this.anchorX = HudAnchorX.LEFT;
                this.offsetX = Math.max(0, x);
            } else if (centerX > screenWidth * 0.65f) {
                this.anchorX = HudAnchorX.RIGHT;
                this.offsetX = Math.max(0, screenWidth - widgetWidth - x);
            } else {
                this.anchorX = HudAnchorX.CENTER;
                this.offsetX = x - (screenWidth - widgetWidth) / 2;
            }

            // Determine best vertical anchor
            int centerY = y + widgetHeight / 2;
            if (centerY < screenHeight * 0.35f) {
                this.anchorY = HudAnchorY.TOP;
                this.offsetY = Math.max(0, y);
            } else if (centerY > screenHeight * 0.65f) {
                this.anchorY = HudAnchorY.BOTTOM;
                this.offsetY = Math.max(0, screenHeight - widgetHeight - y);
            } else {
                this.anchorY = HudAnchorY.CENTER;
                this.offsetY = y - (screenHeight - widgetHeight) / 2;
            }
        }
    }

    /**
     * Advanced smart snapping engine calculating magnetic snaps to screen axes and other widgets.
     */
    public static SnapResult calculateSmartSnap(int rawX, int rawY, int widgetWidth, int widgetHeight,
                                                int screenWidth, int screenHeight, List<WidgetRect> others,
                                                boolean snapping, int accentColor) {
        int x = rawX;
        int y = rawY;
        List<GuideLine> lines = new ArrayList<>();

        if (snapping) {
            int snapDist = 10;
            int guideColor = (accentColor & 0x00FFFFFF) != 0 ? (0xFF000000 | (accentColor & 0x00FFFFFF)) : 0xFF55FF55;
            int centerGuideColor = 0xFF38BDF8;
            int edgeGuideColor = 0xAAFFFFFF;

            // --- Horizontal Snapping ---
            int bestX = x;
            int bestXDiff = snapDist + 1;
            GuideLine bestXLine = null;

            // 1. Screen Left Margins (6px default, 10px standard)
            int[] leftMargins = new int[] { 6, 10 };
            for (int margin : leftMargins) {
                int leftDiff = Math.abs(x - margin);
                if (leftDiff <= snapDist && leftDiff < bestXDiff) {
                    bestXDiff = leftDiff;
                    bestX = margin;
                    bestXLine = new GuideLine(margin, 0, margin, screenHeight, edgeGuideColor);
                }
            }

            // 2. Screen Horizontal Center
            int screenCenterX = (screenWidth - widgetWidth) / 2;
            int centerDiff = Math.abs(x - screenCenterX);
            if (centerDiff <= snapDist && centerDiff < bestXDiff) {
                bestXDiff = centerDiff;
                bestX = screenCenterX;
                bestXLine = new GuideLine(screenWidth / 2, 0, screenWidth / 2, screenHeight, centerGuideColor);
            }

            // 3. Screen Right Margins (6px default, 10px standard)
            int[] rightMargins = new int[] { 6, 10 };
            for (int margin : rightMargins) {
                int screenRightX = screenWidth - widgetWidth - margin;
                int rightDiff = Math.abs(x - screenRightX);
                if (rightDiff <= snapDist && rightDiff < bestXDiff) {
                    bestXDiff = rightDiff;
                    bestX = screenRightX;
                    bestXLine = new GuideLine(screenWidth - margin, 0, screenWidth - margin, screenHeight, edgeGuideColor);
                }
            }

            // 4. Snap to Other Active Widgets
            if (others != null) {
                for (WidgetRect o : others) {
                    int minY = Math.min(y, o.y) - 8;
                    int maxY = Math.max(y + widgetHeight, o.y + o.height) + 8;

                    // Left to Left
                    int lDiff = Math.abs(x - o.x);
                    if (lDiff <= snapDist && lDiff < bestXDiff) {
                        bestXDiff = lDiff;
                        bestX = o.x;
                        bestXLine = new GuideLine(o.x, minY, o.x, maxY, guideColor);
                    }

                    // Right to Right
                    int rTarget = o.x + o.width - widgetWidth;
                    int rDiff = Math.abs(x - rTarget);
                    if (rDiff <= snapDist && rDiff < bestXDiff) {
                        bestXDiff = rDiff;
                        bestX = rTarget;
                        bestXLine = new GuideLine(o.x + o.width, minY, o.x + o.width, maxY, guideColor);
                    }

                    // Center to Center
                    int cTarget = o.x + (o.width - widgetWidth) / 2;
                    int cDiff = Math.abs(x - cTarget);
                    if (cDiff <= snapDist && cDiff < bestXDiff) {
                        bestXDiff = cDiff;
                        bestX = cTarget;
                        int cX = o.x + o.width / 2;
                        bestXLine = new GuideLine(cX, minY, cX, maxY, guideColor);
                    }

                    // Side by side: Right of other widget (gaps: 0px, 2px, 4px, 6px)
                    int[] sideGaps = new int[] { 0, 2, 4, 6 };
                    for (int gap : sideGaps) {
                        int sideRightTarget = o.x + o.width + gap;
                        int srDiff = Math.abs(x - sideRightTarget);
                        if (srDiff <= snapDist && srDiff < bestXDiff) {
                            bestXDiff = srDiff;
                            bestX = sideRightTarget;
                            bestXLine = new GuideLine(o.x + o.width + gap / 2, minY, o.x + o.width + gap / 2, maxY, guideColor);
                        }

                        // Side by side: Left of other widget (gaps: 0px, 2px, 4px, 6px)
                        int sideLeftTarget = o.x - widgetWidth - gap;
                        int slDiff = Math.abs(x - sideLeftTarget);
                        if (slDiff <= snapDist && slDiff < bestXDiff) {
                            bestXDiff = slDiff;
                            bestX = sideLeftTarget;
                            bestXLine = new GuideLine(o.x - gap / 2, minY, o.x - gap / 2, maxY, guideColor);
                        }
                    }
                }
            }

            if (bestXLine != null) {
                x = bestX;
                lines.add(bestXLine);
            }

            // --- Vertical Snapping ---
            int bestY = y;
            int bestYDiff = snapDist + 1;
            GuideLine bestYLine = null;

            // 1. Screen Top Margins (6px default, 10px standard)
            int[] topMargins = new int[] { 6, 10 };
            for (int margin : topMargins) {
                int topDiff = Math.abs(y - margin);
                if (topDiff <= snapDist && topDiff < bestYDiff) {
                    bestYDiff = topDiff;
                    bestY = margin;
                    bestYLine = new GuideLine(0, margin, screenWidth, margin, edgeGuideColor);
                }
            }

            // 2. Screen Vertical Center
            int screenCenterY = (screenHeight - widgetHeight) / 2;
            int vCenterDiff = Math.abs(y - screenCenterY);
            if (vCenterDiff <= snapDist && vCenterDiff < bestYDiff) {
                bestYDiff = vCenterDiff;
                bestY = screenCenterY;
                bestYLine = new GuideLine(0, screenHeight / 2, screenWidth, screenHeight / 2, centerGuideColor);
            }

            // 3. Screen Bottom Margins (6px default, 10px standard)
            int[] bottomMargins = new int[] { 6, 10 };
            for (int margin : bottomMargins) {
                int screenBottomY = screenHeight - widgetHeight - margin;
                int bottomDiff = Math.abs(y - screenBottomY);
                if (bottomDiff <= snapDist && bottomDiff < bestYDiff) {
                    bestYDiff = bottomDiff;
                    bestY = screenBottomY;
                    bestYLine = new GuideLine(0, screenHeight - margin, screenWidth, screenHeight - margin, edgeGuideColor);
                }
            }

            // 4. Snap to Other Active Widgets (Top, Bottom, Center, Stacked Under/Above)
            if (others != null) {
                for (WidgetRect o : others) {
                    int minX = Math.min(x, o.x) - 8;
                    int maxX = Math.max(x + widgetWidth, o.x + o.width) + 8;

                    // Top to Top
                    int tDiff = Math.abs(y - o.y);
                    if (tDiff <= snapDist && tDiff < bestYDiff) {
                        bestYDiff = tDiff;
                        bestY = o.y;
                        bestYLine = new GuideLine(minX, o.y, maxX, o.y, guideColor);
                    }

                    // Bottom to Bottom
                    int bTarget = o.y + o.height - widgetHeight;
                    int bDiff = Math.abs(y - bTarget);
                    if (bDiff <= snapDist && bDiff < bestYDiff) {
                        bestYDiff = bDiff;
                        bestY = bTarget;
                        bestYLine = new GuideLine(minX, o.y + o.height, maxX, o.y + o.height, guideColor);
                    }

                    // Center to Center
                    int cTarget = o.y + (o.height - widgetHeight) / 2;
                    int cDiff = Math.abs(y - cTarget);
                    if (cDiff <= snapDist && cDiff < bestYDiff) {
                        bestYDiff = cDiff;
                        bestY = cTarget;
                        int cY = o.y + o.height / 2;
                        bestYLine = new GuideLine(minX, cY, maxX, cY, guideColor);
                    }

                    // Stacked: directly below other widget (gaps: 0px, 2px, 4px, 6px)
                    int[] stackGaps = new int[] { 0, 2, 4, 6 };
                    for (int gap : stackGaps) {
                        int stackBelowTarget = o.y + o.height + gap;
                        int sbDiff = Math.abs(y - stackBelowTarget);
                        if (sbDiff <= snapDist && sbDiff < bestYDiff) {
                            bestYDiff = sbDiff;
                            bestY = stackBelowTarget;
                            bestYLine = new GuideLine(minX, o.y + o.height + gap / 2, maxX, o.y + o.height + gap / 2, guideColor);
                        }

                        // Stacked: directly above other widget (gaps: 0px, 2px, 4px, 6px)
                        int stackAboveTarget = o.y - widgetHeight - gap;
                        int saDiff = Math.abs(y - stackAboveTarget);
                        if (saDiff <= snapDist && saDiff < bestYDiff) {
                            bestYDiff = saDiff;
                            bestY = stackAboveTarget;
                            bestYLine = new GuideLine(minX, o.y - gap / 2, maxX, o.y - gap / 2, guideColor);
                        }
                    }
                }
            }

            if (bestYLine != null) {
                y = bestY;
                lines.add(bestYLine);
            }
        }

        // Clamp inside screen bounds
        int maxAllowedX = Math.max(2, screenWidth - widgetWidth - 2);
        int maxAllowedY = Math.max(2, screenHeight - widgetHeight - 2);
        int finalX = Math.max(2, Math.min(maxAllowedX, x));
        int finalY = Math.max(2, Math.min(maxAllowedY, y));

        SnapResult res = new SnapResult(finalX, finalY);
        res.guideLines = lines;
        return res;
    }

    /**
     * Calculates the safe, clamped X coordinate for rendering a HUD widget.
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
     * Helper to clamp and snap new coordinates during drag & drop (legacy fallback).
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
     * Helper to clamp and snap new Y coordinates during drag & drop (legacy fallback).
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
