package org.vaadin8.console;

import java.io.Serializable;
import java.util.Set;

/**
 * Interface for providing Commands to the console. One can register a
 * command providers to console instead of individual commands to provide a
 * lot of commands.
 *
 */
public interface CommandProvider extends Serializable {

    /**
     * List all available command from this provider.
     *
     * @param console
     * @return
     */
    Set<String> getAvailableCommands(Console console);

    /**
     * Get Command instance based on command name.
     *
     * @param console
     * @param commandName
     * @return
     */
    Command getCommand(Console console, String commandName);

}
