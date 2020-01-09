/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil.sanitizeExceptionMessage;
import static net.sourceforge.pmd.util.fxdesigner.util.AuxLanguageRegistry.defaultLanguageVersion;
import static net.sourceforge.pmd.util.fxdesigner.util.reactfx.ReactfxUtil.latestValue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.controlsfx.control.PopOver;
import org.reactfx.EventStreams;
import org.reactfx.Subscription;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.collection.LiveList;
import org.reactfx.value.SuspendableVar;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.ClasspathClassLoader;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.services.ASTManager;
import net.sourceforge.pmd.util.fxdesigner.app.services.ASTManagerImpl;
import net.sourceforge.pmd.util.fxdesigner.app.services.TestCreatorService;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.model.testing.LiveTestCase;
import net.sourceforge.pmd.util.fxdesigner.model.testing.LiveViolationRecord;
import net.sourceforge.pmd.util.fxdesigner.popups.AuxclasspathSetupController;
import net.sourceforge.pmd.util.fxdesigner.popups.SimplePopups;
import net.sourceforge.pmd.util.fxdesigner.util.AstTraversalUtil;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.AuxLanguageRegistry;
import net.sourceforge.pmd.util.fxdesigner.util.ResourceUtil;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsOwner;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;
import net.sourceforge.pmd.util.fxdesigner.util.controls.AstTreeView;
import net.sourceforge.pmd.util.fxdesigner.util.controls.DragAndDropUtil;
import net.sourceforge.pmd.util.fxdesigner.util.controls.DynamicWidthChoicebox;
import net.sourceforge.pmd.util.fxdesigner.util.controls.NodeEditionCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.controls.NodeParentageCrumbBar;
import net.sourceforge.pmd.util.fxdesigner.util.controls.PopOverWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.controls.PropertyMapView;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ViolationCollectionView;
import net.sourceforge.pmd.util.fxdesigner.util.reactfx.ReactfxUtil;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;


/**
 * One editor, i.e. source editor and ast tree view. The {@link NodeEditionCodeArea} handles the
 * presentation of different types of nodes in separate layers. This class handles configuration,
 * language selection and such.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public class SourceEditorController extends AbstractController {

    /**
     * When no user-defined test case is loaded, then this is where
     * source changes end up. It's persisted between runs, independently
     * of other test cases.
     */
    private final LiveTestCase defaultTestCase = new LiveTestCase();
    /** Contains the loaded *user-defined* test case. */
    private final SuspendableVar<LiveTestCase> currentlyOpenTestCase = Var.suspendable(Var.newSimpleVar(null));
    private static final Duration AST_REFRESH_DELAY = Duration.ofMillis(100);
    private final ASTManager astManager;
    private final ASTManager oldAstManager;
    private final Var<List<File>> auxclasspathFiles = Var.newSimpleVar(emptyList());
    private final Val<ClassLoader> auxclasspathClassLoader = auxclasspathFiles.<ClassLoader>map(fileList -> {
        try {
            return new ClasspathClassLoader(fileList, SourceEditorController.class.getClassLoader());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }).orElseConst(SourceEditorController.class.getClassLoader());

    @FXML
    private Button searchButton;
    @FXML
    private DynamicWidthChoicebox<LanguageVersion> languageVersionChoicebox;
    @FXML
    private ToolbarTitledPane testCaseToolsTitledPane;
    @FXML
    private Button violationsButton;
    @FXML
    private Button propertiesMapButton;


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
    private Parent codeAreaParent;
    @FXML
    private NodeParentageCrumbBar focusNodeParentageCrumbBar;

    private final PopOverWrapper<LiveTestCase> violationsPopover;
    private final PopOverWrapper<LiveTestCase> propertiesPopover;


    private Var<LanguageVersion> languageVersionUIProperty;


    public SourceEditorController(DesignerRoot designerRoot) {
        super(designerRoot);
        ASTManagerImpl astManagerImpl = new ASTManagerImpl(designerRoot);
        this.astManager = astManagerImpl;
        oldAstManager = new ASTManagerImpl(astManagerImpl, AuxLanguageRegistry::mapNewJavaToOld);

        designerRoot.registerService(DesignerRoot.AST_MANAGER, this.astManager);
        designerRoot.registerService(DesignerRoot.OLD_AST_MANAGER, oldAstManager);

        violationsPopover = new PopOverWrapper<>(this::rebindPopover);
        propertiesPopover = new PopOverWrapper<>(this::rebindPropertiesPopover);
    }

    private PopOver rebindPopover(LiveTestCase testCase, PopOver existing) {
        if (testCase == null && existing != null) {
            existing.hide();
            return existing;
        }

        if (testCase != null) {
            if (existing == null) {
                return ViolationCollectionView.makePopOver(testCase, getDesignerRoot());
            } else {
                ViolationCollectionView view = (ViolationCollectionView) existing.getUserData();
                view.setItems(testCase.getExpectedViolations());
                return existing;
            }
        }
        return null;
    }

    private PopOver rebindPropertiesPopover(LiveTestCase testCase, PopOver existing) {
        if (testCase == null && existing != null) {
            existing.hide();
            PropertyMapView view = (PropertyMapView) existing.getUserData();
            view.unbind();
            return existing;
        }

        if (testCase != null) {
            if (existing == null) {
                return PropertyMapView.makePopOver(testCase, getDesignerRoot());
            } else {
                PropertyMapView view = (PropertyMapView) existing.getUserData();
                view.unbind();
                view.bind(testCase);
                return existing;
            }
        }
        return null;
    }

    @Override
    protected void beforeParentInit() {

        oldAstTreeView.setDebugName("TreeView (old ast)");
        astTreeView.setDebugName("TreeView (new ast)");


        astManager.languageVersionProperty()
                  .map(LanguageVersion::getLanguage)
                  .values()
                  .filter(Objects::nonNull)
                  .subscribe(nodeEditionCodeArea::updateSyntaxHighlighter);

        ((ASTManagerImpl) astManager).classLoaderProperty().bind(auxclasspathClassLoader);

        // default text, will be overwritten by settings restore
        setText(getDefaultText());

        searchButton.setOnAction(e -> astTreeView.focusSearchField());

        TestCreatorService creatorService = getService(DesignerRoot.TEST_CREATOR);

        creatorService.getSourceFetchRequests()
                      .messageStream(true, this)
                      .subscribe(tick -> creatorService.getAdditionRequests().pushEvent(this, currentlyOpenTestCase.getOrElse(defaultTestCase).deepCopy()));

        propertiesMapButton.setOnAction(e -> propertiesPopover.showOrFocus(p -> p.show(propertiesMapButton)));
        violationsButton.setOnAction(e -> violationsPopover.showOrFocus(p -> p.show(violationsButton)));

        violationsButton.textProperty().bind(
            currentlyOpenTestCase.flatMap(it -> it.getExpectedViolations().sizeProperty())
                                 .map(it -> "Expected violations (" + it + ")")
                                 .orElseConst("Expected violations")
        );

        propertiesMapButton.disableProperty().bind(
            currentlyOpenTestCase.flatMap(LiveTestCase::ruleProperty)
                                 .map(ObservableRuleBuilder::getRuleProperties)
                                 .flatMap(LiveList::sizeProperty)
                                 .map(it -> it == 0)
                                 .orElseConst(false)
        );

        DragAndDropUtil.registerAsNodeDragTarget(
            violationsButton,
            range -> {
                LiveViolationRecord record = new LiveViolationRecord();
                record.setRange(range);
                record.setExactRange(true);
                SimplePopups.showActionFeedback(violationsButton, AlertType.CONFIRMATION, "Violation added");
                currentlyOpenTestCase.ifPresent(v -> v.getExpectedViolations().add(record));
            }, getDesignerRoot());

        currentlyOpenTestCase.orElseConst(defaultTestCase)
                             .changes()
                             .subscribe(it -> handleTestOpenRequest(it.getOldValue(), it.getNewValue()));

        currentlyOpenTestCase.values().subscribe(test -> {
            violationsPopover.rebind(test);
            propertiesPopover.rebind(test);
        });



        // CUSTOM
        astManager.compilationUnitProperty().values()
                  .emitBothOnEach(oldAstManager.compilationUnitProperty().values())
                  .subscribe(n -> {
                      Node newAst = n._1;
                      Node oldAst = n._2;
                      if (newAst != null && oldAst != null) {
                          int newCount = (int) AstTraversalUtil.descendantStream(newAst, true).count();
                          int oldCount = (int) AstTraversalUtil.descendantStream(oldAst, true).count();
                          double change = newCount * 100.0 / oldCount;
                          String percentChange = new DecimalFormat("##.#").format(change) + "% of old";

                          numNewNodesLabel.textProperty().setValue("(" + newCount + " nodes, " + percentChange + ")");
                          numOldNodesLabel.textProperty().setValue("(" + oldCount + " nodes)");
                      }

                  });

        // CUSTOM
        EventStreams.valuesOf(highlightRemovedOldNodesToggle.selectedProperty())
                    .subscribe(on -> oldAstTreeView.setAdditionalStyleClasses(on ? SourceEditorController::additionalStyleClasses : null));

    }

    @Override
    public void afterParentInit() {
        initializeLanguageSelector();

        // languageVersionUiProperty is initialised

        ((ASTManagerImpl) astManager).languageVersionProperty().bind(languageVersionUIProperty.orElse(globalLanguageProperty().map(Language::getDefaultVersion)));

        handleTestOpenRequest(defaultTestCase, defaultTestCase);


        Var<String> areaText = Var.fromVal(
            latestValue(nodeEditionCodeArea.plainTextChanges()
                                           .successionEnds(AST_REFRESH_DELAY)
                                           .map(it -> nodeEditionCodeArea.getText())),
            text -> nodeEditionCodeArea.replaceText(text)
        );

        areaText.bindBidirectional(astManager.sourceCodeProperty());


        nodeEditionCodeArea.moveCaret(0, 0);

        editorTitledPane.errorTypeProperty().setValue("Syntax error");
        initTreeView(astManager, astTreeView, editorTitledPane.errorMessageProperty());
        initTreeView(oldAstManager, oldAstTreeView, oldAstTitledPane.errorMessageProperty());

        getDesignerRoot().registerService(DesignerRoot.RICH_TEXT_MAPPER, nodeEditionCodeArea);

        getService(DesignerRoot.TEST_LOADER)
            .messageStream(true, this)
            .subscribe(currentlyOpenTestCase::setValue);


        // this is to hide the toolbar when we're not in test case mode
        currentlyOpenTestCase.map(it -> true).orElseConst(false)
                             .values().distinct()
                             .subscribe(this::toggleTestEditMode);

    }

    private void toggleTestEditMode(boolean isTestCaseMode) {
        if (isTestCaseMode) {
            AnchorPane pane = emptyPane();
            editorTitledPane.setContent(pane);

            AnchorPane otherPane = emptyPane();
            testCaseToolsTitledPane.setContent(otherPane);

            otherPane.getChildren().addAll(codeAreaParent);
            pane.getChildren().addAll(testCaseToolsTitledPane);
        } else {
            AnchorPane otherPane = emptyPane();
            editorTitledPane.setContent(otherPane);
            otherPane.getChildren().addAll(codeAreaParent);
        }
    }

    private static AnchorPane emptyPane() {
        AnchorPane pane = new AnchorPane();
        pane.setPadding(Insets.EMPTY);
        return pane;
    }

    private void handleTestOpenRequest(@NonNull LiveTestCase oldValue, @NonNull LiveTestCase newValue) {
        oldValue.commitChanges();

        if (!newValue.getSource().equals(nodeEditionCodeArea.getText())) {
            nodeEditionCodeArea.replaceText(newValue.getSource());
        }

        if (newValue.getLanguageVersion() == null) {
            newValue.setLanguageVersion(globalLanguageProperty().getValue().getDefaultVersion());
        }

        Subscription sub = Subscription.multi(
            ReactfxUtil.rewireInit(newValue.sourceProperty(), astManager.sourceCodeProperty()),
            ReactfxUtil.rewireInit(newValue.languageVersionProperty(), languageVersionUIProperty),
            () -> propertiesPopover.rebind(null)
        );

        newValue.addCommitHandler(t -> sub.unsubscribe());
    }


    private String getDefaultText() {
        try {
            // TODO this should take language into account
            //  it doesn't handle the case where java is not on the classpath

            return IOUtils.resourceToString(ResourceUtil.resolveResource("placeholders/editor.java"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "class Foo {\n"
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
                + "}";
        }
    }


    private void initializeLanguageSelector() {

        languageVersionChoicebox.setConverter(DesignerUtil.stringConverter(LanguageVersion::getName, AuxLanguageRegistry::getLanguageVersionByName));

        getService(DesignerRoot.APP_GLOBAL_LANGUAGE)
            .values()
            .filter(Objects::nonNull)
            .subscribe(lang -> {
                languageVersionChoicebox.setItems(lang.getVersions().stream().sorted().collect(Collectors.toCollection(LiveArrayList::new)));
                languageVersionChoicebox.getSelectionModel().select(lang.getDefaultVersion());
                boolean disable = lang.getVersions().size() == 1;

                languageVersionChoicebox.setVisible(!disable);
                languageVersionChoicebox.setManaged(!disable);
            });


        languageVersionUIProperty = Var.suspendable(languageVersionChoicebox.valueProperty());
        // this will be overwritten by property restore if needed
        languageVersionUIProperty.setValue(defaultLanguageVersion());
    }



    public void showAuxclasspathSetupPopup() {
        new AuxclasspathSetupController(getDesignerRoot()).show(getMainStage(), auxclasspathFiles.getValue(), auxclasspathFiles::setValue);
    }


    public Var<List<Node>> currentRuleResultsProperty() {
        return nodeEditionCodeArea.currentRuleResultsProperty();
    }


    public Var<List<Node>> currentErrorNodesProperty() {
        return nodeEditionCodeArea.currentErrorNodesProperty();
    }


    public LanguageVersion getLanguageVersion() {
        return languageVersionUIProperty.getValue();
    }


    public void setLanguageVersion(LanguageVersion version) {
        languageVersionUIProperty.setValue(version);
    }


    public Var<LanguageVersion> languageVersionProperty() {
        return languageVersionUIProperty;
    }


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
    public List<File> getAuxclasspathFiles() {
        return auxclasspathFiles.getValue();
    }


    public void setAuxclasspathFiles(List<File> files) {
        auxclasspathFiles.setValue(files);
    }


    @Override
    public List<? extends SettingsOwner> getChildrenSettingsNodes() {
        return Collections.singletonList(defaultTestCase);
    }

    @Override
    public String getDebugName() {
        return "editor";
    }


    /**
     * Refreshes the AST and returns the new compilation unit if the parse didn't fail.
     */
    private static void initTreeView(ASTManager manager,
                                     AstTreeView treeView,
                                     Var<String> errorMessageProperty) {

        manager.sourceCodeProperty()
               .values()
               .filter(StringUtils::isBlank)
               .subscribe(code -> treeView.setAstRoot(null));

        manager.currentExceptionProperty()
               .values()
               .subscribe(e -> {
                   if (e == null) {
                       errorMessageProperty.setValue(null);
                   } else {
                       errorMessageProperty.setValue(sanitizeExceptionMessage(e));
                   }
               });

        manager.compilationUnitProperty()
               .values()
               .filter(Objects::nonNull)
               .subscribe(node -> {
                   errorMessageProperty.setValue("");
                   treeView.setAstRoot(node);
               });
    }


    // CUSTOM
    private static Collection<String> additionalStyleClasses(Node n) {
        if (n == null) {
            return emptySet();
        }
        switch (n.getXPathNodeName()) {
        case "Literal":
            if (n.jjtGetNumChildren() == 0) {
                break;
            }
        case "Arguments":
            if (n.jjtGetNumChildren() == 0) {
                // actually the ArgumentsList
                return emptyList();
            }
            // fallthrough
        case "TypeArgument":
            if (n.jjtGetNumChildren() == 0) {
                // wildcard
                return emptyList();
            }
        case "Expression":
        case "PrimaryExpression":
        case "VariableInitializer":
        case "Type":
        case "TypeBound":
        case "WildcardBound":
        case "ReferenceType":
        case "RSIGNEDSHIFT":
        case "RUNSIGNEDSHIFT":
        case "PrimaryPrefix":
        case "PrimarySuffix":
        case "ClassOrInterfaceBodyDeclaration":
        case "BlockStatement":
        case "Statement":
        case "AssignmentOperator":
        case "Annotation":
        case "MemberValue":
        case "MemberValuePairs":
        case "MemberSelector":
            // removed
            return Arrays.asList("removal-level", "depth-0");
        case "Name":
            switch (n.jjtGetParent().getXPathNodeName()) {
            case "ImportDeclaration":
            case "PackageDeclaration":
                return Arrays.asList("removal-level", "depth-1");
            case "MarkerAnnotation":
            case "SingleMemberAnnotation":
            case "NormalAnnotation":
                return Arrays.asList("removal-level", "depth-0");
            default:
                return emptyList();
            }
        case "AnnotationTypeBodyDeclaration":
        case "TypeDeclaration":
            // proposed removal, possibly controversial
            return Arrays.asList("removal-level", "depth-1");
        default:
        }
        return emptySet();

    }

}
