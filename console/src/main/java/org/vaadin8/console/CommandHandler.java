package org.vaadin8.console;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Interface for providing Commands to the console. One can register a
 * command providers to console instead of individual commands to provide a
 * lot of commands.
 *
 */
public interface CommandHandler extends Serializable {
    String suggest(List<String> argv) throws Exception;

    String execute(List<String> argv, PrintWriter out, PrintWriter err) throws Exception;
}
