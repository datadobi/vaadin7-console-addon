package org.vaadin8.console;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultCommandProvider implements CommandProvider {
    private final Map<String, Command> commands = new HashMap<>();

    /**
     * Add a Command to this Console.
     * <p>
     * This will override the any commands of the same name available via
     * {@link CommandProvider}.
     */
    public void addCommand(final String name, final Command cmd) {
        commands.put(name, cmd);
    }

    /**
     * Remove a command from this console.
     * <p>
     * This does not remove Command available from {@link CommandProvider}.
     *
     * @param cmdName
     */
    public void removeCommand(final String cmdName) {
        commands.remove(cmdName);
    }

    @Override
    public Set<String> getAvailableCommands(Console console) {
        return new HashSet<>(commands.keySet());
    }

    @Override
    public Command getCommand(Console console, String commandName) {
        return commands.get(commandName);
    }
}
