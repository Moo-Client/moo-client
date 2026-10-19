const { contextBridge, ipcRenderer, webUtils } = require('electron');

/**
 * Preload script — exposes a safe API to the renderer process.
 * This is the bridge between Electron's main process and the UI.
 */
contextBridge.exposeInMainWorld('mooAPI', {
    // Window controls
    minimize: () => ipcRenderer.send('window-minimize'),
    maximize: () => ipcRenderer.send('window-maximize'),
    close: () => ipcRenderer.send('window-close'),

    // Game
    launchGame: (options) => ipcRenderer.invoke('launch-game', options),

    // Account & Authentication (Microsoft Premium)
    loginMicrosoft: () => ipcRenderer.invoke('login-microsoft'),
    logoutMicrosoft: () => ipcRenderer.invoke('logout-microsoft'),
    validateSession: () => ipcRenderer.invoke('validate-session'),
    refreshSession: () => ipcRenderer.invoke('refresh-session'),
    getAccount: () => ipcRenderer.invoke('get-account'),
    getAccounts: () => ipcRenderer.invoke('get-accounts'),
    selectAccount: (uuid) => ipcRenderer.invoke('select-account', uuid),
    removeAccount: (uuid) => ipcRenderer.invoke('remove-account', uuid),

    // Settings
    getSettings: () => ipcRenderer.invoke('get-settings'),
    saveSettings: (settings) => ipcRenderer.invoke('save-settings', settings),
    selectJavaPath: () => ipcRenderer.invoke('select-java-path'),

    // Mod info & Modrinth Integration
    getModInfo: () => ipcRenderer.invoke('get-mod-info'),
    searchModrinth: (params) => ipcRenderer.invoke('search-modrinth', params),
    installMod: (projectId, gameVersion) => {
        if (typeof projectId === 'object') {
            return ipcRenderer.invoke('install-mod', projectId);
        }
        return ipcRenderer.invoke('install-mod', { projectId, gameVersion });
    },
    getInstalledMods: (gameVersion = '1.21.4') => ipcRenderer.invoke('get-installed-mods', gameVersion),
    uninstallMod: (filename, gameVersion = '1.21.4') => ipcRenderer.invoke('uninstall-mod', { filename, gameVersion }),
    toggleMod: (filename, enabled, gameVersion = '1.21.4') => ipcRenderer.invoke('toggle-mod', { filename, enabled, gameVersion }),
    installLocalMods: (filePaths, gameVersion = '1.21.4') => ipcRenderer.invoke('install-local-mods', { filePaths, gameVersion }),
    saveModFile: (filename, buffer, gameVersion = '1.21.4') => ipcRenderer.invoke('save-mod-file', { filename, buffer, gameVersion }),
    getModVersions: (projectId, allVersions = false, gameVersion = '1.21.4') => {
        if (typeof projectId === 'object') {
            return ipcRenderer.invoke('get-mod-versions', projectId);
        }
        return ipcRenderer.invoke('get-mod-versions', { projectId, allVersions, gameVersion });
    },
    installModVersion: (versionId, oldFilename, gameVersion = '1.21.4') => ipcRenderer.invoke('install-mod-version', { versionId, oldFilename, gameVersion }),
    checkModUpdates: (gameVersion = '1.21.4') => ipcRenderer.invoke('check-mod-updates', gameVersion),
    getFilePath: (file) => {
        try {
            if (webUtils && typeof webUtils.getPathForFile === 'function') {
                return webUtils.getPathForFile(file);
            }
        } catch (e) {}
        return file && file.path ? file.path : '';
    },
    autoUpgradeMods: (gameVersion = '1.21.11') => ipcRenderer.invoke('auto-upgrade-mods', { gameVersion }),
    migrateAndUpgradeMods: (fromVersion = '1.21.4', toVersion = '1.21.11') => ipcRenderer.invoke('migrate-and-upgrade-mods', { fromVersion, toVersion }),
    openModsFolder: (gameVersion = '1.21.4') => ipcRenderer.invoke('open-mods-folder', gameVersion),

    // Moo Client Core Update
    checkClientUpdate: () => ipcRenderer.invoke('check-client-update'),
    performClientUpdate: () => ipcRenderer.invoke('perform-client-update'),

    // Event listeners
    onAccountUpdated: (callback) => ipcRenderer.on('account-updated', (_, data) => callback(data)),
    onLaunchStatus: (callback) => ipcRenderer.on('launch-status', (_, data) => callback(data)),
    onLaunchProgress: (callback) => ipcRenderer.on('launch-progress', (_, data) => callback(data)),
    onUpdaterStatus: (callback) => ipcRenderer.on('updater-status', (_, data) => callback(data)),
    onUpdaterProgress: (callback) => ipcRenderer.on('updater-progress', (_, data) => callback(data)),
    onClientUpdateProgress: (callback) => ipcRenderer.on('client-update-progress', (_, data) => callback(data)),
});
