package org.moddingx.updatecheckergenerator.platform.impl;

import org.moddingx.cursewrapper.api.CurseWrapper;
import org.moddingx.cursewrapper.api.request.FileFilter;
import org.moddingx.cursewrapper.api.response.FileInfo;
import org.moddingx.cursewrapper.api.response.ModLoader;
import org.moddingx.cursewrapper.api.response.ProjectInfo;
import org.moddingx.cursewrapper.api.response.ReleaseType;
import org.moddingx.updatecheckergenerator.platform.FileKey;
import org.moddingx.updatecheckergenerator.platform.ModdingPlatform;
import org.moddingx.updatecheckergenerator.platform.ProjectData;
import org.moddingx.updatecheckergenerator.platform.ResolvableVersion;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class CursePlatform implements ModdingPlatform<FileInfo> {
    
    private final CurseWrapper api;

    public CursePlatform() {
        this.api = new CurseWrapper(URI.create("https://curse.melanx.de"));
    }

    //new ArrayList<>(api.getFiles(projectId, FileFilter.loader(ModLoader.FORGE)));


    @Override
    public ProjectData project(String projectId) throws IOException {
        ProjectInfo project = api.getProject(validateProjectId(projectId));
        return new ProjectData(project.slug(), project.website().toString());
    }

    @Override
    public List<FileInfo> listFiles(String projectId) throws IOException {
        return api.getFiles(validateProjectId(projectId), FileFilter.loader(ModLoader.FORGE));
    }

    @Override
    public FileKey key(FileInfo file) {
        return new FileKey(Integer.toString(file.projectId()), Integer.toString(file.fileId()));
    }

    @Override
    public String fileName(FileInfo file) {
        return file.name();
    }

    @Override
    public boolean isStable(FileInfo file) {
        return file.releaseType() == ReleaseType.RELEASE;
    }

    @Override
    public Set<String> gameVersions(FileInfo file) {
        return Set.copyOf(file.gameVersions());
    }

    @Override
    public Instant fileDate(FileInfo file) {
        return file.fileDate();
    }

    @Override
    public String changelog(FileInfo file) throws IOException {
        return this.api.getChangelog(file.projectId(), file.fileId());
    }

    @Override
    public ResolvableVersion version(FileInfo file) throws IOException {
        return ResolvableVersion.resolveBy(this, file, new URL("https://www.cursemaven.com/curse/maven/O-" + file.projectId() + "/" + file.fileId() + "/O-" + file.projectId() + "-" + file.fileId() + ".jar"));
    }

    private int validateProjectId(String projectId) throws IOException {
        try {
            return Integer.parseInt(projectId);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid project id: " + projectId, e);
        }
    }
}
