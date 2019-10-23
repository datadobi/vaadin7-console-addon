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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@Theme("demo")
@Title("Console Add-on Demo")
@Push
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

		DefaultConsoleHandler handler = new DefaultConsoleHandler(Executors.newSingleThreadExecutor());
		// Create a console
		final Console console = new Console(handler);
		vl.addComponent(console);

		// Size and greeting
		console.setPs("> ");
		console.setMaxBufferSize(24);
		console.setGreeting("Welcome to Vaadin console demo.");
		console.reset();
		console.setSizeFull();
		console.focus();

		// Publish the methods in the Console class itself for testing purposes.
		handler.addCommandProvider(inspector = new ObjectInspector(console));

		DefaultCommandProvider commandProvider = new DefaultCommandProvider();
		handler.addCommandProvider(commandProvider);

		// # 2
		Command systemCommand = new Command() {
			private static final long serialVersionUID = -5733237166568671987L;

			private Process process;

            @Override
            public void execute(List<String> argv, PrintWriter out, PrintWriter err) throws Exception {
				ProcessBuilder pb = new ProcessBuilder(argv.toArray(new String[0]));
				process = pb.start();
				StringBuilder o;
				try {
					o = new StringBuilder();
					Thread t = new Thread(() -> {
						InputStream in = process.getInputStream();
						try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
							String line;
							while ((line = r.readLine()) != null) {
								out.println(line);
							}
						} catch (IOException e) {
							o.append("[truncated]");
						}
					});
					t.start();
					t.join();
				} finally {
					process.destroy();
				}
			}
		};

		// #
		commandProvider.addCommand("ls", systemCommand);
		commandProvider.addCommand("sleep", systemCommand);

		// Add sample command
		DummyCmd dummy = new DummyCmd();
		commandProvider.addCommand("dir", dummy);
		commandProvider.addCommand("cd", dummy);
		commandProvider.addCommand("mkdir", dummy);
		commandProvider.addCommand("rm", dummy);
		commandProvider.addCommand("pwd", dummy);
		commandProvider.addCommand("more", dummy);
		commandProvider.addCommand("less", dummy);
		commandProvider.addCommand("exit", dummy);
	}

	public static class DummyCmd implements Command {
		private static final long serialVersionUID = -7725047596507450670L;

        @Override
        public void execute(List<String> argv, PrintWriter out, PrintWriter err) throws Exception {
			out.println("Sorry, this is not a real shell and '" + argv.get(0) + "' is unsupported. Try 'help' instead.");
		}
	}
}
