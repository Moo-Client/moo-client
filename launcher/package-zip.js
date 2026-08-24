const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const winUnpacked = path.join(__dirname, 'build-out', 'win-unpacked');
const zipOut = path.join(__dirname, 'build-out', 'moo-client-launcher-win64.zip');

if (!fs.existsSync(winUnpacked)) {
    console.error('win-unpacked directory does not exist:', winUnpacked);
    process.exit(1);
}

if (fs.existsSync(zipOut)) {
    try { fs.unlinkSync(zipOut); } catch (e) { }
}

console.log('Packaging win-unpacked into moo-client-launcher-win64.zip...');

// Use PowerShell with FileShare.ReadWrite to avoid locking issues
const psScript = `
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

\$sourceDir = "${winUnpacked.replace(/\\/g, '\\\\')}"
\$zipPath = "${zipOut.replace(/\\/g, '\\\\')}"

if (Test-Path \$zipPath) { Remove-Item \$zipPath -Force }

\$zip = [System.IO.Compression.ZipFile]::Open(\$zipPath, [System.IO.Compression.ZipArchiveMode]::Create)

Get-ChildItem -Path \$sourceDir -Recurse | Where-Object { -not \$_.PSIsContainer } | ForEach-Object {
    \$relPath = \$_.FullName.Substring(\$sourceDir.Length + 1)
    \$entry = \$zip.CreateEntry(\$relPath, [System.IO.Compression.CompressionLevel]::Optimal)
    \$fs = [System.IO.File]::Open(\$_.FullName, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    \$entryStream = \$entry.Open()
    \$fs.CopyTo(\$entryStream)
    \$entryStream.Close()
    \$fs.Close()
}

\$zip.Dispose()
Write-Host "ZIP created successfully: \$zipPath"
`;

try {
    execSync(`powershell -NoProfile -Command "${psScript.replace(/\n/g, ' ')}"`, { stdio: 'inherit' });
    const stat = fs.statSync(zipOut);
    console.log(`Success! Package size: ${(stat.size / (1024 * 1024)).toFixed(2)} MB`);
} catch (e) {
    console.error('Error creating zip:', e.message);
    process.exit(1);
}
