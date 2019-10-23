package org.vaadin8.console;

import com.vaadin.server.VaadinSession;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.ui.UI;
import org.vaadin8.console.Console.Handler;

import java.io.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default handler for console.
 * 
 */
public class DefaultConsoleHandler implements Handler {

	private static final long serialVersionUID = 1L;

	private List<CommandProvider> commandProviders;
	private AtomicReference<Future<?>> lastCommand;
	private final ExecutorService executorService;

	public DefaultConsoleHandler(ExecutorService executorService) {
		this.executorService = executorService;
		lastCommand = new AtomicReference<>();
	}

	public void addCommandProvider(final CommandProvider commandProvider) {
		if (commandProviders == null) {
			commandProviders = new ArrayList<CommandProvider>();
		}
		commandProviders.add(commandProvider);
	}

	public void removeCommandProvider(final CommandProvider commandProvider) {
		if (commandProviders == null) {
			return;
		}
		commandProviders.remove(commandProvider);
	}

	public void removeAllCommandProviders() {
		if (commandProviders == null) {
			return;
		}
		commandProviders.clear();
	}

	static List<String> parseInput(final String input) {
		List<String> result = new ArrayList<>();

		StringBuilder buffer = new StringBuilder();
		char escapeChar = '\\';
		char strongQuoteChar = '\'';
		char weakQuoteChar = '"';

		boolean inArg = false;
		char quote = 0;
		boolean escape = false;

		for (char c : input.trim().toCharArray()) {
			if (escape) {
				if (quote == weakQuoteChar) {
					switch (c) {
						case '"':
						case '\\':
							buffer.append(c);
							break;
						default:
							buffer.append(escapeChar);
							buffer.append(c);
							break;
					}
				} else {
					switch (c) {
						case 't':
							buffer.append('\t');
							break;
						case 'r':
							buffer.append('\r');
							break;
						case 'n':
							buffer.append('\n');
							break;
						default:
							buffer.append(c);
							break;
					}
				}
				escape = false;
				continue;
			}

			if (c == escapeChar && quote != strongQuoteChar) {
				escape = true;
				continue;
			}

			if (c == strongQuoteChar || c == weakQuoteChar) {
				if (c == quote) {
					quote = 0;
					inArg = false;
					result.add(buffer.toString());
					buffer.setLength(0);
					continue;
				} else if (quote == 0) {
					quote = c;
					inArg = true;
					continue;
				}
			}

			if (quote != 0) {
				buffer.append(c);
			} else if (Character.isWhitespace(c)) {
				if (inArg) {
					inArg = false;
					result.add(buffer.toString());
					buffer.setLength(0);
				}
			} else {
				if (!inArg) {
					inArg = true;
				}
				buffer.append(c);
			}
		}

		if (escape) {
			buffer.append(escapeChar);
		}

		if (inArg) {
			result.add(buffer.toString());
		}

		return result;
	}

	public Set<String> getSuggestions(final Console console, final String input) {

		final String prefix = console.parseCommandPrefix(input);
		if (prefix != null) {
			final Set<String> matches = new HashSet<String>();
			final Set<String> cmds = getCommands(console);
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
		List<String> argv = parseInput(lastInput);

		if (!argv.isEmpty()) {
			Command command = getCommand(console, argv.get(0));
			if (command != null) {
				executeCommand(console, command, argv);
			} else {
				console.print("ERROR: " + argv.get(0) + ": command not found.");
				console.prompt();
			}
		} else {
			console.prompt();
		}
	}

	private void executeCommand(Console console, Command cmd, List<String> argv) {
		try {
			lastCommand.set(executorService.submit(() -> {
				try {
					try (PrintWriter out = new PrintWriter(new ConsoleWriter(console), true);
						 PrintWriter err = new PrintWriter(new ConsoleWriter(console), true)) {
						try {
							cmd.execute(argv, out, err);
						} catch (InterruptedException | InterruptedIOException | CancellationException e) {
							err.println("<aborted>");
						} catch (Throwable e) {
							StringWriter w = new StringWriter();
							PrintWriter pw = new PrintWriter(w);
							e.printStackTrace(pw);
							pw.flush();
							err.println(w.toString());
						}
					}
				} finally {
					lastCommand.set(null);
					run(console, console::prompt);
				}
			}));
		} catch (final Exception e) {
			console.println(e.getClass().getSimpleName() + ": " + e.getMessage());
			console.prompt();
		}
	}

	@Override
	public void controlCharReceived(Console console, char c) {
		if (Character.toLowerCase(c) == 'c') {
			Future<?> future = lastCommand.get();
			if (future != null) {
				future.cancel(true);
			}
		}
	}

	public Command getCommand(Console console, final String cmdName) {

		// Ask from the providers
		if (commandProviders != null) {
			for (final CommandProvider cp : commandProviders) {
				Command cmd = cp.getCommand(console, cmdName);
				if (cmd != null) {
					return cmd;
				}
			}
		}

		// Not found
		return null;
	}

	public Set<String> getCommands(Console console) {
		final Set<String> res = new HashSet<>();
		if (commandProviders != null) {
			for (final CommandProvider cp : commandProviders) {
				if (cp.getAvailableCommands(console) != null)
					res.addAll(cp.getAvailableCommands(console));
			}
		}
		return Collections.unmodifiableSet(res);
	}

	private static class ConsoleWriter extends Writer {
		private final Console console;
		private StringBuilder buffer;

		public ConsoleWriter(Console console) {
			this.console = console;
			buffer = new StringBuilder();
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			buffer.append(cbuf, off, len);
		}

		@Override
		public void flush() throws IOException {
			if (buffer.length() > 0) {
				String text = buffer.toString();
				buffer.setLength(0);
				run(console, () -> console.print(text));
			}
		}

		@Override
		public void close() throws IOException {
			flush();
		}
	}

	private static void run(Console console, Runnable r) {
		UI ui = console.getUI();
		if (ui != null) {
			VaadinSession session = ui.getSession();
			if (session != null) {
				if (session.hasLock()) {
					r.run();
				} else {
					session.access(() -> {
						r.run();
						if (ui.getPushConfiguration().getPushMode() == PushMode.MANUAL) {
							ui.push();
						}
					});
				}
			}
		}
	}
}
