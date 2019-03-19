package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.io.File;

import net.sourceforge.pmd.util.fxdesigner.app.DesignerRoot;

/**
 * Only uses default settings.
 *
 * @author Clément Fournier
 */
public class ForgetfulPersistenceManager extends OnDiskPersistenceManager {

    public ForgetfulPersistenceManager(DesignerRoot root, File input, File output) {
        super(root, null, null);
    }
}
