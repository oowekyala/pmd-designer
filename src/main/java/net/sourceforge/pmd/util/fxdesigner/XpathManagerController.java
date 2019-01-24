/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.model.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.util.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.controls.MutableTabPane;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;


/**
 * Controller for all XPath editor. An XPath panel can be in one of two "states":
 * * Scratchpad: synchronises its language version with the global version of the app.
 * As soon as the user sets a version range themselves with the export wizard, or if
 * the rule was loaded from the rule picker, the language version is locked.
 * * Rule editor: bound to a specific language version range and disabled when the
 * editor's current version is unsupported.
 *
 * @author Clément Fournier
 */
public class XpathManagerController extends AbstractController {
    private final DesignerRoot designerRoot;
    private final MainDesignerController mediator;

    @FXML
    private MutableTabPane<XPathPanelController> xpathEditorsTabPane;


    public XpathManagerController(DesignerRoot designerRoot, MainDesignerController parent) {
        this.designerRoot = designerRoot;
        this.mediator = parent;
    }


    @Override
    protected void beforeParentInit() {

        xpathEditorsTabPane.setControllerSupplier(() -> new XPathPanelController(designerRoot, this));


        selectedEditorProperty().changes()
                                .subscribe(ch -> {
                                    // only the results of the currently opened tab are displayed
                                    mediator.resetXPathResults();
                                    if (ch.getNewValue() != null) {
                                        refreshCurrentXPath(ch.getNewValue());
                                    }
                                });

        Val<ObservableList<Node>> currentXPathResults = selectedEditorProperty().flatMap(XPathPanelController::xpathResultsProperty);

        currentXPathResults.changes()
                           .subscribe(ch -> {
                               if (ch.getNewValue() != null) {
                                   mediator.highlightXPathResults(ch.getNewValue());
                               }
                           });


    }


    @Override
    public void afterParentInit() {
        Platform.runLater(xpathEditorsTabPane::addTabWithNewController);
        xpathEditorsTabPane.getControllers().forEach(XPathPanelController::afterParentInit);
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
                    .ifPresent(event -> {
                        designerRoot.getLogger().logEvent(event);
                        if (event.getCategory() == Category.XPATH_EVALUATION_EXCEPTION) {
                            mediator.resetXPathResults();
                        }
                    });
        }
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
