package net.sourceforge.pmd.util.fxdesigner.app.services;

import static net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil.thisJarPathInHost;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.ExtractionTask.ExtractionTaskBuilder;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages a versioned resource directory on disk. Other components
 * can call in to order the extraction of a resource from the app's
 * jar, which is only executed if the up to date version is not present.
 *
 * <p>This is still very immature, versioning is practically useless.
 *
 * @author Clément Fournier
 */
public class ResourceManager implements ApplicationComponent {

    // bump to invalidate cache
    private static final String TIMESTAMP_VERSION = "11";
    private static final String TIMESTAMP = "-timestamp-";

    private final DesignerRoot designerRoot;

    private final Path myManagedDir;


    public ResourceManager(Path unpackDir, DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;
        myManagedDir = unpackDir;

        File dir = myManagedDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            // there are outdated timestamps
            List<File> thisStamps = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                                          .filter(it -> it.getName().matches("this" + TIMESTAMP + "\\d+"))
                                          .collect(Collectors.toList());
            boolean outOfDate =
                !thisStamps.isEmpty() && thisStamps.stream().noneMatch(it -> it.equals(thisTimeStamp()));

            if (outOfDate) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    logInternalException(e);
                }
            } else {
                // remove old stamps
                thisStamps.stream()
                          .filter(it -> !it.equals(thisTimeStamp()))
                          .forEach(File::delete);
            }
        }
    }


    public Path getRootManagedDir() {
        return myManagedDir;
    }


    /**
     * Extract a file, if it's a directory then nothing is extracted.
     *
     * @param fxdesignerResourcePath      Relative to the dir of {@link ResourceUtil#resolveResource(String)}.
     * @param extractedPathRelativeToThis Explicit
     *
     * @return A future with the final extracted path
     */
    public CompletableFuture<Path> extractResource(String fxdesignerResourcePath, String extractedPathRelativeToThis) {
        return extract(fxdesignerResourcePath, extractedPathRelativeToThis, 0);
    }

    /**
     * Extract a resource, may be a directory or file.
     *
     * @param fxdesignerResourcePath      Relative to the dir of {@link ResourceUtil#resolveResource(String)}.
     * @param extractedPathRelativeToThis Explicit
     * @param maxDepth                    Max depth on which to recurse
     *
     * @return A future with the final extracted path
     */
    public CompletableFuture<Path> extract(String fxdesignerResourcePath,
                                           String extractedPathRelativeToThis,
                                           int maxDepth) {

        return
            jarExtraction(thisJarPathInHost(), true)
                .maxDepth(maxDepth)
                .layoutMapper(p -> myManagedDir.resolve(extractedPathRelativeToThis))
                .exceptionHandler((p, t) -> logInternalException(t))
                .extract()
                .thenApply(nothing -> myManagedDir.resolve(ResourceUtil.resolveResource(fxdesignerResourcePath)));
    }

    public ExtractionTaskBuilder jarExtraction(Path jarFile, boolean stampContents) {
        return ExtractionTask.newBuilder(jarFile, myManagedDir)
                             .postProcessing(p -> jarPostProcessing(p, stampContents));
    }


    private void jarPostProcessing(Path extracted, boolean shouldStampContents) {
        if (shouldStampContents) {
            try {
                // stamp the file
                timestampFor(extracted).toFile().createNewFile();
            } catch (IOException e) {
                logInternalException(e);
            }
        }
    }

    private Path timestampFor(String jarRelativePath) {
        return myManagedDir.resolve(jarRelativePath + TIMESTAMP + TIMESTAMP_VERSION);
    }


    /**
     * Mark that all resources that are managed by this manager are up to
     * date, so they can be picked up on the next run.
     */
    public void markUptodate() {

        try {
            // stamp the file
            thisTimeStamp().createNewFile();
            logInternalDebugInfo(() -> "Locking down on version " + TIMESTAMP_VERSION, () -> "");
        } catch (IOException e) {
            logInternalException(e);
        }

    }

    private File thisTimeStamp() {
        return timestampFor("this").toFile();
    }

    private Path timestampFor(Path unpacked) {
        Path relative = myManagedDir.resolve(unpacked);
        return relative.getParent().resolve(relative.getFileName() + TIMESTAMP + TIMESTAMP_VERSION);
    }

    /**
     * Creates a manager that manages a subdirectory of the directory
     * managed by this dir. Subordinates of the same manager are independent,
     * but if the manager is
     *
     * @param relativePath
     *
     * @return
     */
    public ResourceManager createSubordinate(String relativePath) {
        return new ResourceManager(myManagedDir.resolve(relativePath), getDesignerRoot());
    }

    public Optional<Path> getUnpackedFile(String jarRelativePath) {
        return isUnpacked(jarRelativePath) ? Optional.of(myManagedDir.resolve(jarRelativePath)) : Optional.empty();
    }


    private boolean isUnpacked(String jarRelativePath) {
        return timestampFor(jarRelativePath).toFile().exists();
    }

    @Override
    public Category getLogCategory() {
        return Category.RESOURCE_MANAGEMENT;
    }

    @Override
    public String getDebugName() {
        return "ResourceManager("
            + getService(DesignerRoot.PERSISTENCE_MANAGER).getSettingsDirectory().relativize(getRootManagedDir())
            + ")";
    }

    @Override
    public String toString() {
        return getDebugName();
    }

    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }

}
