package com.mooclient.module;

import com.mooclient.module.modules.FpsModule;
import com.mooclient.module.modules.FreelookModule;
import com.mooclient.module.modules.FullbrightModule;
import com.mooclient.module.modules.ToggleSprintModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton manager for Moo Client modules.
 */
public class ModuleManager {

    private static ModuleManager instance;
    private final List<Module> modules;

    private ModuleManager() {
        this.modules = new ArrayList<>();
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
        }
        return instance;
    }

    public void init() {
        register(new FullbrightModule()); // "Gamma"
        register(new FpsModule()); // "FPS"
        register(new ToggleSprintModule()); // "Sprint"
        register(new FreelookModule()); // "Freelook"
        register(new com.mooclient.module.modules.PotionEffectsModule()); // "Potion Effects"
        register(new com.mooclient.module.modules.NametagsModule()); // "Nametags"
        register(new com.mooclient.module.modules.ZoomModule()); // "Zoom"
        register(new com.mooclient.module.modules.MacroModule()); // "Macro"
        register(new com.mooclient.module.modules.ChatModule()); // "Chat"
        register(new com.mooclient.module.modules.PingModule()); // "Ping"
        register(new com.mooclient.module.modules.WaypointsModule()); // "Waypoints"
        register(new com.mooclient.module.modules.ScoreboardModule()); // "Scoreboard"
        register(new com.mooclient.module.modules.CpsModule()); // "CPS"
        register(new com.mooclient.module.modules.ArmorModule()); // "Armor"
        register(new com.mooclient.module.modules.ShulkerTooltipModule()); // "Shulker Tooltip"
        register(new com.mooclient.module.modules.EmotesModule()); // "Emotes"
        register(new com.mooclient.module.modules.InventoryViewModule()); // "Inventory View"
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public Optional<Module> getModule(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (clazz.isInstance(m)) {
                return clazz.cast(m);
            }
        }
        return null;
    }
}
