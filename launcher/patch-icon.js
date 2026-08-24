const { rcedit } = require('rcedit');
const path = require('path');

const exePath = path.join(__dirname, 'dist-win', 'win-unpacked', 'Moo Client.exe');
const icoPath = path.join(__dirname, 'build', 'icon.ico');

const pkg = require('./package.json');
const ver = pkg.version || '1.6.3';

async function run() {
    // Step 1: Patch icon and version info
    await rcedit(exePath, {
        icon: icoPath,
        'product-version': ver,
        'file-version': ver,
        'version-string': {
            ProductName: 'Moo Client',
            FileDescription: 'Moo Client Launcher',
            CompanyName: 'MooTeam',
            OriginalFilename: 'Moo Client.exe'
        }
    });
    console.log('SUCCESS! Cow icon and version set on Moo Client.exe');
}

run().catch(console.error);
