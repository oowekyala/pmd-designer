/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.util.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.controls.MutableTabPane;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;


/**
 * Controller for all XPath panels. An XPath panel is one of two similar types:
 * * Scratchpad editor: synchronises its language version with the current language version of the app.
 * * Rule editor: bound to a specific language version range and disabled when the editor's current version
 * is unsupported. Also supports editing more metadata.
 *
 * @author Clément Fournier
 */
public class XpathManagerController extends AbstractController {
    private final DesignerRoot designerRoot;
    private final MainDesignerController mediator;

    @FXML
    private MutableTabPane<XPathPanelController> xpathEditorsTabPane;

    private Val<ObservableList<Node>> currentXPathResults;


    public XpathManagerController(DesignerRoot designerRoot, MainDesignerController parent) {
        this.designerRoot = designerRoot;
        this.mediator = parent;
    }


    @Override
    protected void beforeParentInit() {

        xpathEditorsTabPane.setControllerSupplier(() -> new XPathPanelController(designerRoot, this));

        currentXPathResults = selectedEditorProperty().flatMap(XPathPanelController::xpathResultsProperty);


        xpathEditorsTabPane.currentFocusedController()
                           .changes()
                           .subscribe(ch -> {
                               // only the results of the currently opened tab are displayed
                               mediator.resetXPathResults();
                               if (ch.getNewValue() != null) {
                                   refreshCurrentXPath(ch.getNewValue());
                               }
                           });

    }


    @Override
    protected void afterParentInit() {
        Platform.runLater(xpathEditorsTabPane::addTabWithNewController);
    }


    TextAwareNodeWrapper wrapNode(Node node) {
        return mediator.wrapNode(node);
    }


    void onNodeItemSelected(Node n) {
        mediator.onNodeItemSelected(n);
    }


    /**
     * Called by the main controller.
     */
    public void refreshXPath() {
        selectedEditorProperty().ifPresent(this::refreshCurrentXPath);
    }


    /**
     * Called by the children XPath editors with themselves as parameter.
     */
    void refreshCurrentXPath(XPathPanelController editor) {

        if (editor.equals(selectedEditorProperty().getValue())) {
            mediator.getCompilationUnit()
                    .map(r -> editor.evaluateXPath(r, mediator.getLanguageVersion()))
                    .ifPresent(designerRoot.getLogger()::logEvent);
        }
    }


    void resetXpathResultsInSourceEditor() {
        mediator.resetXPathResults();
    }


    public Val<LanguageVersion> globalLanguageVersionProperty() {
        return mediator.languageVersionProperty();
    }


    private Val<XPathPanelController> selectedEditorProperty() {
        return xpathEditorsTabPane.currentFocusedController();
    }


    public void invalidateResults(boolean error) {
        selectedEditorProperty().ifPresent(c -> c.invalidateResults(error));
    }
}
