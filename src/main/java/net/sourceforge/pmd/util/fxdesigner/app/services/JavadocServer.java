package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.util.JarExplorationUtil;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;

/**
 * Manages a set of language-specific servers.
 *
 * @author Clément Fournier
 */
public class JavadocServer implements ApplicationComponent {

    private static final Pattern JAVADOC_JAR_PATTERN =
        Pattern.compile("pmd-([-\\w]+)-\\d++\\.\\d++\\.\\d++-SNAPSHOT-javadoc\\.jar$");

    private final DesignerRoot designerRoot;
    private final Map<Language, LanguageJavadocServer> BY_LANG = new HashMap<>();

    public JavadocServer(DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;

        getService(DesignerRoot.GLOBAL_RESOURCE_MANAGER)
            .unpackJar(JarExplorationUtil.thisJarPathInHost(), this::shouldUnpack, this::nameCleanup)
            .thenAccept(
                manager ->
                    LanguageRegistryUtil.getSupportedLanguages()
                                        .collect(Collectors.toMap(l -> l, l -> nameForLanguage(l.getTerseName())))
                                        .forEach((lang, jarName) -> buildLanguageServer(designerRoot, manager, lang, jarName)));

    }

    private void buildLanguageServer(DesignerRoot designerRoot, ResourceManager resourceManager, Language lang, String jarName) {
        resourceManager.getUnpackedFile(jarName)
                       .ifPresent(jar -> {
                           ResourceManager subResourceManager =
                               new ResourceManager(resourceManager.subdir(jarName + "-exploded"), designerRoot);

                           LanguageJavadocServer server =
                               new LanguageJavadocServer(lang, designerRoot, jar, subResourceManager);

                           BY_LANG.put(lang, server);
                       });
    }

    private boolean shouldUnpack(Path path) {
        return JAVADOC_JAR_PATTERN.matcher(FilenameUtils.getName(path.toString())).matches();
    }

    private String nameForLanguage(String langName) {
        return "javadoc-" + langName + ".jar";

    }

    private String nameCleanup(String unpacked) {
        Matcher matcher = JAVADOC_JAR_PATTERN.matcher(unpacked);
        if (matcher.matches()) {
            return nameForLanguage(matcher.group(1));
        }

        return unpacked;
    }

    public Optional<LanguageJavadocServer> forLanguage(Language language) {
        return Optional.ofNullable(BY_LANG.get(language)).filter(LanguageJavadocServer::isReady);
    }


    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
