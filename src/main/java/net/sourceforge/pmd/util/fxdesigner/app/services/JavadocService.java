package net.sourceforge.pmd.util.fxdesigner.app.services;

import static net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil.getSupportedLanguages;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.ExtractionTask.ExtractionTaskBuilder;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages the javadocs for the whole app.
 *
 * @author Clément Fournier
 */
public class JavadocService implements ApplicationComponent {

    private static final Pattern JAVADOC_JAR_PATTERN =
        Pattern.compile("pmd-([-\\w]+)-\\d++\\.\\d++\\.\\d++-SNAPSHOT-javadoc\\.jar$");

    private final DesignerRoot designerRoot;
    private final Map<Language, LanguageJavadocService> BY_LANG = new HashMap<>();

    /**
     * One directory to store all current javadoc jars.
     */
    private final ResourceManager javadocJars;

    /**
     * One directory for the exploded contents of all registered javadoc jars.
     */
    private final ResourceManager javadocExploded;

    public JavadocService(DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;

        ResourceManager rootManager = getService(DesignerRoot.GLOBAL_RESOURCE_MANAGER).createSubordinate("javadocs");

        javadocJars = rootManager.createSubordinate("jars");
        javadocExploded = rootManager.createSubordinate("exploded");

        // Extract all javadoc jars that are shipped in the fat jar
        // We could add
        javadocJars.jarExtraction(ResourceUtil.thisJarPathInHost())
                   .maxDepth(1)
                   .shouldUnpack(this::shouldUnpackFromThisJar)
                   .simpleRename(this::nameCleanup)
                   .extractAsync()
                   .thenRun(() -> getSupportedLanguages().forEach(lang -> buildLanguageServer(designerRoot, lang, nameForLanguage(lang.getTerseName()))))
                   .thenCombine(javadocExploded.extractResource("javadoc/webview.css", "webview.css"), (a, b) -> true);

    }

    private void buildLanguageServer(DesignerRoot designerRoot, Language lang, String jarName) {
        javadocJars.getUnpackedFile(jarName)
                   .ifPresent(jar -> {
                       // let it unpack in the exploded dir unless the jar is up to date
                       ExtractionTaskBuilder jarExtraction = javadocExploded.jarExtraction(jar)
                                                                            .extractUnless(() -> javadocJars.isUpToDate(jar));
                       LanguageJavadocService langService = new LanguageJavadocService(lang, designerRoot, jarExtraction, javadocExploded);
                       BY_LANG.put(lang, langService);
                       langService.whenReady(() -> javadocJars.sign(jar));
                   });
    }

    private boolean shouldUnpackFromThisJar(Path inJar, Path laidout) {
        return JAVADOC_JAR_PATTERN.matcher(FilenameUtils.getName(inJar.toString())).matches()
            && !javadocJars.isUpToDate(laidout);
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

    public Optional<LanguageJavadocService> forLanguage(Language language) {
        return Optional.ofNullable(BY_LANG.get(language)).filter(LanguageJavadocService::isReady);
    }


    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
