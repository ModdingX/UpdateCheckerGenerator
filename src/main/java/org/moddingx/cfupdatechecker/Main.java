package org.moddingx.cfupdatechecker;

import com.google.gson.JsonObject;
import io.github.noeppi_noeppi.tools.cursewrapper.api.CurseWrapper;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import org.apache.commons.lang3.tuple.Pair;
import org.moddingx.cfupdatechecker.cache.FileCache;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException {
        OptionParser options = new OptionParser(false);
        ArgumentAcceptingOptionSpec<Path> specCfg = options.acceptsAll(List.of("c", "config"), "A list of project ids to generate update checkers for.").withRequiredArg().withValuesConvertedBy(new PathConverter(PathProperties.FILE_EXISTING));
        ArgumentAcceptingOptionSpec<Path> specDir = options.acceptsAll(List.of("d", "dir", "directory"), "Output directory.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        ArgumentAcceptingOptionSpec<Path> specCache = options.acceptsAll(List.of("f", "cache"), "Cache file to use.").withRequiredArg().withValuesConvertedBy(new PathConverter());
        try {
            OptionSet set = options.parse(args);

            if (!set.has(specCfg) || !set.has(specDir)) {
                options.printHelpOn(System.err);
                System.exit(0);
            }

            Stream<String> projectIdStrings = Files.readAllLines(set.valueOf(specCfg)).stream().map(str -> str.contains("#") ? str.substring(0, str.indexOf('#')) : str).map(String::strip).filter(str -> !str.isEmpty()).filter(str -> {
                try {
                    Integer.parseInt(str);
                    return false;
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Invalid integer for project id: " + str);
                }
            });

            Path basePath = set.valueOf(specDir);
            if (!Files.exists(basePath)) Files.createDirectories(basePath);

            FileCache cache = new FileCache();
            if (set.has(specCache)) cache.read(set.valueOf(specCache));

            CurseWrapper api = new CurseWrapper(URI.create("https://curse.melanx.de/"));
            projectIdStrings.forEach(idString -> {
                int projectId = Integer.parseInt(idString);
                try {
                    Pair<String, JsonObject> pair = UpdateCheckerGenerator.generateUpdateChecker(api, projectId, cache);
                    Path path = basePath.resolve(pair.getLeft() + ".json");
                    try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        writer.write(Util.GSON.toJson(pair.getRight()) + "\n");
                    } catch (IOException e) {

                    }
                } catch (IOException e) {

                }
            });

            if (set.has(specCache)) cache.write(set.valueOf(specCache));
        } catch (OptionException e) {
            System.err.println("Option exception: " + e.getMessage());
            options.printHelpOn(System.err);
            System.exit(0);
        }
    }
}
