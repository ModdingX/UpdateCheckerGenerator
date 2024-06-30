package org.moddingx.updatecheckergenerator.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moandjiezana.toml.Toml;
import jakarta.annotation.Nullable;
import org.moddingx.updatecheckergenerator.UpdateCheckerGenerator;
import org.moddingx.updatecheckergenerator.cache.FileCache;
import org.moddingx.updatecheckergenerator.platform.FileKey;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;

import java.io.*;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VersionResolver {

    private static final Pattern MANIFEST_REGEX = Pattern.compile("^\\s*Implementation-Version\\s*:\\s*(.*?)\\s*$");

    private static final String MOD_INFO_TOML = "META-INF/mods.toml";
    private static final String MOD_INFO_LEGACY = "mcmod.info";
    private static final String JAR_MANIFEST = "META-INF/MANIFEST.MF";
    private static final String MODULE_DESCRIPTOR = "module-info.class";
    
    private static final Set<String> FILE_NAMES = Set.of(
            MOD_INFO_TOML, MOD_INFO_LEGACY, JAR_MANIFEST, MODULE_DESCRIPTOR
    );
    
    public static <T> Optional<String> getVersion(ModdingPlatform<T> platform, T file, URL downloadURL, FileCache cache) {
        FileKey key = platform.key(file);
        String resolved = cache.version(key, () -> {
            try {
                return getVersionFromMetadata(downloadURL);
            } catch (Exception e) {
                System.err.println("Failed to get version for '" + platform.fileName(file) + "'");
                e.printStackTrace();
                return "INVALID";
            }
        });
        if (resolved.equals("INVALID")) {
            return Optional.empty();
        } else {
            return Optional.of(resolved);
        }
    }

    private static String getVersionFromMetadata(URL file) throws IOException {
        Map<String, byte[]> dataMap = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(file.openStream())) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                String name = entry.getName().startsWith("/") ? entry.getName().substring(1) : entry.getName();
                if (FILE_NAMES.contains(name)) dataMap.put(name, zin.readAllBytes());
                entry = zin.getNextEntry();
            }
        }
        
        RuntimeException collect = new RuntimeException("Could not resolve version");
        
        String ver = processStrategy(dataMap, MOD_INFO_TOML, collect, VersionResolver::versionFromToml);
        if (ver != null) return ver;
        
        ver = processStrategy(dataMap, MOD_INFO_LEGACY, collect, VersionResolver::versionFromLegacy);
        if (ver != null) return ver;
        
        ver = processStrategy(dataMap, JAR_MANIFEST, collect, VersionResolver::versionFromManifest);
        if (ver != null) return ver;
        
        ver = processStrategy(dataMap, MODULE_DESCRIPTOR, collect, VersionResolver::versionFromModule);
        if (ver != null) return ver;
        
        if (collect.getSuppressed().length == 0) {
            collect.addSuppressed(new IllegalStateException("No matching metadata found in jarfile."));
        }

        throw collect;
    }
    
    @Nullable
    private static String processStrategy(Map<String, byte[]> dataMap, String key, RuntimeException collect, Function<byte[], String> resolver) {
        if (dataMap.containsKey(key)) {
            try {
                return resolver.apply(dataMap.get(key));
            } catch (Exception e) {
                collect.addSuppressed(e);
                return null;
            }
        } else {
            return null;
        }
    }

    private static String versionFromToml(byte[] data) {
        Toml toml = new Toml().read(new StringReader(text(data)));
        List<Toml> tables = toml.getTables("mods");
        if (tables.isEmpty()) throw new IllegalStateException("No mods in mods.toml");
        if (tables.size() != 1) throw new IllegalStateException("Multiple mods in mods.toml");
        String version = tables.getFirst().getString("version").strip();
        if (version.startsWith("$")) throw new IllegalStateException("Version variable in mods.toml");
        return version;
    }

    private static String versionFromLegacy(byte[] data) {
        JsonElement json = UpdateCheckerGenerator.GSON.fromJson(text(data), JsonElement.class);
        JsonObject modObj;
        if (json.isJsonArray()) {
            if (json.getAsJsonArray().size() == 1) {
                modObj = json.getAsJsonArray().get(0).getAsJsonObject();
            } else if (json.getAsJsonArray().isEmpty()) {
                throw new IllegalStateException("No mods defined in mcmod.info");
            } else {
                throw new IllegalStateException("Multiple mods defined in mcmod.info");
            }
        } else if (json.isJsonObject()) {
            modObj = json.getAsJsonObject();
        } else {
            throw new IllegalStateException("Invalid mcmod.info file");
        }
        String version = modObj.get("version").getAsString().strip();
        if (version.startsWith("$")) throw new IllegalStateException("Version variable in mcmod.info");
        return version;
    }

    private static String versionFromManifest(byte[] data) {
        return Arrays.stream(text(data).split("\n"))
                .flatMap(str -> {
                    Matcher match = MANIFEST_REGEX.matcher(str);
                    return match.matches() ? Stream.of(match.group(1).strip()) : Stream.empty();
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No Implementation-Version in jar manifest"));
    }

    private static String versionFromModule(byte[] file) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(file)) {
            ModuleDescriptor module = ModuleDescriptor.read(in);
            return module.version()
                    .map(ModuleDescriptor.Version::toString)
                    .orElseThrow(() -> new IllegalStateException("No version defined in module descriptor"));
        } catch (IOException | InvalidModuleDescriptorException e) {
            throw new IllegalStateException("Failed to read module descriptor", e);
        }
    }

    private static String text(byte[] data) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)))) {
            return String.join("\n", reader.lines().toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
