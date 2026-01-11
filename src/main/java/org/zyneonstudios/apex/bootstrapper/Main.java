package org.zyneonstudios.apex.bootstrapper;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import javax.swing.*;
import java.io.File;

public class Main {

    private static ApexBootstrapper apexBootstrapper;
    private static String url = null;
    private static String path = ".";
    private static File localMetaFile = null;
    private static boolean log = false;
    private static boolean errorLog = false;
    private static boolean frame = false;

    public static void main(String[] args) {
        resolveData(args);

        if(url != null && !url.isEmpty() && localMetaFile != null) {
            try {
                apexBootstrapper = new ApexBootstrapper(url, path, localMetaFile, args, log, errorLog);
                if (frame) {
                    apexBootstrapper.showFrame();
                }
                apexBootstrapper.update();
                apexBootstrapper.hideFrame();
                apexBootstrapper.launch();
            } catch (Exception ex) {
                System.err.println("Failed to initialize bootstrapper: "+ex.getMessage());
                System.exit(1);
            }
        } else {
            System.err.println("Please specify a valid URL and path to a local meta file. Launch with --help for more information.");
            System.exit(1);
        }
    }

    private static void resolveData(String[] args) {
        for(int i = 0; i < args.length; ++i) {
            switch (args[i]) {
                case "--nexus-app":
                    initNexusApp();
                    return;
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
                case "--help":
                    System.out.println("Apex Bootstrapper Help:");
                    System.out.println("--nexus-app              : Initialize with Nexus App settings.");
                    System.out.println("--b-path <path>          : Specify the local path for installation.");
                    System.out.println("Note: The following options are ignored if --nexus-app is specified.");
                    System.out.println("--b-url <url>            : *Specify the URL for the bootstrapper metadata.");
                    System.out.println("--b-file <file>          : *Specify the local metadata file.");
                    System.out.println("--b-log                  : Enable logging.");
                    System.out.println("--b-error                : Enable error logging.");
                    System.out.println("--b-frame                : Show the bootstrapper frame.");
                    System.exit(0);
                    break;
            }
        }
    }

    private static void initNexusApp() {
        url = "https://zyneonstudios.github.io/apex-metadata/nexus-app/bootstrapper-metadata.json";
        localMetaFile = new File("bootstrapper-metadata.json");
        log = true;
        errorLog = true;
        frame = true;
    }

    public static ApexBootstrapper getApexBootstrapper() {
        return apexBootstrapper;
    }
}