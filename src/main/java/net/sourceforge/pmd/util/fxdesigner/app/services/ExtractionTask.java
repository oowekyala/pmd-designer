package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

import javafx.util.Pair;

/**
 * Describes a task extracting files from a jar. This should work both
 * when the app is run from a jar, or from a directory in the IDE. In the
 * latter case when the doc says eg "path relative to the jar", it mean
 * "relative to the filesystem root".
 */
public class ExtractionTask {

    private static final List<String> JAR_MIMES =
        Arrays.asList("application/x-java-archive", "application/java-archive");
    private final Path jarFile;
    private final Path jarRelativePath;
    private final Path destDir;
    private final int maxDepth;
    private final BiPredicate<Path, Path> shouldUnpack;
    private final Function<Path, Path> layoutMapper;
    private final Consumer<Path> postProcessing;
    private final BiConsumer<Path, Throwable> exceptionHandler;

    /**
     * @param jarFile          Jar to unpack
     * @param jarRelativePath  Directory in which to start unpacking. The root is "/"
     * @param maxDepth         Max depth on which to recurse
     * @param shouldUnpack     Filters those files that will be expanded
     * @param exceptionHandler Handles exceptions
     */
    private ExtractionTask(Path jarFile,
                           Path jarRelativePath,
                           Path destDir,
                           int maxDepth,
                           BiPredicate<Path, Path> shouldUnpack,
                           Function<Path, Path> layoutMapper,
                           Consumer<Path> postProcessing,
                           BiConsumer<Path, Throwable> exceptionHandler) {

        this.jarFile = Objects.requireNonNull(jarFile);
        this.jarRelativePath = Objects.requireNonNull(jarRelativePath);
        this.destDir = Objects.requireNonNull(destDir);
        this.maxDepth = maxDepth;
        this.shouldUnpack = Objects.requireNonNull(shouldUnpack);
        this.layoutMapper = Objects.requireNonNull(layoutMapper);
        this.postProcessing = Objects.requireNonNull(postProcessing);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
    }


    private boolean shouldUnpack(Path inJar, Path laidOut) {
        return shouldUnpack.test(inJar, laidOut);
    }

    public Path layout(Path targetPath) {
        return layoutMapper.apply(targetPath);
    }

    private void postProcess(Path extracted) {
        postProcessing.accept(extracted);
    }


    private void handleException(Path p, Throwable t) {
        exceptionHandler.accept(p, t);
    }

    /**
     * Unpacks a [jarFile] to the given [destDir]. Only those entries matching the
     * [unpackFilter] will be unpacked. Each entry is unpacked in parallel. Some
     * additional post processing can be done in parallel.
     *
     * @return A future that is done when all the jar has been processed (including post-processing).
     */
    private CompletableFuture<Void> exec() {

        // list of tasks that normally yield a path to an extracted file
        List<CompletableFuture<Path>> extractionTasks = new ArrayList<>();
        Path walkRoot;
        FileSystem fs = null;
        try {
            URI uri = cleanupUri(jarFile);
            if ("jar".equals(uri.getScheme())) {
                fs = ResourceUtil.getFileSystem(uri);
                walkRoot = fs.getPath(jarRelativePath.toString());
            } else {

                if (jarRelativePath.getRoot() != null) {
                    // remove root
                    walkRoot = jarFile.resolve(jarRelativePath.toString().substring(1));
                } else {
                    walkRoot = jarFile.resolve(jarRelativePath);
                }
            }

            Files.walk(walkRoot, maxDepth)
                 .filter(filePath -> !Files.isDirectory(filePath))
                 .map(filePath -> { // filePath is relative to the file system

                     Path relativePathInZip = walkRoot.relativize(filePath);
                     Path targetPath = layout(destDir.resolve(relativePathInZip.toString()).toAbsolutePath());
                     if (!shouldUnpack(relativePathInZip, targetPath)) {
                         return null;
                     } else {
                         return new Pair<>(filePath, targetPath);
                     }
                 })
                 .filter(Objects::nonNull)
                 .forEach(bothPaths -> {

                     Path filePath = bothPaths.getKey();
                     Path targetPath = bothPaths.getValue();
                     try {
                         Files.createDirectories(targetPath.getParent());
                         // And extract the file
                         Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                     } catch (IOException e) {
                         handleException(filePath, e);
                     }

                     extractionTasks.add(CompletableFuture.completedFuture(targetPath));
                 });
        } catch (IOException | URISyntaxException e) {
            handleException(null, e);
            return CompletableFuture.completedFuture(null);
        }

        final FileSystem finalFs = fs;

        // close file system in all cases
        allOf(extractionTasks).handle((nothing, throwable) -> {
            try {
                if (finalFs != null) {
                    finalFs.close();
                }
            } catch (IOException e) {
                handleException(null, e);
            }
            return null;
        });

        // return a task that completes when every post-processing is done
        return allOf(extractionTasks.stream()
                                    .map(extraction -> extraction.thenAccept(f -> {
                                        try {
                                            postProcess(f);
                                        } catch (Exception e) {
                                            handleException(f, e);
                                        }
                                    }))
                                    .collect(Collectors.toList()))
            .exceptionally(e -> {
                exceptionHandler.accept(null, e);
                return null;
            });

    }

    static ExtractionTaskBuilder newBuilder(Path jarFile, Path destDir) {
        return new ExtractionTaskBuilder(jarFile, destDir);
    }

    private static CompletableFuture<Void> allOf(List<? extends CompletableFuture<?>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static URI cleanupUri(Path jarFile) throws IOException, URISyntaxException {
        final URI uri = jarFile.toUri();
        if (!"jar".equals(uri.getScheme()) && JAR_MIMES.contains(Files.probeContentType(jarFile))) {
            if (uri.getScheme() == null) {
                return new URI("jar:file://" + uri.toString());
            } else {
                return new URI("jar:" + uri.toString());
            }
        }
        return uri;
    }

    /**
     * Builder.
     */
    public static final class ExtractionTaskBuilder {

        private final Path jarFile;
        private final Path destDir;
        private Path jarRelativePath = Paths.get("/");
        private int maxDepth = Integer.MAX_VALUE;
        private BiPredicate<Path, Path> shouldUnpack = (p, q) -> true;
        private Function<Path, Path> layoutMapper = Function.identity();
        private Consumer<Path> postProcessing = p -> {};
        private BiConsumer<Path, Throwable> exceptionHandler = (p, t) -> t.printStackTrace();
        private Supplier<Boolean> doExtraction = () -> true;

        private ExtractionTaskBuilder(Path jarFile, Path destDir) {
            this.jarFile = jarFile;
            this.destDir = destDir;
        }


        public ExtractionTaskBuilder jarRelativePath(Path jarRelativePath) {
            this.jarRelativePath = jarRelativePath;
            return this;
        }

        ExtractionTaskBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * A predicate testing the relative path in the Jar for whether it should
         * be extracted or not. First arg is the path relative to the root of the
         * walk. Second arg is the absolute path laid out by the {@link #layoutMapper(Function)}.
         */
        ExtractionTaskBuilder shouldUnpack(BiPredicate<Path, Path> shouldUnpack) {
            this.shouldUnpack = shouldUnpack;
            return this;
        }

        /**
         * @param layoutMapper Maps paths relative to the {@link #jarRelativePath(Path)}
         *                     to the location they should be extracted in. Using {@link Function#identity()}
         *                     preserves the jar structure entirely. The input path is absolute.
         */
        ExtractionTaskBuilder layoutMapper(Function<Path, Path> layoutMapper) {
            this.layoutMapper = layoutMapper;
            return this;
        }

        ExtractionTaskBuilder simpleRename(Function<String, String> renaming) {
            this.layoutMapper = path -> path.getParent().resolve(renaming.apply(FilenameUtils.getName(path.toString())));
            return this;
        }

        /**
         * @param postProcessing Called for each expanded entry with the
         *                       path of the extracted file
         */
        ExtractionTaskBuilder postProcessing(Consumer<Path> postProcessing) {
            this.postProcessing = this.postProcessing.andThen(postProcessing);
            return this;
        }

        ExtractionTaskBuilder exceptionHandler(BiConsumer<Path, Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        /**
         * If the predicate is not true at the time {@link #extract()} is called,
         * no extraction is performed. This method performs no extraction.
         */
        ExtractionTaskBuilder extractUnless(Supplier<Boolean> guard) {
            this.doExtraction = () -> !guard.get();
            return this;
        }


        private ExtractionTask createExtractionTask() {
            return new ExtractionTask(jarFile,
                                      jarRelativePath,
                                      destDir,
                                      maxDepth,
                                      shouldUnpack,
                                      layoutMapper,
                                      postProcessing,
                                      exceptionHandler);
        }

        /**
         * Unpacks a [jarFile] to the given [destDir]. Only those entries matching the
         * [unpackFilter] will be unpacked. Each entry is unpacked in parallel. Some
         * additional post processing can be done in parallel.
         *
         * @return A future that is done when all the jar has been processed (including post-processing).
         * Never completes exceptionally, but errors are logged using the {@link #exceptionHandler(BiConsumer)}.
         */
        public CompletableFuture<Void> extract() {
            if (doExtraction.get()) {
                return createExtractionTask().exec();
            }
            return CompletableFuture.completedFuture(null);
        }
    }
}
