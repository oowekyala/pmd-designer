/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;


/**
 *
 */
public final class JarExplorationUtil {


    private JarExplorationUtil() {

    }

    private static final Object FILE_SYSTEM_LOCK = new Object();

    private static final List<String> JAR_MIMES =
        Arrays.asList("application/x-java-archive", "application/java-archive");

    /** Finds the classes in the given package by looking in the classpath directories. */
    public static Stream<Class<?>> getClassesInPackage(String packageName) {
        return pathsInResource(Thread.currentThread().getContextClassLoader(), packageName.replace('.', '/'))
            .map((Function<Path, Class<?>>) p -> toClass(p, packageName))
            .filter(Objects::nonNull);
    }

    public static Stream<Path> pathsInResource(ClassLoader classLoader,
                                               String resourcePath) {
        Stream<URL> resources;

        try {
            resources = DesignerIteratorUtil.enumerationAsStream(classLoader.getResources(resourcePath));
        } catch (IOException e) {
            return Stream.empty();
        }

        if (resourcePath.isEmpty()) {
            resources = resources.flatMap(url -> {
                if (url.toString().matches(".*META-INF/versions/\\d+/?")) {
                    try {
                        return Stream.of(url, new URL(url, "../../.."));
                    } catch (MalformedURLException ignored) {

                    }
                }
                return Stream.of(url);
            });
        }

        return resources.distinct().flatMap(resource -> {
            try {
                return getPathsInDir(resource, 1).stream();
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                return Stream.empty();
            }
        });
    }


    /** Maps paths to classes. */
    private static Class<?> toClass(Path path, String packageName) {
        return Optional.of(path)
                       .filter(p -> "class".equalsIgnoreCase(FilenameUtils.getExtension(path.toString())))
            .<Class<?>>map(p -> {
                try {
                    return Class.forName(packageName + "." + FilenameUtils.getBaseName(path.getFileName().toString()));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    return null;
                }
            })
            .orElse(null);
    }

    public static String getJarRelativePath(URI uri) {
        if ("jar".equals(uri.getScheme())) {
            // we have to cut out the path to the jar + '!'
            // to get a path that's relative to the root of the jar filesystem
            // This is equivalent to a packageName.replace('.', '/') but more reusable
            String schemeSpecific = uri.getSchemeSpecificPart();
            return schemeSpecific.substring(schemeSpecific.indexOf('!') + 1);
        } else {
            return uri.getSchemeSpecificPart();
        }
    }

    public static Path thisJarPathInHost() {
        String vdirPath;
        try {
            vdirPath = ClassLoader.getSystemClassLoader().getResource("").toURI().getSchemeSpecificPart();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }

        int jarIdx = vdirPath.indexOf('!');
        if (jarIdx >= 0) {
            // we're in a jar
            vdirPath = vdirPath.substring("file:".length(), jarIdx);
        }

        return FileSystems.getDefault().getPath(vdirPath).toAbsolutePath();
    }

    /**
     * Unpacks a [jarFile] to the given [destDir]. Only those entries matching the
     * [unpackFilter] will be unpacked.
     *
     * @param jarRelativePath  Directory in which to start unpacking. The root is "/"
     * @param maxDepth         Max depth on which to recurse
     * @param unpackFilter     Filters those files that will be expanded
     * @param postProcessing   Called for each expanded entry with the path of the
     *                         extracted file
     * @param exceptionHandler Handles exceptions
     *
     * @return A future that is done when all the jar has been processed.
     */
    public static CompletableFuture<Void> unpackAsync(Path jarFile,
                                                      Path jarRelativePath,
                                                      int maxDepth,
                                                      Path destDir,
                                                      Predicate<Path> unpackFilter,
                                                      Consumer<Path> postProcessing,
                                                      BiConsumer<Path, Throwable> exceptionHandler) {

        List<CompletableFuture<Void>> writeTasks = new ArrayList<>();
        Path walkRoot;
        FileSystem fs = null;
        try {
            URI uri = cleanupUri(jarFile);
            if ("jar".equals(uri.getScheme())) {
                fs = getFileSystem(uri);
                walkRoot = fs.getPath(jarRelativePath.toString());
            } else {
                walkRoot = jarFile;
            }

            Files.walk(walkRoot, maxDepth)
                 .filter(filePath -> !Files.isDirectory(filePath))
                 .filter(unpackFilter)
                 .forEach(filePath -> {
                     Path relativePathInZip = walkRoot.resolve("").relativize(filePath);
                     Path targetPath = destDir.resolve(relativePathInZip.toString()).toAbsolutePath();

                     writeTasks.add(
                         CompletableFuture
                             .runAsync(() -> {
                                 try {
                                     Files.createDirectories(targetPath.getParent());
                                     // And extract the file
                                     Files.copy(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                 } catch (IOException e) {
                                     exceptionHandler.accept(filePath, e);
                                 }
                             })
                             .thenRunAsync(() -> postProcessing.accept(targetPath))
                             .exceptionally(t -> {
                                 exceptionHandler.accept(filePath, t);
                                 return null;
                             })
                     );
                 });
        } catch (IOException | URISyntaxException e) {
            exceptionHandler.accept(null, e);
            return CompletableFuture.completedFuture(null);
        }

        final FileSystem finalFs = fs;

        return writeTasks.stream()
                         .reduce((a, b) -> a.thenCombine(b, (c, d) -> null))
                         .orElse(CompletableFuture.completedFuture(null))
                         .handle((nothing, throwable) -> {
                             try {
                                 if (finalFs != null) {
                                     finalFs.close();
                                 }

                             } catch (IOException e) {
                                 exceptionHandler.accept(null, e);
                             }
                             return null;
                         });
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

    private static List<Path> getPathsInDir(URL url, int maxDepth) throws URISyntaxException, IOException {

        URI uri = url.toURI().normalize();

        if ("jar".equals(uri.getScheme())) {
            // we have to do this to look inside a jar
            try (FileSystem fs = getFileSystem(uri)) {
                Path path = fs.getPath(getJarRelativePath(uri));
                while (maxDepth < 0) {
                    path = path.resolve("..");
                    maxDepth++;
                }

                return Files.walk(path, maxDepth).collect(Collectors.toList()); // buffer everything, before closing the filesystem

            }
        } else {
            Path path = toPath(url);
            while (maxDepth < 0) {
                path = path.resolve("..");
                maxDepth++;
            }
            try (Stream<Path> paths = Files.walk(path, maxDepth)) {
                return paths.collect(Collectors.toList()); // buffer everything, before closing the original stream
            }
        }
    }

    private static FileSystem getFileSystem(URI uri) throws IOException {

        synchronized (FILE_SYSTEM_LOCK) {
            try {
                return FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                return FileSystems.newFileSystem(uri, Collections.<String, String>emptyMap());
            }
        }
    }

    private static Path toPath(URL url) {
        return new File(url.getFile()).toPath();
    }


}
