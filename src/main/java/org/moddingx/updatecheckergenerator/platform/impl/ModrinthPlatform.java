package org.moddingx.updatecheckergenerator.platform.impl;

import com.google.common.collect.Streams;
import com.google.gson.*;
import org.moddingx.updatecheckergenerator.UpdateCheckerGenerator;
import org.moddingx.updatecheckergenerator.platform.FileKey;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;
import org.moddingx.updatecheckergenerator.platform.ProjectData;
import org.moddingx.updatecheckergenerator.platform.ResolvableVersion;
import org.moddingx.updatecheckergenerator.util.Either;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModrinthPlatform implements ModdingPlatform<ModrinthVersion> {

    // Trailing slash is important, so URI#resolve works properly
    private static final URI BASE_URL = URI.create("https://api.modrinth.com/v2/");
    
    private final HttpClient client;

    public ModrinthPlatform() {
        client = HttpClient.newHttpClient();
    }

    @Override
    public ProjectData project(String projectId) throws IOException {
        return withJson(() -> {
            JsonObject json = request("project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8)).getAsJsonObject();
            return new ProjectData(
                    json.get("slug").getAsString(),
                    "https://modrinth.com/" + URLEncoder.encode(json.get("project_type").getAsString(), StandardCharsets.UTF_8) + "/" + URLEncoder.encode(json.get("slug").getAsString(), StandardCharsets.UTF_8)
            );
        });
    }

    @Override
    public List<ModrinthVersion> listFiles(String projectId) throws IOException {
        return withJson(() -> {
            JsonArray array = request("project/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8) + "/version", Map.of(
                    "loaders", "[\"forge\"]"
            )).getAsJsonArray();
            List<ModrinthVersion> files = new ArrayList<>(array.size());
            for (JsonElement elem : array) {
                JsonObject json = elem.getAsJsonObject();
                files.add(new ModrinthVersion(
                        json.get("project_id").getAsString(),
                        json.get("id").getAsString(),
                        json.get("name").getAsString(),
                        json.get("version_number").getAsString(),
                        json.get("version_type").getAsString().toLowerCase(Locale.ROOT),
                        Instant.parse(json.get("date_published").getAsString()),
                        Streams.stream(json.get("game_versions").getAsJsonArray())
                                .map(JsonElement::getAsString)
                                .collect(Collectors.toUnmodifiableSet()),
                        (json.has("changelog") && !json.get("changelog").isJsonNull()) ? json.get("changelog").getAsString() : ""
                ));
            }
            return List.copyOf(files);
        });
    }

    @Override
    public FileKey key(ModrinthVersion file) {
        return new FileKey(file.projectId(), file.versionId());
    }

    @Override
    public String fileName(ModrinthVersion file) {
        return file.fileName();
    }

    @Override
    public boolean isStable(ModrinthVersion file) {
        return "release".equals(file.releaseType());
    }

    @Override
    public Set<String> gameVersions(ModrinthVersion file) {
        return file.gameVersions();
    }

    @Override
    public Instant fileDate(ModrinthVersion file) {
        return file.date();
    }

    @Override
    public String changelog(ModrinthVersion file) throws IOException {
        return file.changelog();
    }

    @Override
    public ResolvableVersion version(ModrinthVersion file) throws IOException {
        return ResolvableVersion.of(file.fileVersion());
    }

    private <T> T withJson(IOSupplier<T> action) throws IOException {
        try {
            return action.get();
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid json structure", e);
        } catch (JsonParseException e) {
            throw new IOException("Invalid json", e);
        }
    }
    
    private JsonElement request(String route) throws IOException {
        return request(route, Map.of());
    }
    
    private JsonElement request(String route, Map<String, String> query) throws IOException {
        String routeStr = route.startsWith("/") ? route.substring(1) : route;
        String queryStr = "";
        if (!query.isEmpty()) {
            queryStr = query.entrySet().stream()
                    .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&", "?", ""));
        }
        URI req = BASE_URL.resolve(routeStr + queryStr);
        try {
            return client.<Either<JsonElement, IOException>>send(HttpRequest.newBuilder().GET()
                            .uri(req)
                            .header("Accept", "application/json")
                            .header("User-Agent", "ModdingX/UpdateCheckerGenerator")
                            .build(), 
                    resp -> {
                        if ((resp.statusCode() / 100) == 2 && resp.statusCode() != 204) {
                            return HttpResponse.BodySubscribers.mapping(
                                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8),
                                    str -> Either.tryWith(() -> UpdateCheckerGenerator.INTERNAL.fromJson(str, JsonElement.class))
                                            .mapRight(ex -> new IOException("Failed to parse json response from modrinth api", ex))
                            );
                        } else {
                            return HttpResponse.BodySubscribers.replacing(Either.right(new IOException("HTTP Status Code: " + resp.statusCode())));
                        }
                    }
            ).body().getOrThrowChecked(Function.identity(), Function.identity());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }
    
    @FunctionalInterface
    private interface IOSupplier<T> {
        
        T get() throws IOException;
    }
}
