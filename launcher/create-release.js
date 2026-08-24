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
                console.log(`  ✓ Upload ${fileName}: HTTP ${res.statusCode}`);
                resolve({ status: res.statusCode });
            });
        });
        req.on('error', reject);
        req.write(fileData);
        req.end();
    });
}

async function ensureRelease(tag, name, body, token) {
    let res = await apiRequest('GET', `/repos/Moo-Client/moo-client/releases/tags/${tag}`, token);
    if (res.status === 200) {
        console.log(`Found release ${tag}:`, res.data.html_url);
        return res.data;
    }
    res = await apiRequest('POST', '/repos/Moo-Client/moo-client/releases', token, {
        tag_name: tag,
        name: name,
        body: body,
        draft: false,
        prerelease: false
    });
    console.log(`Created release ${tag}:`, res.data.html_url);
    return res.data;
}

(async () => {
    const token = getGitHubToken();
    if (!token) { console.error('No token found'); process.exit(1); }

    // =============================================
    // STEP 1: Upload backend assets to 'core-assets'
    // =============================================
    console.log('\n--- 1. Updating backend core-assets ---');
    const coreRelease = await ensureRelease(
        'core-assets',
        'Moo Client Core Backend Assets',
        'Backend runtime packages and delta update assets used by Moo Client Bootstrapper.',
        token
    );

    // Delete existing assets in core-assets
    if (coreRelease.assets && coreRelease.assets.length > 0) {
        for (const asset of coreRelease.assets) {
            console.log(`  Deleting old core asset: ${asset.name}`);
            await apiRequest('DELETE', `/repos/Moo-Client/moo-client/releases/assets/${asset.id}`, token);
        }
    }

    // 1a. Upload ZIP runtime
    const zipPath = path.join(__dirname, 'dist-win', 'moo-client-launcher-win64.zip');
    if (fs.existsSync(zipPath)) {
        console.log('  Uploading moo-client-launcher-win64.zip to core-assets...');
        await uploadAsset(coreRelease.upload_url, token, zipPath, 'moo-client-launcher-win64.zip', 'application/zip');
    }

    // 1b. Upload app.asar
    const asarPath = path.join(__dirname, 'dist-win', 'win-unpacked', 'resources', 'app.asar');
    if (fs.existsSync(asarPath)) {
        console.log('  Uploading app.asar to core-assets...');
        await uploadAsset(coreRelease.upload_url, token, asarPath, 'app.asar', 'application/octet-stream');
    }

    // 1c. Upload moo-client.jar
    const jarPath = path.join(__dirname, '..', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (fs.existsSync(jarPath)) {
        console.log('  Uploading moo-client.jar to core-assets...');
        await uploadAsset(coreRelease.upload_url, token, jarPath, 'moo-client.jar', 'application/java-archive');
        await uploadAsset(coreRelease.upload_url, token, jarPath, `moo-client-${VERSION}.jar`, 'application/java-archive');
    }

    // =============================================
    // STEP 2: Update public release v${VERSION} with ONLY MooClient-Setup.exe
    // =============================================
    console.log(`\n--- 2. Updating public release v${VERSION} ---`);
    const publicRelease = await ensureRelease(
        `v${VERSION}`,
        `Moo Client v${VERSION}`,
        `🚀 **Moo Client v${VERSION}**\n\n✓ Nowy moduł CPS (Clicks Per Second - LPM, PPM lub oba) z czasem rzeczywistym\n✓ Smart Snapping & Alignment Guidelines (linie pomocnicze w edytorze HUD)\n✓ Lekki instalator Bootstrapper (~600 KB)\n✓ Spolszczenie stylów wyglądu i trybów aktywacji`,
        token
    );

    // Delete ALL assets in public release except MooClient-Setup.exe (or clear all and re-upload only bootstrapper)
    if (publicRelease.assets && publicRelease.assets.length > 0) {
        for (const asset of publicRelease.assets) {
            console.log(`  Cleaning up non-bootstrapper asset: ${asset.name}`);
            await apiRequest('DELETE', `/repos/Moo-Client/moo-client/releases/assets/${asset.id}`, token);
        }
    }

    // Upload ONLY MooClient-Setup.exe to public release
    const bootstrapperPath = path.join(__dirname, 'dist-win', 'MooClient-Setup.exe');
    if (fs.existsSync(bootstrapperPath)) {
        const sizeMb = (fs.statSync(bootstrapperPath).size / (1024 * 1024)).toFixed(2);
        console.log(`  Uploading ONLY MooClient-Setup.exe (${sizeMb} MB) to public release...`);
        await uploadAsset(publicRelease.upload_url, token, bootstrapperPath, 'MooClient-Setup.exe', 'application/octet-stream');
    } else {
        console.error('  Bootstrapper binary not found!');
    }

    console.log(`\n🎉 SUCCESS! Public release v${VERSION} now contains ONLY MooClient-Setup.exe!`);
})();
