package org.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Main {

    private static ConfigLoader configLoader = new ConfigLoader();

    public static void main(String[] args) {
        System.out.println(configLoader.getProperty("my.property.name"));

        try {
            if (!isDockerInstalled()) {
                System.out.println("Docker not found. Downloading installer...");
                downloadDockerInstaller();

                System.out.println("Installing Docker...");
                installDocker();

                System.out.println("Please ensure Docker is running and then restart this application.");
            } else {
                System.out.println("Docker is already installed.");

                String dbKind = configLoader.getProperty("database.kind");
                switch (dbKind) {
                    case "cockroach":
                        handleCockroachDB();
                        break;
                    case "postgres":
                        handlePostgres();
                        break;
                    case "oracle":
                        handleOracle();
                        break;
                    default:
                        System.out.println("Unknown database kind.");
                        break;
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void handleCockroachDB() throws IOException, InterruptedException {
        System.out.println("Pulling CockroachDB image...");
        pullDockerImage(configLoader.getProperty("cockroachdb.image"));
        int portDB = getAvailablePortFromQuarkusService("/DB");
        int portUI = getAvailablePortFromQuarkusService("/UI");

        startCockroach(portDB, portUI);
    }

    private static void handlePostgres() throws IOException, InterruptedException {
        System.out.println("Pulling Postgres image...");
        pullDockerImage(configLoader.getProperty("postgres.image"));
        startPostgres();
    }

    private static void handleOracle() throws IOException, InterruptedException {
        System.out.println("Pulling Oracle Express image...");
        pullDockerImage(configLoader.getProperty("oracle.image"));
        startOracle();
    }

    private static void startCockroach(int portDB , int portUI ) throws IOException, InterruptedException {
        ProcessBuilder createNetwork = new ProcessBuilder("docker", "network", "create", configLoader.getProperty("cockroachdb.network.name"));
        createNetwork.start().waitFor();

        ProcessBuilder createVolume = new ProcessBuilder("docker", "volume", "create", configLoader.getProperty("cockroachdb.volume.name"));
        createVolume.start().waitFor();

        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-d",
                "--name=" + configLoader.getProperty("cockroachdb.container.name"),
                "--network=" + configLoader.getProperty("cockroachdb.network.name"),
                "--hostname=" + configLoader.getProperty("cockroachdb.container.hostname"),
                "-p", portDB + ":" + configLoader.getProperty("cockroachdb.internal.port"),
                "-p", portUI + ":" + configLoader.getProperty("cockroachdb.ui.internal.port"),

                "-v", configLoader.getProperty("cockroachdb.volume.name") + ":" + configLoader.getProperty("cockroachdb.data.dir"),
                configLoader.getProperty("cockroachdb.image"),
                configLoader.getProperty("cockroachdb.start.command"),
                configLoader.getProperty("cockroachdb.start.args.insecure"),
                "--listen-addr=" + configLoader.getProperty("cockroachdb.listen-addr"),
                "--advertise-addr=" + configLoader.getProperty("cockroachdb.advertise-addr") + ":" + portDB,
                "--join=" + configLoader.getProperty("cockroachdb.join")
        );
        processBuilder.start().waitFor();
    }

    private static void startPostgres() throws IOException, InterruptedException {
        ProcessBuilder createNetwork = new ProcessBuilder("docker", "network", "create", configLoader.getProperty("postgres.network.name"));
        createNetwork.start().waitFor();

        ProcessBuilder createVolume = new ProcessBuilder("docker", "volume", "create", configLoader.getProperty("postgres.volume.name"));
        createVolume.start().waitFor();

        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-d",
                "--name=" + configLoader.getProperty("postgres.container.name"),
                "--network=" + configLoader.getProperty("postgres.network.name"),
                "--hostname=" + configLoader.getProperty("postgres.container.hostname"),
                "-p", configLoader.getProperty("postgres.external.port") + ":" + configLoader.getProperty("postgres.internal.port"),
                "-e", "POSTGRES_DB=" + configLoader.getProperty("postgres.database"),
                "-e", "POSTGRES_USER=" + configLoader.getProperty("postgres.user"),
                "-e", "POSTGRES_PASSWORD=" + configLoader.getProperty("postgres.password"),
                "-v", configLoader.getProperty("postgres.volume.name") + ":" + configLoader.getProperty("postgres.data.dir"),
                configLoader.getProperty("postgres.image")
        );
        processBuilder.start().waitFor();
    }

    private static void startOracle() throws IOException, InterruptedException {
        ProcessBuilder createNetwork = new ProcessBuilder("docker", "network", "create", configLoader.getProperty("oracle.network.name"));
        createNetwork.start().waitFor();

        ProcessBuilder createVolume = new ProcessBuilder("docker", "volume", "create", configLoader.getProperty("oracle.volume.name"));
        createVolume.start().waitFor();

        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run", "-d",
                "--name=" + configLoader.getProperty("oracle.container.name"),
                "--network=" + configLoader.getProperty("oracle.network.name"),
                "--hostname=" + configLoader.getProperty("oracle.container.hostname"),
                "-p", configLoader.getProperty("oracle.external.port") + ":" + configLoader.getProperty("oracle.internal.port"),
                "-e", "ORACLE_SID=" + configLoader.getProperty("oracle.sid"),
                "-e", "ORACLE_PWD=" + configLoader.getProperty("oracle.password"),
                "-v", configLoader.getProperty("oracle.volume.name") + ":" + configLoader.getProperty("oracle.data.dir"),
                configLoader.getProperty("oracle.image")
        );
        processBuilder.start().waitFor();
    }

    private static String getOsName() {
        return System.getProperty("os.name").toLowerCase();
    }

    private static boolean isDockerInstalled() throws InterruptedException {
        try {
            ProcessBuilder processBuilder;
            if (getOsName().contains("win")) {
                processBuilder = new ProcessBuilder("docker", "--version");
            } else if (getOsName().contains("mac")) {
                processBuilder = new ProcessBuilder("/usr/local/bin/docker", "--version");
            } else { // linux or ubunto
                processBuilder = new ProcessBuilder("/usr/bin/docker", "--version");
            }
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void downloadDockerInstaller() throws IOException {
        String osName = getOsName();
        String urlProperty = "docker.installer.url." +
                (osName.contains("win") ? "windows" : osName.contains("mac") ? "mac" : "ubuntu");
        HttpURLConnection connection = (HttpURLConnection) new URL(configLoader.getProperty(urlProperty)).openConnection();
        connection.setRequestMethod("GET");
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(configLoader.getProperty("docker.installer.path"))) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private static void installDocker() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(configLoader.getProperty("docker.installer.path"));
        processBuilder.inheritIO(); // This makes the command output to be displayed in the terminal

        processBuilder.start().waitFor();
    }

    private static void pullDockerImage(String imageName) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "pull", imageName);
        processBuilder.inheritIO();

        processBuilder.start().waitFor();
    }

    private static int getAvailablePortFromQuarkusService(String type) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(configLoader.getProperty("quarkus.service.port.url") + type).openConnection();
        connection.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine = in.readLine();
        in.close();
        return Integer.parseInt(inputLine.trim());
    }




}
