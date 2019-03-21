/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import java.util.Optional;

import org.reactfx.EventStreams;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.util.DataHolder;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.StageBuilder;

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

    @FXML
    private WebView webView;

    private final Stage myStage;

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

        initNodeSelectionHandling(getDesignerRoot(), EventStreams.never(), false);
    }

    public void showYourself() {
        myStage.show();
    }

    @Override
    public void setFocusNode(Node node, DataHolder options) {

        if (node == null) {
            webView.getEngine().loadContent("<html> <body>Nothing to display</body></html>"); // empty
            return;
        }

        Optional.ofNullable(getGlobalLanguageVersion())
                .map(LanguageVersion::getLanguage)
                .flatMap(l -> getService(DesignerRoot.JAVADOC_SERVER).forLanguage(l))
                .flatMap(server -> server.docUrl(node.getClass()))
                .ifPresent(url -> webView.getEngine().load(url.toString()));


    }
}
