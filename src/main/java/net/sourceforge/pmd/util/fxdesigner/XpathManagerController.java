/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import org.reactfx.collection.LiveArrayList;
import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.CompositeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableXPathRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentSequence;
import net.sourceforge.pmd.util.fxdesigner.util.controls.MutableTabPane;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.fxml.FXML;


/**
 * Controller for all XPath editors. Interfaces between the main app and
 * the individual XPath editors. Also handles persisting the editors (under
 * the form of rule builders).
 *
 * @author Clément Fournier
 */
public class XpathManagerController extends AbstractController<MainDesignerController> implements CompositeSelectionSource {

    @FXML
    private MutableTabPane<XPathPanelController> xpathEditorsTabPane;

    private final ObservableSet<XPathPanelController> currentlySelectedController = FXCollections.observableSet();

    private ObservableList<ObservableXPathRuleBuilder> xpathRuleBuilders = new LiveArrayList<>();
    private int restoredTabIndex = 0;


    public XpathManagerController(MainDesignerController parent) {
        super(parent);
    }


    @Override
    protected void beforeParentInit() {

        xpathEditorsTabPane.setControllerSupplier(() -> new XPathPanelController(this));

        selectedEditorProperty().changes()
                                .subscribe(ch -> {
                                    // only the results of the currently opened tab are displayed
                                    parent.resetXPathResults();
                                    currentlySelectedController.clear();
                                    if (ch.getNewValue() != null) {
                                        currentlySelectedController.add(ch.getNewValue());
                                        refreshCurrentXPath(ch.getNewValue());
                                    }
                                });

        Val<ObservableList<Node>> currentXPathResults = selectedEditorProperty().flatMap(XPathPanelController::xpathResultsProperty);

        currentXPathResults.changes()
                           .subscribe(ch -> {
                               if (ch.getNewValue() != null) {
                                   parent.highlightXPathResults(ch.getNewValue());
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
                    xpathEditorsTabPane.addTabWithController(new XPathPanelController(this, builder));
                }
            }

            xpathEditorsTabPane.getSelectionModel().select(restoredTabIndex);

            // after restoration they're read-only and got for persistence on closing
            xpathRuleBuilders = xpathEditorsTabPane.getControllers().map(XPathPanelController::getRuleBuilder);
        });

    }


    TextAwareNodeWrapper wrapNode(Node node) {
        return parent.wrapNode(node);
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
            parent.getCompilationUnit().ifPresent(r -> editor.evaluateXPath(r, parent.getLanguageVersion()));
            //                        if (event.getCategory() == Category.XPATH_EVALUATION_EXCEPTION) {
            //                            parent.resetXPathResults();
            //                        }
            //                    });
        }
    }


    /**
     * Language version of the editor, used for synced XPath editors.
     */
    public Val<LanguageVersion> globalLanguageVersionProperty() {
        return parent.languageVersionProperty();
    }


    public Val<Language> globalLanguageProperty() {
        return Val.map(globalLanguageVersionProperty(), LanguageVersion::getLanguage);
    }


    private Val<XPathPanelController> selectedEditorProperty() {
        return xpathEditorsTabPane.currentFocusedController();
    }


    /**
     * Called by the main editor when the compilation unit is marked invalid.
     */
    public void invalidateResults(boolean error) {
        selectedEditorProperty().ifPresent(c -> c.invalidateResults(error));
    }

    /*
     *  Persisted properties
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


    @Override
    public ObservableSet<? extends NodeSelectionSource> getSubSelectionSources() {
        return currentlySelectedController;
    }
}
