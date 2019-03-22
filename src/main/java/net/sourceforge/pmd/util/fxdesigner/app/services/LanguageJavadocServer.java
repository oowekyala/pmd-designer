package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private Path explodedJarDir;
    private CompletableFuture<Boolean> isReady;

    public LanguageJavadocServer(Language language,
                                 DesignerRoot designerRoot) {
        this.language = language;
        this.designerRoot = designerRoot;

        isReady = CompletableFuture.supplyAsync(this::getJavadocJar)
                                   .thenCompose(this::unpackFuture)
                                   .thenRunAsync(this::stampCompletion)
                                   .handle((nothing, error) -> true);


    }

    private Path getJavadocJar() {

        return JarExplorationUtil.pathsInResource(Thread.currentThread().getContextClassLoader(), "")
                                 .filter(it -> {
                                     String baseName = FilenameUtils.getName(it.toString());
                                     return baseName.startsWith(
                                         "pmd-" + language.getTerseName())
                                         && baseName.endsWith("-javadoc.jar");
                                 })
                                 .findFirst()
                                 .orElse(null);

    }

    private void stampCompletion() {
        try {
            // create a stamp to mark that the whole archive was unpacked
            explodedJarDir.resolve("timestamp").toFile().createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done with " + explodedJarDir + "!");
    }

    private CompletableFuture<Void> unpackFuture(Path javadocJar) {
        Path deployPath = getService(DesignerRoot.PERSISTENCE_MANAGER).getSettingsDirectory().resolve("javadocs");

        // TODO checksum or what
        explodedJarDir = deployPath.resolve(javadocJar.getFileName());

        if (explodedJarDir.resolve("timestamp").toFile().exists()) {
            // use existing cache
            return CompletableFuture.completedFuture(null);
        }

        return JarExplorationUtil.unpackAsync(javadocJar,
                                              explodedJarDir,
                                              LanguageJavadocServer::shouldExtract,
                                              LanguageJavadocServer::writeCompactJavadoc);
    }

    public Optional<URL> docUrl(Class<? extends Node> clazz, boolean compact) {
        if (!isReady()) { // there's a missed opportunity for parallelism here
            return Optional.empty();
        } else {

            String name = clazz.getName().replace('.', '/') + "";

            if (compact) {
                name = name + "-compact";
            }

            File htmlFile = explodedJarDir.resolve(name + ".html").toFile();

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

    private static Optional<Path> compactedPath(String ref, boolean absolute) {
        if (!isCompactable(ref)) {
            return Optional.empty();
        } else {
            String compactName = FilenameUtils.getPath(ref) + FilenameUtils.getBaseName(ref) + "-compact.html";

            Path result = absolute ? FileSystems.getDefault().getPath("").resolve(compactName) : Paths.get(compactName);
            return Optional.ofNullable(result);
        }
    }

    private static boolean isCompactable(String ref) {
        return !ref.contains("#")
            && FilenameUtils.getExtension(ref).equals("html")
            && Character.isUpperCase(FilenameUtils.getBaseName(ref).charAt(0))
            || "class-use".equals(Paths.get(ref).getParent().getFileName().toString());
    }

    private static void writeCompactJavadoc(Path path) {

        if (!isCompactable(path.toString())) {
            // not compactable
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
        html.select("div.summary").remove();
        html.select("div.details").remove();
        html.select("dl").remove(); // remove stuff like inheritance hierarchy
        html.select("hr").remove();
        html.select("ul.inheritance").remove();
        // looks like a relative link
        Elements relativeLinks = html.select("a[href~=(?i)\\.\\..*]");
        // replace links to this doc with links to the compact versions
        for (Element link : relativeLinks) {
            String href = link.attr("href");
            Optional<Path> refPath = compactedPath(href, false);
            if (refPath.isPresent()) {
                link.attr("href", refPath.get().toString());
            } else {
                link.unwrap();
            }
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

        Optional<Path> compactedPath = compactedPath(path.toString(), true);

        try (BufferedWriter writer =
                 new BufferedWriter(new FileWriter(path.getParent().resolve(compactedPath.get().getFileName().toString()).toFile()))) {
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
