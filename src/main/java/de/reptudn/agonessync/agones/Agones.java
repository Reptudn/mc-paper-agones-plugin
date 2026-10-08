package de.reptudn.agonessync.agones;

import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class Agones {

    public enum ServerState {
        UNKNOWN,      // before first successful call
        SCHEDULED,
        READY,
        RESERVED,
        ALLOCATED,
        SHUTDOWN,
        UNHEALTHY,
        ERROR;

        static ServerState fromAgones(String s) {
            try {
                return valueOf(s.toUpperCase());
            } catch (Exception e) {
                return UNKNOWN;
            }
        }
    }

    private final String base;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private volatile ServerState state = ServerState.UNKNOWN;

    public Agones(String host, int port) {
        this.base = "http://" + host + ":" + port;
    }

    public static Agones fromEnv() {
        String port = System.getenv().getOrDefault("AGONES_SDK_HTTP_PORT", "9358");
        return new Agones("localhost", Integer.parseInt(port));
    }

    public ServerState getState() {
        return state;
    }

    // --- SDK calls ---

    public CompletableFuture<Boolean> ready() {
        return call("POST", "/ready", "{}", ServerState.READY);
    }

    public CompletableFuture<Boolean> allocate() {
        return call("POST", "/allocate", "{}", ServerState.ALLOCATED);
    }

    public CompletableFuture<Boolean> reserve(int seconds) {
        return call("POST", "/reserve", "{\"seconds\":\"" + seconds + "\"}", ServerState.RESERVED);
    }

    public CompletableFuture<Boolean> shutdown() {
        return call("POST", "/shutdown", "{}", ServerState.SHUTDOWN);
    }

    public CompletableFuture<Boolean> health() {
        return call("POST", "/health", "{}", null);
    }

    public CompletableFuture<Boolean> setLabel(String key, String value) {
        return call("PUT", "/metadata/label",
                "{\"key\":\"" + key + "\",\"value\":\"" + value + "\"}", null);
    }

    /** Reads the real state from the sidecar (the controller may have changed it, e.g. via a GameServerAllocation). */
    public CompletableFuture<ServerState> fetchState() {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/gameserver"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    String s = JsonParser.parseString(r.body())
                            .getAsJsonObject()
                            .getAsJsonObject("status")
                            .get("state").getAsString();
                    ServerState st = ServerState.fromAgones(s);
                    state = st;
                    return st;
                })
                .exceptionally(ex -> ServerState.ERROR);
    }

    public CompletableFuture<Boolean> setCounterCount(String name, long count) {
        return call("PATCH", "/v1beta1/counters/" + name, "{\"count\":\"" + count + "\"}", null);
    }

    public CompletableFuture<Boolean> setCounterCapacity(String name, long capacity) {
        return call("PATCH", "/v1beta1/counters/" + name, "{\"capacity\":\"" + capacity + "\"}", null);
    }

    // --- internals ---

    private CompletableFuture<Boolean> call(String method, String path, String body, ServerState onSuccess) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    boolean ok = r.statusCode() == 200;
                    if (ok && onSuccess != null) state = onSuccess;
                    return ok;
                })
                .exceptionally(ex -> false);
    }
}