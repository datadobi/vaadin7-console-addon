package org.vaadin8.console.client;

public interface TextConsoleHandler {
	int CTRL = 1;
	int SHIFT = 2;
	int ALT = 4;
	int META = 8;

	void terminalInput(TextConsole term, String input);

	void suggest(String input);

	void controlChar(String key, int modifiers);

	void previousCommand();

	void nextCommand();
}
