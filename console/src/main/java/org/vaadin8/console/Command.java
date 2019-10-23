package org.vaadin8.console;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;

/**
 * Commands that can be executed against the Component instance. They
 * provide convenient string to method mapping. Basically, a Command is a
 * method that that can be executed in Component. It can have parameters or
 * not.
 *
 */
public interface Command extends Serializable {

    /**
     * Execute a Command with arguments.
     *
     * @param console
     * @param argv
     * @return
     * @throws Exception
     */
    void execute(List<String> argv, PrintWriter out, PrintWriter err) throws Exception;
}
