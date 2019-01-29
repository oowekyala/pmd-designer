/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.LineNumberFactory;
import org.reactfx.value.Val;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.symboltable.NameOccurrence;
import net.sourceforge.pmd.util.ClasspathClassLoader;
import net.sourceforge.pmd.util.fxdesigner.app.AbstractController;
import net.sourceforge.pmd.util.fxdesigner.app.CompositeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.app.NodeSelectionSource;
import net.sourceforge.pmd.util.fxdesigner.model.ASTManager;
import net.sourceforge.pmd.util.fxdesigner.model.ParseAbortedException;
import net.sourceforge.pmd.util.fxdesigner.popups.AuxclasspathSetupController;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.TextAwareNodeWrapper;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.AvailableSyntaxHighlighters;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.HighlightLayerCodeArea;
import net.sourceforge.pmd.util.fxdesigner.util.codearea.HighlightLayerCodeArea.LayerId;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ASTTreeItem;
import net.sourceforge.pmd.util.fxdesigner.util.controls.AstTreeView;
import net.sourceforge.pmd.util.fxdesigner.util.controls.ToolbarTitledPane;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;


/**
 * One editor, i.e. source editor and ast tree view.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public class SourceEditorController extends AbstractController<MainDesignerController> implements CompositeSelectionSource {

    private static final Duration AST_REFRESH_DELAY = Duration.ofMillis(100);

    @FXML
    private ToolbarTitledPane astTitledPane;
    @FXML
    private ToolbarTitledPane editorTitledPane;
    @FXML
    private MenuButton languageSelectionMenuButton;
    @FXML
    private AstTreeView astTreeView;
    @FXML
    private HighlightLayerCodeArea<StyleLayerIds> codeEditorArea;

    private final ASTManager astManager;

    private final Var<Node> currentFocusNode = Var.newSimpleVar(null);
    private final Var<List<File>> auxclasspathFiles = Var.newSimpleVar(emptyList());
    private final Val<ClassLoader> auxclasspathClassLoader = auxclasspathFiles.map(fileList -> {
        try {
            return new ClasspathClassLoader(fileList, SourceEditorController.class.getClassLoader());
        } catch (IOException e) {
            e.printStackTrace();
            return SourceEditorController.class.getClassLoader();
        }
    });

    private Var<LanguageVersion> languageVersionUIProperty;


    public SourceEditorController(MainDesignerController mainController) {
        super(mainController);
        astManager = new ASTManager(mainController.getDesignerRoot());
    }


    @Override
    protected void beforeParentInit() {

        astTreeView.setDesignerRoot(getDesignerRoot());

        initializeLanguageSelector(); // languageVersionProperty() must be initialized

        languageVersionProperty().values()
                                 .filterMap(Objects::nonNull, LanguageVersion::getLanguage)
                                 .distinct()
                                 .subscribe(this::updateSyntaxHighlighter);

        languageVersionProperty().values()
                                 .filter(Objects::nonNull)
                                 .map(LanguageVersion::getShortName)
                                 .map(lang -> "Source Code (" + lang + ")")
                                 .subscribe(editorTitledPane::setTitle);

        codeEditorArea.plainTextChanges()
                      .filter(t -> !t.isIdentity())
                      .successionEnds(AST_REFRESH_DELAY)
                      // Refresh the AST anytime the text, classloader, or language version changes
                      .or(auxclasspathClassLoader.changes())
                      .or(languageVersionProperty().changes())
                      .subscribe(tick -> {
                          // Discard the AST if the language version has changed
                          tick.ifRight(c -> astTreeView.setRoot(null));
                          Platform.runLater(parent::refreshAST);
                      });

        codeEditorArea.setParagraphGraphicFactory(lineNumberFactory());

    }


    @Override
    public void afterParentInit() {
        DesignerUtil.rewire(astManager.languageVersionProperty(), languageVersionUIProperty);
    }


    private void initializeLanguageSelector() {

        ToggleGroup languageToggleGroup = new ToggleGroup();

        DesignerUtil.getSupportedLanguageVersions()
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

        languageVersionUIProperty = DesignerUtil.mapToggleGroupToUserData(languageToggleGroup, DesignerUtil::defaultLanguageVersion);
    }

    private IntFunction<javafx.scene.Node> lineNumberFactory() {
        IntFunction<javafx.scene.Node> base = LineNumberFactory.get(codeEditorArea);
        Val<Integer> activePar = Val.wrap(codeEditorArea.currentParagraphProperty());

        return idx -> {

            javafx.scene.Node label = base.apply(idx);

            activePar.conditionOnShowing(label)
                     .values()
                     .subscribe(p -> label.pseudoClassStateChanged(PseudoClass.getPseudoClass("has-caret"), idx == p));

            // adds a pseudo class if part of the focus node appears on this line
            currentFocusNode.conditionOnShowing(label)
                            .values()
                            .subscribe(n -> label.pseudoClassStateChanged(PseudoClass.getPseudoClass("is-focus-node"),
                                                                          n != null && idx + 1 <= n.getEndLine() && idx + 1 >= n.getBeginLine()));

            return label;
        };
    }


    @Override
    public ObservableSet<? extends NodeSelectionSource> getSubSelectionSources() {
        return FXCollections.observableSet(astTreeView);
    }


    /**
     * Refreshes the AST and returns the new compilation unit if the parse didn't fail.
     */
    public Optional<Node> refreshAST() {
        String source = getText();

        if (StringUtils.isBlank(source)) {
            astTreeView.setRoot(null);
            return Optional.empty();
        }

        Optional<Node> current;

        try {
            current = astManager.updateIfChanged(source, auxclasspathClassLoader.getValue());
        } catch (ParseAbortedException e) {
            astTitledPane.setTitle("Abstract syntax tree (error)");
            return Optional.empty();
        }

        current.ifPresent(this::setUpToDateCompilationUnit);
        return current;
    }


    public void showAuxclasspathSetupPopup() {
        new AuxclasspathSetupController(getDesignerRoot())
            .show(getMainStage(), auxclasspathFiles.getValue(), auxclasspathFiles::setValue);
    }

    private void setUpToDateCompilationUnit(Node node) {
        parent.invalidateAst();
        astTitledPane.setTitle("Abstract syntax tree");
        ASTTreeItem root = ASTTreeItem.getRoot(node);
        astTreeView.setRoot(root);
    }


    private void updateSyntaxHighlighter(Language language) {
        codeEditorArea.setSyntaxHighlighter(AvailableSyntaxHighlighters.getHighlighterForLanguage(language)
                                                                       .orElse(null));
    }


    /** Clears the error nodes. */
    public void clearErrorNodes() {
        codeEditorArea.clearStyleLayer(StyleLayerIds.ERROR);
    }


    /** Clears the name occurrences. */
    public void clearNameOccurences() {
        codeEditorArea.clearStyleLayer(StyleLayerIds.NAME_OCCURENCE);
    }


    /** Clears the highlighting of XPath results. */
    public void clearXPathHighlight() {
        codeEditorArea.clearStyleLayer(StyleLayerIds.XPATH_RESULT);
    }


    /**
     * Highlights the given node (or nothing if null).
     * Removes highlighting on the previously highlighted node.
     */
    @Override
    public void setFocusNode(Node node) {
        // editor is always scrolled when re-selecting a node
        if (node != null) {
            Platform.runLater(() -> scrollEditorToNode(node));
        }

        if (Objects.equals(node, currentFocusNode.getValue())) {
            return;
        }

        currentFocusNode.setValue(node);

        // editor is only restyled if the selection has changed
        Platform.runLater(() -> codeEditorArea.styleNodes(node == null ? emptyList() : singleton(node), StyleLayerIds.FOCUS, true));
    }


    /** Highlights xpath results (xpath highlight). */
    public void highlightXPathResults(Collection<? extends Node> nodes) {
        codeEditorArea.styleNodes(nodes, StyleLayerIds.XPATH_RESULT, true);
    }


    /** Highlights name occurrences (secondary highlight). */
    public void highlightNameOccurrences(Collection<? extends NameOccurrence> occs) {
        codeEditorArea.styleNodes(occs.stream().map(NameOccurrence::getLocation).collect(Collectors.toList()), StyleLayerIds.NAME_OCCURENCE, true);
    }


    /** Highlights nodes that are in error (secondary highlight). */
    public void highlightErrorNodes(Collection<? extends Node> nodes) {
        codeEditorArea.styleNodes(nodes, StyleLayerIds.ERROR, true);
        if (!nodes.isEmpty()) {
            scrollEditorToNode(nodes.iterator().next());
        }
    }


    /** Scroll the editor to a node and makes it visible. */
    private void scrollEditorToNode(Node node) {

        codeEditorArea.moveTo(node.getBeginLine() - 1, 0);

        if (codeEditorArea.getVisibleParagraphs().size() < 1) {
            return;
        }

        int visibleLength = codeEditorArea.lastVisibleParToAllParIndex() - codeEditorArea.firstVisibleParToAllParIndex();

        if (node.getEndLine() - node.getBeginLine() > visibleLength
                || node.getBeginLine() < codeEditorArea.firstVisibleParToAllParIndex()) {
            codeEditorArea.showParagraphAtTop(Math.max(node.getBeginLine() - 2, 0));
        } else if (node.getEndLine() > codeEditorArea.lastVisibleParToAllParIndex()) {
            codeEditorArea.showParagraphAtBottom(Math.min(node.getEndLine(), codeEditorArea.getParagraphs().size()));
        }
    }


    public void clearStyleLayers() {
        codeEditorArea.clearStyleLayers();
    }


    /** Moves the caret to a position and makes the view follow it. */
    public void moveCaret(int line, int column) {
        codeEditorArea.moveTo(line, column);
        codeEditorArea.requestFollowCaret();
    }


    public TextAwareNodeWrapper wrapNode(Node node) {
        return codeEditorArea.wrapNode(node);
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

    /**
     * Returns the most up-to-date compilation unit, or empty if it can't be parsed.
     */
    public Optional<Node> getCompilationUnit() {
        return astManager.getCompilationUnit();
    }


    @PersistentProperty
    public String getText() {
        return codeEditorArea.getText();
    }


    public void setText(String expression) {
        codeEditorArea.replaceText(expression);
    }


    public Val<String> textProperty() {
        return Val.wrap(codeEditorArea.textProperty());
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


    /** Style layers for the code area. */
    private enum StyleLayerIds implements LayerId {
        // caution, the name of the constants are used as style classes

        /** For the currently selected node. */
        FOCUS,
        /** For declaration usages. */
        NAME_OCCURENCE,
        /** For nodes in error. */
        ERROR,
        /** For xpath results. */
        XPATH_RESULT;

        private final String styleClass; // the id will be used as a style class


        StyleLayerIds() {
            this.styleClass = name().toLowerCase(Locale.ROOT).replace('_', '-') + "-highlight";
        }


        /** focus-highlight, xpath-highlight, error-highlight, name-occurrence-highlight */
        @Override
        public String getStyleClass() {
            return styleClass;
        }

    }
}
