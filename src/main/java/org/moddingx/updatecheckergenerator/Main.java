package org.moddingx.updatecheckergenerator;

import com.google.gson.JsonObject;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import org.apache.commons.lang3.tuple.Pair;
import org.moddingx.cursewrapper.api.RequestException;
import org.moddingx.updatecheckergenerator.cache.FileCache;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        OptionParser options = new OptionParser(false);
        ArgumentAcceptingOptionSpec<Platform> specPlatform = options.acceptsAll(List.of("p", "platform"), "The modding platform to fetch the data from.").withRequiredArg().withValuesConvertedBy(Platform.ARG);
        ArgumentAcceptingOptionSpec<Path> specCfg = options.acceptsAll(List.of("c", "config"), "A list of project ids to generate update checkers for.").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING));
        ArgumentAcceptingOptionSpec<Path> specDir = options.acceptsAll(List.of("d", "dir", "directory"), "Output directory.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        ArgumentAcceptingOptionSpec<Path> specCache = options.acceptsAll(List.of("f", "cache"), "Cache file to use.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        try {
//            OptionSet set = options.parse(args);
            OptionSet set = options.parse("-p", "modrinth", "-c", "/tmp/cfuc/m_cfg.txt", "-d", "/tmp/cfuc/m", "-f", "/tmp/cfuc/m.json");
            if (!set.has(specPlatform) || !set.has(specCfg) || !set.has(specDir)) {
                if (!set.has(specPlatform)) System.err.println("Missing required option: " + specPlatform);
                if (!set.has(specCfg)) System.err.println("Missing required option: " + specCfg);
                if (!set.has(specDir)) System.err.println("Missing required option: " + specDir);
                options.printHelpOn(System.err);
                System.exit(0);
            }

            Stream<String> projectIds = Files.readAllLines(set.valueOf(specCfg)).stream()
                    .map(str -> str.contains("#") ? str.substring(0, str.indexOf('#')) : str)
                    .map(String::strip)
                    .filter(str -> !str.isEmpty());

            Path basePath = set.valueOf(specDir);
            if (!Files.exists(basePath)) Files.createDirectories(basePath);

            FileCache cache = new FileCache();
            if (set.has(specCache)) cache.read(set.valueOf(specCache));

            ModdingPlatform<?> platform = set.valueOf(specPlatform).create();
            projectIds.forEach(projectId -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        if (generate(platform, cache, basePath, projectId)) {
                            break;
                        }
                        System.out.println("Failed " + (i + 1) + " time(s)");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            if (set.has(specCache)) cache.write(set.valueOf(specCache));
        } catch (OptionException e) {
            System.err.println("Option exception: " + e.getMessage());
            options.printHelpOn(System.err);
            System.exit(0);
        }
    }

    private static boolean generate(ModdingPlatform<?> platform, FileCache cache, Path basePath, String projectId) throws IOException {
        try {
            Pair<String, JsonObject> pair = UpdateCheckerGenerator.generateUpdateChecker(platform, projectId, cache);
            Path path = basePath.resolve(pair.getLeft() + ".json");
            Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write(UpdateCheckerGenerator.GSON.toJson(pair.getRight()) + "\n");
            writer.close();
            return true;
        } catch (RequestException e) {
            // CurseForge sometimes has problems.
            // SO we catch the CurseWrapper exception here to let it retry.
            return false;
        }
    }
}
