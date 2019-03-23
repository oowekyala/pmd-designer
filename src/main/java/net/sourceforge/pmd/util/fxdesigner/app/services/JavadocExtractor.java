package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

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

    private final DesignerRoot designerRoot;
    private final ResourceManager output;
    private static final Pattern EXCLUDED_FILES =
        // exclude stuff in rule packages,
        // exclude not html files
        // exclude html files that have a "-", they're index pages
        Pattern.compile(".*?lang/\\w+/rule/.*|.*-.*\\.html|(.*(?<!\\.html)$)");

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
        Elements relativeLinks = html.select("a[href]");
        // replace links to this doc with links to the compact versions
        for (Element link : relativeLinks) {
            String href = link.attr("href");
            // external links
            if (!isCompactable(href)) {
                link.unwrap();
            }
        }

        // add css
        html.head()
            .appendElement("link")
            .attr("rel", "stylesheet")
            .attr("type", "text/css")
            .attr("href", path.relativize(output.getRootManagedDir().getParent()).resolve("webview.css").toString());

        html.select("pre.grammar").before("<h4>BNF</h4>");

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)))) {
            html.html(writer);
        } catch (IOException e) {
            logInternalException(e);
        }
    }

    private static Optional<Path> compactedPath(String ref) {
        if (!isCompactable(ref)) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(Paths.get(ref)).filter(Files::exists);
        }
    }

    private static boolean isCompactable(String ref) {
        return !ref.contains("#")
            && !ref.matches("https?:.*")
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
