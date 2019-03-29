/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.app.services;

import org.reactfx.value.Val;

import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.util.fxdesigner.SourceEditorController;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.model.ParseAbortedException;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsOwner;


/**
 * Manages a compilation unit for {@link SourceEditorController}.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public interface ASTManager extends ApplicationComponent, SettingsOwner {


    Val<String> sourceCodeProperty();


    Val<LanguageVersion> languageVersionProperty();


    Val<Node> compilationUnitProperty();


    Val<ClassLoader> classLoaderProperty();


    Val<ParseAbortedException> currentExceptionProperty();

}