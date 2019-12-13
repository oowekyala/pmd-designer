/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.model;

import java.util.List;
import java.util.Objects;

public class VersionedXPathQuery {

    private final String version;
    private final String expression;
    private final List<PropertyDescriptorSpec> definedProperties;


    public VersionedXPathQuery(String version, String expression, List<PropertyDescriptorSpec> definedProperties) {
        this.version = version;
        this.expression = expression;
        this.definedProperties = definedProperties;
    }

    public String getVersion() {
        return version;
    }

    public String getExpression() {
        return expression;
    }

    public List<PropertyDescriptorSpec> getDefinedProperties() {
        return definedProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionedXPathQuery query = (VersionedXPathQuery) o;
        return Objects.equals(version, query.version)
            && Objects.equals(expression, query.expression)
            && Objects.equals(definedProperties, query.definedProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, expression, definedProperties);
    }
}
