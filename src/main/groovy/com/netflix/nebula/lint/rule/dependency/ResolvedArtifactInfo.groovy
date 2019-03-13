package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ResolvedArtifact

class ResolvedArtifactInfo {
    String organization
    String name
    String version
    String type

    static ResolvedArtifactInfo fromResolvedArtifact(ResolvedArtifact resolvedArtifact) {
        ResolvedArtifactInfo info = new ResolvedArtifactInfo()
        info.organization = resolvedArtifact.moduleVersion.id.group
        info.name = resolvedArtifact.moduleVersion.id.name
        info.version = resolvedArtifact.moduleVersion.id.version
        info.type = resolvedArtifact.type
        return info
    }
}
