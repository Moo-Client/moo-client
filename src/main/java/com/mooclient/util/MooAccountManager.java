package com.mooclient.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mooclient.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Account Manager for Moo Client (Microsoft Premium Only).
 * Seamlessly manages and synchronizes accounts with Moo Launcher (~/.mooclient/accounts.json).
 * Invokes the official Moo Launcher Minecraft Login window (MSMC) directly.
 */
public class MooAccountManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LAUNCHER_API_URL = "http://127.0.0.1:49152/api/login-microsoft";

    private static MooAccountManager instance;

    public static class Account {
        private String name;
        private String uuid;
        private String type;
        private String accessToken;
        private String clientToken;
        private String xuid;

        public Account(String name, String uuid, String type, String accessToken, String clientToken, String xuid) {
            this.name = name != null ? name : "Player";
            this.uuid = uuid != null ? uuid : "";
            this.type = "microsoft";
            this.accessToken = accessToken != null ? accessToken : "";
            this.clientToken = clientToken != null ? clientToken : "";
            this.xuid = xuid != null ? xuid : "";
        }

        public String getName() { return name; }
        public String getUuid() { return uuid; }
        public String getType() { return type != null ? type : "microsoft"; }
        public String getAccessToken() { return accessToken; }
        public String getClientToken() { return clientToken; }
        public String getXuid() { return xuid; }

        public UUID getParsedUuid() {
            try {
                if (uuid.contains("-")) {
                    return UUID.fromString(uuid);
                } else if (uuid.length() == 32) {
                    return UUID.fromString(uuid.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                }
            } catch (Exception ignored) {}
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }
    }

    public static class LoginState {
        public boolean inProgress = false;
        public boolean success = false;
        public boolean failed = false;
        public String status = "";
        public String errorMsg = "";
    }

    private final List<Account> accounts = new ArrayList<>();
    private String activeUuid = null;
    private final File accountsFile;
    private final File activeAccountFile;
    private final LoginState loginState = new LoginState();

    private MooAccountManager() {
        File homeDir = new File(System.getProperty("user.home"), ".mooclient");
        if (!homeDir.exists()) homeDir.mkdirs();
        this.accountsFile = new File(homeDir, "accounts.json");
        this.activeAccountFile = new File(homeDir, "account.json");
        load();
    }

    public static synchronized MooAccountManager getInstance() {
        if (instance == null) {
            instance = new MooAccountManager();
        }
        return instance;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public String getActiveUuid() {
        return activeUuid;
    }

    public Account getActiveAccount() {
        if (accounts.isEmpty()) return null;
        for (Account a : accounts) {
            if (a.getUuid().equalsIgnoreCase(activeUuid)) return a;
        }
        return accounts.get(0);
    }

    public boolean isActive(Account acc) {
        if (acc == null) return false;
        if (activeUuid != null && activeUuid.equalsIgnoreCase(acc.getUuid())) return true;
        Account active = getActiveAccount();
        return active != null && active.getUuid().equalsIgnoreCase(acc.getUuid());
    }

    public void selectAccount(Account account) {
        if (account == null) return;
        this.activeUuid = account.getUuid();
        applySession(account);
        save();
    }

    public void removeAccount(String uuid) {
        if (uuid == null) return;
        boolean wasActive = (activeUuid != null && activeUuid.equalsIgnoreCase(uuid));
        accounts.removeIf(a -> a.getUuid().equalsIgnoreCase(uuid));

        if (wasActive && !accounts.isEmpty()) {
            selectAccount(accounts.get(0));
        } else if (accounts.isEmpty()) {
            activeUuid = null;
        }
        save();
    }

    private void applySession(Account account) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return;

            UUID parsedUuid = account.getParsedUuid();
            Session.AccountType accType = Session.AccountType.MSA;
            Optional<String> xuidOpt = (account.getXuid() != null && !account.getXuid().isEmpty()) ? Optional.of(account.getXuid()) : Optional.empty();

            Session newSession = new Session(
                    account.getName(),
                    parsedUuid,
                    account.getAccessToken() != null ? account.getAccessToken() : "",
                    xuidOpt,
                    Optional.empty(),
                    accType
            );

            ((MinecraftClientAccessor) client).moo$setSession(newSession);
        } catch (Throwable e) {
            System.err.println("[MooClient] Failed to apply session: " + e.getMessage());
        }
    }

    public void load() {
        accounts.clear();
        if (accountsFile.exists()) {
            try (FileReader reader = new FileReader(accountsFile, StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    if (obj.has("activeUuid") && !obj.get("activeUuid").isJsonNull()) {
                        this.activeUuid = obj.get("activeUuid").getAsString();
                    }
                    if (obj.has("accounts") && obj.get("accounts").isJsonArray()) {
                        JsonArray arr = obj.getAsJsonArray("accounts");
                        for (JsonElement el : arr) {
                            if (el.isJsonObject()) {
                                JsonObject aObj = el.getAsJsonObject();
                                String name = aObj.has("name") ? aObj.get("name").getAsString() : "Player";
                                String uuid = aObj.has("uuid") ? aObj.get("uuid").getAsString() : "";
                                String type = aObj.has("type") ? aObj.get("type").getAsString() : "microsoft";
                                String accessToken = "";
                                String clientToken = "";
                                String xuid = "";

                                if (aObj.has("mclc") && aObj.get("mclc").isJsonObject()) {
                                    JsonObject mclc = aObj.getAsJsonObject("mclc");
                                    if (mclc.has("access_token")) accessToken = mclc.get("access_token").getAsString();
                                    if (mclc.has("client_token")) clientToken = mclc.get("client_token").getAsString();
                                    if (mclc.has("meta") && mclc.getAsJsonObject("meta").has("xuid")) {
                                        xuid = mclc.getAsJsonObject("meta").get("xuid").getAsString();
                                    }
                                }

                                accounts.add(new Account(name, uuid, type, accessToken, clientToken, xuid));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MooClient] Error loading accounts.json: " + e.getMessage());
            }
        }

        // Add current session if list is empty
        if (accounts.isEmpty()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getSession() != null) {
                String uName = client.getSession().getUsername();
                String uUuid = client.getSession().getUuidOrNull() != null ? client.getSession().getUuidOrNull().toString().replace("-", "") : "";
                Account defaultAcc = new Account(uName, uUuid, "microsoft", client.getSession().getAccessToken(), "", client.getSession().getXuid().orElse(""));
                accounts.add(defaultAcc);
                this.activeUuid = defaultAcc.getUuid();
                save();
            }
        }
    }

    public void save() {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("activeUuid", activeUuid != null ? activeUuid : "");

            JsonArray arr = new JsonArray();
            for (Account a : accounts) {
                JsonObject aObj = new JsonObject();
                aObj.addProperty("name", a.getName());
                aObj.addProperty("uuid", a.getUuid());
                aObj.addProperty("type", "microsoft");

                JsonObject mclc = new JsonObject();
                mclc.addProperty("access_token", a.getAccessToken());
                mclc.addProperty("client_token", a.getClientToken());
                mclc.addProperty("uuid", a.getUuid());
                mclc.addProperty("name", a.getName());
                JsonObject meta = new JsonObject();
                meta.addProperty("xuid", a.getXuid());
                meta.addProperty("type", "msa");
                mclc.add("meta", meta);
                mclc.add("user_properties", new JsonObject());
                aObj.add("mclc", mclc);

                arr.add(aObj);
            }
            obj.add("accounts", arr);

            try (FileWriter writer = new FileWriter(accountsFile, StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            }

            // Sync active account to account.json
            Account active = getActiveAccount();
            if (active != null) {
                try (FileWriter writer = new FileWriter(activeAccountFile, StandardCharsets.UTF_8)) {
                    JsonObject actObj = new JsonObject();
                    actObj.addProperty("name", active.getName());
                    actObj.addProperty("uuid", active.getUuid());
                    actObj.addProperty("type", "microsoft");
                    GSON.toJson(actObj, writer);
                }
            }
        } catch (Exception e) {
            System.err.println("[MooClient] Error saving accounts.json: " + e.getMessage());
        }
    }

    // --- Official Moo Launcher Minecraft Login ---

    public LoginState getLoginState() {
        return loginState;
    }

    public void cancelLogin() {
        loginState.inProgress = false;
        loginState.status = "";
    }

    public void startMicrosoftLogin() {
        if (loginState.inProgress) return;
        loginState.inProgress = true;
        loginState.success = false;
        loginState.failed = false;
        loginState.errorMsg = "";
        loginState.status = "Otwieranie okna logowania Minecraft...";

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Try connecting to the running Moo Launcher local API
                JsonObject resp = null;
                try {
                    resp = getJson(LAUNCHER_API_URL);
                } catch (Exception ignored) {}

                if (resp != null && resp.has("success") && resp.get("success").getAsBoolean()) {
                    load();
                    loginState.inProgress = false;
                    loginState.success = true;
                    loginState.status = "Pomyślnie dodano konto!";
                    return;
                }

                // 2. If launcher API is not responding, launch MSMC auth window process directly
                File homeDir = new File(System.getProperty("user.home"), ".mooclient");
                File authScript = new File(homeDir, "auth-window.js");
                
                // Also check launcher directory
                File ideDir = new File("launcher/auth-window.js");
                String scriptPath = ideDir.exists() ? ideDir.getAbsolutePath() : authScript.getAbsolutePath();

                long lastModBefore = accountsFile.exists() ? accountsFile.lastModified() : 0;
                int countBefore = accounts.size();

                // Launch electron auth-window
                ProcessBuilder pb;
                File launcherExe = new File(System.getenv("LOCALAPPDATA") + "\\Programs\\moo-client-launcher\\Moo Client.exe");
                if (launcherExe.exists()) {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "\"" + launcherExe.getAbsolutePath() + "\"", "--login-microsoft");
                } else {
                    pb = new ProcessBuilder("cmd.exe", "/c", "npx", "electron", scriptPath);
                    if (ideDir.exists()) pb.directory(new File("launcher"));
                }

                Process proc = pb.start();

                // Watch accounts.json for updates (up to 3 minutes)
                long startTime = System.currentTimeMillis();
                while ((System.currentTimeMillis() - startTime) < 180000) {
                    Thread.sleep(800);
                    if (accountsFile.exists() && accountsFile.lastModified() > lastModBefore) {
                        load();
                        if (accounts.size() > countBefore || (activeUuid != null)) {
                            Account active = getActiveAccount();
                            if (active != null) selectAccount(active);
                            loginState.inProgress = false;
                            loginState.success = true;
                            loginState.status = "Pomyślnie dodano konto!";
                            return;
                        }
                    }
                    if (!proc.isAlive()) {
                        break;
                    }
                }

                load();
                loginState.inProgress = false;
            } catch (Exception e) {
                load();
                loginState.inProgress = false;
                loginState.failed = true;
                loginState.errorMsg = e.getMessage() != null ? e.getMessage() : "Błąd logowania.";
                loginState.status = loginState.errorMsg;
            }
        });
    }

    private JsonObject getJson(String urlStr) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(180000); // 3 minutes for user to select account

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        return GSON.fromJson(resp, JsonObject.class);
    }
}
