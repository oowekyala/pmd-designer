/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import static net.sourceforge.pmd.util.fxdesigner.popups.SimplePopups.showLicensePopup;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import org.apache.commons.io.IOUtils;
import org.reactfx.Subscription;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.popups.EventLogController;
import net.sourceforge.pmd.util.fxdesigner.popups.SimplePopups;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;
import net.sourceforge.pmd.util.fxdesigner.util.LimitedSizeStack;
import net.sourceforge.pmd.util.fxdesigner.util.SoftReferenceCache;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;

import javafx.beans.NamedArg;
import javafx.fxml.FXML;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;


/**
 * Main controller of the app. Mediator for subdivisions of the UI.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
@SuppressWarnings("PMD.UnusedPrivateField")
public class MainDesignerController extends AbstractController {


    /* Menu bar */
    @FXML
    private MenuItem aboutMenuItem;
    @FXML
    private MenuItem setupAuxclasspathMenuItem;
    @FXML
    public MenuItem openEventLogMenuItem;
    @FXML
    private MenuItem openFileMenuItem;
    @FXML
    private MenuItem saveMenuItem;
    @FXML
    private MenuItem licenseMenuItem;
    @FXML
    private Menu openRecentMenu;
    @FXML
    private Menu fileMenu;
    /* Bottom panel */
    @FXML
    private SplitPane mainHorizontalSplitPane;
    @FXML
    private Tab metricResultsTab;


    /* Children */
    @FXML
    private RuleEditorsController ruleEditorsController;
    @FXML
    private SourceEditorController sourceEditorController;

    @FXML
    private NodeDetailPaneController nodeDetailsTabController;
    @FXML
    private MetricPaneController metricPaneController;
    @FXML
    private ScopesPanelController scopesPanelController;


    // we cache it but if it's not used the FXML is not created, etc
    private final SoftReferenceCache<EventLogController> eventLogController;

    // Other fields
    private final Stack<File> recentFiles = new LimitedSizeStack<>(5);


    public MainDesignerController(@NamedArg("designerRoot") DesignerRoot designerRoot) {
        super(designerRoot);
        eventLogController = new SoftReferenceCache<>(() -> new EventLogController(designerRoot));
    }



    @Override
    protected void beforeParentInit() {
        getDesignerRoot().getService(DesignerRoot.PERSISTENCE_MANAGER).restoreSettings(this);

        licenseMenuItem.setOnAction(e -> showLicensePopup());
        openFileMenuItem.setOnAction(e -> onOpenFileClicked());
        openRecentMenu.setOnAction(e -> updateRecentFilesMenu());
        openRecentMenu.setOnShowing(e -> updateRecentFilesMenu());
        saveMenuItem.setOnAction(e -> getService(DesignerRoot.PERSISTENCE_MANAGER).persistSettings(this));
        fileMenu.setOnShowing(e -> onFileMenuShowing());
        aboutMenuItem.setOnAction(e -> SimplePopups.showAboutPopup(getDesignerRoot()));

        setupAuxclasspathMenuItem.setOnAction(e -> sourceEditorController.showAuxclasspathSetupPopup());

        openEventLogMenuItem.setOnAction(e -> {
            EventLogController wizard = eventLogController.get();
            Subscription parentToWizSubscription = wizard.errorNodesProperty().values().subscribe(sourceEditorController.currentErrorNodesProperty()::setValue);
            wizard.showPopup(parentToWizSubscription);
        });
        openEventLogMenuItem.textProperty().bind(
            getLogger().numNewLogEntriesProperty().map(i -> "Event _Log (" + (i > 0 ? i : "no") + " new)")
        );

    }


    @Override
    protected void afterChildrenInit() {
        updateRecentFilesMenu();

        sourceEditorController.currentRuleResultsProperty().bind(ruleEditorsController.currentRuleResults());

        metricPaneController.numAvailableMetrics().values().subscribe(n -> {
            metricResultsTab.setText("Metrics\t(" + (n == 0 ? "none" : n) + ")");
            metricResultsTab.setDisable(n == 0);
        });
    }


    private void onFileMenuShowing() {
        openRecentMenu.setDisable(recentFiles.isEmpty());
    }


    private void onOpenFileClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load source from file");
        File file = chooser.showOpenDialog(getMainStage());
        loadSourceFromFile(file);
    }

    private void loadSourceFromFile(File file) {
        if (file != null) {
            try {
                String source = IOUtils.toString(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
                sourceEditorController.setText(source);
                LanguageVersion guess = LanguageRegistryUtil.getLanguageVersionFromExtension(file.getName());
                if (guess != null) { // guess the language from the extension
                    sourceEditorController.setLanguageVersion(guess);
                }

                recentFiles.push(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void updateRecentFilesMenu() {
        List<MenuItem> items = new ArrayList<>();
        List<File> filesToClear = new ArrayList<>();

        for (final File f : recentFiles) {
            if (f.exists() && f.isFile()) {
                CustomMenuItem item = new CustomMenuItem(new Label(f.getName()));
                item.setOnAction(e -> loadSourceFromFile(f));
                item.setMnemonicParsing(false);
                Tooltip.install(item.getContent(), new Tooltip(f.getAbsolutePath()));
                items.add(item);
            } else {
                filesToClear.add(f);
            }
        }
        recentFiles.removeAll(filesToClear);

        if (items.isEmpty()) {
            openRecentMenu.setDisable(true);
            return;
        }

        Collections.reverse(items);

        items.add(new SeparatorMenuItem());
        MenuItem clearItem = new MenuItem();
        clearItem.setText("Clear menu");
        clearItem.setOnAction(e -> {
            recentFiles.clear();
            openRecentMenu.setDisable(true);
        });
        items.add(clearItem);

        openRecentMenu.getItems().setAll(items);
    }


    @PersistentProperty
    public List<File> getRecentFiles() {
        return recentFiles;
    }


    public void setRecentFiles(List<File> files) {
        files.forEach(recentFiles::push);
    }


    @PersistentProperty
    public boolean isMaximized() {
        return getMainStage().isMaximized();
    }


    public void setMaximized(boolean b) {
        getMainStage().setMaximized(!b); // trigger change listener anyway
        getMainStage().setMaximized(b);
    }


    @Override
    public List<AbstractController> getChildren() {
        return Arrays.asList(ruleEditorsController,
                             sourceEditorController,
                             nodeDetailsTabController,
                             metricPaneController,
                             scopesPanelController);
    }



    @Override
    public String getDebugName() {
        return "MAIN";
    }

}
