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
                System.out.println("Pulling quarkus  service image...");
                dockerLogin();
                pullDockerImage(configLoader.getProperty("quarkus.image"));
                startQuarkusService();
                dockerLogout();

            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    private static void dockerLogout() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "logout");
        processBuilder.start().waitFor();
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
    private static void startQuarkusService() throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "run",
                "--name", configLoader.getProperty("quarkus.container.name"),
                "-d",
                "-p", configLoader.getProperty("quarkus.external.port") + ":" + configLoader.getProperty("quarkus.internal.port"),
                configLoader.getProperty("quarkus.image")
        );
        processBuilder.start().waitFor();
    }





    private static String getOsName() {
        System.out.println("OS : " + System.getProperty("os.name").toLowerCase());
        if (System.getProperty("os.name").toLowerCase().contains("linux")){
            return "ubuntu" ;
        }

        return System.getProperty("os.name").toLowerCase() ;
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
        if (osName.contains("ubuntu")) return;

        String urlProperty = "docker.installer.url." +
                (osName.contains("win") ? "windows" : osName.contains("mac") ? "mac" : "unknown");
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

    private static void ensureCurlIsInstalled() throws IOException, InterruptedException {
        if (!isCurlInstalled()) {
            ProcessBuilder installCurl = new ProcessBuilder("apt-get", "install", "curl", "-y");
            installCurl.start().waitFor();
        }
    }
    private static boolean isCurlInstalled() throws InterruptedException {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("curl", "--version");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void installDocker() throws IOException, InterruptedException {
        if (getOsName().contains("ubuntu")) {
            ensureCurlIsInstalled();

            ProcessBuilder updateProcess = new ProcessBuilder("apt-get", "update");
            updateProcess.start().waitFor();

            ProcessBuilder installDependencies = new ProcessBuilder("apt-get", "install", "apt-transport-https", "ca-certificates", "software-properties-common", "-y");
            installDependencies.start().waitFor();

            ProcessBuilder addGPGKey = new ProcessBuilder("sh", "-c", "curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -");
            addGPGKey.start().waitFor();

            ProcessBuilder addDockerRepo = new ProcessBuilder("sh", "-c", "add-apt-repository \"deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable\"");
            addDockerRepo.inheritIO();

            addDockerRepo.start().waitFor();

            ProcessBuilder updateAgain = new ProcessBuilder("apt-get", "update");
            updateAgain.inheritIO();

            updateAgain.start().waitFor();

            ProcessBuilder installDocker = new ProcessBuilder("apt-get", "install", "docker-ce", "-y");

            installDocker.inheritIO();

            installDocker.start().waitFor();
        } else {
            ProcessBuilder processBuilder = new ProcessBuilder(configLoader.getProperty("docker.installer.path"));
            processBuilder.inheritIO();
            processBuilder.start().waitFor();
        }
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

    private static void dockerLogin() throws IOException, InterruptedException {
        String dockerUsername = configLoader.getProperty("docker.username");
        String dockerToken = configLoader.getProperty("docker.token");

        ProcessBuilder processBuilder = new ProcessBuilder("docker", "login", "-u", dockerUsername, "--password-stdin");
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (OutputStream os = process.getOutputStream()) {
            os.write(dockerToken.getBytes());
            os.flush();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to login to Docker Hub.");
        }
    }





}
