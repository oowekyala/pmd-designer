/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FilenameUtils;


/**
 *
 */
public final class JarExplorationUtil {

    // TODO move to some global Util


    private JarExplorationUtil() {

    }

    public static Stream<Path> pathsInResource(ClassLoader classLoader,
                                               String resourcePath) {
        Stream<URL> resources;

        try {
            resources = enumerationAsStream(classLoader.getResources(resourcePath));
        } catch (IOException e) {
            return Stream.empty();
        }

        return resources.flatMap(resource -> {
            try {
                return getPathsInDir(resource).stream();
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                return Stream.empty();
            }
        });
    }

    /** Finds the classes in the given package by looking in the classpath directories. */
    public static Stream<Class<?>> getClassesInPackage(String packageName) {
        return pathsInResource(Thread.currentThread().getContextClassLoader(), packageName.replace('.', '/'))
            .map((Function<Path, Class<?>>) p -> toClass(p, packageName))
            .filter(Objects::nonNull);
    }

    public static CompletableFuture<Void> unpackAsync(Path jarFile, Path destDir) {
        return unpackAsync(jarFile, destDir, p -> true, file -> {});
    }

    public static CompletableFuture<Void> unpackAsync(Path jarFile,
                                                      Path destDir,
                                                      Predicate<Path> unpackFilter,
                                                      Consumer<Path> additionalStages) {
        JarFile prejar;
        try {
            prejar = new JarFile(jarFile.toFile());
        } catch (IOException e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
        final JarFile jar = prejar;
        Enumeration enumEntries = jar.entries();

        List<CompletableFuture<Void>> writeTasks = new ArrayList<>();

        while (enumEntries.hasMoreElements()) {
            JarEntry file = (JarEntry) enumEntries.nextElement();

            Path path = destDir.resolve(file.getName());

            if (file.isDirectory() || !unpackFilter.test(path)) {
                // if its a directory, it will be created anyway
                continue;
            }

            path.getParent().toFile().mkdirs();


            writeTasks.add(CompletableFuture.runAsync(() -> {
                try {
                    try (InputStream is = jar.getInputStream(file);
                         FileOutputStream fos = new FileOutputStream(path.toFile())) {

                        while (is.available() > 0) {  // write contents of 'is' to 'fos'
                            fos.write(is.read());
                        }
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }).thenRunAsync(() -> additionalStages.accept(path)));
        }
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable countTask = () -> {
            System.out.println(writeTasks.stream().filter(CompletableFuture::isDone).count());
        };
        scheduler.scheduleAtFixedRate(countTask, 0, 1000, TimeUnit.MILLISECONDS);

        return writeTasks.stream()
                         .reduce((a, b) -> a.thenCombine(b, (c, d) -> null))
                         .orElse(CompletableFuture.completedFuture(null))
                         .handle((nothing, throwable) -> {
                             try {
                                 // todo should close when the files are extracted, not when the
                                 // user tasks are done
                                 jar.close();
                                 scheduler.shutdown();
                             } catch (IOException e) {
                                 e.printStackTrace();
                             }
                             return null;
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


    private static List<Path> getPathsInDir(URL url) throws URISyntaxException, IOException {

        URI uri = url.toURI();

        if ("jar".equals(uri.getScheme())) {
            // we have to do this to look inside a jar
            try (FileSystem fs = getFileSystem(uri)) {
                // we have to cut out the path to the jar + '!'
                // to get a path that's relative to the root of the jar filesystem
                // This is equivalent to a packageName.replace('.', '/') but more reusable
                String schemeSpecific = uri.getSchemeSpecificPart();
                String fsRelativePath = schemeSpecific.substring(schemeSpecific.indexOf('!') + 1);
                return Files.walk(fs.getPath(fsRelativePath), 1)
                            .collect(Collectors.toList()); // buffer everything, before closing the filesystem

            }
        } else {
            try (Stream<Path> paths = Files.walk(new File(url.getFile()).toPath(), 1)) {
                return paths.collect(Collectors.toList()); // buffer everything, before closing the original stream
            }
        }
    }


    private static FileSystem getFileSystem(URI uri) throws IOException {

        try {
            return FileSystems.getFileSystem(uri);
        } catch (FileSystemNotFoundException e) {
            return FileSystems.newFileSystem(uri, Collections.<String, String>emptyMap());
        }
    }


    // TODO move to IteratorUtil
    private static <T> Stream<T> enumerationAsStream(Enumeration<T> e) {
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(
                new Iterator<T>() {
                    @Override
                    public T next() {
                        return e.nextElement();
                    }


                    @Override
                    public boolean hasNext() {
                        return e.hasMoreElements();
                    }
                },
                Spliterator.ORDERED), false);
    }


}
