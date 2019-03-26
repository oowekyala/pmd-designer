/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.util.List;

import org.reactfx.value.Val;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;

import javafx.collections.ObservableMap;

/**
 * Logs events. Stores the whole log in case no view was open.
 *
 * @author Clément Fournier
 * @since 6.13.0
 */
public interface GlobalStateHolder {


    Var<LanguageVersion> writeableGlobalLanguageVersionProperty();


    // Those are here to search for write-access usages more easily
    Var<Node> writableGlobalCompilationUnitProperty();


    /**
     * Returns the compilation unit of the main editor. Empty if the source
     * is unparsable.
     */
    default Val<Node> globalCompilationUnitProperty() {
        return writableGlobalCompilationUnitProperty();
    }

    // CUSTOM
    Var<Node> writableGlobalOldCompilationUnitProperty();


    // CUSTOM
    default Val<Node> globalOldCompilationUnitProperty() {
        return writableGlobalOldCompilationUnitProperty();
    }


    /**
     * Returns the language version selected on the app. Never empty.
     */
    default Val<LanguageVersion> globalLanguageVersionProperty() {
        return writeableGlobalLanguageVersionProperty();
    }


    default Val<Language> globalLanguageProperty() {
        return globalLanguageVersionProperty().map(LanguageVersion::getLanguage);
    }


    default LanguageVersion getGlobalLanguageVersion() {
        return globalLanguageVersionProperty().getValue();
    }


}
