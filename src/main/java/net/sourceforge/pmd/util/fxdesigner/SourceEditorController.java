/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil.mapToggleGroupToUserData;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil.sanitizeExceptionMessage;
import static net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil.defaultLanguageVersion;
import static net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil.getSupportedLanguageVersions;
import static net.sourceforge.pmd.util.fxdesigner.util.reactfx.ReactfxUtil.rewire;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.reactfx.EventStreams;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.ClasspathClassLoader;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.model.ASTManager;
import net.sourceforge.pmd.util.fxdesigner.model.ParseAbortedException;
import net.sourceforge.pmd.util.fxdesigner.popups.AuxclasspathSetupController;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;
import net.sourceforge.pmd.util.fxdesigner.util.controls.AstTreeView;
import net.sourceforge.pmd.util.fxdesigner.util.controls.NodeEditionCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.controls.NodeParentageCrumbBar;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;
import net.sourceforge.pmd.util.fxdesigner.util.reactfx.VetoableEventStream;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;


/**
 * One editor, i.e. source editor and ast tree view. The {@link NodeEditionCodeArea} handles the
 * presentation of different types of nodes in separate layers. This class handles configuration,
 * language selection and such.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public class SourceEditorController extends AbstractController {

    private static final Duration AST_REFRESH_DELAY = Duration.ofMillis(100);
    private final ASTManager astManager;
    private final ASTManager oldAstManager;
    private final Var<List<File>> auxclasspathFiles = Var.newSimpleVar(emptyList());
    private final Val<ClassLoader> auxclasspathClassLoader = auxclasspathFiles.map(fileList -> {
        try {
            return new ClasspathClassLoader(fileList, SourceEditorController.class.getClassLoader());
        } catch (IOException e) {
            e.printStackTrace();
            return SourceEditorController.class.getClassLoader();
        }
    });
    @FXML
    private ToggleButton highlightRemovedOldNodesToggle;
    @FXML
    private Label numNewNodesLabel;
    @FXML
    private Label numOldNodesLabel;
    @FXML
    private AstTreeView oldAstTreeView;
    @FXML
    private ToolbarTitledPane oldAstTitledPane;
    @FXML
    private ToolbarTitledPane astTitledPane;
    @FXML
    private ToolbarTitledPane editorTitledPane;
    @FXML
    private MenuButton languageSelectionMenuButton;
    @FXML
    private AstTreeView astTreeView;
    @FXML
    private NodeEditionCodeArea nodeEditionCodeArea;
    @FXML
    private NodeParentageCrumbBar focusNodeParentageCrumbBar;


    private Var<LanguageVersion> languageVersionUIProperty;


    public SourceEditorController(DesignerRoot designerRoot) {
        super(designerRoot);
        astManager = new ASTManager(designerRoot);
        oldAstManager = new ASTManager(designerRoot);
    }


    @Override
    protected void beforeParentInit() {
        initializeLanguageSelector(); // languageVersionProperty() must be initialized

        oldAstManager.languageVersionProperty().bind(
            astManager.languageVersionProperty()
                      .map(LanguageVersion::getTerseName)
                      .map(it -> it.replace("java", "oldjava"))
                      .map(LanguageRegistry::findLanguageVersionByTerseName)
        );


        languageVersionProperty().values()
                                 .filterMap(Objects::nonNull, LanguageVersion::getLanguage)
                                 .distinct()
                                 .subscribe(nodeEditionCodeArea::updateSyntaxHighlighter);

        languageVersionProperty().values()
                                 .filter(Objects::nonNull)
                                 .map(LanguageVersion::getShortName)
                                 .map(lang -> "Source Code (" + lang + ")")
                                 .subscribe(editorTitledPane::setTitle);

        nodeEditionCodeArea.plainTextChanges()
                           .filter(t -> !t.isIdentity())
                           .successionEnds(AST_REFRESH_DELAY)
                           // Refresh the AST anytime the text, classloader, or language version changes
                           .or(auxclasspathClassLoader.changes())
                           .or(languageVersionProperty().changes())
                           .subscribe(tick -> {
                               // Discard the AST if the language version has changed
                               tick.ifRight(c -> astTreeView.setRoot(null));
                               refreshAST();
                           });

        // default text, will be overwritten by settings restore
        // TODO this doesn't handle the case where java is not on the classpath
        setText("class Foo {\n"
                    + "\n"
                    + "    /*\n"
                    + "        Welcome to the PMD Rule designer :)\n"
                    + "\n"
                    + "        Type some code in this area\n"
                    + "        \n"
                    + "        On the right, the Abstract Syntax Tree is displayed\n"
                    + "        On the left, you can examine the XPath attributes of\n"
                    + "        the nodes you select\n"
                    + "        \n"
                    + "        You can set the language you'd like to work in with\n"
                    + "        the cog icon above this code area\n"
                    + "     */\n"
                    + "\n"
                    + "    int i = 0;\n"
                    + "}");


        astManager.compilationUnitProperty().values().emitBothOnEach(oldAstManager.compilationUnitProperty().values())
                  .subscribe(n -> {
                      Node newAst = n._1;
                      Node oldAst = n._2;
                      if (newAst != null && oldAst != null) {
                          int newCount = newAst.descendantStream().count();
                          int oldCount = oldAst.descendantStream().count();
                          double change = newCount * 100.0 / oldCount;
                          String percentChange = new DecimalFormat("##.#").format(change) + "% of old";

                          numNewNodesLabel.textProperty().setValue("(" + newCount + " nodes, " + percentChange + ")");
                          numOldNodesLabel.textProperty().setValue("(" + oldCount + " nodes)");
                      }

                  });

        EventStreams.valuesOf(highlightRemovedOldNodesToggle.selectedProperty())
                    .subscribe(on -> oldAstTreeView.setAdditionalStyleClasses(on ? SourceEditorController::additionalStyleClasses : null));

    }

    private static Collection<String> additionalStyleClasses(Node n) {
        if (n == null) {
            return emptySet();
        }
        switch (n.getXPathNodeName()) {
        case "Literal":
            if (n.jjtGetNumChildren() == 0) {
                break;
            }
        case "Expression":
        case "PrimaryExpression":
        case "VariableInitializer":
        case "Type":
        case "ReferenceType":
        case "PrimaryPrefix":
        case "Arguments":
        case "PrimarySuffix":
        case "MemberSelector":
            return Collections.singletonList("removed");
        case "BlockStatement":
        case "TypeArgument":
        case "Statement":
        case "Annotation":
        case "ClassOrInterfaceBodyDeclaration":
        case "AnnotationTypeBodyDeclaration":
        case "TypeDeclaration":
            return Collections.singletonList("proposed-removal");
        default:
        }
        return emptySet();

    }


    @Override
    protected void afterParentInit() {

        // Bind global compilation unit to the main ast manager
        Var<Node> globalCompilationUnit = getGlobalState().writableGlobalCompilationUnitProperty();

        // veto null events to ignore null compilation units if they're
        // followed by a valid one quickly
        VetoableEventStream.vetoableFrom(
            astManager.compilationUnitProperty().values(),
            Objects::isNull,
            (a, b) -> b != null,
            (a, b) -> b,
            Duration.ofMillis(500)
        ).subscribe(globalCompilationUnit::setValue);


        rewire(astManager.languageVersionProperty(), languageVersionUIProperty);
        nodeEditionCodeArea.moveCaret(0, 0);

        getDesignerRoot().registerService(DesignerRoot.RICH_TEXT_MAPPER, nodeEditionCodeArea);
    }


    private void initializeLanguageSelector() {

        ToggleGroup languageToggleGroup = new ToggleGroup();

        getSupportedLanguageVersions()
                    .stream()
                    .sorted(LanguageVersion::compareTo)
                    .map(lv -> {
                        RadioMenuItem item = new RadioMenuItem(lv.getShortName());
                        item.setUserData(lv);
                        return item;
                    })
                    .forEach(item -> {
                        languageToggleGroup.getToggles().add(item);
                        languageSelectionMenuButton.getItems().add(item);
                    });

        languageVersionUIProperty = mapToggleGroupToUserData(languageToggleGroup, LanguageRegistryUtil::defaultLanguageVersion);
        // this will be overwritten by property restore if needed
        languageVersionUIProperty.setValue(defaultLanguageVersion());
    }

    /**
     * Refreshes the AST and returns the new compilation unit if the parse didn't fail.
     */
    public void refreshAST() {
        String source = getText();

        if (StringUtils.isBlank(source)) {
            astTreeView.setAstRoot(null);
            return;
        }


        try {
            // this will push the new compilation unit on the global Val
            astManager.updateIfChanged(source, auxclasspathClassLoader.getValue())
                      .ifPresent(this::setUpToDateCompilationUnit);

            oldAstManager.updateIfChanged(source, auxclasspathClassLoader.getValue())
                         .ifPresent(n -> oldAstTreeView.setAstRoot(n));

        } catch (ParseAbortedException e) {
            editorTitledPane.errorMessageProperty().setValue(sanitizeExceptionMessage(e));
            getGlobalState().writableGlobalCompilationUnitProperty().setValue(null);
        }
    }


    public void showAuxclasspathSetupPopup() {
        new AuxclasspathSetupController(getDesignerRoot()).show(getMainStage(), auxclasspathFiles.getValue(), auxclasspathFiles::setValue);
    }


    private void setUpToDateCompilationUnit(Node node) {
        editorTitledPane.errorMessageProperty().setValue("");
        astTreeView.setAstRoot(node);
    }

    public Var<List<Node>> currentRuleResultsProperty() {
        return nodeEditionCodeArea.currentRuleResultsProperty();
    }


    public Var<List<Node>> currentErrorNodesProperty() {
        return nodeEditionCodeArea.currentErrorNodesProperty();
    }


    @PersistentProperty
    public LanguageVersion getLanguageVersion() {
        return languageVersionUIProperty.getValue();
    }


    public void setLanguageVersion(LanguageVersion version) {
        languageVersionUIProperty.setValue(version);
    }


    public Var<LanguageVersion> languageVersionProperty() {
        return languageVersionUIProperty;
    }


    @PersistentProperty
    public String getText() {
        return nodeEditionCodeArea.getText();
    }


    public void setText(String expression) {
        nodeEditionCodeArea.replaceText(expression);
    }


    public Val<String> textProperty() {
        return Val.wrap(nodeEditionCodeArea.textProperty());
    }


    @PersistentProperty
    public String getAuxclasspathFiles() {
        return auxclasspathFiles.getValue().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
    }


    public void setAuxclasspathFiles(String files) {
        List<File> newVal = Arrays.stream(files.split(File.pathSeparator)).map(File::new).collect(Collectors.toList());
        auxclasspathFiles.setValue(newVal);
    }


    @Override
    public String getDebugName() {
        return "editor";
    }
}
