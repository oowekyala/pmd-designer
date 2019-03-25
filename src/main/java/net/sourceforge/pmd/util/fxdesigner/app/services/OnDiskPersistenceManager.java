/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsOwner;
import net.sourceforge.pmd.util.fxdesigner.util.beans.SettingsPersistenceUtil;

/**
 * Default persistence manager.
 *
 * @author Clément Fournier
 */
public class OnDiskPersistenceManager implements PersistenceManager {

    private final DesignerRoot root;
    private final Path input;
    private final Path output;

    public OnDiskPersistenceManager(DesignerRoot root, Path input, Path output) {
        this.root = root;
        this.input = input;
        this.output = output;
    }


    @Override
    public DesignerRoot getDesignerRoot() {
        return root;
    }

    @Override
    public void restoreSettings(SettingsOwner settingsOwner) {
        if (input == null || !Files.isRegularFile(input) || !Files.exists(input)) {
            return;
        }
        try {
            SettingsPersistenceUtil.restoreProperties(settingsOwner, input.toFile());
        } catch (Exception e) {
            // shouldn't prevent the app from opening
            // in case the file is corrupted, it will be overwritten on shutdown
            logInternalException(e);
        }
    }

    @Override
    public void persistSettings(SettingsOwner settingsOwner) {
        if (output == null) {
            return;
        }

        try {
            SettingsPersistenceUtil.persistProperties(settingsOwner, output.toFile());
        } catch (Exception e) {
            // nevermind
            e.printStackTrace();
        }
    }
}
