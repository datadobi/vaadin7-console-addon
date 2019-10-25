package org.vaadin8.console.client;

import com.vaadin.shared.communication.ServerRpc;

/**
 * ServerRpc is used to pass events from client to server.
 * 
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 22.05.2014 15:30:06
 * 
 */
public interface ConsoleServerRpc extends ServerRpc {
	void previousCommand();

	void nextCommand();

	void input(String input);

	void suggest(String input);

	void controlChar(char c);
}
