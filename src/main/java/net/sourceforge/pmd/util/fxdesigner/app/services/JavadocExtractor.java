package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;

/**
 * @author Clément Fournier
 */
public class JavadocExtractor implements ApplicationComponent {

    private static final String RELATIVE_LINK_SELECTOR = "a[href~=(?i)\\.\\..*]";
    private final DesignerRoot designerRoot;
    private final ResourceManager output;
    private static final Pattern EXCLUDED_FILES = Pattern.compile(".*?lang/\\w+/rule/.*|.*-.*.html");

    public JavadocExtractor(DesignerRoot designerRoot,
                            ResourceManager output) {

        this.designerRoot = designerRoot;
        this.output = output;


    }

    public void extractJar(Path jar) {
        output.jarExtraction(jar)
              .shouldUnpack(JavadocExtractor::shouldExtract)
              .postProcessing(this::postProcess)
              .extractAsync()
              .whenComplete((nothing, t) -> logInternalDebugInfo(() -> "Done loading javadoc", () -> ""))
              .thenRun(() -> {
                  try {
                      Files.deleteIfExists(jar);
                  } catch (IOException e) {
                      logInternalException(e);
                  }
              });
    }


    private void postProcess(Path path) {

        if (!isCompactable(path.toString())) {
            // not compactable
            return;
        }
        Document html;
        try {
            html = Jsoup.parse(path.toFile(), "UTF-8");
            // delete old file
            path.toFile().delete();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // cleanup file

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

        // add css
        html.head()
            .appendElement("link")
            .attr("rel", "stylesheet")
            .attr("type", "text/css")
            .attr("href", path.relativize(output.getRootManagedDir().getParent()).resolve("webview.css").toString());

        html.select("pre.grammar")
            .wrap("<div class='grammar-popup'></div>")
            .stream()
            .map(Element::parent)
            .collect(Collectors.toCollection(Elements::new))
            .wrap("<div class='grammar-popup-anchor'></div>")
            .stream()
            .map(Element::parent)
            .collect(Collectors.toCollection(Elements::new))
            .prepend("See BNF...");

        Optional<Path> compactedPath = compactedPath(path.toString(), true);

        try (BufferedWriter writer =
                 new BufferedWriter(new FileWriter(path.getParent().resolve(compactedPath.get().getFileName().toString()).toFile()))) {
            html.html(writer);
        } catch (IOException e) {
            logInternalException(e);
        }
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
            && Character.isUpperCase(FilenameUtils.getBaseName(ref).charAt(0));
    }

    private static boolean shouldExtract(Path injar, Path laidout) {
        return !EXCLUDED_FILES.matcher(injar.toString()).matches();
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
        return "Javadoc extractor";
    }
}
