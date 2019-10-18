package org.vaadin8.console;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.vaadin8.console.Console.Command;
import org.vaadin8.console.Console.Handler;

/**
 * Default handler for console.
 * 
 */
public class DefaultConsoleHandler implements Handler {

	private static final long serialVersionUID = 1L;

	public void handleException(final Console console, final Exception e, final Command cmd, final String[] argv) {
		e.printStackTrace();
		console.println(e.getClass().getSimpleName() + ": " + e.getMessage());
	}

	public Set<String> getSuggestions(final Console console, final String input) {

		final String prefix = console.parseCommandPrefix(input);
		if (prefix != null) {
			final Set<String> matches = new HashSet<String>();
			final Set<String> cmds = console.getCommands();
			for (final String cmd : cmds) {
				if (cmd.startsWith(prefix)) {
					matches.add(cmd);
				}
			}
			return matches;
		}
		return null;
	}

	public void inputReceived(final Console console, final String lastInput) {
		try {
			CompletableFuture.supplyAsync(() -> {
						Command c = console.parseAndExecuteCommand(lastInput);
						if (c == null || !c.isKilled()) {
							// if command was killed, killReceived handler will issue the prompt
							console.prompt();
						}
						return "";
					})
					// in case of a blocking command, bypass pending long-polling rpc requests by setting up a heartbeat
					.completeOnTimeout(null, 1, TimeUnit.SECONDS)
					.whenComplete((result, error) -> { if (result == null) console.ping(); })
					.get();
		}
		catch (Exception ex) {
			handleException(console, ex, null, null);
		}
	}

	public void killReceived(final Console console) {
		console.abortLastCommand();
		console.prompt();
	}

	public void commandNotFound(final Console console, final String[] argv) {
		console.print("ERROR: " + argv[0] + ": command not found.");
	}

}
