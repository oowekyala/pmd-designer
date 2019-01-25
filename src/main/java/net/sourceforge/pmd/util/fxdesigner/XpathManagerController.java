/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import org.reactfx.collection.LiveArrayList;
import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.model.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableXPathRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.util.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentSequence;
import net.sourceforge.pmd.util.fxdesigner.util.controls.MutableTabPane;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;


/**
 * Controller for all XPath editors. Mediator between the main app and
 * the individual XPath editors. Also handles persisting the editors (under
 * the form of rule builders).
 *
 * @author Clément Fournier
 */
public class XpathManagerController extends AbstractController {
    private final DesignerRoot designerRoot;
    private final MainDesignerController mediator;

    @FXML
    private MutableTabPane<XPathPanelController> xpathEditorsTabPane;


    private ObservableList<ObservableXPathRuleBuilder> xpathRuleBuilders = new LiveArrayList<>();
    private int restoredTabIndex = 0;


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
        Platform.runLater(() -> {
            // those have just been restored
            ObservableList<ObservableXPathRuleBuilder> ruleSpecs = getRuleSpecs();

            if (ruleSpecs.isEmpty()) {
                // add at least one tab
                xpathEditorsTabPane.addTabWithNewController();
            } else {
                for (ObservableXPathRuleBuilder builder : ruleSpecs) {
                    xpathEditorsTabPane.addTabWithController(new XPathPanelController(designerRoot, this, builder));
                }
            }

            xpathEditorsTabPane.getSelectionModel().select(restoredTabIndex);

            // after restoration they're read-only and got for persistence on closing
            xpathRuleBuilders = xpathEditorsTabPane.getControllers().map(XPathPanelController::getRuleBuilder);
        });

    }


    TextAwareNodeWrapper wrapNode(Node node) {
        return mediator.wrapNode(node);
    }


    void onNodeItemSelected(Node n) {
        mediator.onNodeItemSelected(n);
    }


    /**
     * Called by the main controller to refresh the currently open editor.
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


    /**
     * Language version of the editor, used for synced XPath editors.
     */
    public Val<LanguageVersion> globalLanguageVersionProperty() {
        return mediator.languageVersionProperty();
    }


    public Val<Language> globalLanguageProperty() {
        return Val.map(globalLanguageVersionProperty(), LanguageVersion::getLanguage);
    }


    private Val<XPathPanelController> selectedEditorProperty() {
        return xpathEditorsTabPane.currentFocusedController();
    }


    /**
     * Called by the main editor when the compilation unit is marked invalid.
     *
     * @param error
     */
    public void invalidateResults(boolean error) {
        selectedEditorProperty().ifPresent(c -> c.invalidateResults(error));
    }

    /*
        Persisted properties
     */


    @PersistentProperty
    public int getSelectedTabIndex() {
        return xpathEditorsTabPane.getSelectionModel().getSelectedIndex();
    }


    public void setSelectedTabIndex(int i) {
        restoredTabIndex = i;
    }


    // Persist the rule builders
    // Tab creation on app restore is handled in afterParentInit
    @PersistentSequence
    public ObservableList<ObservableXPathRuleBuilder> getRuleSpecs() {
        return xpathRuleBuilders;
    }


}
