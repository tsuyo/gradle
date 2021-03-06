/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories.cachemanager;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.CacheDownloadOptions;
import org.apache.ivy.core.cache.CacheMetadataOptions;
import org.apache.ivy.core.cache.DownloadListener;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.report.MetadataArtifactDownloadReport;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.util.Message;
import org.gradle.api.internal.artifacts.ivyservice.filestore.ArtifactFileStore;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

/**
 * A cache manager for remote repositories, that downloads files and stores them in the FileStore provided.
 */
public class DownloadingRepositoryCacheManager extends AbstractRepositoryCacheManager {
    private final ArtifactFileStore fileStore;

    public DownloadingRepositoryCacheManager(String name, ArtifactFileStore fileStore) {
        super(name);
        this.fileStore = fileStore;
    }

    public ArtifactDownloadReport download(Artifact artifact, ArtifactResourceResolver resourceResolver,
                                           ResourceDownloader resourceDownloader, CacheDownloadOptions options) {
        final ArtifactDownloadReport adr = new ArtifactDownloadReport(artifact);

        DownloadListener listener = options.getListener();
        if (listener != null) {
            listener.needArtifact(this, artifact);
        }

        long start = System.currentTimeMillis();
        try {
            ResolvedResource artifactRef = resourceResolver.resolve(artifact);
            if (artifactRef != null) {
                ArtifactOrigin origin = new ArtifactOrigin(artifact, artifactRef.getResource().isLocal(), artifactRef.getResource().getName());
                if (listener != null) {
                    listener.startArtifactDownload(this, artifactRef, artifact, origin);
                }

                File artifactFile = downloadArtifactFile(artifact, resourceDownloader, artifactRef);

                adr.setSize(artifactFile.length());
                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
                adr.setDownloadStatus(DownloadStatus.SUCCESSFUL);
                adr.setArtifactOrigin(origin);
                adr.setLocalFile(artifactFile);
            } else {
                adr.setDownloadStatus(DownloadStatus.FAILED);
                adr.setDownloadDetails(ArtifactDownloadReport.MISSING_ARTIFACT);
                adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
            }
        } catch (Exception ex) {
            adr.setDownloadStatus(DownloadStatus.FAILED);
            adr.setDownloadDetails(ex.getMessage());
            adr.setDownloadTimeMillis(System.currentTimeMillis() - start);
        }
        if (listener != null) {
            listener.endArtifactDownload(this, artifact, adr, adr.getLocalFile());
        }
        return adr;
    }

    private File downloadArtifactFile(Artifact artifact, ResourceDownloader resourceDownloader, ResolvedResource artifactRef) throws IOException {
        File tempFile = fileStore.getTempFile();
        resourceDownloader.download(artifact, artifactRef.getResource(), tempFile);
        return fileStore.add(artifact.getId(), tempFile);
    }

    public ResolvedModuleRevision cacheModuleDescriptor(DependencyResolver resolver, final ResolvedResource resolvedResource, DependencyDescriptor dd, Artifact moduleArtifact, ResourceDownloader downloader, CacheMetadataOptions options) throws ParseException {
        if (!moduleArtifact.isMetadata()) {
            return null;
        }

        ArtifactResourceResolver artifactResourceResolver = new ArtifactResourceResolver() {
            public ResolvedResource resolve(Artifact artifact) {
                return resolvedResource;
            }
        };
        ArtifactDownloadReport report = download(moduleArtifact, artifactResourceResolver, downloader, new CacheDownloadOptions().setListener(options.getListener()).setForce(true));

        if (report.getDownloadStatus() == DownloadStatus.FAILED) {
            Message.warn("problem while downloading module descriptor: " + resolvedResource.getResource()
                    + ": " + report.getDownloadDetails()
                    + " (" + report.getDownloadTimeMillis() + "ms)");
            return null;
        }

        ModuleDescriptor md = parseModuleDescriptor(resolver, moduleArtifact, options, report.getLocalFile(), resolvedResource.getResource());
        Message.debug("\t" + getName() + ": parsed downloaded md file for " + moduleArtifact.getModuleRevisionId() + "; parsed=" + md.getModuleRevisionId());

        MetadataArtifactDownloadReport madr = new MetadataArtifactDownloadReport(md.getMetadataArtifact());
        madr.setSearched(true);
        madr.setDownloadStatus(report.getDownloadStatus());
        madr.setDownloadDetails(report.getDownloadDetails());
        madr.setArtifactOrigin(report.getArtifactOrigin());
        madr.setDownloadTimeMillis(report.getDownloadTimeMillis());
        madr.setOriginalLocalFile(report.getLocalFile());
        madr.setSize(report.getSize());

        return new ResolvedModuleRevision(resolver, resolver, md, madr);
    }

}
