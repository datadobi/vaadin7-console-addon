package org.vaadin8.console.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.communication.RpcProxy;
import com.vaadin.client.ui.AbstractComponentConnector;
import com.vaadin.client.ui.layout.ElementResizeEvent;
import com.vaadin.client.ui.layout.ElementResizeListener;
import com.vaadin.shared.communication.FieldRpc.FocusAndBlurServerRpc;
import com.vaadin.shared.ui.Connect;
import org.vaadin8.console.Console;

/**
 * Connector binds client-side widget class to server-side component class.
 * Connector lives in the client and the @Connect annotation specifies the
 * corresponding server-side component.
 * 
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 22.05.2014 11:20:45
 * 
 */
@Connect(Console.class)
public class ConsoleConnector extends AbstractComponentConnector implements FocusHandler, ElementResizeListener {

	private static final long serialVersionUID = 2829157055722482839L;

	// ServerRpc is used to send events to server. Communication implementation
	// is automatically created here
	ConsoleServerRpc rpc = RpcProxy.create(ConsoleServerRpc.class, this);

	public ConsoleConnector() {

		// To receive RPC events from server, we register ClientRpc
		// implementation
		registerRpc(ConsoleClientRpc.class, new ConsoleClientRpc() {
			@Override
			public void setInput(String input) {
				getWidget().setInput(input);
			}

			@Override
			public void print(String text) {
				getWidget().print(text);
			}

			@Override
			public void print(String text, String className) {
				getWidget().printWithClass(text, className);
			}

			@Override
			public void println(String text) {
				print(text);
				getWidget().newLine();
			}

			@Override
			public void println(String text, String className) {
				print(text, className);
				getWidget().newLine();
			}

			@Override
			public void newline() {
				getWidget().newLine();
			}

			@Override
			public void clearBuffer() {
				getWidget().clearBuffer();
			}

			@Override
			public void scrollToEnd() {
				getWidget().scrollToEnd();
			}

			@Override
			public void bell() {
				getWidget().bell();
			}
		});

		getWidget().setHandler(new TextConsoleHandler() {

			@Override
			public void terminalInput(TextConsole term, String input) {
				rpc.input(input);
			}

			@Override
			public void controlChar(String key, int modifiers) {
				rpc.controlChar(key, modifiers);
			}

			@Override
			public void previousCommand() {
				rpc.previousCommand();
			}

			@Override
			public void nextCommand() {
				rpc.nextCommand();
			}

			@Override
			public void suggest(String input) {
				rpc.suggest(input);
			}
		});

	}

	@Override
	public void init() {
		super.init();
		getLayoutManager().addElementResizeListener(getWidget().getElement(), this);
	}

	@Override
	public void onUnregister() {
		super.onUnregister();
		getLayoutManager().removeElementResizeListener(getWidget().getElement(), this);
	}

	@Override
	public void onElementResize(ElementResizeEvent elementResizeEvent) {
		getWidget().resized();
	}

	// We must implement createWidget() to create correct type of widget
	@Override
	protected Widget createWidget() {
		TextConsole widget = GWT.create(TextConsole.class);
		return widget;
	}

	// We must implement getWidget() to cast to correct type
	@Override
	public TextConsole getWidget() {
		return (TextConsole) super.getWidget();
	}

	// We must implement getState() to cast to correct type
	@Override
	public ConsoleState getState() {
		return (ConsoleState) super.getState();
	}

	@Override
	public void onFocus(FocusEvent event) {
		// EventHelper.updateFocusHandler ensures that this is called only when
		// there is a listener on server side
		getRpcProxy(FocusAndBlurServerRpc.class).focus();
	}
}
