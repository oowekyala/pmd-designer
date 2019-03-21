/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.util.DataHolder;

import javafx.fxml.FXML;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

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
    }

    @Override
    protected void beforeParentInit() {



    }


    @Override
    public void setFocusNode(Node node, DataHolder options) {

    }
}
