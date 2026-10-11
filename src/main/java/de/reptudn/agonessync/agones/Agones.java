package de.reptudn.agonessync.agones;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class Agones {

    private final String base;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public Agones(String host, int port, Logger log) {
        this.base = "http://" + host + ":" + port;
        this.log = log;
    }

    public static Agones fromEnv(Logger log) {
        String port = System.getenv().getOrDefault("AGONES_SDK_HTTP_PORT", "9358");
        return new Agones("localhost", Integer.parseInt(port), log);
    }

    public CompletableFuture<Boolean> ready()    { return post("/ready"); }
    public CompletableFuture<Boolean> health()   { return post("/health"); }
    public CompletableFuture<Boolean> shutdown() { return post("/shutdown"); }

    private CompletableFuture<Boolean> post(String path) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() != 200) {
                        log.warning("Agones " + path + " -> " + r.statusCode() + " " + r.body());
                        return false;
                    }
                    if (!path.equals("/health")) log.info("Agones " + path + " OK");
                    return true;
                })
                .exceptionally(ex -> {
                    log.warning("Agones " + path + " failed: " + ex);
                    return false;
                });
    }
}