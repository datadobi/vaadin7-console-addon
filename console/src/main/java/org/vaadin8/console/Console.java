package org.vaadin8.console;

import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Component;
import org.vaadin8.console.client.ConsoleClientRpc;
import org.vaadin8.console.client.ConsoleServerRpc;
import org.vaadin8.console.client.ConsoleState;
import org.vaadin8.console.client.TextConsoleHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * This is the server-side UI component that provides public API for Console.
 * 
 * @author Sami Ekblad / Vaadin
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 22.05.2014 17:30:06
 * 
 */
public class Console extends AbstractComponent implements Component.Focusable {
	private static final long serialVersionUID = 590258219352859644L;

	private static final int RESET_NONE = 0;
	private static final int RESET_PROMPT = 1;
	private static final int RESET_CLEAR = 2;

	private Handler handler;

	private LinkedList<List<Span>> scrollBuffer = new LinkedList<>();
	private List<Span> lineBuffer = new ArrayList<>();

	private String greeting;

	/**
	 * The tab order number of this field.
	 */
	private int tabIndex = 0;

	private boolean clear;

	public Console(Console.Handler handler) {
		setHandler(handler);
		// To receive events from the client, we register ServerRpc
		registerRpc(new ServerRpc());
	}

	public void beforeClientResponse(boolean initial) {
		super.beforeClientResponse(initial);
		ConsoleClientRpc rpc = getRpcProxy(ConsoleClientRpc.class);

		if (clear) {
			rpc.clearBuffer();
		}

		if (initial) {
			for (List<Span> line : scrollBuffer) {
				printLine(rpc, line);
				rpc.newline();
			}
			printLine(rpc, lineBuffer);
		}

		clear = false;
	}

	private void printLine(ConsoleClientRpc rpc, List<Span> line) {
		for (Span span : line) {
			rpc.print(span.text, span.className);
		}
	}

	// We must override getState() to cast the state to ConsoleState
	@Override
	public ConsoleState getState() {
		return (ConsoleState) super.getState();
	}

	@Override
	protected ConsoleState getState(boolean markAsDirty) {
		return (ConsoleState) super.getState(markAsDirty);
	}

	public boolean isWrap() {
		return getState(false).wrap;
	}

	public void setWrap(final boolean wrap) {
		getState().wrap = wrap;
	}

	/**
	 * @return true, if method scrollToEnd will only work if last scroll state
	 *         was "end"
	 */
	public boolean isScrollLock() {
		return getState(false).scrollLock;
	}

	/**
	 * @param isScrollLock
	 *            if true - method scrollToEnd will only work if last scroll
	 *            state was "end"
	 */
	public void setScrollLock(final boolean isScrollLock) {
		getState().scrollLock = isScrollLock;
	}

	/**
	 * Overridden to filter client-side calculation/changes and avoid loops.
	 */
	@Override
	public void setWidth(float width, Unit unit) {
		if (width != getWidth() || unit != getWidthUnits()) {
			super.setWidth(width, unit);
			getState().width = width + unit.getSymbol();
		}
	}

	/**
	 * Overridden to filter client-side calculation/changes and avoid loops.
	 */
	@Override
	public void setHeight(float height, Unit unit) {
		if (height != getHeight() || unit != getHeightUnits()) {
			super.setHeight(height, unit);
			getState().height = height + unit.getSymbol();
		}
	}

	public void bell() {
		getRpcProxy(ConsoleClientRpc.class).bell();
	}

	public void clearCommandHistory() {
		// TODO
	}

	String parseCommandPrefix(final String input) {
		if (input == null) {
			return null;
		}
		if (!input.endsWith(" ")) {
			List<String> argv = DefaultConsoleHandler.parseInput(input);
			if (!argv.isEmpty()) {
				return argv.get(argv.size() - 1);
			}
		}
		return "";
	}

	protected static int count(final String sourceString, final char lookFor) {
		if (sourceString == null) {
			return -1;
		}
		int count = 0;
		for (int i = 0; i < sourceString.length(); i++) {
			final char c = sourceString.charAt(i);
			if (c == lookFor) {
				count++;
			}
		}
		return count;
	}

	public String getGreeting() {
		return greeting;
	}

	public String getPs() {
		return getState(false).ps;
	}

	public int getMaxBufferSize() {
		return getState(false).maxBufferSize;
	}

	public void setGreeting(final String greeting) {
		this.greeting = greeting;
	}

	public void setPs(final String ps) {
		getState().ps = ps;
	}

	public void setMaxBufferSize(final int lines) {
		getState().maxBufferSize = lines;
	}

	public void prompt() {
		prompt("");
	}

	public void prompt(String inputText) {
		getState().promptVisible = true;
		getState().inputEditable = true;
		ConsoleClientRpc rpc = getRpcProxy(ConsoleClientRpc.class);
		rpc.setInput(inputText);
		rpc.scrollToEnd();
	}

	public void print(String string) {
		println(string, null);
	}

	public void print(String string, String className) {
		addToLineBuffer(string, className);
		if (className == null || className.isEmpty()) {
			getRpcProxy(ConsoleClientRpc.class).print(string);
		} else {
			getRpcProxy(ConsoleClientRpc.class).print(string, className);
		}
	}

	public void println(String string) {
		println(string, null);
	}

	public void println(String string, String className) {
		addToLineBuffer(string, className);
		flushCurrentLineToBuffer(false);
		if (className == null || className.isEmpty()) {
			getRpcProxy(ConsoleClientRpc.class).println(string);
		} else {
			getRpcProxy(ConsoleClientRpc.class).println(string, className);
		}
	}

	private void addToLineBuffer(String string, String className) {
		lineBuffer.add(new Span(string, className));
	}

	private void flushCurrentLineToBuffer(boolean force) {
		if (!lineBuffer.isEmpty() || force) {
			scrollBuffer.addLast(new ArrayList<>(lineBuffer));

			while(scrollBuffer.size() > getMaxBufferSize()) {
				scrollBuffer.removeFirst();
			}

			lineBuffer.clear();
		}
	}

	public void reset() {
		clearBuffer();

		handler.clearHistory(this);

		String greeting = getGreeting();
		if (greeting != null && !greeting.isEmpty()) {
			addToLineBuffer(greeting, null);
			flushCurrentLineToBuffer(false);
		}

		clear = true;
		prompt();
		markAsDirty();
	}

	public void clearBuffer() {
		scrollBuffer.clear();
		lineBuffer.clear();

		clear = true;
		markAsDirty();
	}

	public void scrollToEnd() {
		getRpcProxy(ConsoleClientRpc.class).scrollToEnd();
	}

	/**
	 * Gets the Tabulator index of this Focusable component.
	 * 
	 * @see com.vaadin.ui.Component.Focusable#getTabIndex()
	 */
	public int getTabIndex() {
		return tabIndex;
	}

	/**
	 * Sets the Tabulator index of this Focusable component.
	 * 
	 * @see com.vaadin.ui.Component.Focusable#setTabIndex(int)
	 */
	public void setTabIndex(final int tabIndex) {
		this.tabIndex = tabIndex;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void focus() {
		super.focus();
	}

   /**
	 * Get the current Console Handler.
	 * 
	 * @return
	 */
	public Handler getHandler() {
		return handler;
	}

	/**
	 * Set the handler for this console.
	 * 
	 * @see Handler
	 * @param handler
	 */
	public void setHandler(Handler handler) {
		this.handler = Objects.requireNonNull(handler);
	}

	private class ServerRpc implements ConsoleServerRpc {
		private static final long serialVersionUID = 443398479527027435L;

		@Override
		public void previousCommand() {
			String input = handler.previousCommand(Console.this);
			if (input != null) {
				prompt(input);
			}
		}

		@Override
		public void nextCommand() {
			String input = handler.nextCommand(Console.this);
			if (input != null) {
				prompt(input);
			}
		}

		@Override
		public void input(String input) {
			getState().promptVisible = false;
			println(getPs() + input, null);
			scrollToEnd();
			handler.inputReceived(Console.this, input);
		}

		@Override
		public void controlChar(String key, int modifiers) {
			handler.controlCharReceived(Console.this, key, modifiers);
		}

		@Override
		public void suggest(String input) {
			getState().inputEditable = false;
			scrollToEnd();
			handler.suggest(Console.this, input);
		}
	};

	/**
	 * Console Handler interface.
	 *
	 * Handler provides a hook to handle various console related events and
	 * override the default processing.
	 *
	 */
	public interface Handler extends Serializable {
		int CTRL = TextConsoleHandler.CTRL;
		int SHIFT = TextConsoleHandler.SHIFT;
		int ALT = TextConsoleHandler.ALT;
		int META = TextConsoleHandler.META;

		/**
		 * Called when user has entered input to the Console and presses enter
		 * to execute it.
		 *
		 * @param console
		 * @param lastInput
		 */
		void inputReceived(Console console, String lastInput);

		/**
		 * Called when a user has pressed CTRL+C.
		 *
		 * @param console
		 */
		void controlCharReceived(Console console, String key, int modifiers);

		void clearHistory(Console console);

		String nextCommand(Console console);

		String previousCommand(Console console);

		void suggest(Console console, String input);
	}

	private static class Span {
		private final String text;
		private final String className;

		public Span(String text, String className) {
			this.text = text;
			this.className = className;
		}
	}
}
