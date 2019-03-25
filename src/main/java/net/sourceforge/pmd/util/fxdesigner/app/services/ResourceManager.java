package net.sourceforge.pmd.util.fxdesigner.app.services;

import static net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil.thisJarPathInHost;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.apache.commons.io.FileUtils;

import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.ExtractionTask.ExtractionTaskBuilder;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Manages a resource directory on disk. Other components
 * can call in to order the extraction of a resource from the app's
 * jar.
 *
 * <p>This is still very immature...
 *
 * @author Clément Fournier
 */
public class ResourceManager implements ApplicationComponent {


    private final DesignerRoot designerRoot;

    private final Path myManagedDir;


    public ResourceManager(DesignerRoot designerRoot, Path unpackDir) {
        this.designerRoot = designerRoot;
        myManagedDir = unpackDir;

    }

    public final Path getRootManagedDir() {
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
            jarExtraction(thisJarPathInHost())
                .maxDepth(maxDepth)
                .jarRelativePath(Paths.get(ResourceUtil.resolveResource(fxdesignerResourcePath)))
                .layoutMapper(p -> myManagedDir.resolve(extractedPathRelativeToThis).resolve(myManagedDir.relativize(p)))
                .exceptionHandler((p, t) -> logInternalException(t))
                .extract()
                .thenApply(nothing -> myManagedDir.resolve(ResourceUtil.resolveResource(fxdesignerResourcePath)));
    }

    public ExtractionTaskBuilder jarExtraction(Path jarFile) {
        return ExtractionTask.newBuilder(jarFile, myManagedDir);
    }


    /**
     * Creates a manager that manages a subdirectory of the directory
     * managed by this dir.
     */
    public ResourceManager createSubordinate(String dirRelativePath) {
        return new ResourceManager(getDesignerRoot(), myManagedDir.resolve(dirRelativePath));
    }

    /**
     * Creates a manager that manages the same directory.
     */
    public ResourceManager createAlias() {
        return new ResourceManager(getDesignerRoot(), myManagedDir);
    }


    /**
     * Creates a manager that manages a the same directory.
     */
    public ResourceManager close() {

        return this;
    }

    /**
     * Returns an absolute path to the file addressed by the relative
     * path.
     */
    public Optional<Path> getUnpackedFile(String dirRelativePath) {
        return Optional.of(myManagedDir.resolve(dirRelativePath).toAbsolutePath()).filter(it -> Files.exists(it));
    }

    /**
     * Output a checksum for the file.
     *
     * @return true if the file was signed successfully
     */
    public boolean sign(Path file) {
        return signOp(file, false, (target, checksumPath) -> {
            long checksum;
            try {
                checksum = FileUtils.checksumCRC32(target.toFile());
                Files.deleteIfExists(checksumPath);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(checksumPath))) {
                dos.writeLong(checksum);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return true;
        });
    }

    /**
     * Checks whether the file matches its recorded checksum. Returns false if the
     * file has no recorded checksum. Use {@link #sign(Path)} to record a checksum.
     */
    public boolean isUpToDate(Path path) {
        return signOp(path, false, (target, checksumPath) -> {
            if (!Files.exists(checksumPath)) {
                return false;
            }


            long cachedChecksum;
            long actualChecksum;
            try (DataInputStream is = new DataInputStream(Files.newInputStream(checksumPath))) {
                cachedChecksum = is.readLong();
                actualChecksum = FileUtils.checksumCRC32(target.toFile());
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return cachedChecksum == actualChecksum;
        });
    }


    private <T> T signOp(Path target, T noFile, BiFunction<Path, Path, T> fileAndChecksumConsumer) {

        if (!Files.exists(target)) {
            return noFile;
        }

        Path checksumPath = target.getParent().resolve(target.getFileName().toString() + "-checksum");

        return fileAndChecksumConsumer.apply(target, checksumPath);
    }


    @Override
    public Category getLogCategory() {
        return Category.RESOURCE_MANAGEMENT;
    }

    @Override
    public String getDebugName() {
        return "ResourceManager("
            + getService(DesignerRoot.DISK_MANAGER).getSettingsDirectory().relativize(getRootManagedDir())
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
