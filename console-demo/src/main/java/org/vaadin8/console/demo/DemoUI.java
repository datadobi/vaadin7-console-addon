package org.vaadin8.console.demo;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.*;
import org.vaadin8.console.*;
import org.vaadin8.console.Console;

import javax.servlet.annotation.WebServlet;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

@Theme("demo")
@Title("Console Add-on Demo")
@Push
@SuppressWarnings("serial")
public class DemoUI extends UI {

	protected static final String HELP = "Sample Vaadin shell. Following command are available:\n";

	@WebServlet(value = "/*", asyncSupported = true)
	@VaadinServletConfiguration(productionMode = false, ui = DemoUI.class, widgetset = "org.vaadin8.console.demo.DemoWidgetSet")
	public static class Servlet extends VaadinServlet {
	}

	@Override
	protected void init(VaadinRequest request) {
		getPage().setTitle("Vaadin Console Demo");

		TabSheet tabs = new TabSheet();
		tabs.setSizeFull();
		setContent(tabs);

		tabs.addTab(createConsole(), "Console 1");
		tabs.addTab(createConsole(), "Console 2");
		tabs.addTab(createConsole(), "Console 3");
	}

	private Component createConsole() {
		VerticalLayout vl = new VerticalLayout();
		vl.setSizeFull();

		DefaultConsoleHandler handler = new DefaultConsoleHandler(Executors.newSingleThreadExecutor(), new CommandHandler());
		// Create a console
		final Console console = new Console(handler);
		vl.addComponent(console);

		// Size and greeting
		console.setPs("> ");
		console.setMaxBufferSize(200);
		console.setGreeting("Welcome to Vaadin console demo.");
		console.reset();
		console.setSizeFull();
		console.focus();

		return vl;
	}

	private static class CommandHandler implements org.vaadin8.console.CommandHandler {
		private static Set<String> systemCommands = new HashSet<>(Arrays.asList("ls", "sleep", "echo"));

		@Override
		public String suggest(List<String> argv) throws Exception {
			if (argv.size() != 1) {
				return null;
			}

			String cmd = argv.get(0).toLowerCase();
			for (String validCommand : systemCommands) {
				if (validCommand.startsWith(cmd.toLowerCase()) && !validCommand.equals(cmd)) {
					return validCommand;
				}
			}
			return null;
		}

		@Override
		public String execute(List<String> argv, PrintWriter out, PrintWriter err) throws Exception {
			if (argv.isEmpty()) {
				return null;
			}

			String cmd = argv.get(0);
			if (systemCommands.contains(cmd.toLowerCase())) {
				executeSystemCommand(argv, out, err);
			} else if (cmd.equals("clock")) {
				while(true) {
					out.println(Instant.now());
					Thread.sleep(1000);
				}
			} else {
				err.println("command not found: " + cmd);
			}
			return null;
		}

		public void executeSystemCommand(List<String> argv, PrintWriter out, PrintWriter err) throws Exception {
			ProcessBuilder pb = new ProcessBuilder(argv.toArray(new String[0]));
			Process process = pb.start();
			try {
				Thread outReader = new Thread(() -> {
					InputStream in = process.getInputStream();
					try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
						String line;
						while ((line = r.readLine()) != null) {
							out.println(line);
						}
					} catch (IOException e) {
						out.println("[truncated]");
					}
				});
				Thread errReader = new Thread(() -> {
					InputStream in = process.getErrorStream();
					try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
						String line;
						while ((line = r.readLine()) != null) {
							err.println(line);
						}
					} catch (IOException e) {
						err.println("[truncated]");
					}
				});

				outReader.start();
				errReader.start();
				try {
					outReader.join();
					errReader.join();
				} catch (InterruptedException e) {
					outReader.interrupt();
					errReader.interrupt();
					throw e;
				}
			} finally {
				process.destroy();
			}
		}
	}
}
