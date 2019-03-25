package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages the javadocs for the whole app. Basically, we unpack some
 * javadoc jars that are found in the shipped javadoc, then we run
 *
 * @author Clément Fournier
 */
public class JavadocService implements ApplicationComponent, CloseableService {

    private static final Pattern JAVADOC_JAR_PATTERN =
        Pattern.compile("pmd-([-\\w]+)-\\d++\\.\\d++\\.\\d++-SNAPSHOT-javadoc\\.jar$");

    private final DesignerRoot designerRoot;

    /** One directory to store the javadoc jars while they're being processed. */
    private final ResourceManager javadocJars;

    /** One directory for the exploded contents of all registered javadoc jars. */
    private final ResourceManager javadocExploded;
    private final ScheduledExecutorService executor;

    public JavadocService(DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;

        ResourceManager rootManager = getService(DesignerRoot.DISK_MANAGER).getRootResourcesManager().createSubordinate("javadocs");

        javadocJars = rootManager.createSubordinate("jars");
        javadocExploded = rootManager.createSubordinate("exploded");
        executor = Executors.newScheduledThreadPool(1, runnable -> new Thread(runnable, "Javadoc extractor"));

        // schedule the javadoc extraction after the app is done initializing
        executor.schedule(this::extractAndLog, 4, TimeUnit.SECONDS);
    }


    public void extractDocsNow() {
        executor.schedule(this::extractAndLog, 0, TimeUnit.MILLISECONDS);
    }

    private void extractAndLog() {
        long start = System.nanoTime();
        extractDocsImpl();
        long end = System.nanoTime();
        logInternalDebugInfo(() -> "Javadoc extractor report", () -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            long wallclockTime = end - start;
            long cpuTime = threadMXBean.getCurrentThreadCpuTime();
            long userTime = threadMXBean.getCurrentThreadUserTime();
            return "CPU time: " + cpuTime + " ns (" + cpuTime / 1000000 + " ms)\n"
                + "User time: " + userTime + " ns (" + userTime / 1000000 + " ms)\n"
                + "Wallclock time: " + wallclockTime + " ns (" + wallclockTime / 1000000 + " ms)\n"
                + "JVM runtime: " + uptimeMs + " ms \n"
                ;
        });
    }

    private void extractDocsImpl() {
        JavadocExtractor extractor = new JavadocExtractor(designerRoot, javadocExploded);
        // Extract all javadoc jars that are shipped in the fat jar
        // We could add
        javadocJars.jarExtraction(ResourceUtil.thisJarPathInHost())
                   .maxDepth(1)
                   .shouldUnpack(this::shouldUnpackFromThisJar)
                   .simpleRename(this::nameCleanup)
                   .postProcessing(extractor::extractJar)
                   .extract();

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
    public Category getLogCategory() {
        return Category.JAVADOC_SERVICE;
    }

    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }

    @Override
    public void close() throws Exception {
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
