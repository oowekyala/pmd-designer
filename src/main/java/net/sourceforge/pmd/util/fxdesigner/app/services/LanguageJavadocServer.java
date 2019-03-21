package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FilenameUtils;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.util.JarExplorationUtil;

/**
 * @author Clément Fournier
 */
public class LanguageJavadocServer implements ApplicationComponent {

    private final Language language;
    private final DesignerRoot designerRoot;
    private Path serverPath;
    private CompletableFuture<Boolean> isReady;

    public LanguageJavadocServer(Language language,
                                 DesignerRoot designerRoot) {
        this.language = language;
        this.designerRoot = designerRoot;
        Path deployPath = getService(DesignerRoot.PERSISTENCE_MANAGER).getSettingsDirectory().resolve("javadocs");

        isReady =
            JarExplorationUtil.pathsInResource(Thread.currentThread().getContextClassLoader(), "")
                              .filter(it -> {
                                  String baseName = FilenameUtils.getName(it.toString());
                                  return baseName.startsWith("pmd-" + language.getTerseName())
                                      && baseName.endsWith("-javadoc.jar");
                              })
                              .findFirst()
                              .map(jar -> {
                                  // TODO checksum or what
                                  serverPath = deployPath.resolve(jar.getFileName());

                                  if (serverPath.resolve("timestamp").toFile().exists()) {
                                      // use existing cache
                                      return CompletableFuture.completedFuture(null);
                                  }

                                  return JarExplorationUtil.unpackAsync(jar, serverPath);
                              })
                              .map(f -> f.thenApply(nothing -> {
                                  try {
                                      // create a stamp to mark that the whole archive was unpacked
                                      serverPath.resolve("timestamp").toFile().createNewFile();
                                  } catch (IOException e) {
                                      e.printStackTrace();
                                  }
                                  System.out.println("Done with " + serverPath + "!");
                                  return true;
                              }))
                              .orElse(CompletableFuture.completedFuture(false));


    }

    public Optional<URL> docUrl(Class<? extends Node> clazz, boolean compact) {
        if (!isReady()) { // there's a missed opportunity for parallelism here
            return Optional.empty();
        } else {

            String name = clazz.getName().replace('.', '/') + ".html";

            File htmlFile = serverPath.resolve(name).toFile();

            try {
                return htmlFile.exists() ? Optional.of(htmlFile.toURI().toURL()) : Optional.empty();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }


    public boolean isReady() {
        try {
            return isReady.isDone() && isReady.get();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
