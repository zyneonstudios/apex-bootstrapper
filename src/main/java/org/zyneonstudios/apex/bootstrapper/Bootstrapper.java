package org.zyneonstudios.apex.bootstrapper;

public interface Bootstrapper {

    /**
     * Gets the current installed version of the desired project.
     * @return String currentVersion
     */
    String getCurrentVersion();

    /**
     * Gets the newest available version of the desired project.
     * @return String lastestVersion
     */
    String getLatestVersion();

    /**
     * Gets the url of the metadata JSON object for the desired project's bootstrapper.
     * @return String metadataJsonUrl
     */
    String getJsonUrl();

    /**
     * Gets the metadata JSON object of the desired project's bootstrapper.
     * @return String metadataJsonObject
     */
    String getJson();

    /**
     * Gets the path to the directory of the desired project.
     * @return String executableDirectoryPath
     */
    String getPath();

    /**
     * Checks if the current version equals the latest version.
     * @return boolean isLatest
     */
    boolean isLatest();

    /**
     * Checks if the bootstrapper has a connection with the desired projects' metadata url.
     * @return boolean isOffline
     */
    boolean isOffline();

    /**
     * Checks if the bootstrapper has a fallback version of the desired project to fall back to it if the update fails.
     * @return boolean hasFallback
     */
    boolean hasFallback();

    /**
     * Checks if the auto-update feature is enabled for the desired project.
     * @return boolean isAutoUpdateEnabled
     */
    boolean isAutoUpdateEnabled();

    /**
     * Forces an update of the desired project to the latest version, ignoring if it is already up to date.
     * @return boolean forceUpdateSuccess
     */
    boolean forceUpdate();

    /**
     * Updates the desired project to the latest version.
     * @return boolean updateSuccess
     */
    boolean update();

    /**
     * Launches the executable of the desired projects and returns the exit code when the program stops.
     * @return int exitCode
     */
    int launch();

    /**
     * Launches the fallback version of the desired project and returns the exit code when the projects stops.
     * @return int exitCode
     */
    int launchFallback();
}