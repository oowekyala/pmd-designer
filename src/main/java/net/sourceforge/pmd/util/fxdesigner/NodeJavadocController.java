/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import java.util.Optional;

import org.reactfx.EventStreams;
import org.reactfx.value.Val;
import org.w3c.dom.Document;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.util.DataHolder;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.StageBuilder;

import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * The javadoc pane.
 *
 * @author Clément Fournier
 */
public class NodeJavadocController extends AbstractController implements NodeSelectionSource {

    private final Stage myStage;
    @FXML
    private WebView webView;

    protected NodeJavadocController(DesignerRoot root) {
        super(root);

        myStage = new StageBuilder().withOwner(getMainStage())
                                    .withModality(Modality.NONE)
                                    .withStyle(StageStyle.DECORATED)
                                    .withFxml(DesignerUtil.getFxml("node-javadoc.fxml"), this)
                                    .withTitle("Node Javadoc")
                                    .newStage();
    }

    @Override
    protected void beforeParentInit() {
        webView.getEngine().loadContent("<html> <body>Nothing to display</body></html>"); // empty
        webView.getEngine().setOnAlert(str -> System.out.println(str.getData()));
        webView.getEngine().setOnError(str -> str.getException().printStackTrace());
        initNodeSelectionHandling(getDesignerRoot(), EventStreams.never(), false);
    }

    public void showYourself() {
        myStage.show();
        getDesignerRoot().getService(DesignerRoot.NODE_SELECTION_CHANNEL)
                         .latestMessage()
                         .getOpt()
                         .ifPresent(nse -> setFocusNode(nse.selected, nse.options));

    }

    @Override
    public void setFocusNode(Node node, DataHolder options) {
        if (!myStage.isShowing()) {
            return;
        }
        if (node == null) {
            webView.getEngine().loadContent("<html> <body>Nothing to display</body></html>"); // empty
            return;
        }

        Optional.ofNullable(getGlobalLanguageVersion())
                .map(LanguageVersion::getLanguage)
                .flatMap(l -> getService(DesignerRoot.JAVADOC_SERVER).forLanguage(l))
                .flatMap(server -> server.docUrl(node.getClass(), true))
                .filter(url -> !url.toString().equals(webView.getEngine().getLocation()))
                .ifPresent(url -> {
                    webView.getEngine().load(url.toString());
                    Val.wrap(webView.getEngine().getLoadWorker().stateProperty())
                       .values()
                       .filter(it -> it == State.SUCCEEDED)
                       .subscribeForOne(state -> {
                           Document document = webView.getEngine().getDocument();
                           org.w3c.dom.Node header = document.getElementsByTagName("header").item(0);
                           if (header != null)
                           header.getParentNode().removeChild(header);
                       });
                });
    }

}
