using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Diagnostics;

namespace MooClient.Bootstrapper
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new InstallerForm());
        }
    }

    public class InstallerForm : Form
    {
        // Win32 Dragging
        public const int WM_NCLBUTTONDOWN = 0xA1;
        public const int HTCAPTION = 0x2;
        [DllImport("user32.dll")]
        public static extern bool ReleaseCapture();
        [DllImport("user32.dll")]
        public static extern int SendMessage(IntPtr hWnd, int Msg, int wParam, int lParam);

        // UI Controls
        private string statusText = "Inicjalizacja...";
        private string detailsText = "Sprawdzanie dostępnych aktualizacji...";
        private float progressPercentage = 0f;
        private bool isClosing = false;
        private Rectangle closeBtnRect;
        private Rectangle minBtnRect;
        private bool closeHovered = false;
        private bool minHovered = false;
        private Image logoImage = null;

        // Colors
        private readonly Color ColorBg = Color.FromArgb(17, 17, 22);
        private readonly Color ColorCard = Color.FromArgb(24, 24, 34);
        private readonly Color ColorBorder = Color.FromArgb(60, 255, 255, 255);
        private readonly Color ColorAccent = Color.FromArgb(34, 197, 94);       // #22C55E
        private readonly Color ColorAccentLight = Color.FromArgb(74, 222, 128);  // #4ADE80
        private readonly Color ColorTextWhite = Color.FromArgb(245, 245, 250);
        private readonly Color ColorTextMuted = Color.FromArgb(160, 160, 175);

        public InstallerForm()
        {
            this.Text = "Moo Client Setup";
            this.FormBorderStyle = FormBorderStyle.None;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.Size = new Size(460, 220);
            this.BackColor = ColorBg;
            this.DoubleBuffered = true;
            this.ShowIcon = true;

            // Load Embedded or Local Icon
            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("icon.ico"))
                {
                    if (stream != null)
                    {
                        this.Icon = new Icon(stream);
                    }
                    else
                    {
                        this.Icon = Icon.ExtractAssociatedIcon(Assembly.GetExecutingAssembly().Location);
                    }
                }
            }
            catch { }

            // Load Embedded Logo Image
            try
            {
                using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("logo.png"))
                {
                    if (stream != null)
                    {
                        logoImage = Image.FromStream(stream);
                    }
                }
            }
            catch { }

            closeBtnRect = new Rectangle(this.Width - 36, 8, 28, 24);
            minBtnRect = new Rectangle(this.Width - 68, 8, 28, 24);

            this.MouseDown += InstallerForm_MouseDown;
            this.MouseMove += InstallerForm_MouseMove;
            this.MouseClick += InstallerForm_MouseClick;
            this.Paint += InstallerForm_Paint;

            // Start Installation Task
            Task.Run(() => RunInstallation());
        }

        private void InstallerForm_MouseDown(object sender, MouseEventArgs e)
        {
            if (e.Button == MouseButtons.Left && e.Y <= 40 && !closeBtnRect.Contains(e.Location) && !minBtnRect.Contains(e.Location))
            {
                ReleaseCapture();
                SendMessage(this.Handle, WM_NCLBUTTONDOWN, HTCAPTION, 0);
            }
        }

        private void InstallerForm_MouseMove(object sender, MouseEventArgs e)
        {
            bool ch = closeBtnRect.Contains(e.Location);
            bool mh = minBtnRect.Contains(e.Location);
            if (ch != closeHovered || mh != minHovered)
            {
                closeHovered = ch;
                minHovered = mh;
                this.Invalidate();
            }
        }

        private void InstallerForm_MouseClick(object sender, MouseEventArgs e)
        {
            if (e.Button == MouseButtons.Left)
            {
                if (closeBtnRect.Contains(e.Location))
                {
                    isClosing = true;
                    this.Close();
                }
                else if (minBtnRect.Contains(e.Location))
                {
                    this.WindowState = FormWindowState.Minimized;
                }
            }
        }

        private void UpdateUI(string status, string details, float progress)
        {
            if (this.IsDisposed || isClosing) return;
            try
            {
                this.BeginInvoke(new Action(() =>
                {
                    if (!string.IsNullOrEmpty(status)) this.statusText = status;
                    if (!string.IsNullOrEmpty(details)) this.detailsText = details;
                    if (progress >= 0f) this.progressPercentage = Math.Min(100f, Math.Max(0f, progress));
                    this.Invalidate();
                }));
            }
            catch { }
        }

        private void InstallerForm_Paint(object sender, PaintEventArgs e)
        {
            Graphics g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

            // 1. Outer Border
            using (Pen borderPen = new Pen(ColorBorder, 1f))
            {
                g.DrawRectangle(borderPen, 0, 0, this.Width - 1, this.Height - 1);
            }

            // 2. Header Bar
            using (Brush cardBrush = new SolidBrush(ColorCard))
            {
                g.FillRectangle(cardBrush, 1, 1, this.Width - 2, 40);
            }
            using (Pen sepPen = new Pen(Color.FromArgb(30, 255, 255, 255), 1f))
            {
                g.DrawLine(sepPen, 1, 41, this.Width - 2, 41);
            }

            // Logo Icon & Title
            int iconX = 14;
            int iconY = 8;
            if (logoImage != null)
            {
                g.DrawImage(logoImage, new Rectangle(iconX, iconY, 24, 24));
            }
            else if (this.Icon != null)
            {
                g.DrawIcon(this.Icon, new Rectangle(iconX, iconY, 24, 24));
            }

            int titleX = iconX + 32;
            using (Font titleFont = new Font("Segoe UI", 10.5f, FontStyle.Bold))
            using (Brush titleBrush = new SolidBrush(ColorTextWhite))
            {
                g.DrawString("MOO CLIENT", titleFont, titleBrush, titleX, 10);
                SizeF titleSize = g.MeasureString("MOO CLIENT", titleFont);

                // Dynamic badge "INSTALLER" with rounded pill background
                int badgeX = (int)(titleX + titleSize.Width + 6);
                int badgeY = 12;
                using (Font badgeFont = new Font("Segoe UI", 7.5f, FontStyle.Bold))
                {
                    SizeF badgeSize = g.MeasureString("INSTALLER", badgeFont);
                    int badgeW = (int)badgeSize.Width + 10;
                    int badgeH = 16;

                    using (Brush bgBrush = new SolidBrush(Color.FromArgb(35, 34, 197, 94)))
                    using (Pen borderPen = new Pen(Color.FromArgb(80, 74, 222, 128), 1f))
                    {
                        g.FillRectangle(bgBrush, badgeX, badgeY, badgeW, badgeH);
                        g.DrawRectangle(borderPen, badgeX, badgeY, badgeW, badgeH);
                    }

                    using (Brush badgeBrush = new SolidBrush(ColorAccentLight))
                    {
                        g.DrawString("INSTALLER", badgeFont, badgeBrush, badgeX + 5, badgeY + 1);
                    }
                }
            }

            // Minimize Button
            if (minHovered)
            {
                using (Brush b = new SolidBrush(Color.FromArgb(40, 255, 255, 255)))
                {
                    g.FillRectangle(b, minBtnRect);
                }
            }
            using (Font ctrlFont = new Font("Segoe UI", 9f, FontStyle.Bold))
            using (Brush ctrlBrush = new SolidBrush(ColorTextMuted))
            {
                g.DrawString("─", ctrlFont, ctrlBrush, minBtnRect.X + 8, minBtnRect.Y + 4);
            }

            // Close Button
            if (closeHovered)
            {
                using (Brush b = new SolidBrush(Color.FromArgb(220, 239, 68, 68)))
                {
                    g.FillRectangle(b, closeBtnRect);
                }
            }
            using (Font ctrlFont = new Font("Segoe UI", 9f, FontStyle.Bold))
            using (Brush ctrlBrush = new SolidBrush(closeHovered ? Color.White : ColorTextMuted))
            {
                g.DrawString("✕", ctrlFont, ctrlBrush, closeBtnRect.X + 8, closeBtnRect.Y + 4);
            }

            // 3. Status Information
            using (Font statusFont = new Font("Segoe UI", 11f, FontStyle.Bold))
            using (Brush statusBrush = new SolidBrush(ColorTextWhite))
            {
                g.DrawString(this.statusText, statusFont, statusBrush, 24, 60);
            }

            using (Font detailsFont = new Font("Segoe UI", 9f, FontStyle.Regular))
            using (Brush detailsBrush = new SolidBrush(ColorTextMuted))
            {
                g.DrawString(this.detailsText, detailsFont, detailsBrush, 24, 88);
            }

            // 4. Custom Progress Bar
            int barX = 24;
            int barY = 124;
            int barW = this.Width - 48;
            int barH = 14;

            // Track Background
            using (Brush trackBrush = new SolidBrush(Color.FromArgb(30, 30, 42)))
            {
                g.FillRectangle(trackBrush, barX, barY, barW, barH);
            }
            using (Pen trackPen = new Pen(Color.FromArgb(40, 255, 255, 255), 1f))
            {
                g.DrawRectangle(trackPen, barX, barY, barW, barH);
            }

            // Fill Bar
            int fillW = (int)Math.Round((this.progressPercentage / 100f) * barW);
            if (fillW > 0)
            {
                using (LinearGradientBrush fillBrush = new LinearGradientBrush(
                    new Rectangle(barX, barY, Math.Max(1, fillW), barH),
                    ColorAccent, ColorAccentLight, LinearGradientMode.Horizontal))
                {
                    g.FillRectangle(fillBrush, barX + 1, barY + 1, Math.Max(0, fillW - 2), barH - 2);
                }
            }

            // Percentage Label
            string pctText = string.Format("{0:0}%", this.progressPercentage);
            using (Font pctFont = new Font("Segoe UI", 8.5f, FontStyle.Bold))
            using (Brush pctBrush = new SolidBrush(ColorAccentLight))
            {
                SizeF sz = g.MeasureString(pctText, pctFont);
                g.DrawString(pctText, pctFont, pctBrush, barX + barW - sz.Width, 144);
            }

            // Footer info
            using (Font footFont = new Font("Segoe UI", 7.5f, FontStyle.Regular))
            using (Brush footBrush = new SolidBrush(Color.FromArgb(100, 100, 120)))
            {
                g.DrawString("Moo Client • Najlepszy darmowy klient Minecraft", footFont, footBrush, 24, 186);
            }
        }

        // =============================================
        // Installation & Download Engine
        // =============================================
        private void RunInstallation()
        {
            try
            {
                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | (SecurityProtocolType)3072;

                UpdateUI("Łączenie z serwerem...", "Sprawdzanie najnowszej wersji na GitHub...", 5f);
                Thread.Sleep(300);

                string appData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string targetDir = Path.Combine(appData, "Programs", "Moo Client");
                string exePath = Path.Combine(targetDir, "Moo Client.exe");

                // Step 1: Query GitHub API
                string releaseApiUrl = "https://api.github.com/repos/Moo-Client/moo-client/releases/latest";
                string json = FetchStringWithUserAgent(releaseApiUrl);

                string zipUrl = null;
                string asarUrl = null;
                string jarUrl = null;
                string versionTag = "1.6.4";

                if (!string.IsNullOrEmpty(json))
                {
                    // Parse tag_name
                    int tagIdx = json.IndexOf("\"tag_name\":");
                    if (tagIdx > 0)
                    {
                        int start = json.IndexOf("\"", tagIdx + 11) + 1;
                        int end = json.IndexOf("\"", start);
                        if (start > 0 && end > start)
                        {
                            versionTag = json.Substring(start, end - start).Replace("v", "");
                        }
                    }

                    // Find assets
                    zipUrl = ExtractBrowserDownloadUrl(json, "moo-client-launcher-win64.zip");
                    if (string.IsNullOrEmpty(zipUrl)) zipUrl = ExtractBrowserDownloadUrl(json, ".zip");
                    asarUrl = ExtractBrowserDownloadUrl(json, "app.asar");
                    jarUrl = ExtractBrowserDownloadUrl(json, ".jar");
                }

                // Fallbacks if not found directly in assets (uses core-assets)
                if (string.IsNullOrEmpty(zipUrl))
                {
                    zipUrl = "https://github.com/Moo-Client/moo-client/releases/download/core-assets/moo-client-launcher-win64.zip";
                }
                if (string.IsNullOrEmpty(asarUrl))
                {
                    asarUrl = "https://github.com/Moo-Client/moo-client/releases/download/core-assets/app.asar";
                }
                if (string.IsNullOrEmpty(jarUrl))
                {
                    jarUrl = "https://github.com/Moo-Client/moo-client/releases/download/core-assets/moo-client.jar";
                }

                Directory.CreateDirectory(targetDir);

                // Step 2: Check if base client files already exist
                bool hasFullClient = File.Exists(exePath);

                if (!hasFullClient)
                {
                    // Download full package zip
                    UpdateUI("Pobieranie plików klienta...", "Pobieranie paczki bazowej (~65 MB)...", 10f);
                    string tempZip = Path.Combine(Path.GetTempPath(), "moo-client-launcher-" + Guid.NewGuid().ToString("N") + ".zip");

                    bool downloaded = DownloadFileWithProgress(zipUrl, tempZip, 10f, 75f);
                    if (!downloaded || !File.Exists(tempZip) || new FileInfo(tempZip).Length < 10000)
                    {
                        // Fallback: download asar directly if zip is not available
                        UpdateUI("Pobieranie launchera...", "Pobieranie pakietu aplikacji app.asar...", 20f);
                        string resourcesDir = Path.Combine(targetDir, "resources");
                        Directory.CreateDirectory(resourcesDir);
                        string asarDest = Path.Combine(resourcesDir, "app.asar");
                        DownloadFileWithProgress(asarUrl, asarDest, 20f, 80f);
                    }
                    else
                    {
                        // Extract zip
                        UpdateUI("Wypakowywanie...", "Instalowanie plików w " + targetDir, 80f);
                        ExtractZipToDirectory(tempZip, targetDir);
                        try { File.Delete(tempZip); } catch { }
                    }
                }
                else
                {
                    // Fast update: only download latest app.asar
                    UpdateUI("Aktualizacja plików...", "Pobieranie najnowszej wersji app.asar...", 20f);
                    string resourcesDir = Path.Combine(targetDir, "resources");
                    Directory.CreateDirectory(resourcesDir);
                    string asarDest = Path.Combine(resourcesDir, "app.asar");
                    DownloadFileWithProgress(asarUrl, asarDest, 20f, 85f);
                }

                // Step 3: Check and update moo-client.jar if needed
                string offlineDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".mooclient", "offline", "multiver");
                Directory.CreateDirectory(offlineDir);
                string jarDest = Path.Combine(offlineDir, "moo-client.jar");

                if (!string.IsNullOrEmpty(jarUrl))
                {
                    UpdateUI("Sprawdzanie silnika gry...", "Weryfikacja moo-client.jar...", 90f);
                    string tempJar = Path.Combine(Path.GetTempPath(), "moo-client-" + versionTag + ".jar");
                    if (DownloadFileWithProgress(jarUrl, tempJar, 90f, 96f))
                    {
                        try { File.Copy(tempJar, jarDest, true); } catch { }
                        try { File.Delete(tempJar); } catch { }
                    }
                }

                // Step 4: Create Shortcuts (Desktop and Start Menu)
                UpdateUI("Tworzenie skrótów...", "Tworzenie skrótu na Pulpicie i w Menu Start...", 98f);
                CreateShortcuts(exePath, targetDir);

                // Step 5: Launch Client
                UpdateUI("Gotowe!", "Uruchamianie Moo Client...", 100f);
                Thread.Sleep(600);

                if (File.Exists(exePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = exePath,
                        WorkingDirectory = targetDir
                    });
                }

                this.BeginInvoke(new Action(() => this.Close()));
            }
            catch (Exception ex)
            {
                UpdateUI("Wystąpił błąd", "Błąd: " + ex.Message, 0f);
                MessageBox.Show("Nie udało się ukończyć instalacji:\n" + ex.Message, "Moo Client Setup", MessageBoxButtons.OK, MessageBoxIcon.Error);
                this.BeginInvoke(new Action(() => this.Close()));
            }
        }

        private string FetchStringWithUserAgent(string url)
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.UserAgent = "MooClient-Bootstrapper/1.0";
                req.Timeout = 8000;
                req.Accept = "application/vnd.github.v3+json";
                using (HttpWebResponse res = (HttpWebResponse)req.GetResponse())
                using (StreamReader reader = new StreamReader(res.GetResponseStream(), Encoding.UTF8))
                {
                    return reader.ReadToEnd();
                }
            }
            catch
            {
                return null;
            }
        }

        private string ExtractBrowserDownloadUrl(string json, string pattern)
        {
            if (string.IsNullOrEmpty(json)) return null;
            int idx = 0;
            while ((idx = json.IndexOf("\"browser_download_url\":", idx)) > 0)
            {
                int start = json.IndexOf("\"", idx + 23) + 1;
                int end = json.IndexOf("\"", start);
                if (start > 0 && end > start)
                {
                    string url = json.Substring(start, end - start);
                    if (url.IndexOf(pattern, StringComparison.OrdinalIgnoreCase) >= 0)
                    {
                        return url;
                    }
                }
                idx = end;
            }
            return null;
        }

        private bool DownloadFileWithProgress(string url, string destPath, float startProgress, float endProgress)
        {
            try
            {
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.UserAgent = "MooClient-Bootstrapper/1.0";
                req.Timeout = 60000;

                using (HttpWebResponse res = (HttpWebResponse)req.GetResponse())
                {
                    long totalBytes = res.ContentLength;
                    using (Stream src = res.GetResponseStream())
                    using (FileStream dst = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        byte[] buffer = new byte[32768];
                        long downloaded = 0;
                        int read;
                        DateTime lastTime = DateTime.UtcNow;
                        long lastDownloaded = 0;
                        double currentSpeedMbps = 0;

                        while ((read = src.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            if (isClosing) return false;
                            dst.Write(buffer, 0, read);
                            downloaded += read;

                            DateTime now = DateTime.UtcNow;
                            double elapsed = (now - lastTime).TotalSeconds;
                            if (elapsed >= 0.3)
                            {
                                currentSpeedMbps = ((downloaded - lastDownloaded) / (1024.0 * 1024.0)) / elapsed;
                                lastTime = now;
                                lastDownloaded = downloaded;

                                float curRange = (totalBytes > 0) ? (float)downloaded / totalBytes : 0.5f;
                                float overallPct = startProgress + curRange * (endProgress - startProgress);

                                string speedStr = string.Format("{0:0.0} MB/s", currentSpeedMbps);
                                string progressStr = (totalBytes > 0)
                                    ? string.Format("{0:0.0} MB / {1:0.0} MB • {2}", (downloaded / 1048576.0), (totalBytes / 1048576.0), speedStr)
                                    : string.Format("{0:0.0} MB pobrano • {1}", (downloaded / 1048576.0), speedStr);

                                UpdateUI(null, progressStr, overallPct);
                            }
                        }
                    }
                }
                return true;
            }
            catch (Exception ex)
            {
                Console.WriteLine("Download failed for " + url + ": " + ex.Message);
                return false;
            }
        }

        private void ExtractZipToDirectory(string zipPath, string targetDir)
        {
            using (ZipArchive archive = ZipFile.OpenRead(zipPath))
            {
                int total = archive.Entries.Count;
                int current = 0;

                foreach (ZipArchiveEntry entry in archive.Entries)
                {
                    current++;
                    string destinationPath = Path.GetFullPath(Path.Combine(targetDir, entry.FullName));
                    if (!destinationPath.StartsWith(Path.GetFullPath(targetDir), StringComparison.OrdinalIgnoreCase))
                    {
                        continue; // Protect against Zip Slip
                    }

                    if (string.IsNullOrEmpty(entry.Name))
                    {
                        Directory.CreateDirectory(destinationPath);
                    }
                    else
                    {
                        Directory.CreateDirectory(Path.GetDirectoryName(destinationPath));
                        entry.ExtractToFile(destinationPath, true);
                    }

                    if (current % 10 == 0 || current == total)
                    {
                        float pct = 80f + ((float)current / total) * 16f;
                        UpdateUI("Wypakowywanie...", string.Format("Instalowanie {0}/{1} plików...", current, total), pct);
                    }
                }
            }
        }

        private void CreateShortcuts(string exePath, string targetDir)
        {
            try
            {
                string desktopPath = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string startMenuPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.StartMenu), "Programs");
                string iconPath = Path.Combine(targetDir, "build", "icon.ico");
                if (!File.Exists(iconPath)) iconPath = exePath;

                string desktopShortcut = Path.Combine(desktopPath, "Moo Client.lnk");
                string startMenuShortcut = Path.Combine(startMenuPath, "Moo Client.lnk");

                CreateWScriptShortcut(desktopShortcut, exePath, iconPath, "Moo Client Minecraft Launcher");
                CreateWScriptShortcut(startMenuShortcut, exePath, iconPath, "Moo Client Minecraft Launcher");
            }
            catch { }
        }

        private void CreateWScriptShortcut(string shortcutPath, string targetPath, string iconPath, string description)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                dynamic shell = Activator.CreateInstance(shellType);
                dynamic shortcut = shell.CreateShortcut(shortcutPath);
                shortcut.TargetPath = targetPath;
                shortcut.WorkingDirectory = Path.GetDirectoryName(targetPath);
                shortcut.Description = description;
                if (File.Exists(iconPath))
                {
                    shortcut.IconLocation = iconPath + ",0";
                }
                shortcut.Save();
            }
            catch { }
        }
    }
}
