/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util;

import static net.sourceforge.pmd.lang.LanguageRegistry.findLanguageByTerseName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;

/**
 * Utilities to extend the functionality of {@link LanguageRegistry}.
 *
 * @author Clément Fournier
 */
public final class LanguageRegistryUtil {

    private static final String DEFAULT_LANGUAGE_NAME = "Java";
    private static List<LanguageVersion> supportedLanguageVersions;
    private static Map<String, LanguageVersion> extensionsToLanguage;

    private LanguageRegistryUtil() {

    }

    public static LanguageVersion findLanguageVersionByTerseName(String tname) {
        String[] s = tname.split(" ");
        Language lang = findLanguageByTerseName(s[0]);
        return lang.getVersion(s[1]);
    }

    @NonNull
    public static LanguageVersion defaultLanguageVersion() {
        return defaultLanguage().getDefaultVersion();
    }


    // TODO need a notion of dialect in core + language services
    public static boolean isXmlDialect(Language language) {
        switch (language.getTerseName()) {
        case "xml":
        case "pom":
        case "wsql":
        case "fxml":
        case "xsl":
            return true;
        default:
            return false;
        }
    }

    public static Language plainTextLanguage() {
        Language fallback = LanguageRegistry.findLanguageByTerseName(PlainTextLanguage.TERSE_NAME);
        if (fallback != null) {
            return fallback;
        } else {
            throw new AssertionError("No plain text language?");
        }
    }

    @NonNull
    public static Language defaultLanguage() {
        Language defaultLanguage = LanguageRegistry.getLanguage(DEFAULT_LANGUAGE_NAME);
        return defaultLanguage != null ? defaultLanguage : plainTextLanguage();
    }

    private static Map<String, LanguageVersion> getExtensionsToLanguageMap() {
        Map<String, LanguageVersion> result = new HashMap<>();
        getSupportedLanguageVersions().stream()
                                      .map(LanguageVersion::getLanguage)
                                      .distinct()
                                      .collect(Collectors.toMap(Language::getExtensions,
                                                                Language::getDefaultVersion))
                                      .forEach((key, value) -> key.forEach(ext -> result.put(ext, value)));
        return result;
    }

    @Nullable
    public static synchronized LanguageVersion getLanguageVersionFromExtension(String filename) {
        if (extensionsToLanguage == null) {
            extensionsToLanguage = getExtensionsToLanguageMap();
        }

        if (filename.indexOf('.') > 0) {
            String[] tokens = filename.split("\\.");
            return extensionsToLanguage.get(tokens[tokens.length - 1]);
        }
        return null;
    }

    private static boolean filterLanguageVersion(LanguageVersion lv) {
        return !StringUtils.containsIgnoreCase(lv.getLanguage().getName(), "dummy")
            && !lv.getTerseName().equals("oldjava");
    }

    public static synchronized List<LanguageVersion> getSupportedLanguageVersions() {
        if (supportedLanguageVersions == null) {
            supportedLanguageVersions = findAllVersions().stream().filter(LanguageRegistryUtil::filterLanguageVersion).collect(Collectors.toList());
        }
        return supportedLanguageVersions;
    }

    private static List<LanguageVersion> findAllVersions() {
        return LanguageRegistry.getLanguages().stream().flatMap(l -> l.getVersions().stream()).collect(Collectors.toList());
    }

    @NonNull
    public static LanguageVersion getLanguageVersionByName(String name) {
        return getSupportedLanguageVersions().stream()
                                             .filter(it -> it.getName().equals(name))
                                             .findFirst()
                                             .orElse(defaultLanguageVersion());
    }

    @NonNull
    public static Stream<Language> getSupportedLanguages() {
        return getSupportedLanguageVersions().stream().map(LanguageVersion::getLanguage).distinct();
    }

    @NonNull
    public static Language findLanguageByShortName(String shortName) {
        return getSupportedLanguages().filter(it -> it.getShortName().equals(shortName))
                                      .findFirst()
                                      .orElse(defaultLanguage());
    }

    @NonNull
    public static Language findLanguageByName(String n) {
        return getSupportedLanguages().filter(it -> it.getName().equals(n))
                                      .findFirst()
                                      .orElse(defaultLanguage());
    }

    public static Val<LanguageVersion> mapNewJavaToOld(Val<LanguageVersion> newJavaVer) {
        return newJavaVer.map(LanguageRegistryUtil::mapNewJavaToOld);
    }

    public static Val<Language> oldJavaLangProperty(Val<Boolean> useOld) {
        return useOld.map(old -> old ? findLanguageByTerseName("oldjava") : findLanguageByTerseName("java"));
    }

    public static LanguageVersion mapNewJavaToOld(LanguageVersion newJavaVer) {
        return Optional.of(newJavaVer)
                       .map(LanguageVersion::getVersion)
                       .map(findLanguageByTerseName("oldjava")::getVersion)
                       .orElseGet(() -> findLanguageByTerseName("oldjava").getDefaultVersion());
    }


}
