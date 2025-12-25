package org.zyneonstudios.apex.bootstrapper;

import java.io.File;

public class Main {

    private static ApexBootstrapper apexBootstrapper;
    private static String url = null;
    private static String path = ".";
    private static File localMetaFile = null;
    private static boolean log = false;
    private static boolean errorLog = true;
    private static boolean frame = false;

    public static void main(String[] args) {
        resolveData(args);
        if(url != null && !url.isEmpty() && localMetaFile != null) {
            apexBootstrapper = new ApexBootstrapper(url, path, localMetaFile, args, log, errorLog);
            if(frame) {
                apexBootstrapper.showFrame();
            }
            apexBootstrapper.update();
            apexBootstrapper.hideFrame();
            apexBootstrapper.launch();
        }
    }

    private static void resolveData(String[] args) {
        for(int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--b-url":
                    url = args[i + 1];
                    break;
                case "--b-path":
                    path = args[i + 1];
                    break;
                case "--b-log":
                    log = true;
                    break;
                case "--b-error":
                    errorLog = true;
                    break;
                case "--b-file":
                    localMetaFile = new File(args[i + 1]);
                    break;
                case "--b-frame":
                    frame = true;
                    break;
            }
        }
    }

    public static ApexBootstrapper getApexBootstrapper() {
        return apexBootstrapper;
    }
}