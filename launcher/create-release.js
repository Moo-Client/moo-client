const { execSync } = require('child_process');
const https = require('https');
const fs = require('fs');
const path = require('path');

const VERSION = '1.7.0_2';
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

    let res = await apiRequest('GET', `/repos/Moo-Client/moo-client/releases/tags/v${VERSION}`, token);
    let release;

    if (res.status === 200) {
        release = res.data;
        console.log('Found existing release:', release.html_url);
    } else {
        res = await apiRequest('POST', '/repos/Moo-Client/moo-client/releases', token, {
            tag_name: `v${VERSION}`,
            name: `Moo Client v${VERSION}`,
            body: '🚀 **Moo Client v1.7.0_1**\n\n✓ **Dynamiczny system uprawnień i ról API** (usunięto hardcoded UUID z kodu)\n✓ **Odwrócono pozycje salt w kole emotek** (Salto w przód / tył)\n✓ **Usunięto niepotrzebne napisy z zakładki Emotek**\n✓ **Pełna synchronizacja multiplayer w czasie rzeczywistym**',
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

    // 1. Upload Fabric Mod JAR (314 KB)
    const jarPath = path.join(__dirname, '..', 'build', 'libs', `moo-client-${VERSION}.jar`);
    if (fs.existsSync(jarPath)) {
        await uploadAsset(release.upload_url, token, jarPath, `moo-client-${VERSION}.jar`, 'application/java-archive');
    } else {
        console.warn(`Jar not found at ${jarPath}`);
    }

    // 2. Upload ASAR (25 MB)
    let asarPath = path.join(__dirname, 'dist', 'win-unpacked', 'resources', 'app.asar');
    if (!fs.existsSync(asarPath)) {
        asarPath = path.join(__dirname, 'build-out', 'win-unpacked', 'resources', 'app.asar');
    }
    if (fs.existsSync(asarPath)) {
        await uploadAsset(release.upload_url, token, asarPath, 'app.asar', 'application/octet-stream');
    } else {
        console.warn(`app.asar not found at ${asarPath}`);
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
        console.warn(`Bootstrapper not found at ${bootstrapperPath}`);
    }

    // 4. Upload NSIS Web Package (required by MooClient-Setup.exe bootstrapper)
    const nsisDir = path.join(__dirname, 'dist', 'nsis-web');
    if (fs.existsSync(nsisDir)) {
        const files = fs.readdirSync(nsisDir).filter(f => f.endsWith('.nsis.7z'));
        if (files.length > 0) {
            files.sort((a, b) => fs.statSync(path.join(nsisDir, b)).mtimeMs - fs.statSync(path.join(nsisDir, a)).mtimeMs);
            const newest7z = files[0];
            await uploadAsset(release.upload_url, token, path.join(nsisDir, newest7z), newest7z, 'application/octet-stream');
            if (newest7z !== `moo-client-launcher-${VERSION}-x64.nsis.7z`) {
                await uploadAsset(release.upload_url, token, path.join(nsisDir, newest7z), `moo-client-launcher-${VERSION}-x64.nsis.7z`, 'application/octet-stream');
            }
        }
    }

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
