/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.app.services;

import java.nio.file.Path;

/**
 * Manages the settings directory for the current user. This includes
 * saving and restoring the state of the app, the
 *
 * @author Clément Fournier
 */
public interface GlobalDiskManager {

    /**
     * Gets the main settings directory of the app. This directory
     * contains all {@linkplain ResourceManager resource directories},
     * the files containing the user-specific settings, etc. By default
     * it's somewhere in {@code ${user.home}/.pmd}.
     */
    Path getSettingsDirectory();


    /**
     * The root resources directory. Resources are not user data, they
     * are meant for the app.
     */
    ResourceManager getRootResourcesManager();

}
