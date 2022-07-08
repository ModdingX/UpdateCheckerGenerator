package org.moddingx.updatecheckergenerator.platform;

import org.moddingx.updatecheckergenerator.cache.FileCache;
import org.moddingx.updatecheckergenerator.version.VersionResolver;

import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

public class ResolvableVersion {
    
    private final Function<FileCache, Optional<String>> version;
    
    private ResolvableVersion(Function<FileCache, Optional<String>> version) {
        this.version = version;
    }
    
    public Optional<String> version(FileCache cache) {
        return this.version.apply(cache);
    }
    
    public static ResolvableVersion of(String version) {
        return new ResolvableVersion(cache -> Optional.of(version));
    }
    
    public static <T> ResolvableVersion resolveBy(ModdingPlatform<T> platform, T file, URL downloadURL) {
        return new ResolvableVersion(cache -> VersionResolver.getVersion(platform, file, downloadURL, cache));
    }
}
