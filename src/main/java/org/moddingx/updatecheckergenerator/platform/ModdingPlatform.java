package org.moddingx.updatecheckergenerator.platform;

import org.moddingx.updatecheckergenerator.ModLoader;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ModdingPlatform<T> {
    
    ProjectData project(String projectId) throws IOException;
    List<T> listFiles(String projectId, Set<ModLoader> loaders) throws IOException;
    
    FileKey key(T file);
    String fileName(T file);
    boolean isStable(T file);
    Set<String> gameVersions(T file);
    Instant fileDate(T file);
    String changelog(T file) throws IOException;
    ResolvableVersion version(T file) throws IOException;
}
