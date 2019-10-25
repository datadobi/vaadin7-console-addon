package org.vaadin8.console.client;

public interface TextConsoleHandler {

	void terminalInput(TextConsole term, String input);

	void suggest(String input);

	void controlChar(char c);

	void previousCommand();

	void nextCommand();
}
