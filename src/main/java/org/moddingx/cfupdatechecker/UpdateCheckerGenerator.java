package org.moddingx.cfupdatechecker;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import io.github.noeppi_noeppi.tools.cursewrapper.api.CurseWrapper;
import io.github.noeppi_noeppi.tools.cursewrapper.api.request.FileFilter;
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.FileInfo;
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.ModLoader;
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.ProjectInfo;
import io.github.noeppi_noeppi.tools.cursewrapper.api.response.ReleaseType;
import org.apache.commons.lang3.tuple.Pair;
import org.moddingx.cfupdatechecker.cache.FileCache;
import org.moddingx.cfupdatechecker.version.VersionResolver;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpdateCheckerGenerator {

    public static Pair<String, JsonObject> generateUpdateChecker(CurseWrapper api, int projectId, FileCache cache) throws IOException {
        ProjectInfo project = api.getProject(projectId);
        System.out.println("Generating version checker for " + project.slug());

        List<FileInfo> filesSorted = api.getFiles(projectId, FileFilter.loader(ModLoader.FORGE));
        filesSorted.sort(Comparator.comparing(FileInfo::fileDate).reversed());
        List<String> allGameVersions = filesSorted.stream().flatMap(info -> info.gameVersions().stream()).sorted().toList();

        JsonObject json = new JsonObject();
        json.addProperty("homepage", project.website().toString());

        JsonObject promos = new JsonObject();
        for (String gameVersion : allGameVersions) {
            Pair<Optional<String>, Optional<String>> recommendedLatest = processGameVersionPromos(gameVersion, filesSorted, cache);
            if (recommendedLatest.getLeft().isPresent())
                promos.addProperty(gameVersion + "-recommended", recommendedLatest.getLeft().get());
            if (recommendedLatest.getRight().isPresent())
                promos.addProperty(gameVersion + "-latest", recommendedLatest.getRight().get());
        }
        json.add("promos", promos);

        for (String gameVersion : allGameVersions) {
            JsonObject releases = new JsonObject();
            List<Pair<FileInfo, String>> files = Lists.reverse(filesSorted.stream()
                    .filter(file -> file.gameVersions().contains(gameVersion))
                    .flatMap(file -> VersionResolver.getVersion(file, cache)
                            .map(v -> Pair.of(file, v)).stream())
                    .collect(Collectors.toList()));
            for (Pair<FileInfo, String> pair : files) {
                FileInfo file = pair.getLeft();
                String version = pair.getRight();
                releases.addProperty(version, cache.changelog(new FileCache.FileKey(file.projectId(), file.fileId()), () -> {
                    try {
                        return ChangelogProcessor.process(api.getChangelog(file.projectId(), file.fileId()));
                    } catch (IOException e) {
                        return "";
                    }
                }));
            }
            json.add(gameVersion, releases);
        }

        return Pair.of(project.slug(), json);
    }

    private static Pair<Optional<String>, Optional<String>> processGameVersionPromos(String gameVersion, List<FileInfo> allFilesSorted, FileCache cache) {
        List<FileInfo> files = allFilesSorted.stream().filter(info -> info.gameVersions().contains(gameVersion)).toList();
        Optional<FileInfo> recommended = Optional.empty();
        for (FileInfo file : files) {
            if (file.releaseType() == ReleaseType.RELEASE) {
                recommended = Optional.of(file);
                break;
            }
        }
        Optional<FileInfo> latest = Optional.empty();
        for (FileInfo file : files) {
            latest = Optional.of(file);
            break;
        }

        return Pair.of(recommended.flatMap(file -> VersionResolver.getVersion(file, cache)), latest.flatMap(file -> VersionResolver.getVersion(file, cache)));
    }
}
