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
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;

/**
 * @author Clément Fournier
 */
public class LanguageJavadocServer implements ApplicationComponent {

    private static final String RELATIVE_LINK_SELECTOR = "a[href~=(?i)\\.\\..*]";
    private final Language language;
    private final DesignerRoot designerRoot;
    private final ResourceManager resourceManager;
    private CompletableFuture<Boolean> isReady;

    public LanguageJavadocServer(Language language,
                                 DesignerRoot designerRoot,
                                 Path javadocJar,
                                 ResourceManager resourceManager) {
        this.language = language;
        this.designerRoot = designerRoot;
        this.resourceManager = resourceManager;

        isReady = resourceManager.unpackJar(javadocJar,
                                            Paths.get("/"),
                                            Integer.MAX_VALUE,
                                            LanguageJavadocServer::shouldExtract,
                                            s -> s,
                                            this::postProcess)
                                 .handle((r, t) -> {
                                     // finalize the resource manager
                                     r.markUptodate();
                                     return true;
                                 });


    }

    public Optional<URL> docUrl(Class<? extends Node> clazz, boolean compact) {
        if (!isReady()) { // there's a missed opportunity for parallelism here
            return Optional.empty();
        } else {

            String name = clazz.getName().replace('.', '/') + "";

            if (compact) {
                name = name + "-compact";
            }

            File htmlFile = resourceManager.getRootManagedDir().resolve(name + ".html").toFile();

            try {
                return htmlFile.exists() ? Optional.of(htmlFile.toURI().toURL()) : Optional.empty();
            } catch (MalformedURLException e) {
                logInternalException(e);
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

    private void postProcess(Path path) {

        if (!isCompactable(path.toString())) {
            // not compactable
            return;
        }
        Document html;
        try {
            html = Jsoup.parse(path.toFile(), "UTF-8");
            path.toFile().delete();
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
        Elements relativeLinks = html.select(RELATIVE_LINK_SELECTOR);
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

        // external links
        html.select("a[href]").not(RELATIVE_LINK_SELECTOR)
            .forEach(org.jsoup.nodes.Node::unwrap);


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
            logInternalException(e);
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

    @Override
    public String toString() {
        return getDebugName();
    }

    @Override
    public Category getLogCategory() {
        return Category.JAVADOC_SERVER;
    }

    @Override
    public String getDebugName() {
        return "LanguageJavadocServer(" + language.getTerseName() + ")";
    }
}
