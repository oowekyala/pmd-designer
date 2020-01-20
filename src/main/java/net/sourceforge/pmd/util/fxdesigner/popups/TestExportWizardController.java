/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.popups;

import java.io.File;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.reactfx.Subscription;

import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.model.testing.TestCollection;
import net.sourceforge.pmd.util.fxdesigner.model.testing.TestXmlDumper;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.StageBuilder;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.AvailableSyntaxHighlighters;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.SyntaxHighlightingCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.syntaxhighlighting.XmlSyntaxHighlighter;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ControlUtil;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Modality;
import javafx.stage.Stage;


/**
 * Controller for the "Export tests to XML" dialog.
 *
 * @author Clément Fournier
 */
public final class TestExportWizardController extends AbstractController {

    private final Stage myPopupStage;
    @Nullable
    private String originalFile;
    @FXML
    private ToolbarTitledPane titledPane;
    @FXML
    private Button saveToFileButton;
    @FXML
    private Button copyResultButton;
    @FXML
    private SyntaxHighlightingCodeArea exportResultArea;


    public TestExportWizardController(DesignerRoot root) {
        super(root);
        this.myPopupStage = createStage(root.getMainStage());
    }

    private Stage createStage(Stage mainStage) {
        return new StageBuilder().withOwner(mainStage)
                                 .withFxml(DesignerUtil.getFxml("test-export-wizard"), getDesignerRoot(), this)
                                 .withModality(Modality.WINDOW_MODAL)
                                 .withTitle("Export test cases")
                                 .newStage();
    }


    public Subscription bindToTestCollection(TestCollection testCollection) {
        return testCollection.modificationTicks().subscribe(it -> {
            try {
                String xml = TestXmlDumper.dumpXmlTests(testCollection);
                titledPane.errorMessageProperty().setValue(null);
                exportResultArea.replaceText(xml);
                File origin = testCollection.getOrigin();
                originalFile = origin != null ? origin.getAbsolutePath() : null;
            } catch (Exception e) {
                reportDumpException(e);
            }
        });
    }

    /** Set the given subscription as close handler and show. */
    public void showYourself(Subscription parentBinding) {
        myPopupStage.setOnCloseRequest(e -> parentBinding.unsubscribe());
        exportResultArea.setSyntaxHighlighter(new XmlSyntaxHighlighter());
        myPopupStage.show();
    }

    private void reportDumpException(Throwable e) {
        logUserException(e, Category.TEST_EXPORT_EXCEPTION);
        titledPane.errorMessageProperty().setValue(e.getMessage());
    }

    @Override
    protected void beforeParentInit() {
        exportResultArea.setSyntaxHighlighter(AvailableSyntaxHighlighters.XML);


        ControlUtil.copyToClipboardButton(copyResultButton, exportResultArea::getText);
        ControlUtil.saveToFileButton(saveToFileButton, myPopupStage, exportResultArea::getText, this, () -> originalFile);
    }
}
