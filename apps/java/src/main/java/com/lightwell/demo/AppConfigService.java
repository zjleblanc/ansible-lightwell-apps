package com.lightwell.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.lightwell.demo.model.AppConfig;
import com.lightwell.demo.model.PatchSource;
import com.lightwell.demo.model.PatchTimelineEntry;
import com.lightwell.demo.model.ServiceInfo;
import com.lightwell.demo.model.TrackedDependency;

/**
 * Loads config/app_config.yaml via SnakeYAML at startup.
 */
@Service
public class AppConfigService {

    private static final String CONFIG_RESOURCE = "config/app_config.yaml";

    private final Map<String, Object> rawConfig;
    private final AppConfig config;

    public AppConfigService() {
        this.rawConfig = readYaml();
        this.config = toAppConfig(rawConfig);
    }

    public AppConfig getConfig() {
        return config;
    }

    /** Full YAML document as a nested Map, for the /api/config passthrough. */
    public Map<String, Object> getRawConfig() {
        return rawConfig;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml() {
        try (InputStream in = new ClassPathResource(CONFIG_RESOURCE).getInputStream()) {
            Map<String, Object> loaded = new Yaml().load(in);
            return loaded != null ? loaded : Map.of();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + CONFIG_RESOURCE, e);
        }
    }

    @SuppressWarnings("unchecked")
    private AppConfig toAppConfig(Map<String, Object> raw) {
        Map<String, Object> serviceMap = (Map<String, Object>) raw.getOrDefault("service", Map.of());
        ServiceInfo service = new ServiceInfo(
                String.valueOf(serviceMap.getOrDefault("name", "")),
                String.valueOf(serviceMap.getOrDefault("description", "")),
                String.valueOf(serviceMap.getOrDefault("version", "")));

        Map<String, Object> patchSourceMap = (Map<String, Object>) raw.getOrDefault("patch_source", Map.of());
        PatchSource patchSource = new PatchSource(
                String.valueOf(patchSourceMap.getOrDefault("name", "")),
                String.valueOf(patchSourceMap.getOrDefault("url", "")),
                String.valueOf(patchSourceMap.getOrDefault("provider", "")));

        Map<String, Object> dependenciesMap = (Map<String, Object>) raw.getOrDefault("dependencies", Map.of());
        List<Map<String, Object>> trackedList =
                (List<Map<String, Object>>) dependenciesMap.getOrDefault("tracked", List.of());
        List<TrackedDependency> tracked = trackedList.stream()
                .map(entry -> new TrackedDependency(
                        String.valueOf(entry.get("name")),
                        String.valueOf(entry.getOrDefault("role", ""))))
                .toList();

        List<Map<String, Object>> timelineList =
                (List<Map<String, Object>>) raw.getOrDefault("patch_timeline", List.of());
        List<PatchTimelineEntry> timeline = timelineList.stream()
                .map(entry -> new PatchTimelineEntry(
                        String.valueOf(entry.get("date")),
                        String.valueOf(entry.get("version")),
                        String.valueOf(entry.get("event"))))
                .toList();

        return new AppConfig(service, patchSource, tracked, timeline);
    }
}
