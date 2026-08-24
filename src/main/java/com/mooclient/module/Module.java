package com.mooclient.module;

/**
 * Base class for all Moo Client modules.
 * Each module represents a toggleable feature (e.g. Fullbright, ESP, HUD elements).
 */
public abstract class Module {

    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;

    public Module(String name, String description, Category category) {
        this(name, description, category, false);
    }

    public Module(String name, String description, Category category, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = defaultEnabled;
    }

    /**
     * Called when the module is enabled.
     */
    public void onEnable() {
    }

    /**
     * Called when the module is disabled.
     */
    public void onDisable() {
    }

    /**
     * Toggles the module on/off.
     */
    public void toggle() {
        this.enabled = !this.enabled;
        if (this.enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /**
     * Sets the enabled state directly.
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            toggle();
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Module categories for organizing in the GUI menu.
     */
    public enum Category {
        RENDER("Render"),
        HUD("HUD"),
        PVP("PvP"),
        MISC("Misc");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
