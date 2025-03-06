package org.moddingx.updatecheckergenerator.cache;

import com.google.common.collect.Streams;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.moddingx.updatecheckergenerator.UpdateCheckerGenerator;
import org.moddingx.updatecheckergenerator.platform.FileKey;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FileCache {

    public static final int VERSION = 5;
    
    private final Map<FileKey, String> changelogs = new HashMap<>();
    private final Map<FileKey, String> versions = new HashMap<>();

    public String changelog(FileKey fileKey, Supplier<String> changelog) {
        return this.changelogs.computeIfAbsent(fileKey, key -> changelog.get());
    }

    public String version(FileKey fileKey, Supplier<String> version) {
        return this.versions.computeIfAbsent(fileKey, key -> version.get());
    }

    public void read(Path path) {
        this.changelogs.clear();
        this.versions.clear();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject json = UpdateCheckerGenerator.INTERNAL.fromJson(reader, JsonObject.class);
                int cacheVersion = json.get("version").getAsInt();
                if (cacheVersion == FileCache.VERSION) {
                    this.changelogs.putAll(this.readMap(json.get("changelogs")));
                    this.versions.putAll(this.readMap(json.get("versions")));
                }
            } catch (IOException | JsonSyntaxException e) {
                System.out.println("Failed to read file cache: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                this.changelogs.clear();
                this.versions.clear();
            }
        }
    }

    private Map<FileKey, String> readMap(JsonElement data) {
        return Streams.stream(data.getAsJsonArray())
                .map(JsonElement::getAsJsonObject)
                .map(this::readFile)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<FileKey, String> readFile(JsonObject data) {
        String projectId = data.get("project").getAsString();
        String fileId = data.get("file").getAsString();
        String changelog = data.get("value").getAsString();
        return Map.entry(new FileKey(projectId, fileId), changelog);
    }

    public void write(Path path) {
        try {
            if (Files.notExists(path.getParent())) Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                JsonObject json = new JsonObject();
                json.addProperty("version", FileCache.VERSION);
                json.add("changelogs", this.writeMap(this.changelogs));
                json.add("versions", this.writeMap(this.versions));
                writer.write(UpdateCheckerGenerator.INTERNAL.toJson(json) + "\n");
            }
        } catch (IOException e) {
            System.out.println("Failed to write file cache: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private JsonElement writeMap(Map<FileKey, String> map) {
        JsonArray array = new JsonArray();
        map.entrySet().forEach(entry -> array.add(this.writeFile(entry)));
        return array;
    }

    private JsonObject writeFile(Map.Entry<FileKey, String> entry) {
        JsonObject data = new JsonObject();
        data.addProperty("project", entry.getKey().projectId());
        data.addProperty("file", entry.getKey().fileId());
        data.addProperty("value", entry.getValue());
        return data;
    }
}
