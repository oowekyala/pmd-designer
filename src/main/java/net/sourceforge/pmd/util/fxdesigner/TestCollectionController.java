/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import java.io.File;

import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableXPathRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.model.testing.LiveTestCase;
import net.sourceforge.pmd.util.fxdesigner.model.testing.TestCollection;
import net.sourceforge.pmd.util.fxdesigner.model.testing.TestXmlParser;
import net.sourceforge.pmd.util.fxdesigner.popups.SimplePopups;
import net.sourceforge.pmd.util.fxdesigner.popups.TestExportWizardController;
import net.sourceforge.pmd.util.fxdesigner.util.SoftReferenceCache;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ControlUtil;
import net.sourceforge.pmd.util.fxdesigner.util.controls.HelpfulPlaceholder;
import net.sourceforge.pmd.util.fxdesigner.util.controls.TestCaseListCell;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;

public class TestCollectionController extends AbstractController {

    @FXML
    private MenuButton addTestMenuButton;
    @FXML
    private ToolbarTitledPane titledPane;
    @FXML
    public ListView<LiveTestCase> testsListView;
    @FXML
    private MenuItem importTestsButton;
    @FXML
    private MenuItem addTestButton;
    @FXML
    private MenuItem addFromSourceButton;
    @FXML
    private Button exportTestsButton;

    private final SoftReferenceCache<TestExportWizardController> exportWizard;
    private ToggleGroup loadedToggleGroup = new ToggleGroup();

    private final ObservableXPathRuleBuilder builder;

    protected TestCollectionController(DesignerRoot root, ObservableXPathRuleBuilder builder) {
        super(root);
        this.builder = builder;

        this.exportWizard = new SoftReferenceCache<>(() -> new TestExportWizardController(root));
    }

    public Val<LanguageVersion> getDefaultLanguageVersion() {
        return builder.languageProperty().map(Language::getDefaultVersion);
    }

    @Override
    protected void beforeParentInit() {

        testsListView.setCellFactory(c -> new TestCaseListCell(this));
        testsListView.setEditable(true);
        testsListView.setPlaceholder(
            HelpfulPlaceholder.withMessage("This rule has no tests yet")
                              .withSuggestedAction("Import from file", importTestsButton::fire)
                              .withSuggestedAction("Add empty test case", addTestButton::fire)
                              .withSuggestedAction("Add from current source", addFromSourceButton::fire)
                              .build()
        );

        ControlUtil.makeListViewNeverScrollHorizontal(testsListView);

        this.testsListView.setItems(getTestCollection().getStash());


        addFromSourceButton.setOnAction(e -> {
            getService(DesignerRoot.TEST_CREATOR).getSourceFetchRequests().pushEvent(this, null);
            testCreatedFeedback();
        });


        addTestButton.setOnAction(any -> {
            getTestCollection().addTestCase(new LiveTestCase(builder).unfreeze());
            testCreatedFeedback();
        });

        importTestsButton.setOnAction(any -> importTestsFromFile());

        exportTestsButton.setOnAction(evt -> {
            TestExportWizardController wizard = exportWizard.get();
            wizard.showYourself(wizard.bindToTestCollection(getTestCollection()));
        });

        // disable export if no test cases
        exportTestsButton.disableProperty().bind(
            getTestCollection().getStash().sizeProperty().map(it -> it == 0)
        );

        getService(DesignerRoot.TEST_CREATOR)
            .getAdditionRequests()
            .messageStream(true, this)
            .subscribe(ltc -> getTestCollection().addTestCase(ltc.unfreeze()));

    }

    private void importTestsFromFile() {
        FileChooser chooser = new FileChooser();
        File origin = getTestCollection().getOrigin();
        chooser.setInitialFileName(origin == null ? null : origin.getAbsolutePath());
        chooser.setTitle("Load source from file");
        File file = chooser.showOpenDialog(getMainStage());

        if (file == null) {
            SimplePopups.showActionFeedback(addTestMenuButton, AlertType.INFORMATION, "No file chosen");
            return;
        }

        try {
            TestCollection coll = TestXmlParser.parseXmlTests(file.toPath(), builder);
            getTestCollection().addAll(coll);
            getTestCollection().setOrigin(file);
            SimplePopups.showActionFeedback(addTestMenuButton, AlertType.CONFIRMATION,
                                            "Imported " + coll.getStash().size() + " test cases");
        } catch (Exception e) {
            SimplePopups.showActionFeedback(addTestMenuButton, AlertType.ERROR, "Error while importing, see event log");
            logUserException(e, Category.TEST_LOADING_EXCEPTION);
        }
    }

    private void testCreatedFeedback() {
        SimplePopups.showActionFeedback(addTestMenuButton, AlertType.CONFIRMATION, "Test created");
    }

    @Override
    public void afterParentInit() {
        super.afterParentInit();
        getTestCollection().initOwner();
    }

    private TestCollection getTestCollection() {
        return builder.getTestCollection();
    }

    public void deleteTestCase(LiveTestCase tc) {
        ObservableList<LiveTestCase> items = testsListView.getItems();
        int idx = items.indexOf(tc);

        if (idx >= 0) {
            items.remove(idx);
            if (!tc.isFrozen()) {
                unloadTestCase();

                if (!items.isEmpty()) {
                    if (idx == 0) {
                        loadTestCase(0);
                    } else {
                        loadTestCase(idx - 1);
                    }
                }
            }
        }
    }

    public void unloadTestCase() {
        getService(DesignerRoot.TEST_LOADER).pushEvent(this, null);
    }

    public void loadTestCase(int index) {
        LiveTestCase live = getTestCollection().export(index);
        if (live == null) {
            logInternalException(new RuntimeException("Wrong index in test list: " + index));
            return;
        }

        getService(DesignerRoot.TEST_LOADER).pushEvent(this, live);
    }


    public ToggleGroup getLoadedToggleGroup() {
        return loadedToggleGroup;
    }

    public void setLoadedToggleGroup(ToggleGroup loadedToggleGroup) {
        this.loadedToggleGroup = loadedToggleGroup;
    }

    public void duplicate(LiveTestCase testCase) {
        getTestCollection().addTestCase(testCase.deepCopy().unfreeze());
    }

    public Val<LiveTestCase> selectedTestCase() {
        return Val.wrap(loadedToggleGroup.selectedToggleProperty())
                  .map(it -> (LiveTestCase) it.getUserData());
    }
}
