package org.zyneonstudios.apex.bootstrapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class ApexBootstrapper implements Bootstrapper {

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

    public ApexBootstrapper(String url, String path, File localMetaDataFile, String[] args,boolean outputLogs, boolean outputErrors) {
        this.outputLogs = outputLogs;
        this.outputErrors = outputErrors;
        this.args = args;
        log("Initializing ApexBootstrapper with URL: " + url + " and Path: " + path);
        this.url = url;
        if (path.endsWith("/") || path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        this.path = path;
        if (new File(path).mkdirs()) {
            log("Created directory path (first launch): " + path);
        }
        String data = getData(url);
        if (data == null) {
            log("No data found. Switching to offline mode.");
            this.json = "{}";
            this.offline = true;
        } else {
            log("Data found, fetching information...");
            this.json = data;
            this.offline = false;
        }

        this.localMetaDataFile = localMetaDataFile;
        log("Found local meta data...");
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

        if (localMetaData.has("forceUpdate")) {
            forceUpdate = localMetaData.get("forceUpdate").getAsBoolean();
        } else {
            forceUpdate = false;
        }

        JsonObject jsonObject = new Gson().fromJson(data, JsonObject.class);
        if(jsonObject == null) {
            throw new NullPointerException("The data could not be parsed.");
        }
        if (jsonObject.has("version")) {
            latestVersion = jsonObject.get("version").getAsString();
        } else {
            log("No version info in metadata, using current version as latest.");
            latestVersion = currentVersion;
        }

        this.name = jsonObject.get("name").getAsString();

        if (jsonObject.has("downloadUrl")) {
            executableUrl = jsonObject.get("downloadUrl").getAsString();
            log("Latest version is: " + latestVersion);
        } else {
            executableUrl = "";
            log("No download URL found for latest version. Reverting to current version.");
            latestVersion = currentVersion;
            forceUpdate = false;
        }

        System.gc();
    }

    private String getData(String urlString) {
        log("Fetching data from: " + urlString);
        try {
            URL url = new URI(urlString).toURL();
            String protocol = url.getProtocol();

            if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);

                try {
                    int code = connection.getResponseCode();
                    if (code >= 200 && code < 300) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
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
                } finally {
                    connection.disconnect();
                }
            } else {
                try (java.io.InputStream in = url.openStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
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
        }
    }

    private JsonObject getLocalMetaData() {
        try {
            if (localMetaDataFile != null && localMetaDataFile.exists() && localMetaDataFile.isFile()) {
                BufferedReader reader = new BufferedReader(new java.io.FileReader(localMetaDataFile));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
                reader.close();
                return new Gson().fromJson(content.toString(), JsonObject.class);
            } else {
                throw new RuntimeException("No local meta data found. File does not exist.");
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
            if (!new File(getExecutablePath()).exists() || forceUpdate || (!isLatest()&&autoUpdate)) {
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
        log("Skipping update: " + (isOffline() ? "No connection with the update server." : "Latest version already installed."));
        return false;
    }

    private boolean updateVersion() {
        currentVersion = latestVersion;
        if (localMetaData.has("installedVersion")) {
            localMetaData.remove("installedVersion");
        }
        if(localMetaData.has("forceUpdate")) {
            localMetaData.remove("forceUpdate");
        }
        if(!localMetaData.has("autoUpdate")) {
            localMetaData.addProperty("autoUpdate", true);
        }
        localMetaData.addProperty("forceUpdate", false);
        localMetaData.addProperty("installedVersion", latestVersion);
        try {
            java.io.FileWriter writer = new java.io.FileWriter(localMetaDataFile);
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(localMetaData));
            writer.close();
            log("Updated local metadata file with new version.");
            return true;
        } catch (Exception e) {
            logError("Error updating local metadata file: " + e.getMessage());
        }
        return false;
    }

    private boolean downloadExecutable() {
        String destinationPath = path + File.separator + "cache" + File.separator;
        if(!new File(destinationPath).mkdirs()) {
            for(File f : new File(destinationPath).listFiles()) {
                if(f.isFile()&&f.getName().endsWith(".jar")&&f.getName().startsWith(name+"-v")) {
                    if(!f.delete()) {
                        logError("Failed to delete local old executable: " + f.getAbsolutePath());
                    }
                }
            }
        }
        destinationPath = destinationPath + name + "-v" + latestVersion + ".jar";

        log("Downloading executable from: " + executableUrl + " to: " + destinationPath);
        try {
            if (new File(destinationPath).exists()) {
                if (!new File(destinationPath).delete()) {
                    logError("Failed to delete existing file at destination. Aborting download.");
                    return false;
                }
            }
            URL url = new URI(executableUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            java.io.FileOutputStream outputStream = new java.io.FileOutputStream(destinationPath);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = connection.getInputStream().read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            reader.close();
            connection.disconnect();
            log("Download completed successfully.");
            return true;
        } catch (Exception e) {
            logError("Error downloading executable: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int launch() {
        String executablePath = getExecutablePath();
        if(!new File(executablePath).exists()) {
            logError("Executable not found at path: " + executablePath);
            return -1;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", executablePath);
            if (args != null) {
                for (String arg : args) {
                    processBuilder.command().add(arg);
                }
            }
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            log("Launching "+executablePath+"...");
            int exitCode = process.waitFor();
            if(exitCode == -2) {
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

    public void showFrame() {



         if(bootstrapperFrame == null) {
             bootstrapperFrame = new JFrame("Apex Bootstrapper");
             JProgressBar progressBar = new JProgressBar();
             progressBar.setIndeterminate(true);
             bootstrapperFrame.setLayout(new BorderLayout());
             bootstrapperFrame.add(new JLabel("Checking for updates..."), BorderLayout.CENTER);
             bootstrapperFrame.add(progressBar, BorderLayout.SOUTH);
             bootstrapperFrame.setSize(400, 150);
             bootstrapperFrame.setResizable(false);
             bootstrapperFrame.setLocationRelativeTo(null);
             bootstrapperFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
             bootstrapperFrame.setVisible(true);
         } else {
             bootstrapperFrame.setVisible(true);
         }
    }

    public void hideFrame() {
        if(bootstrapperFrame != null) {
            bootstrapperFrame.setVisible(false);
            bootstrapperFrame.dispose();
            bootstrapperFrame = null;
            System.gc();
        }
    }

    public String getName() {
        return name;
    }

    public String getExecutablePath() {
        return path + File.separator + "cache" + File.separator + name + "-v" + getCurrentVersion() + ".jar";
    }
}