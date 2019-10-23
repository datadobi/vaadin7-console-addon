package org.vaadin8.console.client;

public interface TextConsoleHandler {

	public void terminalInput(TextConsole term, String input);

	public void suggest(String input);

	public void controlChar(char c);
}
