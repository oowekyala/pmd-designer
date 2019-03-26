/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.model;

import org.reactfx.EventStream;
import org.reactfx.collection.LiveList;
import org.reactfx.value.Var;

import net.sourceforge.pmd.lang.rule.XPathRule;
import net.sourceforge.pmd.util.fxdesigner.util.DesignerUtil;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil.PersistentProperty;


/**
 * Specialises rule builders for XPath rules.
 *
 * @author Clément Fournier
 * @since 6.0.0
 */
public class ObservableXPathRuleBuilder extends ObservableRuleBuilder {


    private final Var<String> xpathVersion = Var.newSimpleVar(DesignerUtil.defaultXPathVersion());
    private final Var<String> xpathExpression = Var.newSimpleVar("");

    public ObservableXPathRuleBuilder() {
        setClazz(XPathRule.class);
    }

    @PersistentProperty
    public String getXpathVersion() {
        return xpathVersion.getValue();
    }


    public void setXpathVersion(String xpathVersion) {
        this.xpathVersion.setValue(xpathVersion);
    }


    public Var<String> xpathVersionProperty() {
        return xpathVersion;
    }


    @PersistentProperty
    public String getXpathExpression() {
        return xpathExpression.getValue();
    }


    public void setXpathExpression(String value) {
        xpathExpression.setValue(value);
    }


    public Var<String> xpathExpressionProperty() {
        return xpathExpression;
    }


    /**
     * Pushes an event every time the rule needs to be re-evaluated.
     */
    public EventStream<?> modificationsTicks() {
        return languageProperty().values()
                             .or(xpathVersion.values())
                             .or(xpathExpression.values())
                             .or(rulePropertiesProperty().values().flatMap(LiveList::changesOf));
    }

    // TODO: Once the xpath expression changes, we'll need to rebuild the rule
    //    @Override
    //    public Optional<Rule> build() throws IllegalArgumentException {
    //        return super.build();
    //    }
}
