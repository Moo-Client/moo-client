const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');
const path = require('path');

const VERSION = '2.1.7';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
const REPO_OWNER = 'Moo-Client';
const REPO_NAME = 'moo-client';

function getGitHubToken() {
    if (GITHUB_TOKEN) return GITHUB_TOKEN;
    try {
        const input = 'protocol=https\nhost=github.com\n\n';
        const result = execSync('git credential fill', { input, encoding: 'utf8', timeout: 5000 });
        const match = result.match(/password=(.+)/);
        return match ? match[1].trim() : null;
    } catch (e) { return null; }
}

function apiRequest(method, apiPath, token, body) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'api.github.com',
            path: apiPath,
            method,
            headers: {
                'Authorization': `token ${token}`,
                'User-Agent': 'MooClient-Builder',
                'Accept': 'application/vnd.github.v3+json'
            }
        };
        if (body) options.headers['Content-Type'] = 'application/json';
        const req = https.request(options, res => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => resolve({ status: res.statusCode, data: JSON.parse(data || '{}') }));
        });
        req.on('error', reject);
        if (body) req.write(JSON.stringify(body));
        req.end();
    });
}

function uploadAsset(uploadUrl, token, filePath, fileName, contentType) {
    const cleanUrl = uploadUrl.replace('{?name,label}', '') + '?name=' + encodeURIComponent(fileName);
    console.log(`Uploading ${fileName} (${(fs.statSync(filePath).size / (1024 * 1024)).toFixed(1)} MB)...`);
    try {
        const cmd = `curl.exe --retry 3 -s -o NUL -w "%{http_code}" -X POST -H "Authorization: token ${token}" -H "User-Agent: MooClient-Builder" -H "Content-Type: ${contentType}" --data-binary "@${filePath.replace(/\\/g, '/')}" "${cleanUrl}"`;
        const output = execSync(cmd, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024, timeout: 600000 });
        const code = output.trim();
        console.log(`Upload ${fileName}: HTTP ${code}`);
        return Promise.resolve({ status: parseInt(code, 10) || 200 });
    } catch (e) {
        console.error(`Failed uploading ${fileName}:`, e.message);
        return Promise.reject(e);
    }
}

(async () => {
    const token = getGitHubToken();
    if (!token) { console.error('No token'); process.exit(1); }

    const CHANGELOG_BODY = `🚀 **Moo Client v${VERSION}**

🖥️ **Dynamiczny tytuł okna gry i statusy:**
- 🏷️ **Poprawka tytułu okna**: Usunięto sztywne oznaczenie 1.21.4 z lewego górnego rogu okna gry. Okno dynamicznie wyświetla teraz dokładną wersję Minecrafta, na której uruchomiono klienta (np. \`Minecraft 1.21.11 - Moo Client\` lub \`Minecraft 1.21.4 - Moo Client\`).
- 🎮 **Discord RPC i GUI**: Statusy Discord Rich Presence oraz nagłówki wewnątrz klienta pobierają teraz rzeczywistą wersję gry przez Fabric Loader API.

🔄 **Inteligentny silnik auto-aktualizacji i migracji modów:**
- ⚡ **Auto-konwersja Drag & Drop**: Przy upuszczeniu modów z wersji 1.21.4 do wersji 1.21.11 launcher automatycznie odpytuje Modrinth, pobiera najnowszą kompatybilną wersję .jar dla 1.21.11 i zastępuje niekompatybilny plik.
- 🛡️ **Weryfikacja przed startem gry (Pre-Launch check)**: Przed uruchomieniem Minecrafta 1.21.11 launcher automatycznie sprawdza wszystkie zainstalowane mody i aktualizuje niekompatybilne pliki z 1.21.4 do 1.21.11, zapobiegając błędom Fabric Loadera.
- 📥 **Przycisk migracji i aktualizacji w GUI**: W zakładce Modyfikacje dodano dedykowany przycisk *„Aktualizuj do wersji gry”* oraz banner umożliwiający natychmiastowe przeniesienie i uaktualnienie całej kolekcji modów z 1.21.4 do 1.21.11 jednym kliknięciem.`;

    let release;
    let res = await apiRequest('GET', `/repos/Moo-Client/moo-client/releases/tags/v${VERSION}`, token);
    if (res.status === 200 && res.data && res.data.upload_url) {
        release = res.data;
        console.log('Found existing release:', release.html_url);
        await apiRequest('PATCH', `/repos/Moo-Client/moo-client/releases/${release.id}`, token, {
            name: `Moo Client v${VERSION}`,
            body: CHANGELOG_BODY
        });
        console.log('Updated release title and changelog body on GitHub.');
    } else {
        res = await apiRequest('POST', '/repos/Moo-Client/moo-client/releases', token, {
            tag_name: `v${VERSION}`,
            name: `Moo Client v${VERSION}`,
            body: CHANGELOG_BODY,
            draft: false,
            prerelease: false
        });
        if (res.status === 201 || res.status === 200) {
            release = res.data;
            console.log('Created clean release:', release.html_url);
        } else {
            console.error('Error creating release:', res.data);
            process.exit(1);
        }
    }

    if (process.argv.includes('--body-only')) {
        console.log('Changelog description updated successfully (--body-only). Exiting.');
        process.exit(0);
    }

    // Delete old/wrong assets
    if (release.assets && release.assets.length > 0) {
        for (const asset of release.assets) {
            console.log('Deleting old asset:', asset.name);
            await apiRequest('DELETE', `/repos/Moo-Client/moo-client/releases/assets/${asset.id}`, token);
        }
    }

    // 1. Upload Fabric Mod JARs
    let jarPath = path.join(__dirname, '..', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (!fs.existsSync(jarPath)) {
        const v12111Jar = path.join(__dirname, '..', 'versions', '1.21.11', 'build', 'libs', `moo-client-${VERSION}.jar`);
        const v1214Jar = path.join(__dirname, '..', 'versions', '1.21.4', 'build', 'libs', `moo-client-${VERSION}.jar`);
        if (fs.existsSync(v12111Jar)) jarPath = v12111Jar;
        else if (fs.existsSync(v1214Jar)) jarPath = v1214Jar;
    }
    if (fs.existsSync(jarPath)) {
        await uploadAsset(release.upload_url, token, jarPath, `moo-client-${VERSION}.jar`, 'application/java-archive');
    } else {
        console.error(`ERROR: Jar not found at ${jarPath}`);
        process.exit(1);
    }

    const v1214 = path.join(__dirname, '..', 'versions', '1.21.4', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (fs.existsSync(v1214)) {
        await uploadAsset(release.upload_url, token, v1214, `moo-client-1.21.4-${VERSION}.jar`, 'application/java-archive');
    }
    const v12111 = path.join(__dirname, '..', 'versions', '1.21.11', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (fs.existsSync(v12111)) {
        await uploadAsset(release.upload_url, token, v12111, `moo-client-1.21.11-${VERSION}.jar`, 'application/java-archive');
    }

    // 2. Upload ASAR
    let asarPath = path.join(__dirname, 'dist', 'win-unpacked', 'resources', 'app.asar');
    if (!fs.existsSync(asarPath)) {
        asarPath = path.join(__dirname, 'build-out', 'win-unpacked', 'resources', 'app.asar');
    }
    if (fs.existsSync(asarPath)) {
        await uploadAsset(release.upload_url, token, asarPath, 'app.asar', 'application/octet-stream');
    } else {
        console.error(`ERROR: app.asar not found at ${asarPath}`);
        process.exit(1);
    }

    // 3. Upload Bootstrapper (MooClient-Setup.exe)
    let bootstrapperPath = path.join(__dirname, 'dist', 'nsis-web', 'MooClient-Setup.exe');
    if (!fs.existsSync(bootstrapperPath)) {
        bootstrapperPath = path.join(__dirname, 'dist-win', 'MooClient-Setup.exe');
    }
    if (!fs.existsSync(bootstrapperPath)) {
        bootstrapperPath = path.join(__dirname, 'build-out', 'MooClient-Setup.exe');
    }
    if (fs.existsSync(bootstrapperPath)) {
        await uploadAsset(release.upload_url, token, bootstrapperPath, 'MooClient-Setup.exe', 'application/octet-stream');
    } else {
        console.error(`ERROR: Bootstrapper not found at ${bootstrapperPath}`);
        process.exit(1);
    }

    // 4. Upload NSIS Web Package strictly matching current version
    const nsisDir = path.join(__dirname, 'dist', 'nsis-web');
    const expected7zName = `moo-client-launcher-${VERSION}-x64.nsis.7z`;
    let target7zPath = path.join(nsisDir, expected7zName);
    if (!fs.existsSync(target7zPath)) {
        const matchingFiles = fs.readdirSync(nsisDir).filter(f => f.endsWith('.nsis.7z') && f.includes(VERSION));
        if (matchingFiles.length > 0) {
            target7zPath = path.join(nsisDir, matchingFiles[0]);
        } else {
            console.error(`FATAL: File ${expected7zName} not found in ${nsisDir}!`);
            process.exit(1);
        }
    }
    const final7zName = path.basename(target7zPath);
    await uploadAsset(release.upload_url, token, target7zPath, final7zName, 'application/octet-stream');

    // 5. Upload Standalone Installer EXE (if exists)
    let exePath = path.join(__dirname, 'dist', `Moo Client Setup ${VERSION}.exe`);
    if (!fs.existsSync(exePath)) {
        exePath = path.join(__dirname, 'build-out', `Moo Client Setup ${VERSION}.exe`);
    }
    if (fs.existsSync(exePath)) {
        await uploadAsset(release.upload_url, token, exePath, `Moo.Client.Setup.${VERSION}.exe`, 'application/octet-stream');
    }

    console.log(`ALL v${VERSION} ASSETS UPLOADED AND REPLACED SUCCESSFULLY!`);
})();
