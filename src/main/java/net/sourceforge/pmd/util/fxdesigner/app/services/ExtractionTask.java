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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;

/**
 * Describes a task extracting files from a jar. This should work both when
 * the app is run from a jar, or from a directory in the IDE.
 */
public class ExtractionTask {

    private static final List<String> JAR_MIMES =
        Arrays.asList("application/x-java-archive", "application/java-archive");
    private final Path jarFile;
    private final Path jarRelativePath;
    private final Path destDir;
    private final int maxDepth;
    private final Predicate<Path> shouldUnpack;
    private final Function<Path, Path> layoutMapper;
    private final Consumer<Path> postProcessing;
    private final BiConsumer<Path, Throwable> exceptionHandler;

    /**
     * @param jarFile          Jar to unpack
     * @param jarRelativePath  Directory in which to start unpacking. The root is "/"
     * @param maxDepth         Max depth on which to recurse
     * @param shouldUnpack     Filters those files that will be expanded
     * @param layoutMapper     Maps the resolved output path (identical to the layout in the jar)
     *                         to the extracted location. Using {@link Function#identity()} preserves
     *                         the extracted structure entirely. The input path is absolute.
     * @param postProcessing   Called for each expanded entry with the path of the
     *                         extracted file
     * @param exceptionHandler Handles exceptions
     */
    private ExtractionTask(Path jarFile,
                           Path jarRelativePath,
                           Path destDir,
                           int maxDepth,
                           Predicate<Path> shouldUnpack,
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


    public Path getJarFile() {
        return jarFile;
    }

    public Path getJarRelativePath() {
        return jarRelativePath;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public boolean shouldUnpack(Path inJar) {
        return shouldUnpack.test(inJar);
    }

    public Path getDestDir() {
        return destDir;
    }

    public Path layout(Path targetPath) {
        return layoutMapper.apply(targetPath);
    }

    public void postProcess(Path extracted) {
        postProcessing.accept(extracted);
    }


    public void handleException(Path p, Throwable t) {
        exceptionHandler.accept(p, t);
    }

    /**
     * Unpacks a [jarFile] to the given [destDir]. Only those entries matching the
     * [unpackFilter] will be unpacked. Each entry is unpacked in parallel. Some
     * additional post processing can be done in parallel.
     *
     * @return A future that is done when all the jar has been processed (including post-processing).
     */
    private CompletableFuture<Void> execAsync() {

        // list of tasks that normally yield a path to an extracted file
        List<CompletableFuture<Path>> extractionTasks = new ArrayList<>();
        Path walkRoot;
        FileSystem fs = null;
        try {
            URI uri = cleanupUri(getJarFile());
            if ("jar".equals(uri.getScheme())) {
                fs = ResourceUtil.getFileSystem(uri);
                walkRoot = fs.getPath(getJarRelativePath().toString());
            } else {
                walkRoot = getJarFile();
            }

            Files.walk(walkRoot, getMaxDepth())
                 .filter(filePath -> !Files.isDirectory(filePath))
                 .filter(this::shouldUnpack)
                 .forEach(filePath -> {
                     Path relativePathInZip = walkRoot.resolve("").relativize(filePath);
                     Path targetPath = layout(getDestDir().resolve(relativePathInZip.toString()).toAbsolutePath());

                     extractionTasks.add(
                         CompletableFuture
                             .supplyAsync(() -> {
                                 try {
                                     Files.createDirectories(targetPath.getParent());
                                     // And extract the file
                                     Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                 } catch (IOException e) {
                                     handleException(filePath, e);
                                 }

                                 return targetPath;
                             })
                             .exceptionally(t -> {
                                 handleException(relativePathInZip, t);
                                 return null;
                             })
                     );
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
                                    .collect(Collectors.toList()));

    }

    public static ExtractionTaskBuilder newBuilder(Path jarFile, Path destDir) {
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
        private Predicate<Path> shouldUnpack = p -> true;
        private Function<Path, Path> layoutMapper = Function.identity();
        private Consumer<Path> postProcessing = p -> {};
        private BiConsumer<Path, Throwable> exceptionHandler = (p, t) -> t.printStackTrace();
        private Supplier<Boolean> shouldRun = () -> true;

        private ExtractionTaskBuilder(Path jarFile, Path destDir) {
            this.jarFile = jarFile;
            this.destDir = destDir;
        }


        public ExtractionTaskBuilder jarRelativePath(Path jarRelativePath) {
            this.jarRelativePath = jarRelativePath;
            return this;
        }

        public ExtractionTaskBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public ExtractionTaskBuilder shouldUnpack(Predicate<Path> shouldUnpack) {
            this.shouldUnpack = shouldUnpack;
            return this;
        }

        public ExtractionTaskBuilder layoutMapper(Function<Path, Path> layoutMapper) {
            this.layoutMapper = layoutMapper;
            return this;
        }

        public ExtractionTaskBuilder simpleRename(Function<String, String> renaming) {
            this.layoutMapper = path -> path.getParent().resolve(renaming.apply(FilenameUtils.getName(path.toString())));
            return this;
        }

        public ExtractionTaskBuilder postProcessing(Consumer<Path> postProcessing) {
            this.postProcessing = this.postProcessing.andThen(postProcessing);
            return this;
        }

        public ExtractionTaskBuilder exceptionHandler(BiConsumer<Path, Throwable> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public ExtractionTaskBuilder runUnless(Supplier<Boolean> shouldRun) {
            this.shouldRun = shouldRun;
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
         */
        public CompletableFuture<Void> extract() {
            if (!shouldRun.get()) {
                return CompletableFuture.completedFuture(null);
            }

            return createExtractionTask().execAsync();
        }
    }
}
