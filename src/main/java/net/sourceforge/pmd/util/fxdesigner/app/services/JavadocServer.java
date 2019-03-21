package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.util.fxdesigner.app.ApplicationComponent;
import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.util.LanguageRegistryUtil;

/**
 * Manages a set of language-specific servers.
 *
 * @author Clément Fournier
 */
public class JavadocServer implements ApplicationComponent {

    private final DesignerRoot designerRoot;
    private final Map<Language, LanguageJavadocServer> BY_LANG = new HashMap<>();

    public JavadocServer(DesignerRoot designerRoot) {
        this.designerRoot = designerRoot;

        LanguageRegistryUtil.getSupportedLanguages()
                            .forEach(lang -> BY_LANG.put(lang, new LanguageJavadocServer(lang, designerRoot)));

    }


    public Optional<LanguageJavadocServer> forLanguage(Language language) {
        return Optional.ofNullable(BY_LANG.get(language)).filter(LanguageJavadocServer::isReady);
    }


    @Override
    public DesignerRoot getDesignerRoot() {
        return designerRoot;
    }
}
