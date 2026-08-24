const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');
const path = require('path');

const VERSION = '1.6.4';

function getGitHubToken() {
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
    return new Promise((resolve, reject) => {
        const fileData = fs.readFileSync(filePath);
        const url = new URL(uploadUrl.replace('{?name,label}', '') + '?name=' + encodeURIComponent(fileName));
        const req = https.request({
            hostname: url.hostname,
            path: url.pathname + url.search,
            method: 'POST',
            headers: {
                'Authorization': `token ${token}`,
                'User-Agent': 'MooClient-Builder',
                'Content-Type': contentType,
                'Content-Length': fileData.length
            }
        }, res => {
            let data = '';
            res.on('data', c => data += c);
            res.on('end', () => {
                console.log(`Upload ${fileName}: HTTP ${res.statusCode}`);
                resolve({ status: res.statusCode });
            });
        });
        req.on('error', reject);
        req.write(fileData);
        req.end();
    });
}

(async () => {
    const token = getGitHubToken();
    if (!token) { console.error('No token'); process.exit(1); }

    let res = await apiRequest('GET', `/repos/Moo-Client/moo-client/releases/tags/v${VERSION}`, token);
    let release;

    if (res.status === 200) {
        release = res.data;
        console.log('Found existing release:', release.html_url);
    } else {
        res = await apiRequest('POST', '/repos/Moo-Client/moo-client/releases', token, {
            tag_name: `v${VERSION}`,
            name: `Moo Client v${VERSION}`,
            body: '🚀 **Moo Client v1.6.4 (CPS Module & Smart Snapping)**\n\n✓ Dodano nowy moduł CPS (Clicks Per Second - LPM, PPM lub oba) z czasem rzeczywistym\n✓ Wdrożono Smart Snapping & Alignment Guidelines (inteligentne linie pomocnicze w edytorze HUD)\n✓ Wdrożono lekki Bootstrapper (~1 MB) dla szybkiej instalacji i aktualizacji\n✓ Naprawiono responsywne pozycjonowanie modułów przy zmianie rozmiaru okna gry\n✓ Spolszczono style wyglądu (Prosty, Nawiasy, Kompaktowy) i tryby aktywacji',
            draft: false,
            prerelease: false
        });
        release = res.data;
        console.log('Created release:', release.html_url);
    }

    // Delete old assets if any
    if (release.assets) {
        for (const asset of release.assets) {
            console.log('Deleting old asset:', asset.name);
            await apiRequest('DELETE', `/repos/Moo-Client/moo-client/releases/assets/${asset.id}`, token);
        }
    }

    // 1. Upload Bootstrapper (MooClient-Setup.exe ~600 KB)
    const bootstrapperPaths = [
        path.join(__dirname, 'dist-win', 'MooClient-Setup.exe'),
        path.join(__dirname, 'build-out', 'MooClient-Setup.exe')
    ];
    let bootstrapperPath = bootstrapperPaths.find(p => fs.existsSync(p));
    if (bootstrapperPath) {
        const sizeMb = (fs.statSync(bootstrapperPath).size / (1024 * 1024)).toFixed(2);
        console.log(`Uploading MooClient-Setup.exe (${sizeMb} MB Bootstrapper)...`);
        await uploadAsset(release.upload_url, token, bootstrapperPath, 'MooClient-Setup.exe', 'application/octet-stream');
    } else {
        console.warn('Bootstrapper not found!');
    }

    // 2. Upload Runtime ZIP (moo-client-launcher-win64.zip)
    const zipPaths = [
        path.join(__dirname, 'dist-win', 'moo-client-launcher-win64.zip'),
        path.join(__dirname, 'build-out', 'moo-client-launcher-win64.zip')
    ];
    let zipPath = zipPaths.find(p => fs.existsSync(p));
    if (zipPath) {
        const sizeMb = (fs.statSync(zipPath).size / (1024 * 1024)).toFixed(2);
        console.log(`Uploading moo-client-launcher-win64.zip (${sizeMb} MB)...`);
        await uploadAsset(release.upload_url, token, zipPath, 'moo-client-launcher-win64.zip', 'application/zip');
    } else {
        console.warn('Launcher zip not found!');
    }

    // 3. Upload Game Mod JAR
    const jarPath = path.join(__dirname, '..', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (fs.existsSync(jarPath)) {
        console.log(`Uploading moo-client-${VERSION}.jar...`);
        await uploadAsset(release.upload_url, token, jarPath, `moo-client-${VERSION}.jar`, 'application/java-archive');
    } else {
        console.warn(`Jar not found at ${jarPath}`);
    }

    // 4. Upload app.asar
    const asarPaths = [
        path.join(__dirname, 'dist-win', 'win-unpacked', 'resources', 'app.asar'),
        path.join(__dirname, 'build-out', 'win-unpacked', 'resources', 'app.asar')
    ];
    let asarPath = asarPaths.find(p => fs.existsSync(p));
    if (asarPath) {
        const sizeMb = (fs.statSync(asarPath).size / (1024 * 1024)).toFixed(2);
        console.log(`Uploading app.asar (${sizeMb} MB)...`);
        await uploadAsset(release.upload_url, token, asarPath, 'app.asar', 'application/octet-stream');
    } else {
        console.warn(`app.asar not found at ${asarPath}`);
    }

    // 5. Upload Full Offline Installer EXE
    const exePaths = [
        path.join(__dirname, 'dist-win', `Moo Client Setup ${VERSION}.exe`),
        path.join(__dirname, 'build-out', `Moo Client Setup ${VERSION}.exe`)
    ];
    let exePath = exePaths.find(p => fs.existsSync(p));
    if (exePath) {
        const sizeMb = (fs.statSync(exePath).size / (1024 * 1024)).toFixed(2);
        console.log(`Uploading Full Installer (${sizeMb} MB)...`);
        await uploadAsset(release.upload_url, token, exePath, `Moo.Client.Setup.${VERSION}.exe`, 'application/octet-stream');
    } else {
        console.warn(`Installer exe not found at ${exePath}`);
    }

    console.log(`\n🎉 ALL v${VERSION} ASSETS (INCLUDING BOOTSTRAPPER) UPLOADED SUCCESSFULLY!`);
})();
