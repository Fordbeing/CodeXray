package com.codexray.service;

import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.UUID;

@Service
public class GitCloneService {

    @Value("${codexray.clone.base-dir:/tmp/codexray-clones}")
    private String baseDir;

    public String clone(String repoUrl) throws Exception {
        String dirName = UUID.randomUUID().toString();
        File targetDir = new File(baseDir, dirName);

        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(targetDir)
                .setCloneAllBranches(false)
                .setDepth(1)
                .call()) {
            // clone completed
        }

        return targetDir.getAbsolutePath();
    }
}
