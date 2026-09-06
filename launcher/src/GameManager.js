const { Client, Authenticator } = require('minecraft-launcher-core');
const msmc = require('msmc');
const path = require('path');
const fs = require('fs');
const os = require('os');
const https = require('https');

/**
 * GameManager — handles Minecraft installation, launching and Microsoft Authentication.
 */
class GameManager {
    constructor() {
        this.gameDir = path.join(os.homedir(), '.mooclient');
        this.settingsPath = path.join(this.gameDir, 'settings.json');
        this.accountsPath = path.join(this.gameDir, 'accounts.json');
        this.accountPath = path.join(this.gameDir, 'account.json');
        this.modsDir = path.join(this.gameDir, 'mods');

        this.ensureDir(this.gameDir);
        this.ensureDir(this.modsDir);

        this.defaultSettings = {
            username: 'MooPlayer',
            ram: '4',
            javaPath: '',
            resolution: { width: 1280, height: 720 },
        };
    }

    ensureDir(dir) {
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
    }

    getSettings() {
        try {
            if (fs.existsSync(this.settingsPath)) {
                const data = fs.readFileSync(this.settingsPath, 'utf8');
                return { ...this.defaultSettings, ...JSON.parse(data) };
            }
        } catch (e) {
            console.error('Error reading settings:', e);
        }
        return { ...this.defaultSettings };
    }

    saveSettings(settings) {
        try {
            this.ensureDir(this.gameDir);
            fs.writeFileSync(this.settingsPath, JSON.stringify(settings, null, 2));
        } catch (e) {
            console.error('Error saving settings:', e);
        }
    }

    getAccountsData() {
        try {
            if (fs.existsSync(this.accountsPath)) {
                const data = JSON.parse(fs.readFileSync(this.accountsPath, 'utf8'));
                if (data && Array.isArray(data.accounts)) return data;
            }
            // Migration from legacy single account.json
            if (fs.existsSync(this.accountPath)) {
                const legacy = JSON.parse(fs.readFileSync(this.accountPath, 'utf8'));
                if (legacy && legacy.name) {
                    const data = {
                        activeUuid: legacy.uuid || legacy.name,
                        accounts: [legacy]
                    };
                    this.saveAccountsData(data);
                    return data;
                }
            }
        } catch (e) {
            console.error('Error reading accounts.json:', e);
        }
        return { activeUuid: null, accounts: [] };
    }

    saveAccountsData(data) {
        try {
            this.ensureDir(this.gameDir);
            fs.writeFileSync(this.accountsPath, JSON.stringify(data, null, 2));

            // Sync active account to account.json for compatibility
            const active = data.accounts.find(a => a.uuid === data.activeUuid) || (data.accounts.length > 0 ? data.accounts[0] : null);
            if (active) {
                fs.writeFileSync(this.accountPath, JSON.stringify(active, null, 2));
            } else if (fs.existsSync(this.accountPath)) {
                fs.unlinkSync(this.accountPath);
            }
        } catch (e) {
            console.error('Error saving accounts.json:', e);
        }
    }

    getAllAccounts() {
        const data = this.getAccountsData();
        return {
            activeUuid: data.activeUuid,
            accounts: data.accounts.map(acc => ({
                name: acc.name,
                uuid: acc.uuid,
                type: acc.type || 'microsoft',
                isActive: acc.uuid === data.activeUuid
            }))
        };
    }

    getAccount() {
        const data = this.getAccountsData();
        if (!data.accounts || data.accounts.length === 0) return null;
        return data.accounts.find(a => a.uuid === data.activeUuid) || data.accounts[0] || null;
    }

    saveAccount(account) {
        const data = this.getAccountsData();
        if (!account) {
            if (data.activeUuid) {
                data.accounts = data.accounts.filter(a => a.uuid !== data.activeUuid);
                data.activeUuid = data.accounts.length > 0 ? data.accounts[0].uuid : null;
            } else {
                data.accounts = [];
                data.activeUuid = null;
            }
        } else {
            const existingIdx = data.accounts.findIndex(a => a.uuid === account.uuid || (a.name && account.name && a.name.toLowerCase() === account.name.toLowerCase()));
            if (existingIdx >= 0) {
                data.accounts[existingIdx] = account;
            } else {
                data.accounts.push(account);
            }
            data.activeUuid = account.uuid;
        }
        this.saveAccountsData(data);
    }

    selectAccount(uuid) {
        const data = this.getAccountsData();
        const found = data.accounts.find(a => a.uuid === uuid);
        if (found) {
            data.activeUuid = found.uuid;
            this.saveAccountsData(data);
            return { success: true, account: found };
        }
        return { success: false, error: 'Konto nie zostało znalezione' };
    }

    removeAccount(uuid) {
        const data = this.getAccountsData();
        data.accounts = data.accounts.filter(a => a.uuid !== uuid);
        if (data.activeUuid === uuid) {
            data.activeUuid = data.accounts.length > 0 ? data.accounts[0].uuid : null;
        }
        this.saveAccountsData(data);
        return { success: true, activeAccount: this.getAccount() };
    }

    /**
     * Checks if account access_token is currently valid with Mojang Session API
     */
    async validateSession(account) {
        if (!account || !account.mclc || !account.mclc.access_token) return false;
        return new Promise((resolve) => {
            const req = https.request('https://api.minecraftservices.com/minecraft/profile', {
                method: 'GET',
                headers: {
                    'Authorization': `Bearer ${account.mclc.access_token}`,
                    'User-Agent': 'MooClient-Launcher'
                },
                timeout: 4000
            }, (res) => {
                resolve(res.statusCode === 200);
            });
            req.on('error', () => resolve(false));
            req.on('timeout', () => { req.destroy(); resolve(false); });
            req.end();
        });
    }

    /**
     * Silently refreshes account session using Microsoft Refresh Token
     */
    async refreshAccount(account) {
        if (!account) return { success: false, error: 'Brak konta' };
        if (!account.refreshToken) {
            return { success: false, error: 'Brak tokenu odświeżania' };
        }

        try {
            const authManager = new msmc.Auth("select_account");
            const xbox = await authManager.refresh(account.refreshToken);
            const token = await xbox.getMinecraft();

            if (!token.validate()) {
                throw new Error('Nie udało się zweryfikować odświeżonego konta Minecraft');
            }

            const mclcAuth = token.mclc();
            const updatedAccount = {
                ...account,
                name: token.profile.name,
                uuid: token.profile.id,
                mclc: mclcAuth,
                refreshToken: xbox.save() || account.refreshToken,
                lastValidated: Date.now()
            };

            this.saveAccount(updatedAccount);
            console.log(`[Auth] Sesja dla konta ${updatedAccount.name} została pomyślnie odświeżona!`);
            return { success: true, account: updatedAccount };
        } catch (e) {
            console.warn(`[Auth] Błąd podczas odświeżania sesji: ${e.message}`);
            return { success: false, error: e.message };
        }
    }

    /**
     * Proactively validates and refreshes the active account in background
     */
    async autoRefreshActiveAccount() {
        const account = this.getAccount();
        if (!account || !account.refreshToken) return null;

        try {
            const isValid = await this.validateSession(account);
            const fourHours = 4 * 60 * 60 * 1000;
            const isStale = !account.lastValidated || (Date.now() - account.lastValidated > fourHours);

            if (!isValid || isStale) {
                console.log(`[Auth] Ciche odświeżanie tokena w tle dla ${account.name}...`);
                const res = await this.refreshAccount(account);
                if (res.success && res.account) {
                    return res.account;
                }
            }
        } catch (e) {
            console.warn('[Auth] Błąd podczas auto-odświeżania konta:', e.message);
        }
        return account;
    }

    /**
     * Ensures active session is 100% valid before launching, auto-refreshing in background if needed
     */
    async ensureValidSession(onProgress = () => {}) {
        let account = this.getAccount();
        if (!account || !account.mclc) {
            onProgress('Wymagane logowanie kontem Microsoft...', 10);
            account = await this.loginMicrosoft();
            return account;
        }

        onProgress('Weryfikacja sesji gracza...', 5);
        const isValid = await this.validateSession(account);

        if (isValid) {
            console.log(`[Auth] Sesja dla konta ${account.name} jest aktywna.`);
            return account;
        }

        console.log(`[Auth] Sesja dla konta ${account.name} wygasła. Próba automatycznego odświeżenia w tle...`);
        onProgress('Odświeżanie sesji konta w tle...', 8);

        const refreshResult = await this.refreshAccount(account);
        if (refreshResult.success && refreshResult.account) {
            return refreshResult.account;
        }

        console.log(`[Auth] Ciche odświeżanie nie powiodło się. Otwieranie logowania Microsoft...`);
        onProgress('Sesja wygasła. Logowanie do Microsoft...', 10);
        account = await this.loginMicrosoft();
        return account;
    }

    /**
     * Log in with official Microsoft Minecraft Account
     */
    async loginMicrosoft() {
        const iconPath = path.join(__dirname, 'renderer', 'logo.png');
        const authManager = new msmc.Auth("select_account");
        const xbox = await authManager.launch("electron", {
            width: 520,
            height: 680,
            resizable: true,
            center: true,
            alwaysOnTop: true,
            backgroundColor: '#09090b',
            title: "Moo Client — Logowanie Microsoft",
            icon: iconPath,
        });
        const token = await xbox.getMinecraft();

        if (!token.validate()) {
            throw new Error('Nie udało się zweryfikować konta Minecraft!');
        }

        const refreshToken = xbox.save();
        const mclcAuth = token.mclc();
        const account = {
            name: token.profile.name,
            uuid: token.profile.id,
            mclc: mclcAuth,
            refreshToken: refreshToken,
            type: 'microsoft',
            lastValidated: Date.now()
        };

        this.saveAccount(account);
        return account;
    }

    logout() {
        this.saveAccount(null);
        return { success: true };
    }

    async getLatestStableLoader() {
        return new Promise((resolve) => {
            https.get('https://meta.fabricmc.net/v2/versions/loader', { headers: { 'User-Agent': 'MooClient-Launcher/1.0.0' } }, (res) => {
                let data = '';
                res.on('data', c => data += c);
                res.on('end', () => {
                    try {
                        const list = JSON.parse(data);
                        const stable = list.find(v => v.stable) || list[0];
                        resolve(stable?.version || '0.19.3');
                    } catch (e) {
                        resolve('0.19.3');
                    }
                });
            }).on('error', () => resolve('0.19.3'));
        });
    }

    async ensureFabricVersion(gameVersion = '1.21.4') {
        const loaderVersion = await this.getLatestStableLoader();
        const customVersionName = `fabric-loader-${gameVersion}`;
        const versionDir = path.join(this.gameDir, 'versions', customVersionName);
        const versionJsonPath = path.join(versionDir, `${customVersionName}.json`);

        // If cached profile exists, verify it uses the latest loader version and clean arguments
        if (fs.existsSync(versionJsonPath)) {
            try {
                const existing = JSON.parse(fs.readFileSync(versionJsonPath, 'utf8'));
                let modified = false;
                if (existing.arguments && Array.isArray(existing.arguments.jvm)) {
                    existing.arguments.jvm = existing.arguments.jvm.map(arg => {
                        if (typeof arg === 'string' && arg.includes('= ')) {
                            modified = true;
                            return arg.replace(/=\s+/g, '=').trim();
                        }
                        return typeof arg === 'string' ? arg.trim() : arg;
                    });
                }
                if (modified) {
                    fs.writeFileSync(versionJsonPath, JSON.stringify(existing, null, 2), 'utf8');
                }
                const hasLoader = existing.libraries?.some(lib => lib.name?.includes(`net.fabricmc:fabric-loader:${loaderVersion}`));
                if (hasLoader) {
                    return customVersionName;
                }
            } catch (e) {}
        }

        fs.mkdirSync(versionDir, { recursive: true });
        const url = `https://meta.fabricmc.net/v2/versions/loader/${gameVersion}/${loaderVersion}/profile/json`;

        return new Promise((resolve, reject) => {
            https.get(url, { headers: { 'User-Agent': 'MooClient-Launcher/1.0.0' } }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        const json = JSON.parse(data);
                        json.id = customVersionName;
                        if (json.arguments && Array.isArray(json.arguments.jvm)) {
                            json.arguments.jvm = json.arguments.jvm.map(arg => {
                                if (typeof arg === 'string') {
                                    return arg.replace(/=\s+/g, '=').trim();
                                }
                                return arg;
                            });
                        }
                        fs.writeFileSync(versionJsonPath, JSON.stringify(json, null, 2));
                        resolve(customVersionName);
                    } catch (e) {
                        reject(e);
                    }
                });
            }).on('error', reject);
        });
    }

    getJavaMajorVersion(javaPath) {
        if (!javaPath) return 0;
        try {
            const { spawnSync } = require('child_process');
            const res = spawnSync(javaPath, ['-version'], { encoding: 'utf8', timeout: 5000, windowsHide: true });
            const output = (res.stderr || '') + (res.stdout || '');
            const match = output.match(/version "([0-9]+)(\.([0-9]+))?/);
            if (match) {
                let major = parseInt(match[1], 10);
                if (major === 1 && match[3]) major = parseInt(match[3], 10);
                return major;
            }
        } catch (e) {}
        return 0;
    }

    resolveJavawPath(customPath) {
        if (customPath && fs.existsSync(customPath)) {
            if (process.platform === 'win32' && customPath.toLowerCase().endsWith('java.exe')) {
                const javaw = customPath.slice(0, -8) + 'javaw.exe';
                if (fs.existsSync(javaw) && this.getJavaMajorVersion(javaw) >= 21) return javaw;
            }
            if (this.getJavaMajorVersion(customPath) >= 21) return customPath;
        }

        if (process.platform === 'win32') {
            const homedir = os.homedir();
            const programFiles = process.env.ProgramFiles || 'C:\\Program Files';
            const programFilesX86 = process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)';

            const searchRoots = [
                path.join(this.gameDir, 'runtime', 'java-21'),
                process.env.JAVA_HOME,
                path.join(homedir, 'AppData', 'Local', 'Programs', 'Eclipse Adoptium'),
                path.join(homedir, 'AppData', 'Local', 'Programs', 'Java'),
                path.join(homedir, 'AppData', 'Local', 'Programs', 'Microsoft'),
                path.join(programFiles, 'Eclipse Adoptium'),
                path.join(programFiles, 'Java'),
                path.join(programFiles, 'Microsoft'),
                path.join(programFiles, 'BellSoft'),
                path.join(programFiles, 'Zulu'),
                path.join(programFilesX86, 'Minecraft Launcher', 'runtime', 'java-runtime-delta'),
                path.join(programFilesX86, 'Minecraft Launcher', 'runtime', 'java-runtime-gamma')
            ];

            const findBinary = (dir, depth = 0) => {
                if (depth > 4 || !fs.existsSync(dir)) return null;
                try {
                    const entries = fs.readdirSync(dir, { withFileTypes: true });
                    let javaFound = null;
                    for (const e of entries) {
                        const full = path.join(dir, e.name);
                        if (e.isFile()) {
                            if (e.name.toLowerCase() === 'javaw.exe') {
                                if (this.getJavaMajorVersion(full) >= 21) return full;
                            }
                            if (e.name.toLowerCase() === 'java.exe') {
                                if (this.getJavaMajorVersion(full) >= 21) javaFound = full;
                            }
                        }
                    }
                    if (javaFound) {
                        const javaw = javaFound.slice(0, -8) + 'javaw.exe';
                        if (fs.existsSync(javaw)) return javaw;
                        return javaFound;
                    }
                    for (const e of entries) {
                        if (e.isDirectory()) {
                            const found = findBinary(path.join(dir, e.name), depth + 1);
                            if (found) return found;
                        }
                    }
                } catch (e) {}
                return null;
            };

            for (const root of searchRoots) {
                if (root && fs.existsSync(root)) {
                    const found = findBinary(root);
                    if (found) return found;
                }
            }

            // Check PATH if javaw is >= 21
            if (this.getJavaMajorVersion('javaw') >= 21) return 'javaw';
            if (this.getJavaMajorVersion('java') >= 21) return 'java';
            return null;
        }

        if (this.getJavaMajorVersion('java') >= 21) return 'java';
        return null;
    }

    async ensureJava21(onProgress = () => {}) {
        const settings = this.getSettings();
        const existing = this.resolveJavawPath(settings.javaPath);
        if (existing) {
            return existing;
        }

        // Check if bundled/downloaded runtime exists
        const runtimeDir = path.join(this.gameDir, 'runtime', 'java-21');
        const findInRuntime = (dir, depth = 0) => {
            if (depth > 4 || !fs.existsSync(dir)) return null;
            try {
                const entries = fs.readdirSync(dir, { withFileTypes: true });
                for (const e of entries) {
                    const full = path.join(dir, e.name);
                    if (e.isFile() && e.name.toLowerCase() === 'javaw.exe') {
                        if (this.getJavaMajorVersion(full) >= 21) return full;
                    }
                    if (e.isDirectory()) {
                        const f = findInRuntime(full, depth + 1);
                        if (f) return f;
                    }
                }
            } catch (e) {}
            return null;
        };

        const runtimeJavaw = findInRuntime(runtimeDir);
        if (runtimeJavaw) return runtimeJavaw;

        // Auto-download Java 21 JRE for Windows
        onProgress('Pobieranie oficjalnej Javy 21 dla Minecrafta...', 10);
        fs.mkdirSync(runtimeDir, { recursive: true });
        const zipPath = path.join(this.gameDir, 'runtime', 'java21-temp.zip');

        const downloadUrl = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse';

        await new Promise((resolve, reject) => {
            const file = fs.createWriteStream(zipPath);
            const fetch = (targetUrl) => {
                https.get(targetUrl, { headers: { 'User-Agent': 'MooClient-Launcher' } }, (res) => {
                    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                        return fetch(res.headers.location);
                    }
                    if (res.statusCode !== 200) {
                        return reject(new Error(`Błąd pobierania Javy: HTTP ${res.statusCode}`));
                    }

                    const total = parseInt(res.headers['content-length'] || '0', 10);
                    let downloaded = 0;

                    res.on('data', (chunk) => {
                        downloaded += chunk.length;
                        if (total > 0) {
                            const pct = Math.min(80, 10 + Math.round((downloaded / total) * 70));
                            onProgress(`Pobieranie Javy 21: ${Math.round(downloaded / (1024 * 1024))}MB / ${Math.round(total / (1024 * 1024))}MB`, pct);
                        }
                    });

                    res.pipe(file);
                    file.on('finish', () => {
                        file.close(resolve);
                    });
                }).on('error', (err) => {
                    try { fs.unlinkSync(zipPath); } catch (e) {}
                    reject(err);
                });
            };
            fetch(downloadUrl);
        });

        onProgress('Rozpakowywanie Javy 21...', 85);
        try {
            const { execSync } = require('child_process');
            execSync(`powershell -NoProfile -NonInteractive -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${runtimeDir}' -Force"`, { stdio: 'ignore', windowsHide: true });
            try { fs.unlinkSync(zipPath); } catch (e) {}
        } catch (e) {
            console.error('Error extracting Java 21:', e);
        }

        const downloadedJavaw = findInRuntime(runtimeDir);
        if (downloadedJavaw) {
            onProgress('Java 21 pomyślnie zainstalowana!', 95);
            return downloadedJavaw;
        }

        return 'javaw';
    }

    /**
     * Launch Minecraft with Fabric and the Moo Client mod.
     */
    async launch(options = {}, onProgress = () => {}) {
        const settings = this.getSettings();
        const rawRam = parseInt(options.ram || settings.ram || '4', 10) || 4;
        const versionNumber = options.version || '1.21.4';

        // Safe memory calculation: cap at reasonable max based on system RAM
        const totalSysRamGB = Math.round(os.totalmem() / (1024 * 1024 * 1024));
        const safeMaxRam = Math.min(rawRam, Math.max(2, totalSysRamGB - 2));

        const launcher = new Client();

        // Authorization: validate & auto-refresh session if expired
        onProgress('Weryfikacja i przygotowanie sesji gracza...', 5);
        const account = await this.ensureValidSession(onProgress);
        if (!account || !account.mclc) {
            throw new Error('Musisz być zalogowany kontem Microsoft Premium, aby zagrać!');
        }
        const auth = account.mclc;

        onProgress('Weryfikacja środowiska Java 21...', 10);
        const javaExecutable = await this.ensureJava21(onProgress);

        onProgress('Przygotowywanie profilu Fabric...', 30);
        const customFabric = await this.ensureFabricVersion(versionNumber);

        console.log(`[Launch] Using Java: ${javaExecutable}, RAM: max ${safeMaxRam}G (requested ${rawRam}G)`);

        const offlineDir = path.join(this.gameDir, 'offline', 'multiver');
        if (!fs.existsSync(offlineDir)) {
            fs.mkdirSync(offlineDir, { recursive: true });
        }
        const devModPath = path.join(offlineDir, 'moo-client-dev.jar');
        const prodModPath = path.join(offlineDir, 'moo-client.jar');
        const offlineModPath = (options.isDev && fs.existsSync(devModPath)) ? devModPath : prodModPath;
        const fabricApiPath = path.join(offlineDir, 'fabric-api.jar');
        console.log(`[Launch] Core mod selected (${options.isDev ? 'DEV MODE' : 'PRODUCTION'}): ${offlineModPath}`);

        // Clean any stray core jars (moo-client and fabric-api) from the public user mods/ folder
        const modsDir = path.join(this.gameDir, 'mods');
        if (fs.existsSync(modsDir)) {
            try {
                fs.readdirSync(modsDir).filter(f => {
                    const lower = f.toLowerCase();
                    return (lower.startsWith('moo-client') || lower.startsWith('fabric-api')) && (lower.endsWith('.jar') || lower.endsWith('.disabled'));
                }).forEach(f => {
                    try { fs.unlinkSync(path.join(modsDir, f)); } catch(e){}
                });
            } catch (e) {}
        }

        const customArgs = [
            '-XX:+UseG1GC',
            '-XX:+ParallelRefProcEnabled',
            '-XX:MaxGCPauseMillis=50',
            '-XX:+UnlockExperimentalVMOptions',
            '-XX:+DisableExplicitGC',
            '-XX:+AlwaysPreTouch',
            '-XX:G1NewSizePercent=30',
            '-XX:G1MaxNewSizePercent=40',
            '-XX:G1ReservePercent=20',
            '-XX:G1HeapWastePercent=5',
            '-XX:G1MixedGCCountTarget=4',
            '-XX:InitiatingHeapOccupancyPercent=15',
            '-XX:G1MixedGCLiveThresholdPercent=90',
            '-XX:G1RSetUpdatingPauseTimePercent=5',
            '-XX:SurvivorRatio=32',
            '-XX:+PerfDisableSharedMem',
            '-XX:MaxTenuringThreshold=1'
        ];

        // Dynamic injection of core mods via Fabric addMods argument (Lunar Client architecture)
        const coreModsToInject = [offlineModPath, fabricApiPath].filter(p => fs.existsSync(p));
        if (coreModsToInject.length > 0) {
            const addModsArg = coreModsToInject.join(path.delimiter);
            customArgs.push(`-Dfabric.addMods=${addModsArg}`);
            console.log(`[Launch] Injected core mods via -Dfabric.addMods: ${addModsArg}`);
        }

        const launchOpts = {
            authorization: auth,
            root: this.gameDir,
            javaPath: javaExecutable,
            version: {
                number: versionNumber,
                type: 'release',
                custom: customFabric,
            },
            memory: {
                max: `${safeMaxRam}G`,
                min: '1024M',
            },
            customArgs: customArgs,
            window: {
                width: settings.resolution?.width || 1280,
                height: settings.resolution?.height || 720,
            },
            overrides: {
                detached: true
            }
        };

        launcher.on('debug', (e) => console.log('[MC Debug]', e));
        launcher.on('data', (e) => console.log('[MC Data]', e));
        launcher.on('progress', (e) => {
            const percent = Math.round((e.task / e.total) * 100);
            onProgress(`Pobieranie plików: ${e.type}`, percent);
        });
        launcher.on('download-status', (e) => {
            const percent = Math.round((e.current / e.total) * 100);
            onProgress(`Pobieranie zasobów gry...`, percent);
        });
        launcher.on('arguments', () => onProgress('Uruchamianie silnika gry...', 95));
        launcher.on('close', (code) => onProgress(`Gra zamknięta (kod: ${code})`, 0));

        onProgress('Uruchamianie Minecrafta...', 50);
        await launcher.launch(launchOpts);
        onProgress('Gra została uruchomiona!', 100);
    }
}

module.exports = GameManager;
