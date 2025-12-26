package org.zyneonstudios.apex.bootstrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class ApexBootstrapper implements Bootstrapper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String url;
    private final String path;
    private final String json;
    private final String name;
    private final boolean offline;
    private String currentVersion;
    private String latestVersion;
    private final File localMetaDataFile;
    private final JsonObject localMetaData;
    private final boolean autoUpdate;
    private boolean forceUpdate;

    private final String executableUrl;
    private final boolean outputLogs;
    private final boolean outputErrors;
    private final String[] args;

    private JFrame bootstrapperFrame = null;

    public ApexBootstrapper(String url) {
        this(url,".",new File("./bootstrapper-meta.json"),new String[0],true,true);
    }

    public ApexBootstrapper(String url, String[] args) {
        this(url,".",new File("./bootstrapper-meta.json"),args,true,true);
    }

    public ApexBootstrapper(String url, String path, File localMetaDataFile, String[] args, boolean outputLogs, boolean outputErrors) {
        this.outputLogs = outputLogs;
        this.outputErrors = outputErrors;
        this.args = args;
        log("Initializing ApexBootstrapper with URL: " + url + " and Path: " + path);
        this.url = url;
        if (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        this.path = path;
        File base = new File(path);
        if (base.mkdirs()) {
            log("Created directory path (first launch): " + path);
        }

        String fetched = getData(url);
        if (fetched == null) {
            log("No data found. Switching to offline mode.");
            this.json = "{}";
            this.offline = true;
        } else {
            log("Data found, fetching information...");
            this.json = fetched;
            this.offline = false;
        }

        this.localMetaDataFile = localMetaDataFile;
        if(localMetaDataFile!=null&&localMetaDataFile.exists()) {
            log("Found local meta data...");
        }
        this.localMetaData = getLocalMetaData();

        if (localMetaData.has("installedVersion")) {
            currentVersion = localMetaData.get("installedVersion").getAsString();
        } else {
            currentVersion = "0";
        }
        log("Current version is: " + currentVersion);

        if (localMetaData.has("autoUpdate")) {
            autoUpdate = localMetaData.get("autoUpdate").getAsBoolean();
        } else {
            autoUpdate = true;
        }

        String productName = "application";
        if(localMetaData.has("installedProduct")) {
            productName = localMetaData.get("installedProduct").getAsString();
        }

        if (localMetaData.has("forceUpdate")) {
            forceUpdate = localMetaData.get("forceUpdate").getAsBoolean();
        } else {
            forceUpdate = false;
        }

        JsonObject jsonObject = GSON.fromJson(this.json, JsonObject.class);
        if (jsonObject == null) {
            throw new NullPointerException("The data could not be parsed.");
        }
        if (jsonObject.has("version")) {
            latestVersion = jsonObject.get("version").getAsString();
        } else {
            log("No version info in metadata, using current version as latest.");
            latestVersion = currentVersion;
        }

        this.name = jsonObject.has("name") ? jsonObject.get("name").getAsString() : productName;

        if (jsonObject.has("downloadUrl")) {
            executableUrl = jsonObject.get("downloadUrl").getAsString();
            log("Latest version is: " + latestVersion);
        } else {
            executableUrl = "";
            log("No download URL found for latest version. Reverting to current version.");
            latestVersion = currentVersion;
            forceUpdate = false;
        }

        if(offline && !localMetaDataFile.exists()) {
            if(Desktop.isDesktopSupported()) {
                Main.initLookAndFeel();
                JDialog errorDialog = new JDialog();
                errorDialog.setTitle("Apex Bootstrapper - Error");
                errorDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JLabel messageLabel = new JLabel("<html><body style='width: 300px; padding: 10px;'>The local meta data file does not exist. An internet connection is required for the first launch.</body></html>");
                messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
                errorDialog.getContentPane().add(messageLabel, BorderLayout.CENTER);
                errorDialog.setSize(350, 150);
                errorDialog.setResizable(false);
                errorDialog.setLocationRelativeTo(null);
                errorDialog.setModal(true);
                errorDialog.setVisible(true);
            }
            throw new RuntimeException("The local meta data file does not exist. An internet connection is required for the first launch.");
        }

        System.gc();
    }

    private String getData(String urlString) {
        log("Fetching data from: " + urlString);
        HttpURLConnection connection = null;
        try {
            URL url = new URI(urlString).toURL();
            String protocol = url.getProtocol();

            if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "ApexBootstrapper/1.0");

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream in = connection.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        return response.toString();
                    }
                } else {
                    logError("HTTP error: " + code + " - " + connection.getResponseMessage());
                    return null;
                }
            } else {
                try (InputStream in = url.openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                }
            }
        } catch (Exception e) {
            logError("Error fetching data from URL: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JsonObject getLocalMetaData() {
        try {
            if (localMetaDataFile != null && localMetaDataFile.exists() && localMetaDataFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(localMetaDataFile))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                    JsonObject obj = GSON.fromJson(content.toString(), JsonObject.class);
                    return obj != null ? obj : new JsonObject();
                }
            } else {
                log("Local metadata file not found, using defaults.");
                return new JsonObject();
            }
        } catch (Exception e) {
            logError("Error reading local metadata file: " + e.getMessage());
        }
        return new JsonObject();
    }

    @Override
    public String getCurrentVersion() {
        return currentVersion;
    }

    @Override
    public String getLatestVersion() {
        return latestVersion;
    }

    @Override
    public String getJsonUrl() {
        return url;
    }

    @Override
    public String getJson() {
        return json;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean isLatest() {
        return currentVersion.equals(latestVersion);
    }

    @Override
    public boolean isOffline() {
        return offline;
    }

    @Override
    public boolean isAutoUpdateEnabled() {
        return autoUpdate;
    }

    @Override
    public boolean forceUpdate() {
        return forceUpdate;
    }

    @Override
    public boolean hasFallback() {
        return false;
    }

    @Override
    public boolean update() {
        if (!isOffline()) {
            if (!new File(getExecutablePath()).exists() || forceUpdate || (!isLatest() && autoUpdate)) {
                label.setText("Updating to version " + latestVersion + "...");
                label.setForeground(Color.white);
                if (downloadExecutable()) {
                    if (updateVersion()) {
                        log("Update to version " + latestVersion + " completed successfully.");
                    } else {
                        logError("Failed to update version information after download. The updater will start the update again on next launch.");
                        return false;
                    }
                    return true;
                } else {
                    logError("Update to version " + latestVersion + " failed during download.");
                    return false;
                }
            }
        }
        log("Skipping update: " + (isOffline() ? "No connection with the update server." : "Latest version already installed or updates are disabled."));
        return false;
    }

    @SuppressWarnings("all")
    private boolean updateVersion() {
        currentVersion = latestVersion;
        if (localMetaData.has("installedVersion")) {
            localMetaData.remove("installedVersion");
        }
        if (localMetaData.has("forceUpdate")) {
            localMetaData.remove("forceUpdate");
        }
        if (!localMetaData.has("autoUpdate")) {
            localMetaData.addProperty("autoUpdate", true);
        }
        if (localMetaData.has("installedProduct")) {
            localMetaData.remove("installedProduct");
        }
        localMetaData.addProperty("forceUpdate", false);
        localMetaData.addProperty("installedProduct", name);
        localMetaData.addProperty("installedVersion", latestVersion);
        try {
            if (localMetaDataFile != null) {
                File parent = localMetaDataFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileWriter writer = new FileWriter(localMetaDataFile)) {
                    writer.write(GSON.toJson(localMetaData));
                }
                log("Updated local metadata file with new version.");
                return true;
            } else {
                logError("Local metadata file reference is null.");
            }
        } catch (Exception e) {
            logError("Error updating local metadata file: " + e.getMessage());
        }
        return false;
    }

    private boolean downloadExecutable() {
        File cacheDir = new File(path, "cache");
        if (!cacheDir.exists()) {
            if (!cacheDir.mkdirs()) {
                logError("Failed to create cache directory: " + cacheDir.getAbsolutePath());
                return false;
            }
        } else {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".jar") && f.getName().startsWith(name + "-v")) {
                        if (!f.delete()) {
                            logError("Failed to delete local old executable: " + f.getAbsolutePath());
                        }
                    }
                }
            }
        }

        File destination = new File(cacheDir, name + "-v" + latestVersion + ".jar");

        log("Downloading executable from: " + executableUrl + " to: " + destination.getAbsolutePath());
        HttpURLConnection connection = null;
        try {
            if (destination.exists() && !destination.delete()) {
                logError("Failed to delete existing file at destination. Aborting download.");
                return false;
            }
            URL url = new URI(executableUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "ApexBootstrapper/1.0");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                logError("HTTP error downloading executable: " + code + " - " + connection.getResponseMessage());
                return false;
            }

            try (InputStream in = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            log("Download completed successfully.");
            return true;
        } catch (Exception e) {
            logError("Error downloading executable: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public int launch() {
        String executablePath = getExecutablePath();
        if (!new File(executablePath).exists()) {
            logError("Executable not found at path: " + executablePath);
            return -1;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command().add("java");
            processBuilder.command().add("-jar");
            processBuilder.command().add(executablePath);
            if (args != null) {
                for (String arg : args) {
                    processBuilder.command().add(arg);
                }
            }
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            log("Launching " + executablePath + "...");
            int exitCode = process.waitFor();
            if (exitCode == -2) {
                return launch();
            }
            return exitCode;
        } catch (Exception e) {
            logError("Error launching executable: " + e.getMessage());
            return -1;
        }
    }

    @Override
    public int launchFallback() {
        logError("This bootstrapper does not have a fallback version.");
        return -1;
    }

    private void log(String message) {
        if (outputLogs) {
            System.out.println("[APEX-BOOTSTRAPPER] " + message);
        }
    }

    private void logError(String message) {
        if (outputErrors) {
            System.err.println("[APEX-BOOTSTRAPPER] " + message);
        }
    }

    private final JLabel label = new JLabel("Checking for updates...");

    public void showFrame() {
        if (bootstrapperFrame == null) {
            bootstrapperFrame = new JFrame("Apex Bootstrapper");
            bootstrapperFrame.getRootPane().putClientProperty("JRootPane.titleBarBackground", Color.black);
            bootstrapperFrame.getRootPane().putClientProperty("JRootPane.titleBarForeground", Color.white);
            bootstrapperFrame.getRootPane().setBackground(Color.black);
            bootstrapperFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            JPanel panel = new JPanel();
            panel.setBackground(null);
            panel.setLayout(new BorderLayout());
            bootstrapperFrame.setLayout(new BorderLayout());
            label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
            label.setForeground(Color.white);
            panel.add(label, BorderLayout.CENTER);
            panel.add(progressBar, BorderLayout.SOUTH);
            bootstrapperFrame.add(panel, BorderLayout.CENTER);
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            bootstrapperFrame.setSize(350, 100);
            bootstrapperFrame.setResizable(false);
            bootstrapperFrame.setLocationRelativeTo(null);
            bootstrapperFrame.setVisible(true);
        } else {
            bootstrapperFrame.setVisible(true);
        }
    }

    public void hideFrame() {
        if (bootstrapperFrame != null) {
            bootstrapperFrame.setVisible(false);
            bootstrapperFrame.dispose();
            bootstrapperFrame = null;
            System.gc();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public String getExecutablePath() {
        return path + File.separator + "cache" + File.separator + name + "-v" + getCurrentVersion() + ".jar";
    }
}
