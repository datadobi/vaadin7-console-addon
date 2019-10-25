package org.vaadin8.console.client;

import com.vaadin.shared.communication.ClientRpc;

/**
 * ClientRpc is used to pass events from server to client. For sending
 * information about the changes to component state, use State instead.
 * 
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 22.05.2014 11:01:38
 * 
 */
public interface ConsoleClientRpc extends ClientRpc {
	void setInput(String input);

	void print(String text);
	void print(String text, String className);
	void println(String text);
	void println(String text, String className);

	void newline();

	void clearBuffer();

	void scrollToEnd();

	void bell();
}