/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import org.jsoup.Jsoup;
import org.reactfx.EventStreams;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.util.DataHolder;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * The javadoc pane.
 *
 * @author Clément Fournier
 */
public class NodeJavadocController extends AbstractController implements NodeSelectionSource {

    @FXML
    private WebView webView;

    protected NodeJavadocController(DesignerRoot root) {
        super(root);
// popup
//        new StageBuilder().withOwner(getMainStage())
//                                    .withModality(Modality.NONE)
//                                    .withStyle(StageStyle.DECORATED)
//                                    .withFxml(DesignerUtil.getFxml("node-javadoc.fxml"), this)
//                                    .withTitle("Node Javadoc")
//                                    .newStage();
    }

    @Override
    protected void beforeParentInit() {
        webView.getEngine().loadContent(noSelectionHtml()); // empty
        webView.getEngine().setOnAlert(str -> System.out.println(str.getData()));
        webView.getEngine().setOnError(str -> str.getException().printStackTrace());
        initNodeSelectionHandling(getDesignerRoot(), EventStreams.never(), false);

        webView.getEngine().setCreatePopupHandler(popupFeatures -> {
            System.out.println("fofo");
            Stage stage = new Stage(StageStyle.UTILITY);
            WebView wv2 = new WebView();
            stage.setScene(new Scene(wv2));
            stage.show();
            return wv2.getEngine();
        });

    }

    private String noSelectionHtml() {
        return Jsoup.parse("<html> </html>")
                    .body()
                    .text("Select a node to display its Javadoc")
                    .ownerDocument()
                    .wholeText();
    }

    @Override
    public void setFocusNode(Node node, DataHolder options) {

        if (node == null) {
            webView.getEngine().loadContent(noSelectionHtml()); // empty
            return;
        }

        getService(DesignerRoot.JAVADOC_SERVER)
            .docUrl(node.getClass())
            .filter(url -> !url.toString().equals(webView.getEngine().getLocation()))
            .ifPresent(url -> webView.getEngine().load(url.toString()));
    }

}
