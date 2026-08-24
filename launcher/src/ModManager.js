const fs = require('fs');
const path = require('path');
const os = require('os');
const https = require('https');
const http = require('http');

/**
 * ModManager — handles auto-updating the Moo Client Fabric mod from GitHub.
 * 
 * Flow:
 * 1. Fetches mod-version.json from the GitHub repo (raw URL)
 * 2. Compares remote version with locally installed version
 * 3. Downloads new .jar if update is available
 * 4. Places it in the mods/ folder
 */
class ModManager {
    constructor() {
        this.gameDir = path.join(os.homedir(), '.mooclient');
        this.modsDir = path.join(this.gameDir, 'mods');
        this.offlineDir = path.join(this.gameDir, 'offline', 'multiver');
        this.coreModPath = path.join(this.offlineDir, 'moo-client.jar');
        this.localVersionPath = path.join(this.gameDir, 'installed-mod-version.json');

        // GitHub raw URL for version check
        this.versionUrl = 'https://raw.githubusercontent.com/Moo-Client/moo-client/main/mod-version.json';

        this.ensureDir(this.modsDir);
        this.ensureDir(this.offlineDir);
        this.cleanOldMods();
        this.ensureBundledMod();
    }

    static isNewerVersion(remote, local) {
        if (!remote) return false;
        if (!local) return true;
        const rParts = String(remote).replace(/^v/i, '').split('.').map(n => parseInt(n, 10) || 0);
        const lParts = String(local).replace(/^v/i, '').split('.').map(n => parseInt(n, 10) || 0);
        for (let i = 0; i < Math.max(rParts.length, lParts.length); i++) {
            const r = rParts[i] || 0;
            const l = lParts[i] || 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        return false;
    }

    ensureDir(dir) {
        if (!fs.existsSync(dir)) {
            fs.mkdirSync(dir, { recursive: true });
        }
    }

    /**
     * Deploys the bundled mod jar into offline/multiver/ if not already installed.
     */
    ensureBundledMod() {
        try {
            const pkg = require('../package.json');
            const defaultVer = pkg.version || '1.6.2';
            const bundledPath = path.join(__dirname, '..', 'assets', 'moo-client.jar');
            const devBuildPath = path.join(__dirname, '..', '..', 'build', 'libs', `moo-client-${defaultVer}.jar`);

            this.ensureDir(this.offlineDir);

            let sourceJar = null;
            if (fs.existsSync(devBuildPath)) {
                sourceJar = devBuildPath;
            } else if (fs.existsSync(bundledPath)) {
                sourceJar = bundledPath;
            }

            if (sourceJar) {
                const srcStats = fs.statSync(sourceJar);
                const needsCopy = !fs.existsSync(this.coreModPath) || 
                    fs.statSync(this.coreModPath).size !== srcStats.size || 
                    fs.statSync(this.coreModPath).mtimeMs < srcStats.mtimeMs;
                if (needsCopy) {
                    fs.copyFileSync(sourceJar, this.coreModPath);
                    if (sourceJar !== bundledPath && fs.existsSync(path.dirname(bundledPath))) {
                        try { fs.copyFileSync(sourceJar, bundledPath); } catch (e) {}
                    }
                    fs.writeFileSync(this.localVersionPath, JSON.stringify({
                        version: defaultVer,
                        minecraft: '1.21.4',
                        installedAt: new Date().toISOString(),
                    }, null, 2));
                    console.log(`Core mod deployed to offline folder: ${this.coreModPath}`);
                }
            }
            this.cleanOldMods();
        } catch (e) {
            console.warn('Could not deploy bundled mod:', e.message);
        }
    }

    /**
     * Returns the locally installed mod version info.
     */
    getLocalVersion() {
        try {
            if (fs.existsSync(this.localVersionPath)) {
                const data = fs.readFileSync(this.localVersionPath, 'utf8');
                return JSON.parse(data);
            }
        } catch (e) {
            console.error('Error reading local mod version:', e);
        }
        const pkg = require('../package.json');
        return { version: pkg.version || '1.6.2', minecraft: '1.21.4' };
    }

    /**
     * Fetches the remote version info from GitHub Releases API.
     * This uses /releases/latest so we never need to manually update mod-version.json.
     * The release tag (e.g. "v1.1.0") IS the version, changelog comes from the release body,
     * and download URL is extracted from the release assets (moo-client-*.jar).
     * Falls back to mod-version.json if the Releases API fails.
     */
    async getRemoteVersion() {
        try {
            return await this.fetchFromReleasesAPI();
        } catch (e) {
            console.warn('Releases API fetch failed, falling back to raw version:', e.message);
            try {
                return await this.fetchRawVersion();
            } catch (e2) {
                return await this.fetchFromContentsAPI();
            }
        }
    }

    /**
     * PRIMARY: Fetches latest version from GitHub Releases API (/releases/latest).
     * No manual file updates needed — creating a GitHub release is enough.
     */
    async fetchFromReleasesAPI() {
        return new Promise((resolve, reject) => {
            const apiUrl = 'https://api.github.com/repos/Moo-Client/moo-client/releases/latest';
            https.get(apiUrl, { headers: { 'User-Agent': 'MooClient-Launcher', 'Accept': 'application/vnd.github.v3+json' } }, (res) => {
                if (res.statusCode !== 200) {
                    reject(new Error(`Releases API returned HTTP ${res.statusCode}`));
                    return;
                }
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        const release = JSON.parse(data);
                        const version = (release.tag_name || '').replace(/^v/i, '');
                        if (!version) {
                            reject(new Error('No tag_name in release'));
                            return;
                        }

                        // Find the .jar asset download URL
                        let downloadUrl = '';
                        if (release.assets && Array.isArray(release.assets)) {
                            const jarAsset = release.assets.find(a => a.name && a.name.match(/^moo-client.*\.jar$/i));
                            if (jarAsset) {
                                downloadUrl = jarAsset.browser_download_url;
                            }
                        }
                        // Fallback download URL if no asset found
                        if (!downloadUrl) {
                            downloadUrl = `https://github.com/Moo-Client/moo-client/releases/download/core-assets/moo-client.jar`;
                        }

                        resolve({
                            version: version,
                            minecraft: '1.21.4',
                            download_url: downloadUrl,
                            changelog: release.body || ''
                        });
                    } catch (e) {
                        reject(new Error('Failed to parse releases API response'));
                    }
                });
            }).on('error', reject);
        });
    }

    /**
     * FALLBACK 1: Fetches mod-version.json via GitHub Contents API.
     */
    async fetchFromContentsAPI() {
        return new Promise((resolve, reject) => {
            const apiUrl = 'https://api.github.com/repos/Moo-Client/moo-client/contents/mod-version.json';
            https.get(apiUrl, { headers: { 'User-Agent': 'MooClient-Launcher' } }, (res) => {
                if (res.statusCode === 200) {
                    let data = '';
                    res.on('data', chunk => data += chunk);
                    res.on('end', () => {
                        try {
                            const json = JSON.parse(data);
                            if (json.content) {
                                const decoded = Buffer.from(json.content, 'base64').toString('utf8');
                                return resolve(JSON.parse(decoded));
                            }
                        } catch (e) { }
                        reject(new Error('Could not parse contents API response'));
                    });
                } else {
                    reject(new Error(`Contents API returned HTTP ${res.statusCode}`));
                }
            }).on('error', reject);
        });
    }

    /**
     * PRIMARY: Fetches mod-version.json via raw.githubusercontent.com with cache-buster.
     */
    async fetchRawVersion() {
        return new Promise((resolve, reject) => {
            const rawUrl = `${this.versionUrl}?_t=${Date.now()}`;
            const fetch = (url) => {
                const client = url.startsWith('https') ? https : http;
                client.get(url, { headers: { 'User-Agent': 'MooClient-Launcher', 'Cache-Control': 'no-cache, no-store, must-revalidate' } }, (res) => {
                    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                        fetch(res.headers.location);
                        return;
                    }
                    if (res.statusCode !== 200) {
                        reject(new Error(`Raw version URL returned HTTP ${res.statusCode}`));
                        return;
                    }
                    let data = '';
                    res.on('data', chunk => data += chunk);
                    res.on('end', () => {
                        try {
                            const json = JSON.parse(data);
                            resolve(json);
                        } catch (e) {
                            reject(e);
                        }
                    });
                }).on('error', reject);
            };
            fetch(rawUrl);
        });
    }

    /**
     * Downloads a file from a URL to a local path.
     */
    async downloadFile(url, destPath, onProgress = () => { }) {
        return new Promise((resolve, reject) => {
            const fetch = (url) => {
                const client = url.startsWith('https') ? https : http;
                client.get(url, { headers: { 'User-Agent': 'MooClient-Launcher' } }, (res) => {
                    // Handle redirects (GitHub releases use them)
                    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                        fetch(res.headers.location);
                        return;
                    }
                    if (res.statusCode !== 200) {
                        reject(new Error(`Download failed: HTTP ${res.statusCode}`));
                        return;
                    }

                    const totalSize = parseInt(res.headers['content-length'] || '0', 10);
                    let downloadedSize = 0;
                    const fileStream = fs.createWriteStream(destPath);

                    res.on('data', (chunk) => {
                        downloadedSize += chunk.length;
                        if (totalSize > 0) {
                            const percent = Math.round((downloadedSize / totalSize) * 100);
                            onProgress(percent);
                        }
                    });

                    res.pipe(fileStream);
                    fileStream.on('finish', () => {
                        fileStream.close();
                        resolve();
                    });
                    fileStream.on('error', (err) => {
                        fs.unlink(destPath, () => { });
                        reject(err);
                    });
                }).on('error', (err) => {
                    fs.unlink(destPath, () => { });
                    reject(err);
                });
            };
            fetch(url);
        });
    }

    /**
     * Removes old internal core mod JARs from the user mods folder (moo-client and fabric-api).
     */
    cleanOldMods() {
        try {
            if (!fs.existsSync(this.modsDir)) return;
            const files = fs.readdirSync(this.modsDir);
            for (const file of files) {
                const lower = file.toLowerCase();
                if ((lower.startsWith('moo-client') || lower.startsWith('fabric-api')) && (lower.endsWith('.jar') || lower.endsWith('.disabled'))) {
                    try {
                        fs.unlinkSync(path.join(this.modsDir, file));
                        console.log(`Cleaned core library from user mods folder: ${file}`);
                    } catch (e) { }
                }
            }
        } catch (e) {
            console.error('Error cleaning old mods:', e);
        }
    }

    /**
     * Ensures Fabric API (Core Runtime) is safely present in the protected offline/multiver folder.
     */
    async ensureFabricApi(onProgress = () => { }) {
        try {
            this.ensureDir(this.offlineDir);
            this.cleanOldMods();

            const targetPath = path.join(this.offlineDir, 'fabric-api.jar');
            if (fs.existsSync(targetPath) && fs.statSync(targetPath).size > 100000) {
                return true;
            }

            onProgress('Pobieranie biblioteki Fabric API (Core Runtime)...', 15);
            const fabricApiUrl = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/p96k10UR/fabric-api-0.119.4%2B1.21.4.jar';
            const tempPath = path.join(this.offlineDir, 'fabric-api.jar.download');

            await this.downloadFile(fabricApiUrl, tempPath, (percent) => {
                onProgress(`Pobieranie Fabric API: ${percent}%`, 15 + Math.round(percent * 0.15));
            });

            if (fs.existsSync(tempPath) && fs.statSync(tempPath).size > 100000) {
                if (fs.existsSync(targetPath)) {
                    try { fs.unlinkSync(targetPath); } catch (e) { }
                }
                fs.renameSync(tempPath, targetPath);
                console.log('Fabric API saved to protected offline folder:', targetPath);
                return true;
            } else {
                if (fs.existsSync(tempPath)) {
                    try { fs.unlinkSync(tempPath); } catch (e) { }
                }
                throw new Error('Pobieranie Fabric API nie powiodło się');
            }
        } catch (e) {
            console.error('Error ensuring Fabric API:', e);
            return false;
        }
    }

    /**
     * Main method: checks for updates and downloads if needed.
     * @param {Function} onProgress - Callback (status, progressPercent)
     * @returns {boolean} true if mod was updated, false if already up to date
     */
    async checkAndUpdate(onProgress = () => { }) {
        try {
            onProgress('Checking for mod updates...', 10);

            // Try to get remote version
            let remoteVersion;
            try {
                remoteVersion = await this.getRemoteVersion();
            } catch (e) {
                console.log('Could not check for mod updates (offline?):', e.message);
                onProgress('Offline — using cached mod', 30);
                return false;
            }

            const localVersion = this.getLocalVersion();
            onProgress(`Latest mod version: v${remoteVersion.version}`, 20);

            // Compare versions using semver
            if (!ModManager.isNewerVersion(remoteVersion.version, localVersion.version)) {
                onProgress('Mod is up to date!', 40);
                return false;
            }

            // Download new version
            onProgress(`Downloading core update v${remoteVersion.version}...`, 30);

            this.ensureDir(this.offlineDir);
            const tempJarPath = path.join(this.offlineDir, `moo-client-${remoteVersion.version}.download`);

            await this.downloadFile(remoteVersion.download_url, tempJarPath, (percent) => {
                const scaledPercent = 30 + Math.round(percent * 0.4); // Scale to 30-70 range
                onProgress(`Downloading mod: ${percent}%`, scaledPercent);
            });

            if (fs.existsSync(tempJarPath) && fs.statSync(tempJarPath).size > 10000) {
                // Save to offline/multiver/moo-client.jar
                if (fs.existsSync(this.coreModPath)) {
                    try { fs.unlinkSync(this.coreModPath); } catch (e) { }
                }
                fs.renameSync(tempJarPath, this.coreModPath);

                // Clean any stray mods from mods/
                this.cleanOldMods();

                // Save installed version info
                fs.writeFileSync(this.localVersionPath, JSON.stringify({
                    version: remoteVersion.version,
                    minecraft: remoteVersion.minecraft || '1.21.4',
                    installedAt: new Date().toISOString(),
                }, null, 2));

                onProgress(`Mod updated to v${remoteVersion.version}!`, 70);
                return true;
            } else {
                if (fs.existsSync(tempJarPath)) {
                    try { fs.unlinkSync(tempJarPath); } catch (e) { }
                }
                throw new Error('Pobrany plik moda jest uszkodzony.');
            }
        } catch (error) {
            console.error('Mod update error:', error);
            onProgress(`Mod update failed: ${error.message}`, 0);
            return false;
        }
    }

    /**
     * Search Modrinth for Fabric mods compatible with Minecraft 1.21.4
     */
    async searchModrinth(query = '', limit = 24, offset = 0, index = 'downloads') {
        return new Promise((resolve, reject) => {
            const facets = encodeURIComponent(JSON.stringify([
                ["categories:fabric"],
                ["versions:1.21.4"],
                ["project_type:mod"]
            ]));
            const q = encodeURIComponent(query.trim());
            const url = `https://api.modrinth.com/v2/search?query=${q}&facets=${facets}&index=${index}&limit=${limit}&offset=${offset}`;

            https.get(url, { headers: { 'User-Agent': 'MooClient-Launcher/1.0.0' } }, (res) => {
                if (res.statusCode !== 200) {
                    reject(new Error(`Modrinth API error: HTTP ${res.statusCode}`));
                    return;
                }
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        resolve(JSON.parse(data));
                    } catch (e) {
                        reject(new Error('Invalid JSON from Modrinth API'));
                    }
                });
            }).on('error', reject);
        });
    }

    /**
     * Helper to fetch JSON from URL with User-Agent
     */
    async fetchJson(url) {
        return new Promise((resolve, reject) => {
            const client = url.startsWith('https') ? https : http;
            client.get(url, { headers: { 'User-Agent': 'MooClient-Launcher/1.0.0' } }, (res) => {
                if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                    this.fetchJson(res.headers.location).then(resolve).catch(reject);
                    return;
                }
                if (res.statusCode !== 200) {
                    reject(new Error(`HTTP ${res.statusCode}`));
                    return;
                }
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try {
                        resolve(JSON.parse(data));
                    } catch (e) {
                        reject(new Error('Invalid JSON response'));
                    }
                });
            }).on('error', reject);
        });
    }

    /**
     * Install a mod from Modrinth by project ID or slug for 1.21.4 Fabric
     * Automatically resolves and downloads all required dependencies!
     */
    async installModrinthMod(projectId, gameVersion = '1.21.4', visited = new Set(), installedList = []) {
        if (!projectId || visited.has(projectId)) {
            return { success: true, installedList };
        }
        visited.add(projectId);

        try {
            const versionsUrl = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?game_versions=%5B%22${gameVersion}%22%5D&loaders=%5B%22fabric%22%5D`;
            const versions = await this.fetchJson(versionsUrl);

            if (!versions || versions.length === 0) {
                // If this was a dependency and no 1.21.4 specific version exists, try general fabric versions
                const fallbackUrl = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?loaders=%5B%22fabric%22%5D`;
                try {
                    const fallbackVersions = await this.fetchJson(fallbackUrl);
                    if (fallbackVersions && fallbackVersions.length > 0) {
                        const v = fallbackVersions[0];
                        const file = v.files.find(f => f.primary) || v.files[0];
                        if (file && file.url) {
                            const targetPath = path.join(this.modsDir, file.filename);
                            if (!fs.existsSync(targetPath)) {
                                await this.downloadFile(file.url, targetPath, () => { });
                                installedList.push({ filename: file.filename, name: v.name || projectId });
                            }
                        }
                    }
                } catch (e) { }

                if (installedList.length === 0 && visited.size === 1) {
                    throw new Error(`Brak wersji moda dla Fabric ${gameVersion}`);
                }
                return { success: true, installedList };
            }

            const version = versions[0];
            const file = version.files.find(f => f.primary) || version.files[0];
            if (!file || !file.url) {
                throw new Error('Brak pliku pobierania w wersji moda');
            }

            const targetPath = path.join(this.modsDir, file.filename);
            if (!fs.existsSync(targetPath)) {
                await this.downloadFile(file.url, targetPath, () => { });
                installedList.push({ filename: file.filename, name: version.name || projectId, projectId });
            }

            // Automatically resolve and install all REQUIRED dependencies
            if (Array.isArray(version.dependencies) && version.dependencies.length > 0) {
                for (const dep of version.dependencies) {
                    if (dep.dependency_type === 'required') {
                        if (dep.version_id) {
                            try {
                                const depVersion = await this.fetchJson(`https://api.modrinth.com/v2/version/${dep.version_id}`);
                                if (depVersion && depVersion.files && depVersion.files.length > 0) {
                                    const depFile = depVersion.files.find(f => f.primary) || depVersion.files[0];
                                    const depPath = path.join(this.modsDir, depFile.filename);
                                    if (!fs.existsSync(depPath)) {
                                        await this.downloadFile(depFile.url, depPath, () => { });
                                        installedList.push({ filename: depFile.filename, name: depVersion.name });
                                    }
                                }
                            } catch (err) {
                                console.warn(`Could not install dependency version ${dep.version_id}:`, err.message);
                            }
                        } else if (dep.project_id && !visited.has(dep.project_id)) {
                            // Recursively install project dependency
                            try {
                                await this.installModrinthMod(dep.project_id, gameVersion, visited, installedList);
                            } catch (err) {
                                console.warn(`Could not install dependency project ${dep.project_id}:`, err.message);
                            }
                        }
                    }
                }
            }

            return {
                success: true,
                filename: file.filename,
                version: version.version_number,
                installedCount: installedList.length,
                installedList,
            };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }

    /**
     * Get list of currently installed mods in .mooclient/mods/ with icons and metadata
     */
    getInstalledMods() {
        try {
            if (!fs.existsSync(this.modsDir)) return [];
            const files = fs.readdirSync(this.modsDir);
            const AdmZip = require('adm-zip');

            const mods = files
                .filter(file => (file.endsWith('.jar') || file.endsWith('.jar.disabled')) && !file.toLowerCase().startsWith('moo-client') && !file.toLowerCase().startsWith('fabric-api'))
                .map(file => {
                    const isEnabled = !file.endsWith('.disabled');
                    const cleanName = isEnabled ? file : file.replace(/\.disabled$/, '');
                    const filePath = path.join(this.modsDir, file);
                    const stats = fs.statSync(filePath);

                    let displayName = cleanName;
                    let modVersion = '';
                    let modId = '';
                    let iconData = null;

                    try {
                        const zip = new AdmZip(filePath);
                        const modJsonEntry = zip.getEntry('fabric.mod.json');
                        if (modJsonEntry) {
                            const modJson = JSON.parse(modJsonEntry.getData().toString('utf8'));
                            if (modJson.id) modId = modJson.id;
                            if (modJson.name) displayName = modJson.name;
                            if (modJson.version) modVersion = modJson.version;
                            let iconPath = modJson.icon;
                            if (typeof iconPath === 'object' && iconPath !== null) {
                                iconPath = iconPath['128'] || iconPath['64'] || iconPath['32'] || Object.values(iconPath)[0];
                            }
                            if (iconPath) {
                                if (iconPath.startsWith('/')) iconPath = iconPath.substring(1);
                                const iconEntry = zip.getEntry(iconPath);
                                if (iconEntry) {
                                    iconData = 'data:image/png;base64,' + iconEntry.getData().toString('base64');
                                }
                            }
                        }
                    } catch (e) { }

                    return {
                        filename: file,
                        cleanName: cleanName,
                        name: displayName,
                        version: modVersion,
                        modId: modId || cleanName.replace(/\.jar$/, ''),
                        icon: iconData,
                        size: stats.size,
                        enabled: isEnabled,
                        modifiedAt: stats.mtime,
                        isCore: false,
                    };
                });

            mods.sort((a, b) => a.name.localeCompare(b.name));

            return mods;
        } catch (e) {
            console.error('Error listing installed mods:', e);
            return [];
        }
    }

    /**
     * Enable / disable a mod by renaming it to .jar or .jar.disabled
     */
    toggleMod(filename, targetEnabled) {
        try {
            const currentPath = path.join(this.modsDir, filename);
            if (!fs.existsSync(currentPath)) return { success: false, error: 'Plik nie istnieje' };

            let newFilename = filename;
            if (targetEnabled && filename.endsWith('.disabled')) {
                newFilename = filename.replace(/\.disabled$/, '');
            } else if (!targetEnabled && !filename.endsWith('.disabled')) {
                newFilename = filename + '.disabled';
            }

            if (newFilename !== filename) {
                const newPath = path.join(this.modsDir, newFilename);
                fs.renameSync(currentPath, newPath);
            }
            return { success: true, newFilename, enabled: targetEnabled };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }

    /**
     * Install local .jar mods by copying them from disk into .mooclient/mods/
     */
    /**
     * Copy local .jar files into .mooclient/mods/
     * Includes intelligent multi-file archive extraction detection for WinRAR, 7-Zip, etc.
     */
    async installLocalMods(filePaths) {
        try {
            this.ensureDir(this.modsDir);
            const installed = [];
            const processedNames = new Set();
            const tmpDir = os.tmpdir();
            const tmpLower = tmpDir.toLowerCase();

            // 1. Process passed filePaths
            for (const srcPath of filePaths) {
                if (typeof srcPath !== 'string' || !srcPath.trim()) continue;

                const dir = path.dirname(srcPath);
                const dirLower = dir.toLowerCase();
                const isArchiveTemp = dirLower.includes('rar') ||
                    dirLower.includes('7z') ||
                    dirLower.includes('zip') ||
                    dirLower.includes('.rartemp') ||
                    dirLower.startsWith(tmpLower);

                // Wait up to 2.5 seconds for WinRAR extraction to complete
                for (let attempt = 0; attempt < 25; attempt++) {
                    if (fs.existsSync(dir)) {
                        try {
                            const foundJars = fs.readdirSync(dir).filter(f => f.toLowerCase().endsWith('.jar'));
                            if (foundJars.length > 0) {
                                await new Promise(r => setTimeout(r, 150));
                                break;
                            }
                        } catch (e) { }
                    }
                    if (fs.existsSync(srcPath)) break;
                    await new Promise(r => setTimeout(r, 100));
                }

                if (isArchiveTemp && fs.existsSync(dir) && dirLower !== tmpLower) {
                    try {
                        const allSiblingFiles = fs.readdirSync(dir);
                        for (const sFile of allSiblingFiles) {
                            if (sFile.toLowerCase().endsWith('.jar')) {
                                const fullSiblingPath = path.join(dir, sFile);
                                const filename = path.basename(sFile);
                                if (!processedNames.has(filename.toLowerCase())) {
                                    processedNames.add(filename.toLowerCase());
                                    const destPath = path.join(this.modsDir, filename);
                                    fs.copyFileSync(fullSiblingPath, destPath);
                                    installed.push(filename);
                                }
                            }
                        }
                    } catch (e) {
                        console.error('Error reading temp archive directory:', e);
                    }
                } else if (fs.existsSync(srcPath) && srcPath.toLowerCase().endsWith('.jar')) {
                    const filename = path.basename(srcPath);
                    if (!processedNames.has(filename.toLowerCase())) {
                        processedNames.add(filename.toLowerCase());
                        const destPath = path.join(this.modsDir, filename);
                        fs.copyFileSync(srcPath, destPath);
                        installed.push(filename);
                    }
                }
            }

            // 2. Also check if WinRAR created a recent temp folder in %TEMP% in the last 15 seconds
            try {
                const now = Date.now();
                const tempEntries = fs.readdirSync(tmpDir);
                for (const entry of tempEntries) {
                    const entryLower = entry.toLowerCase();
                    if (entryLower.includes('rar$') || entryLower.includes('.rartemp') || entryLower.includes('7z')) {
                        const fullTempFolder = path.join(tmpDir, entry);
                        const stat = fs.statSync(fullTempFolder);
                        if (stat.isDirectory() && (now - stat.mtimeMs) < 15000) {
                            const subFiles = fs.readdirSync(fullTempFolder);
                            for (const sFile of subFiles) {
                                if (sFile.toLowerCase().endsWith('.jar')) {
                                    const fullPath = path.join(fullTempFolder, sFile);
                                    const filename = path.basename(sFile);
                                    if (!processedNames.has(filename.toLowerCase())) {
                                        processedNames.add(filename.toLowerCase());
                                        const destPath = path.join(this.modsDir, filename);
                                        fs.copyFileSync(fullPath, destPath);
                                        installed.push(filename);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (err) {
                console.warn('Temp directory scan error:', err);
            }

            return { success: true, installed, count: installed.length };
        } catch (e) {
            console.error('Error copying local mods:', e);
            return { success: false, error: e.message };
        }
    }

    /**
     * Fetch all available versions for a Modrinth project with full metadata & changelog
     */
    async getModVersions(projectId, gameVersion = '1.21.4', allVersions = false) {
        try {
            let url;
            if (allVersions) {
                url = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?loaders=%5B%22fabric%22%5D`;
            } else {
                url = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?game_versions=%5B%22${gameVersion}%22%5D&loaders=%5B%22fabric%22%5D`;
            }

            let versions = await this.fetchJson(url);
            if (!versions || versions.length === 0) {
                // Fallback to all fabric versions
                const fallbackUrl = `https://api.modrinth.com/v2/project/${encodeURIComponent(projectId)}/version?loaders=%5B%22fabric%22%5D`;
                versions = await this.fetchJson(fallbackUrl);
            }

            return {
                success: true,
                versions: (versions || []).map(v => ({
                    id: v.id,
                    name: v.name || v.version_number,
                    version_number: v.version_number,
                    version_type: v.version_type,
                    date_published: v.date_published,
                    downloads: v.downloads,
                    game_versions: v.game_versions || [],
                    loaders: v.loaders || [],
                    changelog: v.changelog || '',
                    files: v.files || []
                }))
            };
        } catch (e) {
            console.error(`Error fetching versions for project ${projectId}:`, e);
            return { success: false, error: e.message, versions: [] };
        }
    }

    /**
     * Install a specific version of a mod by its Modrinth version ID
     * If oldFilename is provided, it deletes the older jar file to prevent conflicts.
     */
    async installModVersion(versionId, oldFilename = null) {
        try {
            const versionUrl = `https://api.modrinth.com/v2/version/${encodeURIComponent(versionId)}`;
            const version = await this.fetchJson(versionUrl);
            if (!version || !version.files || version.files.length === 0) {
                throw new Error('Nie znaleziono pliku dla tej wersji');
            }

            const file = version.files.find(f => f.primary) || version.files[0];
            const destPath = path.join(this.modsDir, file.filename);

            // Download new file
            await this.downloadFile(file.url, destPath);

            // If replacing an older version file with different name, delete old file
            if (oldFilename && oldFilename !== file.filename) {
                const oldPath = path.join(this.modsDir, oldFilename);
                if (fs.existsSync(oldPath)) {
                    fs.unlinkSync(oldPath);
                }
            }

            return {
                success: true,
                filename: file.filename,
                version: version.version_number,
                name: version.name
            };
        } catch (e) {
            console.error(`Error installing version ${versionId}:`, e);
            return { success: false, error: e.message };
        }
    }

    /**
     * Helper to send POST JSON to URL with User-Agent
     */
    async postJson(url, payload) {
        return new Promise((resolve, reject) => {
            const data = JSON.stringify(payload);
            const req = https.request(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(data),
                    'User-Agent': 'MooClient-Launcher/1.0.0'
                }
            }, (res) => {
                if (res.statusCode !== 200) {
                    reject(new Error(`HTTP ${res.statusCode}`));
                    return;
                }
                let body = '';
                res.on('data', chunk => body += chunk);
                res.on('end', () => {
                    try {
                        resolve(JSON.parse(body));
                    } catch (e) {
                        reject(new Error('Invalid JSON response'));
                    }
                });
            });
            req.on('error', reject);
            req.write(data);
            req.end();
        });
    }

    /**
     * Check for updates on all installed mods using official Modrinth hash comparison
     * 100% accurate: 0 false positives, instantly checks all mods in 1 request!
     */
    async checkModUpdates(gameVersion = '1.21.4') {
        try {
            if (!fs.existsSync(this.modsDir)) return { success: true, updates: [] };
            const files = fs.readdirSync(this.modsDir).filter(f => (f.endsWith('.jar') || f.endsWith('.jar.disabled')) && !f.toLowerCase().startsWith('moo-client'));

            if (files.length === 0) return { success: true, updates: [] };

            const crypto = require('crypto');
            const fileMap = {};

            for (const file of files) {
                try {
                    const filePath = path.join(this.modsDir, file);
                    const fileBuf = fs.readFileSync(filePath);
                    const sha1 = crypto.createHash('sha1').update(fileBuf).digest('hex');
                    const sha512 = crypto.createHash('sha512').update(fileBuf).digest('hex');
                    const cleanName = file.replace(/\.disabled$/, '');
                    fileMap[sha1] = { filename: file, cleanName, sha1, sha512 };
                } catch (e) { }
            }

            const sha1List = Object.keys(fileMap);
            if (sha1List.length === 0) return { success: true, updates: [] };

            const updateRes = await this.postJson('https://api.modrinth.com/v2/version_files/update', {
                hashes: sha1List,
                algorithm: 'sha1',
                loaders: ['fabric'],
                game_versions: [gameVersion]
            });

            const updates = [];
            const installedMods = this.getInstalledMods();

            for (const [hash, newVer] of Object.entries(updateRes || {})) {
                const local = fileMap[hash];
                if (!local) continue;

                // Check if returned version is the EXACT SAME file as installed
                const isSameFile = newVer.files?.some(f =>
                    f.hashes?.sha1 === local.sha1 ||
                    f.hashes?.sha512 === local.sha512 ||
                    f.filename === local.cleanName ||
                    f.filename === local.filename
                );

                if (!isSameFile) {
                    const matchingMod = installedMods.find(m => m.filename === local.filename);
                    const primaryFile = newVer.files?.find(f => f.primary) || newVer.files?.[0];

                    updates.push({
                        filename: local.filename,
                        name: matchingMod?.name || local.cleanName,
                        currentVersion: matchingMod?.version || '',
                        latestVersion: newVer.version_number || newVer.name,
                        versionId: newVer.id,
                        projectId: newVer.project_id,
                        icon: matchingMod?.icon || 'logo.png',
                        newFilename: primaryFile?.filename || local.filename
                    });
                }
            }

            return { success: true, updates };
        } catch (e) {
            console.error('Error checking mod updates:', e);
            return { success: false, error: e.message, updates: [] };
        }
    }

    /**
     * Delete a mod from .mooclient/mods/
     */
    uninstallMod(filename) {
        try {
            if (filename.toLowerCase().startsWith('moo-client')) {
                throw new Error('Nie można usunąć głównego modułu Moo Client!');
            }
            const filePath = path.join(this.modsDir, filename);
            if (fs.existsSync(filePath)) {
                fs.unlinkSync(filePath);
                return { success: true };
            }
            return { success: false, error: 'Plik nie istnieje' };
        } catch (e) {
            return { success: false, error: e.message };
        }
    }

    /**
     * Save a raw binary buffer directly as a .jar mod into .mooclient/mods/
     * Perfect for Drag & Drop from WinRAR, 7-Zip, browsers, etc.
     */
    saveModBuffer(filename, buffer) {
        try {
            if (!filename || !filename.toLowerCase().endsWith('.jar')) {
                return { success: false, error: 'Plik musi mieć rozszerzenie .jar' };
            }
            this.ensureDir(this.modsDir);
            const safeName = path.basename(filename);
            const targetPath = path.join(this.modsDir, safeName);

            const buf = Buffer.isBuffer(buffer) ? buffer : Buffer.from(buffer);
            fs.writeFileSync(targetPath, buf);
            return { success: true, filename: safeName };
        } catch (e) {
            console.error('Error saving mod buffer:', e);
            return { success: false, error: e.message };
        }
    }
}

module.exports = ModManager;
