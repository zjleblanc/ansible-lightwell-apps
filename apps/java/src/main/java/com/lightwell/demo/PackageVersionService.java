package com.lightwell.demo;

import java.security.CodeSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.stereotype.Service;

import com.lightwell.demo.model.PackageVersion;
import com.lightwell.demo.model.TrackedDependency;

/**
 * Reports installed versions of tracked dependencies, annotated with
 * Lightwell provenance (from the version string) and role metadata from
 * app_config.yaml.
 */
@Service
public class PackageVersionService {

    private static final List<String> TRACKED_PACKAGES =
            List.of("spring-core", "json", "spring-boot", "thymeleaf", "snakeyaml");

    // A class from each tracked jar, used to resolve its version when the
    // jar's manifest doesn't set Implementation-Version (see
    // versionFromJarFileName below).
    private static final Map<String, Class<?>> REPRESENTATIVE_CLASSES = Map.of(
            "spring-core", SpringVersion.class,
            "spring-boot", SpringBootVersion.class,
            "json", org.json.JSONObject.class,
            "thymeleaf", org.thymeleaf.TemplateEngine.class,
            "snakeyaml", org.yaml.snakeyaml.Yaml.class);

    // Lightwell-remediated jars append a ".rhlw-<id>" suffix to the
    // upstream version, e.g. "6.1.13.rhlw-00001".
    private static final Pattern LIGHTWELL_SUFFIX = Pattern.compile("\\.rhlw-(?<patchId>\\w+)$");

    public List<PackageVersion> getPackageVersions(List<TrackedDependency> trackedDeps) {
        Map<String, TrackedDependency> depByName = new HashMap<>();
        for (TrackedDependency dep : trackedDeps) {
            depByName.put(dep.name(), dep);
        }
        return TRACKED_PACKAGES.stream()
                .map(name -> toPackageVersion(name, depByName.get(name)))
                .toList();
    }

    private PackageVersion toPackageVersion(String packageName, TrackedDependency dep) {
        String rawVersion = resolveInstalledVersion(packageName);
        Matcher matcher = LIGHTWELL_SUFFIX.matcher(rawVersion);
        String baseVersion = rawVersion;
        String patchId = null;
        if (matcher.find()) {
            baseVersion = rawVersion.substring(0, matcher.start());
            patchId = matcher.group("patchId");
        }
        return new PackageVersion(packageName, baseVersion, patchId != null, patchId,
                dep != null ? dep.role() : "");
    }

    private String resolveInstalledVersion(String packageName) {
        String version = switch (packageName) {
            case "spring-core" -> SpringVersion.getVersion();
            case "spring-boot" -> SpringBootVersion.getVersion();
            default -> null;
        };

        Class<?> representative = REPRESENTATIVE_CLASSES.get(packageName);
        if (version == null && representative != null) {
            version = representative.getPackage().getImplementationVersion();
        }
        if (version == null && representative != null) {
            version = versionFromJarFileName(representative, packageName);
        }
        return version != null ? version : "unknown";
    }

    /**
     * Falls back to parsing "<artifactId>-<version>.jar" out of the jar's
     * own file name when the manifest doesn't set Implementation-Version
     * (true of the org.json:json and snakeyaml jars on Maven Central).
     */
    private String versionFromJarFileName(Class<?> representativeClass, String artifactId) {
        CodeSource codeSource = representativeClass.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        String location = codeSource.getLocation().toString();
        int trailingBang = location.lastIndexOf('!');
        String jarPath = trailingBang >= 0 ? location.substring(0, trailingBang) : location;
        String fileName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".jar")) {
            return null;
        }
        String withoutExtension = fileName.substring(0, fileName.length() - ".jar".length());
        String prefix = artifactId + "-";
        return withoutExtension.startsWith(prefix)
                ? withoutExtension.substring(prefix.length())
                : withoutExtension;
    }
}
