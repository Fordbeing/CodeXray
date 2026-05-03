package com.codexray.common;

import java.util.Set;

public final class Constants {

    private Constants() {}

    public static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "__pycache__", "target", "build",
            "dist", "out", "bin", "obj", "vendor", ".mvn", ".gradle", "venv", ".venv",
            "env", ".env", ".next", ".nuxt", "coverage", ".nyc_output", "tmp", "temp",
            "logs", "log"
    );
}
