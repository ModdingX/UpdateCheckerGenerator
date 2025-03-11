package org.moddingx.updatecheckergenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.moddingx.updatecheckergenerator.cache.FileCache;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;
import org.moddingx.updatecheckergenerator.platform.ProjectData;

import java.io.IOException;
import java.util.*;

public class UpdateCheckerGenerator {

    public static final Gson GSON;
    public static final Gson INTERNAL;

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    static {
        GsonBuilder builder = new GsonBuilder();
        builder.disableHtmlEscaping();
        INTERNAL = builder.create();
    }

    public static <T> Pair<String, JsonObject> generateUpdateChecker(ModdingPlatform<T> platform, Set<ModLoader> loaders, String projectId, FileCache cache) throws IOException {
        ProjectData project = platform.project(projectId);
        System.out.println("Generating update checker for " + project.slug());

        List<T> filesSorted = new ArrayList<>(platform.listFiles(projectId, loaders));
        filesSorted.sort(Comparator.comparing(platform::fileDate));
        List<String> allGameVersions = filesSorted.stream().flatMap(file -> platform.gameVersions(file).stream()).sorted().toList();

        JsonObject json = new JsonObject();
        json.addProperty("homepage", project.homepage());

        JsonObject promos = new JsonObject();
        for (String gameVersion : allGameVersions) {
            VersionPromo promo = processGameVersionPromos(platform, gameVersion, filesSorted, cache);
            if (promo.recommended().isPresent())
                promos.addProperty(gameVersion + "-recommended", promo.recommended().get());
            if (promo.latest().isPresent())
                promos.addProperty(gameVersion + "-latest", promo.latest().get());
        }
        json.add("promos", promos);

        for (String gameVersion : allGameVersions) {
            JsonObject releases = new JsonObject();
            
            for (T file : filesSorted) {
                if (!platform.gameVersions(file).contains(gameVersion)) continue;
                Optional<String> version = resolveVersion(platform, file, cache);
                if (version.isEmpty()) continue;
                
                releases.addProperty(version.get(), cache.changelog(platform.key(file), () -> {
                    try {
                        return ChangelogProcessor.process(platform.changelog(file));
                    } catch (IOException e) {
                        return "";
                    }
                }));
            }
            
            json.add(gameVersion, releases);
        }

        return Pair.of(project.slug(), json);
    }

    private static <T> VersionPromo processGameVersionPromos(ModdingPlatform<T> platform, String gameVersion, List<T> allFilesSorted, FileCache cache) {
        List<T> files = allFilesSorted.stream()
                .filter(file -> platform.gameVersions(file).contains(gameVersion))
                .toList();
        
        Optional<T> recommended = Optional.empty();
        for (T file : files) {
            if (platform.isStable(file)) recommended = Optional.of(file);
        }
        
        Optional<T> latest = Optional.empty();
        for (T file : files) {
            latest = Optional.of(file);
        }

        return new VersionPromo(
                recommended.flatMap(file -> resolveVersion(platform, file, cache)),
                latest.flatMap(file -> resolveVersion(platform, file, cache))
        );
    }
    
    private static <T> Optional<String> resolveVersion(ModdingPlatform<T> platform, T file, FileCache cache) {
        try {
            return platform.version(file).version(cache);
        } catch (IOException e) {
            System.err.println("Failed to get version for '" + platform.fileName(file) + "'");
            e.printStackTrace();
            return Optional.empty();
        }
    }
    
    private record VersionPromo(Optional<String> recommended, Optional<String> latest) {}
}
