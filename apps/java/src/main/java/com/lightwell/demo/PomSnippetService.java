package com.lightwell.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Reads apps/java/pom.xml (bundled onto the classpath by the Maven
 * resources plugin, see pom.xml's <build><resources>) for display on the
 * dashboard.
 */
@Service
public class PomSnippetService {

    private static final String POM_RESOURCE = "pom.xml";

    public String readPomXml() {
        try (InputStream in = new ClassPathResource(POM_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + POM_RESOURCE, e);
        }
    }
}
