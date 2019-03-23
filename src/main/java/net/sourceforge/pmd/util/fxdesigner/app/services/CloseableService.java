package net.sourceforge.pmd.util.fxdesigner.app.services;

/**
 * A service that has a shutdown hook called when the app terminates.
 *
 * @author Clément Fournier
 */
public interface CloseableService {


    void close() throws Exception;

}
