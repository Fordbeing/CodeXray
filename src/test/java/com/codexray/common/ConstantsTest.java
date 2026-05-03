package com.codexray.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void skipDirs_shouldContainCommonDirectories() {
        assertTrue(Constants.SKIP_DIRS.contains(".git"));
        assertTrue(Constants.SKIP_DIRS.contains("node_modules"));
        assertTrue(Constants.SKIP_DIRS.contains("target"));
        assertTrue(Constants.SKIP_DIRS.contains("build"));
        assertTrue(Constants.SKIP_DIRS.contains("__pycache__"));
        assertTrue(Constants.SKIP_DIRS.contains(".idea"));
        assertTrue(Constants.SKIP_DIRS.contains(".vscode"));
    }

    @Test
    void skipDirs_shouldBeUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                Constants.SKIP_DIRS.add("test"));
    }

    @Test
    void skipDirs_shouldNotBeEmpty() {
        assertFalse(Constants.SKIP_DIRS.isEmpty());
        assertTrue(Constants.SKIP_DIRS.size() >= 10);
    }
}
