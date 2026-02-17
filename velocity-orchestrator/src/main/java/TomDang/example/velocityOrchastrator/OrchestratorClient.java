package TomDang.example.velocityOrchestrator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OrchestratorClient {
    private final String baseUrl;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public OrchestratorClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public InstanceInfo spawn(String runId) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("runId", runId);
        body.addProperty("template", "template_server");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/instances"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Spawn failed: " + res.statusCode() + " " + res.body());
        }
        return gson.fromJson(res.body(), InstanceInfo.class);
    }

    public InstanceInfo get(String instanceId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/instances/" + instanceId))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Get failed: " + res.statusCode() + " " + res.body());
        }
        return gson.fromJson(res.body(), InstanceInfo.class);
    }

    public void delete(String instanceId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/instances/" + instanceId))
                .timeout(Duration.ofSeconds(10))
                .DELETE()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Delete failed: " + res.statusCode() + " " + res.body());
        }
    }

    public record InstanceInfo(String instanceId, String name, String host, int port, String status) {}
}