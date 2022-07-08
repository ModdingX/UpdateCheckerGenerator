package org.moddingx.updatecheckergenerator.platform.impl;

import java.time.Instant;
import java.util.Set;

public record ModrinthVersion(
        String projectId,
        String versionId,
        String fileName,
        String fileVersion,
        String releaseType,
        Instant date,
        Set<String> gameVersions,
        String changelog
) {}
