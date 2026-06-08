package com.example.pcaExamAnalyze.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads a local {@code .env} file into JVM system properties so placeholders like
 * {@code ${DB_URL}} and {@code ${MAIL_USERNAME}} resolve when running locally.
 *
 * <p>Called from {@code main()} before {@code SpringApplication.run(...)}. This replaces the
 * third-party {@code spring-dotenv} library, which hooks in via the legacy
 * {@code META-INF/spring.factories} mechanism that Spring Boot 4 no longer honours — so under
 * Boot 4 it silently loaded nothing.
 *
 * <p>Existing system properties / real environment variables are never overwritten, so values
 * injected by the host (e.g. Render in production) always win. A missing {@code .env} is ignored.
 */
public final class DotenvLoader {

    private DotenvLoader() {
    }

    /**
     * Reads {@code .env} from the common launch locations and sets any keys as system properties.
     *
     * <p>The app is often launched either from {@code backend/} or from the repository root with
     * {@code mvn -f backend/pom.xml ...}; in the second case the credentials live at
     * {@code backend/.env}, so include that path explicitly.
     */
    public static void load() {
        Path envFile = firstExisting(
                Path.of(".env"),
                Path.of("backend", ".env"),
                Path.of("..", ".env"),
                Path.of("..", "backend", ".env"));
        if (envFile == null) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // unreadable .env — rely on real env vars / defaults
        }
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripQuotes(line.substring(eq + 1).strip());
            // Don't override real environment variables or -D system properties.
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path firstExisting(Path... candidates) {
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
