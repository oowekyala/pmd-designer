package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages the javadocs for the whole app. Basically, we unpack some
 * javadoc jars that are found in the shipped javadoc, then we run
 *
 * @author Clément Fournier
 */
public class JavadocService implements ApplicationComponent {

    private static final Pattern JAVADOC_JAR_PATTERN =
        Pattern.compile("pmd-([-\\w]+)-\\d++\\.\\d++\\.\\d++-SNAPSHOT-javadoc\\.jar$");

    private final DesignerRoot designerRoot;

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
        JavadocExtractor extractor = new JavadocExtractor(designerRoot, javadocExploded);


        // Extract all javadoc jars that are shipped in the fat jar
        // We could add
        javadocJars.jarExtraction(ResourceUtil.thisJarPathInHost())
                   .maxDepth(1)
                   .shouldUnpack(this::shouldUnpackFromThisJar)
                   .simpleRename(this::nameCleanup)
                   .postProcessing(extractor::extractJar)
                   .extractAsync();

        javadocExploded.extract("javadoc/", "", Integer.MAX_VALUE);
    }


    public Optional<URL> docUrl(Class<? extends Node> clazz) {
        String name = clazz.getName().replace('.', '/') + "";

        return javadocExploded.getUnpackedFile(name + ".html")
                              .map(htmlFile -> {
                                  try {
                                      return htmlFile.toUri().toURL();
                                  } catch (MalformedURLException e) {
                                      logInternalException(e);
                                      return null;
                                  }
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


    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
