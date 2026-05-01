package com.codexray.service;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class GitCloneService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

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

    /**
     * 递归删除克隆的仓库目录。
     */
    public void cleanup(String repoPath) {
        Path path = Path.of(repoPath);
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("Cleaned up clone: {}", repoPath);
        } catch (IOException e) {
            log.warn("Failed to cleanup clone: {}", repoPath, e);
        }
    }
}
