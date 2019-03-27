/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;


import static net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil.sanitizeExceptionMessage;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.reactfx.EventStreams;
import org.reactfx.Subscription;
import org.reactfx.SuspendableEventStream;
import org.reactfx.collection.LiveArrayList;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.rule.xpath.XPathRuleQuery;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.app.services.CloseableService;
import net.sourceforge.pmd.util.fxdesigner.app.services.LogEntry.Category;
import net.sourceforge.pmd.util.fxdesigner.app.services.ASTManager;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.model.ObservableXPathRuleBuilder;
import net.sourceforge.pmd.util.fxdesigner.model.XPathEvaluationException;
import net.sourceforge.pmd.util.fxdesigner.model.XPathEvaluator;
import net.sourceforge.pmd.util.fxdesigner.popups.ExportXPathWizardController;
import net.sourceforge.pmd.util.fxdesigner.util.DataHolder;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;
import net.sourceforge.pmd.util.fxdesigner.util.SoftReferenceCache;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.autocomplete.CompletionResultSource;
import net.sourceforge.pmd.util.fxdesigner.util.autocomplete.XPathAutocompleteProvider;
import net.sourceforge.pmd.util.fxdesigner.util.autocomplete.XPathCompletionSource;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.SyntaxHighlightingCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.syntaxhighlighting.XPathSyntaxHighlighter;
import net.sourceforge.pmd.util.fxdesigner.util.controls.HelpfulPlaceholder;
import net.sourceforge.pmd.util.fxdesigner.util.controls.PropertyTableView;
import net.sourceforge.pmd.util.fxdesigner.util.controls.TitleOwner;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;
import net.sourceforge.pmd.util.fxdesigner.util.controls.XpathViolationListCell;
import net.sourceforge.pmd.util.fxdesigner.util.reactfx.ReactfxUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


/**
 * Editor for an XPath rule. This object maintains an {@link ObservableRuleBuilder} which stores information
 * about the currently edited rule. The properties of that builder are rewired to the export wizard's fields
 * when it's open. The wizard is just one view on the builder's data, which is supposed to offer the most
 * customization options. Other views can be implemented in a similar way, for example, PropertyView
 * implements a view over the properties of the builder.
 *
 * @author Clément Fournier
 * @see ExportXPathWizardController
 * @since 6.0.0
 */
public final class XPathRuleEditorController extends AbstractController implements NodeSelectionSource, TitleOwner, CloseableService {

    private static final String NO_MATCH_MESSAGE = "No match in text";
    private static final Duration XPATH_REFRESH_DELAY = Duration.ofMillis(100);
    private static final Pattern JAXEN_MISSING_PROPERTY_EXTRACTOR = Pattern.compile("Variable (\\w+)");
    private static final Pattern SAXON_MISSING_PROPERTY_EXTRACTOR = Pattern.compile("Undeclared variable in XPath expression: \\$(\\w+)");
    private final SoftReferenceCache<ExportXPathWizardController> exportWizard;
    private final ObservableXPathRuleBuilder ruleBuilder;
    private final Var<ObservableList<Node>> myXpathResults = Var.newSimpleVar(null);
    private final Var<List<Node>> currentResults = Var.newSimpleVar(Collections.emptyList());
    @FXML
    public ToolbarTitledPane expressionTitledPane;
    @FXML
    public Button exportXpathToRuleButton;
    @FXML
    private MenuButton xpathVersionMenuButton;
    @FXML
    private PropertyTableView propertyTableView;
    @FXML
    private SyntaxHighlightingCodeArea xpathExpressionArea;
    @FXML
    private ToolbarTitledPane violationsTitledPane;
    @FXML
    private ListView<TextAwareNodeWrapper> xpathResultListView;
    // ui property
    private Var<String> xpathVersionUIProperty = Var.newSimpleVar(XPathRuleQuery.XPATH_2_0);
    private SuspendableEventStream<TextAwareNodeWrapper> selectionEvents;
    @FXML
    private Label languageLabel;
    @FXML
    // CUSTOM
    private ToggleButton oldJavaToggle;

    public XPathRuleEditorController(DesignerRoot root) {
        this(root, new ObservableXPathRuleBuilder());
    }

    /**
     * Creates a controller with an existing rule builder.
     */
    public XPathRuleEditorController(DesignerRoot root, ObservableXPathRuleBuilder ruleBuilder) {
        super(root);
        this.ruleBuilder = ruleBuilder;

        this.exportWizard = new SoftReferenceCache<>(() -> new ExportXPathWizardController(getDesignerRoot()));
    }

    @Override
    protected void beforeParentInit() {

        initGenerateXPathFromStackTrace();
        initialiseVersionSelection();

        expressionTitledPane.titleProperty().bind(xpathVersionUIProperty.map(v -> "XPath Expression (" + v + ")"));

        xpathResultListView.setCellFactory(v -> new XpathViolationListCell());

        exportXpathToRuleButton.setOnAction(e -> showExportXPathToRuleWizard());

        getRuleBuilder().modificationsTicks()
                        .or(getService(DesignerRoot.AST_MANAGER).compilationUnitProperty().values())
                        .successionEnds(XPATH_REFRESH_DELAY)
                        .subscribe(tick -> {

                            ASTManager manager = oldJavaToggle.isSelected() // CUSTOM
                                                 ? getService(DesignerRoot.OLD_AST_MANAGER)
                                                 : getService(DesignerRoot.AST_MANAGER);

                            refreshResults(manager);
                        });

        selectionEvents = EventStreams.valuesOf(xpathResultListView.getSelectionModel().selectedItemProperty()).suppressible();

        initNodeSelectionHandling(getDesignerRoot(),
                                  selectionEvents.filter(Objects::nonNull).map(TextAwareNodeWrapper::getNode).map(NodeSelectionEvent::of),
                                  false);

        violationsTitledPane.titleProperty().bind(currentResults.map(List::size).map(n -> "Matched nodes (" + n + ")"));

    }


    private void initialiseVersionSelection() {
        ToggleGroup xpathVersionToggleGroup = new ToggleGroup();

        List<String> versionItems = new ArrayList<>();
        versionItems.add(XPathRuleQuery.XPATH_1_0);
        versionItems.add(XPathRuleQuery.XPATH_1_0_COMPATIBILITY);
        versionItems.add(XPathRuleQuery.XPATH_2_0);

        versionItems.forEach(v -> {
            RadioMenuItem item = new RadioMenuItem("XPath " + v);
            item.setUserData(v);
            item.setToggleGroup(xpathVersionToggleGroup);
            xpathVersionMenuButton.getItems().add(item);
        });

        xpathVersionUIProperty = DesignerUtil.mapToggleGroupToUserData(xpathVersionToggleGroup, DesignerUtil::defaultXPathVersion);

        xpathVersionProperty().setValue(XPathRuleQuery.XPATH_2_0);
    }


    private void initGenerateXPathFromStackTrace() {

        ContextMenu menu = new ContextMenu();

        MenuItem item = new MenuItem("Generate from stack trace...");
        item.setOnAction(e -> {
            try {
                Stage popup = new Stage();
                FXMLLoader loader = new FXMLLoader(DesignerUtil.getFxml("generate-xpath-from-stack-trace.fxml"));
                Parent root = loader.load();
                Button button = (Button) loader.getNamespace().get("generateButton");
                TextArea area = (TextArea) loader.getNamespace().get("stackTraceArea");

                ValidationSupport validation = new ValidationSupport();

                validation.registerValidator(area, Validator.createEmptyValidator("The stack trace may not be empty"));
                button.disableProperty().bind(validation.invalidProperty());

                button.setOnAction(f -> {
                    DesignerUtil.stackTraceToXPath(area.getText()).ifPresent(xpathExpressionArea::replaceText);
                    popup.close();
                });

                popup.setScene(new Scene(root));
                popup.initStyle(StageStyle.UTILITY);
                popup.initModality(Modality.WINDOW_MODAL);
                popup.initOwner(getDesignerRoot().getMainStage());
                popup.show();
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
        });

        menu.getItems().add(item);

        xpathExpressionArea.addEventHandler(MouseEvent.MOUSE_CLICKED, t -> {
            if (t.getButton() == MouseButton.SECONDARY) {
                menu.show(xpathExpressionArea, t.getScreenX(), t.getScreenY());
            }
        });
    }

    @Override
    public void close() {
        xpathExpressionArea.setSyntaxHighlighter(null);
    }

    @Override
    public void afterParentInit() {
        // CUSTOM
        if (getRuleBuilder().getLanguage().getTerseName().equals("oldjava")) {
            oldJavaToggle.setSelected(true);
        }

        bindToParent();

        // init autocompletion only after binding to mediator and settings restore
        // otherwise the popup is shown on startup
        Supplier<CompletionResultSource> suggestionMaker = () -> XPathCompletionSource.forLanguage(getRuleBuilder().getLanguage());
        new XPathAutocompleteProvider(xpathExpressionArea, suggestionMaker).initialiseAutoCompletion();


    }

    // Binds the underlying rule parameters to the mediator UI, disconnecting it from the wizard if need be
    private void bindToParent() {
        // CUSTOM
        DesignerUtil.rewire(getRuleBuilder().languageProperty(),
                            LanguageRegistryUtil.oldJavaLangProperty(ReactfxUtil.booleanVar(oldJavaToggle.selectedProperty())));

        ReactfxUtil.rewireInit(getRuleBuilder().xpathVersionProperty(), xpathVersionProperty());
        ReactfxUtil.rewireInit(getRuleBuilder().xpathExpressionProperty(), xpathExpressionProperty());

        DesignerUtil.rewireInit(getRuleBuilder().rulePropertiesProperty(),
                                propertyTableView.rulePropertiesProperty(),
                                propertyTableView::setRuleProperties);

        xpathExpressionArea.setSyntaxHighlighter(new XPathSyntaxHighlighter());

    }

    @Override
    public void setFocusNode(final Node node, DataHolder options) {
        Optional<TextAwareNodeWrapper> firstResult = xpathResultListView.getItems().stream()
                                                                        .filter(wrapper -> wrapper.getNode().equals(node))
                                                                        .findFirst();

        // with Java 9, Optional#ifPresentOrElse can be used
        if (firstResult.isPresent()) {
            selectionEvents.suspendWhile(() -> xpathResultListView.getSelectionModel().select(firstResult.get()));
        } else {
            xpathResultListView.getSelectionModel().clearSelection();
        }
    }

    /**
     * Evaluate the contents of the XPath expression area
     * on the global compilation unit. This updates the xpath
     * result panel, and can log XPath exceptions to the
     * event log panel.
     */
    private void refreshResults(ASTManager manager) {

        try {
            String xpath = xpathExpressionProperty().getValue();
            if (StringUtils.isBlank(xpath)) {
                updateResults(false, false, Collections.emptyList(), "Type an XPath expression to show results");
                return;
            }

            Node compilationUnit = manager.compilationUnitProperty().getValue();

            if (compilationUnit == null) {
                updateResults(false, true, Collections.emptyList(), "Compilation unit is invalid");
                return;
            }


            LanguageVersion version = manager.languageVersionProperty().getValue();

            ObservableList<Node> results
                = FXCollections.observableArrayList(XPathEvaluator.evaluateQuery(compilationUnit,
                                                                                 version,
                                                                                 getRuleBuilder().getXpathVersion(),
                                                                                 xpath,
                                                                                 ruleBuilder.getRuleProperties()));

            updateResults(false, false, results, NO_MATCH_MESSAGE);
            // Notify that everything went OK so we can avoid logging very recent exceptions
            raiseParsableXPathFlag();
        } catch (XPathEvaluationException e) {
            updateResults(true, false, Collections.emptyList(), sanitizeExceptionMessage(e));
            logUserException(e, Category.XPATH_EVALUATION_EXCEPTION);
        }

    }


    public void showExportXPathToRuleWizard() {
        ExportXPathWizardController wizard = exportWizard.get();
        wizard.showYourself(bindToExportWizard(wizard));
    }


    /**
     * Binds the properties of the panel to the export wizard.
     *
     * @param exportWizard The caller
     */
    private Subscription bindToExportWizard(ExportXPathWizardController exportWizard) {

        return exportWizard.bindToRuleBuilder(getRuleBuilder()).and(this::bindToParent);

    }


    public Var<String> xpathExpressionProperty() {
        return Var.fromVal(xpathExpressionArea.textProperty(), xpathExpressionArea::replaceText);
    }


    public Var<String> xpathVersionProperty() {
        return xpathVersionUIProperty;
    }


    public ObservableXPathRuleBuilder getRuleBuilder() {
        return ruleBuilder;
    }


    @Override
    public Val<String> titleProperty() {

        Val<Function<String, String>> languagePrefix =
            getRuleBuilder().languageProperty()
                            .map(Language::getTerseName)
                            .map(lname -> rname -> lname + "/" + rname);

        return getRuleBuilder().nameProperty()
                               .orElseConst("NewRule")
                               .mapDynamic(languagePrefix);
    }

    public Val<List<Node>> currentResultsProperty() {
        return currentResults;
    }

    public Var<ObservableList<Node>> xpathResultsProperty() {
        return myXpathResults;
    }

    private void updateResults(boolean xpathError,
                               boolean otherError,
                               List<Node> results,
                               String emptyResultsPlaceholder) {

        javafx.scene.Node emptyLabel = xpathError || otherError
                                       ? getErrorPlaceholder(emptyResultsPlaceholder)
                                       : new Label(emptyResultsPlaceholder);

        xpathResultListView.setPlaceholder(emptyLabel);

        xpathResultListView.setItems(results.stream().map(getDesignerRoot().getService(DesignerRoot.RICH_TEXT_MAPPER)::wrapNode).collect(Collectors.toCollection(LiveArrayList::new)));
        this.currentResults.setValue(results);
        // only show the error label here when it's an xpath error
        expressionTitledPane.errorMessageProperty().setValue(xpathError ? emptyResultsPlaceholder : "");
    }


    private javafx.scene.Node getErrorPlaceholder(String message) {

        return getMissingPropertyName(message)
            .map(
                name ->
                    HelpfulPlaceholder.withMessage("Undeclared property in XPath expression: $" + name)
                                      .withSuggestedAction(
                                          "Add property",
                                          () -> propertyTableView.onAddPropertyClicked(name)
                                      )
            )
            .orElseGet(() -> HelpfulPlaceholder.withMessage(message))
            .withLeftColumn(new FontIcon("fas-exclamation-triangle"))
            .build();
    }


    private Optional<String> getMissingPropertyName(String errorMessage) {
        Pattern nameExtractor = XPathRuleQuery.XPATH_1_0.equals(getRuleBuilder().getXpathVersion())
                                ? JAXEN_MISSING_PROPERTY_EXTRACTOR
                                : SAXON_MISSING_PROPERTY_EXTRACTOR;

        Matcher matcher = nameExtractor.matcher(errorMessage);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

}
