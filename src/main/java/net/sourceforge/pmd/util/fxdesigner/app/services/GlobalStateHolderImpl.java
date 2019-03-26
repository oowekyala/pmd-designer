/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.util.HashMap;
import java.util.List;

import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;

import com.sun.javafx.collections.ObservableMapWrapper;
import javafx.collections.ObservableMap;

/**
 * @author Clément Fournier
 */
public class GlobalStateHolderImpl implements GlobalStateHolder {

    private final Var<Node> globalCompilationUnit = Var.newSimpleVar(null);
    // CUSTOM
    private final Var<Node> globalOldCompilationUnit = Var.newSimpleVar(null);
    private final Var<LanguageVersion> globalLanguageVersion = Var.newSimpleVar(LanguageRegistryUtil.defaultLanguageVersion());

    @Override
    public Var<Node> writableGlobalOldCompilationUnitProperty() {
        return globalOldCompilationUnit;
    }

    @Override
    public Var<Node> writableGlobalCompilationUnitProperty() {
        return globalCompilationUnit;
    }


    @Override
    public Var<LanguageVersion> writeableGlobalLanguageVersionProperty() {
        return globalLanguageVersion;
    }
}
