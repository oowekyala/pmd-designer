/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util;

import java.util.Objects;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;


/**
 * Represents a non-empty range of language versions.
 * Necessarily bound to a single language.
 * TODO refactor XPath export wizard to use that
 * TODO refactor Rule to use that
 *
 * @author Clément Fournier
 */
public final class LanguageVersionRange {

    private final Language language;
    // if either of those are null then the range is unbounded in the direction
    // if both are null then the language is the only comparison made by include
    private final LanguageVersion min;
    private final LanguageVersion max;


    private LanguageVersionRange(Language language, LanguageVersion min, LanguageVersion max) {
        this.language = Objects.requireNonNull(language);
        this.min = min;
        this.max = max;

        if (min != null && max != null) {
            assert min.getLanguage().equals(max.getLanguage());
            assert min.compareTo(max) <= 0;
        }
    }


    /** Gets the language this range applies to. Never null. */
    public Language getLanguage() {
        return language;
    }


    /** Gets the minimum version allowed, inclusive. If null then there is no lower bound. */
    public LanguageVersion getMin() {
        return min;
    }


    /** Gets the maximum version allowed, inclusive. If null then there is no upper bound. */
    public LanguageVersion getMax() {
        return max;
    }


    /**
     * Returns true if this range contains the given version.
     * This is only true if the language of the given version
     * is the same as the language of this range and if the
     * version is included inside this range.
     */
    public boolean includes(LanguageVersion version) {
        return getLanguage().equals(version.getLanguage())
            && (min == null || version.compareTo(min) >= 0)
            && (max == null || version.compareTo(max) <= 0);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LanguageVersionRange that = (LanguageVersionRange) o;
        return Objects.equals(min, that.min) &&
            Objects.equals(max, that.max);
    }


    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    /* Construction */


    public static LanguageVersionRange boundedBy(LanguageVersion min, LanguageVersion max) {
        if (min == null && max == null) {
            throw new IllegalArgumentException("LanguageVersionRange.boundedBy expects at least one non-null argument");
        }

        Language lang = min == null ? max.getLanguage() : min.getLanguage();

        return new LanguageVersionRange(lang, min, max);
    }


    public static LanguageVersionRange allOf(Language language) {
        if (language == null) {
            throw new IllegalArgumentException("LanguageVersionRange.allOf expects a non-null argument");
        }
        return new LanguageVersionRange(language, null, null);
    }


    public static LanguageVersionRange only(LanguageVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("LanguageVersionRange.only expects a non-null argument");
        }
        return new LanguageVersionRange(version.getLanguage(), version, version);
    }


}
