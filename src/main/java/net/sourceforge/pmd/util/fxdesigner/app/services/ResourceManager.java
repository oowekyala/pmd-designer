package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import net.sourceforge.pmd.util.fxdesigner.util.JarExplorationUtil;

/**
 * Manages a set of language-specific servers.
 *
 * @author Clément Fournier
 */
public class ResourceManager implements ApplicationComponent {

    // bump to invalidate cache
    private static final String TIMESTAMP_VERSION = "8";
    private static final String TIMESTAMP = "-timestamp-";

    private final DesignerRoot designerRoot;

    private final Path resourcesUnixPath;


    public ResourceManager(Path unpackDir, DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;
        resourcesUnixPath = unpackDir;

        File dir = resourcesUnixPath.toFile();
        if (dir.exists() && dir.isDirectory()) {
            // there are outdated timestamps
            List<File> thisStamps = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                                          .filter(it -> it.getName().matches("this" + TIMESTAMP + "\\d+"))
                                          .collect(Collectors.toList());
            boolean outOfDate = !thisStamps.isEmpty()
                && thisStamps.stream().noneMatch(it -> it.equals(thisTimeStamp()));

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
        return resourcesUnixPath;
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
     * @param jarRelativePath  Directory in which to start unpacking. The root is "/"
     * @param maxDepth         Max depth on which to recurse
     * @param jarFile        Jar to unpack
     * @param shouldUnpack   Files to unpack in the jar
     * @param finalRename    File rename
     * @param postProcessing Actions to run on the final file
     *
     * @return A future that completes when the jar has been closed,
     * with this resource manager as value
     */
    public CompletableFuture<ResourceManager> unpackJar(Path jarFile,
                                                        Path jarRelativePath,
                                                        int maxDepth,
                                                        Predicate<Path> shouldUnpack,
                                                        Function<String, String> finalRename,
                                                        Consumer<Path> postProcessing) {
        if (thisTimeStamp().exists()) {
            return CompletableFuture.completedFuture(this);
        }

        return JarExplorationUtil.unpackAsync(jarFile,
                                              jarRelativePath,
                                              maxDepth,
                                              resourcesUnixPath,
                                              shouldUnpack.and(path -> !isUnpacked(JarExplorationUtil.getJarRelativePath(path.toUri()))),
                                              unpacked -> {
                                                  File finalFile =
                                                      unpacked.getParent().resolve(finalRename.apply(FilenameUtils.getName(unpacked.toString()))).toFile();
                                                  unpacked.toFile().renameTo(finalFile);
                                                  try {
                                                      // stamp the file
                                                      timestampFor(finalFile.toPath()).toFile().createNewFile();
                                                  } catch (IOException e) {
                                                      logInternalException(e);
                                                  }
                                                  // post processing task
                                                  postProcessing.accept(finalFile.toPath());
                                              },
                                              (f, e) -> logInternalException(e))
                                 .handle((nothing, t) -> {
                                     if (t != null) {
                                         logInternalException(t);
                                     }
                                     return this;
                                 });
    }


    /**
     * Mark that all resources that are managed by this manager are up to
     * date, so they can be picked up on the next run.
     */
    public void markUptodate() {

        try {
            // stamp the file
            thisTimeStamp().createNewFile();
        } catch (IOException e) {
            logInternalException(e);
        }

    }

    private File thisTimeStamp() {
        return timestampFor("this").toFile();
    }

    private Path timestampFor(String jarRelativePath) {
        return resourcesUnixPath.resolve(jarRelativePath + TIMESTAMP + TIMESTAMP_VERSION);
    }


    private Path timestampFor(Path unpacked) {
        Path relative = resourcesUnixPath.resolve(unpacked);
        return relative.getParent().resolve(relative.getFileName() + TIMESTAMP + TIMESTAMP_VERSION);
    }

    public Path subdir(String relativePath) {
        return resourcesUnixPath.resolve(relativePath);
    }

    private boolean isUnpacked(String jarRelativePath) {
        return timestampFor(jarRelativePath).toFile().exists();
    }

    public Optional<Path> getUnpackedFile(String jarRelativePath) {
        return isUnpacked(jarRelativePath) ? Optional.of(resourcesUnixPath.resolve(jarRelativePath)) : Optional.empty();
    }


    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
