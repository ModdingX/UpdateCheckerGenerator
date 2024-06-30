package org.moddingx.updatecheckergenerator;

import com.google.gson.JsonObject;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
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
        OptionSpec<Platform> specPlatform = options.acceptsAll(List.of("p", "platform"), "The modding platform to fetch the data from.").withRequiredArg().withValuesConvertedBy(Platform.ARG);
        OptionSpec<Path> specCfg = options.acceptsAll(List.of("c", "config"), "A list of project ids to generate update checkers for.").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING));
        OptionSpec<Path> specDir = options.acceptsAll(List.of("d", "dir", "directory"), "Output directory.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        OptionSpec<Path> specCache = options.acceptsAll(List.of("f", "cache"), "Cache file to use.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        OptionSpec<Void> specPretty = options.acceptsAll(List.of("pretty"), "Pretty-print the output json.");
        try {
            OptionSet set = options.parse(args);
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

            boolean pretty = set.has(specPretty);
            
            ModdingPlatform<?> platform = set.valueOf(specPlatform).create();
            projectIds.forEach(projectId -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        if (generate(platform, cache, basePath, projectId, pretty)) {
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

    private static boolean generate(ModdingPlatform<?> platform, FileCache cache, Path basePath, String projectId, boolean pretty) throws IOException {
        try {
            Pair<String, JsonObject> pair = UpdateCheckerGenerator.generateUpdateChecker(platform, projectId, cache);
            Path path = basePath.resolve(pair.getLeft() + ".json");
            Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            writer.write((pretty ? UpdateCheckerGenerator.GSON : UpdateCheckerGenerator.INTERNAL).toJson(pair.getRight()) + "\n");
            writer.close();
            return true;
        } catch (RequestException e) {
            // CurseForge sometimes has problems.
            // So we catch the CurseWrapper exception here to let it retry.
            return false;
        }
    }
}
