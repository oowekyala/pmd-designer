package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

                                  return JarExplorationUtil.unpackAsync(jar,
                                                                        serverPath,
                                                                        LanguageJavadocServer::shouldExtract,
                                                                        LanguageJavadocServer::writeCompactJavadoc);
                              })
                              .map(f -> f.thenApplyAsync(nothing -> {
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

            String name = clazz.getName().replace('.', '/') + "";

            if (compact) {
                name = name + "-compact";
            }

            File htmlFile = serverPath.resolve(name + ".html").toFile();

            try {
                return htmlFile.exists() ? Optional.of(htmlFile.toURI().toURL()) : Optional.empty();
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }
    }

    private static boolean shouldExtract(Path path) {
        return !path.toString().matches(".*?lang/\\w+/rule/.*");
    }

    private static void writeCompactJavadoc(Path path) {
        if (!FilenameUtils.getExtension(path.toString()).equals("html")
            || Character.isLowerCase(FilenameUtils.getBaseName(path.toString()).charAt(0))
            || "class-use".equals(path.getParent().getFileName().toString())) {
            return;
        }


        Document html;
        try {
            html = Jsoup.parse(path.toFile(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        html.selectFirst("header").remove();
        html.selectFirst("footer").remove();
        html.selectFirst("div.header h2").remove();
        html.selectFirst("div.summary").remove();
        html.selectFirst("div.details").remove();
        html.select("dl").remove(); // remove stuff like inheritance hierarchy
        html.selectFirst("hr").remove();
        html.selectFirst("ul.inheritance").remove();
        Elements relativeLinks = html.select("a[href~=(?i)\\.\\..*\\.html(#.*)?]");
        // replace links to this doc with links to the compact versions
        for (Element link : relativeLinks) {
            String href = link.attr("href");
            link.attr("href", FilenameUtils.getBaseName(href) + "-compact.html");
        }

        html.head()
            .appendElement("style")
            .text("pre {\n"
                      + "  overflow-x: auto;\n"
                      + "  white-space: pre-wrap;\n"
                      + "  white-space: -moz-pre-wrap;\n"
                      + "  white-space: -pre-wrap;\n"
                      + "  white-space: -o-pre-wrap;\n"
                      + "  word-wrap: break-word;\n"
                      + "}");

        html.select("pre").attr("style", "white-space: pre-wrap;");


        String compactName = FilenameUtils.getBaseName(path.toString()) + "-compact.html";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.getParent().resolve(compactName).toFile()))) {
            html.html(writer);
        } catch (IOException e) {
            e.printStackTrace();
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
