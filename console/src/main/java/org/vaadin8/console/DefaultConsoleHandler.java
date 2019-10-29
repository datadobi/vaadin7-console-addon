package org.vaadin8.console;

import com.vaadin.server.VaadinSession;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.ui.Component;
import com.vaadin.ui.UI;
import org.vaadin8.console.Console.Handler;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Default handler for console.
 * 
 */
public class DefaultConsoleHandler implements Handler {

	private static final long serialVersionUID = 1L;

	public final String outputStyle = null;
	public final String errorStyle = "term-color-red";
	private final CommandHandler handler;

	private List<String> history = new ArrayList<>();
	private int historyIndex = 0;

	private final ExecutorService executorService;
	private AtomicReference<Future<?>> runningCommand;

	public DefaultConsoleHandler(ExecutorService executorService, CommandHandler handler) {
		this.executorService = executorService;
		this.handler = handler;
		runningCommand = new AtomicReference<>();
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

	public void inputReceived(final Console console, final String input) {
		String trimmedInput = input.trim();
		List<String> argv = parseInput(trimmedInput);

		if (!argv.isEmpty()) {
			addToHistory(trimmedInput);
		}

		try {
			CompletableFuture<String> future = new CompletableFuture<>();
			ConsoleQueue outputQueue = new ConsoleQueue(console);

			Future<?> taskFuture = executorService.submit(() -> {
				if (future.isCancelled()) {
					return;
				}

				Timer t = new Timer();
				t.schedule(
						new TimerTask() {
							@Override
							public void run() {
								if (!future.isCancelled()) {
									outputQueue.flush();
								}
							}
						},
						250, 250
				);

				try {
					try (PrintWriter out = new PrintWriter(new ConsoleWriter(outputQueue, outputStyle), true);
						 PrintWriter err = new PrintWriter(new ConsoleWriter(outputQueue, errorStyle), true)) {
						try {
							future.complete(handler.execute(argv, out, err));
						} catch (InterruptedException | InterruptedIOException | CancellationException e) {
							// Ignored
						} catch (Throwable e) {
							StringWriter w = new StringWriter();
							PrintWriter pw = new PrintWriter(w);
							e.printStackTrace(pw);
							pw.flush();
							err.println(w.toString());
						}
					}
				} finally {
					t.cancel();
					if (!future.isCancelled()) {
						outputQueue.flush();
						future.complete(null);
					}
				}
			});

			runningCommand.set(future);
			future.whenComplete((newPs, throwable) -> {
				if (future.isCancelled()) {
					taskFuture.cancel(true);

					ConsoleQueue.run(console, () -> {
						outputQueue.flushQueue();
						console.println("<aborted>", errorStyle);
						console.prompt();
						runningCommand.set(null);
					});
				} else {
					ConsoleQueue.run(console, () -> {
						outputQueue.flushQueue();
						if (newPs != null) {
							console.setPs(newPs);
						}
						console.prompt();
						runningCommand.set(null);
					});
				}
			});
		} catch (final Exception e) {
			console.println(e.getClass().getSimpleName() + ": " + e.getMessage());
			console.prompt();
			runningCommand.set(null);
		}
	}

	@Override
	public void controlCharReceived(Console console, String key, int modifiers) {
		switch (key) {
			case "Esc":
			case "Escape":
				abortCommand();
				break;
		}
	}

	public boolean isCommandRunning() {
		return runningCommand.get() != null;
	}

	public boolean abortCommand() {
		Future<?> future = runningCommand.get();
		if (future != null) {
			future.cancel(true);
			return true;
		} else {
			return false;
		}
	}

	private void addToHistory(String input) {
		history.add(input);
		historyIndex = history.size();
	}

	@Override
	public void clearHistory(Console console) {
		history.clear();
		historyIndex = 0;
	}

	@Override
	public String nextCommand(Console console) {
		historyIndex++;
		if (historyIndex >= history.size()) {
			historyIndex = history.size();
			return "";
		} else {
			return history.get(historyIndex);
		}
	}

	@Override
	public String previousCommand(Console console) {
		historyIndex--;
		if (historyIndex < 0) {
			historyIndex = 0;
			return null;
		} else {
			return history.get(historyIndex);
		}
	}

	@Override
	public void suggest(Console console, String input) {
		executorService.submit(() -> {
			String suggestion = null;
			try {
				suggestion = handler.suggest(parseInput(input));
			} catch (InterruptedException e) {
				// Ignored
			} catch (Exception e) {
				// Ignored
			} finally {
				String finalSuggestion = suggestion;
				ConsoleQueue.run(console, () -> {
					if (finalSuggestion == null) {
						console.bell();
						console.prompt(input);
					} else {
						console.prompt(finalSuggestion);
					}
				});
			}
		});
	}

	private static class ConsoleQueue {
		public static final int QUEUE_SIZE = 20;
		public static final int AUTOFLUSH_SIZE = 20;
		public static final int BATCH_SIZE = 20;

		private final Console console;
		private BlockingQueue<Consumer<Console>> workQueue;

		public ConsoleQueue(Console console) {
			this.console = console;
			this.workQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
		}

		public void queue(Consumer<Console> task) throws InterruptedIOException {
			try {
				workQueue.put(task);
			} catch (InterruptedException e) {
				throw new InterruptedIOException();
			}
		}

		public void flush() {
			if (workQueue.isEmpty()) {
				return;
			}

			run(console, this::flushQueue);
		}

		static void run(Component component, Runnable r) {
			UI ui = component.getUI();
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

		private void flushQueue() {
			Consumer<Console> consumer;
			int itemsFlushed = 0;
			while((consumer = workQueue.poll()) != null && itemsFlushed < BATCH_SIZE) {
				consumer.accept(console);
				itemsFlushed++;
			}
		}
	}

	private static class ConsoleWriter extends Writer {
		private final ConsoleQueue consoleQueue;
		private final String className;
		private StringBuilder buffer;

		public ConsoleWriter(ConsoleQueue console, String className) {
			this.consoleQueue = console;
			this.className = className;
			buffer = new StringBuilder();
		}

		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			buffer.append(cbuf, off, len);
		}

		@Override
		public void flush() throws IOException {
			flush(false);
		}

		private void flush(boolean flushRemaining) throws InterruptedIOException {
			if (buffer.length() == 0) {
				return;
			}

			int start = 0;
			int end;
			List<String> lines = new ArrayList<>();
			while (start < buffer.length() && (end = buffer.indexOf("\n", start)) != -1) {
				String line = buffer.substring(start, end);
				lines.add(line);
				start = end + 1;
			}

			String tail;
			if (start < buffer.length()) {
				String remaining = buffer.substring(start);

				if (flushRemaining) {
					lines.add(remaining);
					tail = null;
				} else {
					tail = remaining;
				}
			} else {
				tail = null;
			}

			buffer.setLength(0);

			if (!lines.isEmpty()) {
				for (String line : lines) {
					consoleQueue.queue(c -> c.println(line, className));
				}
				if (tail != null) {
					consoleQueue.queue(c -> c.print(tail, className));
				}
			}
		}

		@Override
		public void close() throws IOException {
			flush(true);
		}
	}
}
