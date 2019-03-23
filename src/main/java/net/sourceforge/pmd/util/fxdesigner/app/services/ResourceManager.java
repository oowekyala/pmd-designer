package net.sourceforge.pmd.util.fxdesigner.app.services;

import static net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil.thisJarPathInHost;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages a set of language-specific servers.
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
        return ResourceUtil.unpackAsync(
            thisJarPathInHost(),
            Paths.get(ResourceUtil.resolveResource(fxdesignerResourcePath)),
            maxDepth,
            myManagedDir,
            p -> true,
            p -> myManagedDir.resolve(extractedPathRelativeToThis),
            p -> {},
            (f, p) -> {}
        ).thenApply(nothing -> myManagedDir.resolve(ResourceUtil.resolveResource(fxdesignerResourcePath)));
    }

    public CompletableFuture<ResourceManager> unpackJar(Path jarFile,
                                                        Path jarRelativePath,
                                                        int maxDepth,
                                                        Predicate<Path> shouldUnpack,
                                                        Function<String, String> finalRename) {
        return unpackJar(jarFile,
                         jarRelativePath,
                         maxDepth,
                         shouldUnpack,
                         finalRename,
                         p -> {});
    }

    /**
     * Unpacks a jar if not already unpacked.
     *
     * @param jarRelativePath Directory in which to start unpacking. The root is "/"
     * @param maxDepth        Max depth on which to recurse
     * @param jarFile         Jar to unpack
     * @param shouldUnpack    Files to unpack in the jar
     * @param finalRename     File rename
     * @param postProcessing  Actions to run on the final file
     *
     * @return A future that completes when the jar has been closed,
     *     with this resource manager as value
     */
    public CompletableFuture<ResourceManager> unpackJar(Path jarFile,
                                                        Path jarRelativePath,
                                                        int maxDepth,
                                                        Predicate<Path> shouldUnpack,
                                                        Function<String, String> finalRename,
                                                        Consumer<Path> postProcessing) {
        if (thisTimeStamp().exists()) {
            logInternalDebugInfo(() -> "Everything up to date", () -> "");
            getLogger().logEvent(LogEntry.javadocServiceEntry(this, "", false));
            return CompletableFuture.completedFuture(this);
        }

        logInternalDebugInfo(() -> "Unpacking jar...", jarFile::toString);


        return ResourceUtil
            .unpackAsync(
                jarFile,
                jarRelativePath,
                maxDepth,
                myManagedDir,
                shouldUnpack.and(path -> !isUnpacked(ResourceUtil.getJarRelativePath(path.toUri()))),
                targetPath -> renamed(targetPath, finalRename),
                extracted -> jarPostProcessing(postProcessing, extracted),
                (f, e) -> logInternalException(e)
            )
            .handle((nothing, t) -> {
                if (t != null) {
                    logInternalException(t);
                }
                return this;
            });
    }


    private void jarPostProcessing(Consumer<Path> postProcessing, Path extracted) {
        try {
            // stamp the file
            timestampFor(extracted).toFile().createNewFile();
        } catch (IOException e) {
            logInternalException(e);
        }
        // post processing task
        postProcessing.accept(extracted);
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

    public ResourceManager createSubordinate(String relativePath) {
        return new ResourceManager(myManagedDir.resolve(relativePath), getDesignerRoot());
    }

    public Optional<Path> getUnpackedFile(String jarRelativePath) {
        return isUnpacked(jarRelativePath) ? Optional.of(myManagedDir.resolve(jarRelativePath)) : Optional.empty();
    }


    private boolean isUnpacked(String jarRelativePath) {
        return timestampFor(jarRelativePath).toFile().exists();
    }

    private static Path renamed(Path path, Function<String, String> renaming) {
        return path.getParent().resolve(renaming.apply(FilenameUtils.getName(path.toString())));
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
