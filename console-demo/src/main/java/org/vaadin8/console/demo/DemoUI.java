package org.vaadin8.console.demo;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.*;
import org.vaadin8.console.Console;
import org.vaadin8.console.Console.Command;
import org.vaadin8.console.ObjectInspector;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

@Theme("demo")
@Title("Console Add-on Demo")
@SuppressWarnings("serial")
public class DemoUI extends UI {

	protected static final String HELP = "Sample Vaadin shell. Following command are available:\n";
	private ObjectInspector inspector;

	@WebServlet(value = "/*", asyncSupported = true)
	@VaadinServletConfiguration(productionMode = false, ui = DemoUI.class, widgetset = "org.vaadin8.console.demo.DemoWidgetSet")
	public static class Servlet extends VaadinServlet {
	}

	@Override
	protected void init(VaadinRequest request) {
		getPage().setTitle("Vaadin Console Demo");

		VerticalLayout vl = new VerticalLayout();
		vl.setSizeFull();
		setContent(vl);

		// Create a console
		final Console console = new Console();
		vl.addComponent(console);

		// Size and greeting
		console.setPs("> ");
		console.setMaxBufferSize(24);
		console.setGreeting("Welcome to Vaadin console demo.");
		console.reset();
		console.setSizeFull();
		console.focus();

		// Publish the methods in the Console class itself for testing purposes.
		console.addCommandProvider(inspector = new ObjectInspector(console));

		// Add help command
		Command helpCommand = new Console.Command() {
			private static final long serialVersionUID = 2838665604270727844L;

			public String getUsage(Console console, String[] argv) {
				return argv[0] + " <command>";
			}

			public Object execute(Console console, String[] argv) throws Exception {
				if (argv.length == 2) {
					Command hc = console.getCommand(argv[1]);
					ArrayList<String> cmdArgv = new ArrayList<String>(Arrays.asList(argv));
					cmdArgv.remove(0);
					return "Usage: " + hc.getUsage(console, cmdArgv.toArray(new String[] {}));
				}
				return listAvailableCommands();
			}

			public void kill() { /* not supported */ }

			public boolean isKilled() { return false; }
		};

		// Bind the same command with multiple names
		console.addCommand("help", helpCommand);
		console.addCommand("info", helpCommand);
		console.addCommand("man", helpCommand);
		// #

		// # 2
		Command systemCommand = new Command() {
			private static final long serialVersionUID = -5733237166568671987L;

			private Process process;
			private boolean killed;

			public Object execute(Console console, String[] argv) throws Exception {
				// simulate a blocking command
				if (argv[0].equals("sleep")) argv = new String[] { "sleep", "10" };
				killed = false;
				process = Runtime.getRuntime().exec(argv);
				InputStream in = process.getInputStream();
				StringBuilder o = new StringBuilder();
				try (InputStreamReader r = new InputStreamReader(in)) {
					int c;
					while ((c = r.read()) != -1) {
						o.append((char) c);
					}
				} catch (IOException e) {
					o.append("[truncated]");
				}
				return killed ? null : o.toString();
			}

			public void kill() {
				System.out.println("# killed");
				killed = true;
				if (process != null)
					process.destroy();
			}

			public boolean isKilled() {
				return killed;
			}

			public String getUsage(Console console, String[] argv) {
				// TODO Auto-generated method stub
				return null;
			}
		};

		// #
		console.addCommand("ls", systemCommand);
		console.addCommand("sleep", systemCommand);

		// Add sample command
		DummyCmd dummy = new DummyCmd();
		console.addCommand("dir", dummy);
		console.addCommand("cd", dummy);
		console.addCommand("mkdir", dummy);
		console.addCommand("rm", dummy);
		console.addCommand("pwd", dummy);
		console.addCommand("more", dummy);
		console.addCommand("less", dummy);
		console.addCommand("exit", dummy);
	}

	public static class DummyCmd implements Console.Command {
		private static final long serialVersionUID = -7725047596507450670L;

		public Object execute(Console console, String[] argv) throws Exception {
			return "Sorry, this is not a real shell and '" + argv[0] + "' is unsupported. Try 'help' instead.";
		}

		public void kill() { /* unsupported */ }

		public boolean isKilled() { return false; }

		public String getUsage(Console console, String[] argv) {
			return "Sorry, this is not a real shell and '" + argv[0] + "' is unsupported. Try 'help' instead.";
		}
	}

	private String listAvailableCommands() {
		StringBuilder res = new StringBuilder();
		for (String cmd : inspector.getAvailableCommands()) {
			res.append(" ");
			res.append(cmd);
		}
		return res.toString().trim();
	}
}
