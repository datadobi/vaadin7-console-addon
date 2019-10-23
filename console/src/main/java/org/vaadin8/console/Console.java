package org.vaadin8.console;

import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Component;
import org.vaadin8.console.ansi.ANSICodeConverter;
import org.vaadin8.console.ansi.DefaultANSICodeConverter;
import org.vaadin8.console.client.ConsoleClientRpc;
import org.vaadin8.console.client.ConsoleServerRpc;
import org.vaadin8.console.client.ConsoleState;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * This is the server-side UI component that provides public API for Console.
 * 
 * @author Sami Ekblad / Vaadin
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 22.05.2014 17:30:06
 * 
 */
public class Console extends AbstractComponent implements Component.Focusable {

	// To process events from the client, we implement ServerRpc
	private ConsoleServerRpc rpc = new ConsoleServerRpc() {

		private static final long serialVersionUID = 443398479527027435L;

		@Override
		public void input(String input) {
			handleInput(input);
		}

		@Override
		public void suggest(String input) {
			handleSuggest(input);
		}

		@Override
		public void controlChar(char c) {
			handleControlChar(c);
		}
	};

	public Console(Console.Handler handler) {
		setHandler(handler);
		setANSIToCSSConverter(new DefaultANSICodeConverter());
		// To receive events from the client, we register ServerRpc
		registerRpc(rpc);
		reset();
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

	private static final long serialVersionUID = 590258219352859644L;
	private Handler handler;
	private ANSICodeConverter ansiToCSSconverter;
	private boolean isConvertANSIToCSS = false;

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
	 * The tab order number of this field.
	 */
	private int tabIndex = 0;
	@SuppressWarnings("unused")
	private Integer fontw;
	@SuppressWarnings("unused")
	private Integer fonth;
	private PrintStream printStream;
	private String lastSuggestInput;

	/**
	 * Console Handler interface.
	 * 
	 * Handler provides a hook to handle various console related events and
	 * override the default processing.
	 * 
	 */
	public interface Handler extends Serializable {

		/**
		 * Called when user uses TAB to complete the command entered into the
		 * Console input.
		 * 
		 * @param console
		 * @param lastInput
		 * @return
		 */
		Set<String> getSuggestions(Console console, String lastInput);

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
		void controlCharReceived(Console console, char c);
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

	private void handleSuggest(final String input) {

		final boolean cancelIfNotASingleMatch = (input != null && !input.equals(lastSuggestInput));
		lastSuggestInput = input;

		final Set<String> matches = handler.getSuggestions(this, input);

		if (matches == null || matches.size() == 0) {
			bell();
			return;
		}

		// Output the original
		final String prefix = parseCommandPrefix(input);
		String output = input.substring(0, input.lastIndexOf(prefix));
		if (matches.size() == 1) {
			// Output the only match
			output += matches.iterator().next() + " "; // append the single
			// match
		} else {

			// We output until the common prefix
			StringBuilder commonPrefix = new StringBuilder(prefix);
			final int maxLen = matches.iterator().next().length();
			for (int i = prefix.length(); i < maxLen; i++) {
				char c = 0;
				boolean charMatch = true;
				for (final String m : matches) {
					if (c == 0) {
						c = m.charAt(i);
					} else if (i < m.length()) {
						charMatch = m.charAt(i) == c;
						if (!charMatch) {
							break;
						}
					} else {
						charMatch = false;
						break;
					}
				}
				if (charMatch) {
					commonPrefix.append(c);
				}
			}
			output += commonPrefix.toString();
			if (prefix.equals(commonPrefix.toString()) && !cancelIfNotASingleMatch) {
				final StringBuilder suggestions = new StringBuilder("\n");
				for (final String m : matches) {
					suggestions.append(" ").append(m);
				}
				print(suggestions.toString());
			} else {
				bell();
				lastSuggestInput = output; // next suggest will not beep
			}
		}
		prompt(output);
		focus();

	}

	public void bell() {
		getRpcProxy(ConsoleClientRpc.class).bell();
	}

	private void handleInput(final String input) {

		// Ask registered handler
		handler.inputReceived(this, input);
	}

	private void handleControlChar(char c) {

		handler.controlCharReceived(this, c);
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

	public void print(final String output) {
		if (isConvertANSIToCSS) {
			getRpcProxy(ConsoleClientRpc.class).print("");
			appendWithProcessingANSICodes(output);
		} else {
			getRpcProxy(ConsoleClientRpc.class).print(output);
		}
	}

	/**
	 * Print text with predefined in theme CSS class.
	 * 
	 * @param output
	 * @param className
	 *            CSS class name for string
	 */
	public void print(final String output, final String className) {
		if (className == null) {
			print(output);
			return;
		}
		getRpcProxy(ConsoleClientRpc.class).printWithClass(output, className);
	}

	public String getGreeting() {
		return getState(false).greeting;
	}

	public String getPs() {
		return getState(false).ps;
	}

	public int getMaxBufferSize() {
		return getState(false).maxBufferSize;
	}

	public void setGreeting(final String greeting) {
		getState().greeting = greeting;
	}

	public void setPs(final String ps) {
		getState().ps = ps;
	}

	public void setMaxBufferSize(final int lines) {
		getState().maxBufferSize = lines;
	}

	public void prompt() {
		getRpcProxy(ConsoleClientRpc.class).prompt();
	}

	public void prompt(final String initialInput) {
		getRpcProxy(ConsoleClientRpc.class).prompt(initialInput);
	}

	public void println(final String string) {
		if (isConvertANSIToCSS) {
			getRpcProxy(ConsoleClientRpc.class).print("");
			appendWithProcessingANSICodes(string + "\n");
		} else
			getRpcProxy(ConsoleClientRpc.class).println(string);
	}

	/**
	 * Print text with predefined in theme CSS class.
	 * 
	 * @param string
	 * @param className
	 *            CSS class name for string
	 */
	public void println(final String string, final String className) {
		if (className == null) {
			println(string);
			return;
		}
		getRpcProxy(ConsoleClientRpc.class).printlnWithClass(string, className);
	}

	/**
	 * @param string
	 *            text to append to the last printed line
	 * @return this Console object
	 */
	public Console append(final String string) {
		if (isConvertANSIToCSS)
			appendWithProcessingANSICodes(string);
		else
			getRpcProxy(ConsoleClientRpc.class).append(string);
		return this;
	}

	private void appendWithProcessingANSICodes(String sOutput) {
		String splitted[] = sOutput.split(ANSICodeConverter.ANSI_PATTERN);
		String notPrintedYet = new String(sOutput);
		for (int i = 0; i < splitted.length; i++) {
			String nextStr = splitted[i];
			if (i == 0 && nextStr.length() == 0)
				continue;
			String cssClasses = "";
			Pattern firstAnsi = Pattern.compile("^(" + ANSICodeConverter.ANSI_PATTERN + ")+\\Q" + nextStr + "\\E.*", Pattern.DOTALL);
			if (firstAnsi.matcher(notPrintedYet).matches()) {
				while (firstAnsi.matcher(notPrintedYet).matches()) {
					String ansi = notPrintedYet.replaceAll("\\Q" + notPrintedYet.replaceAll("^(" + ANSICodeConverter.ANSI_PATTERN + "){1}", "") + "\\E", "");
					cssClasses += ansiToCSSconverter.convertANSIToCSS(ansi) + " ";
					notPrintedYet = notPrintedYet.replaceAll("^(" + ANSICodeConverter.ANSI_PATTERN + "){1}", "");
				}
				notPrintedYet = notPrintedYet.replaceAll("^\\Q" + nextStr + "\\E", "");
			} else
				notPrintedYet = notPrintedYet.replaceFirst("\\Q" + nextStr + "\\E", "");
			cssClasses = cssClasses.trim();
			if (cssClasses.length() > 0)
				getRpcProxy(ConsoleClientRpc.class).appendWithClass(nextStr, cssClasses);
			else
				getRpcProxy(ConsoleClientRpc.class).append(nextStr);
		}
	}

	/**
	 * Append text with predefined in theme CSS class.
	 * 
	 * @param string
	 *            text to append to the last printed line
	 * @param className
	 *            CSS class name for string
	 * @return this Console object
	 */
	public Console append(final String string, final String className) {
		if (className == null)
			return append(string);
		getRpcProxy(ConsoleClientRpc.class).appendWithClass(string, className);
		return this;
	}

	public void newLine() {
		getRpcProxy(ConsoleClientRpc.class).newLine();
	}

	/**
	 * Print new line only if new line not exists at the end of console
	 */
	public void newLineIfNotEndsWithNewLine() {
		getRpcProxy(ConsoleClientRpc.class).newLineIfNotEndsWithNewLine();
	}

	public void reset() {
		getRpcProxy(ConsoleClientRpc.class).reset();
	}

	public void clear() {
		formFeed();
	}

	public void formFeed() {
		getRpcProxy(ConsoleClientRpc.class).ff();
	}

	public void carriageReturn() {
		getRpcProxy(ConsoleClientRpc.class).cr();
	}

	public void lineFeed() {
		getRpcProxy(ConsoleClientRpc.class).lf();
	}

	public void clearCommandHistory() {
		getRpcProxy(ConsoleClientRpc.class).clearHistory();
	}

	public void clearBuffer() {
		getRpcProxy(ConsoleClientRpc.class).clearBuffer();
	}

	public void scrollToEnd() {
		getRpcProxy(ConsoleClientRpc.class).scrollToEnd();
	}

	/**
	 * Focus input element of console.
	 */
	public void focusInput() {
		getRpcProxy(ConsoleClientRpc.class).focusInput();
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

	/* PrintStream implementation for console output. */

	public PrintStream getPrintStream() {
		if (printStream == null) {
			printStream = new PrintStream(new OutputStream() {

				ByteArrayOutputStream buffer = new ByteArrayOutputStream();

				@Override
				public void write(final int b) throws IOException {
					buffer.write(b);
					// Line buffering
					if (13 == b) {
						flush();
					}
				}

				@Override
				public void flush() throws IOException {
					super.flush();
					buffer.flush();
					Console.this.print(buffer.toString());
					buffer.reset();
				}
			}, true);
		}
		return printStream;
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

	public ANSICodeConverter getANSIToCSSConverter() {
		return ansiToCSSconverter;
	}

	public void setANSIToCSSConverter(ANSICodeConverter converter) {
		this.ansiToCSSconverter = converter != null ? converter : new DefaultANSICodeConverter();
	}

	/**
	 * Converting raw output with ANSI escape sequences to output with
	 * CSS-classes.
	 * 
	 * @return
	 */
	public boolean isConvertANSIToCSS() {
		return isConvertANSIToCSS;
	}

	/**
	 * Converting raw output with ANSI escape sequences to output with
	 * CSS-classes.
	 * 
	 * @param isConvertANSIToCSS
	 */
	public void setConvertANSIToCSS(boolean isConvertANSIToCSS) {
		this.isConvertANSIToCSS = isConvertANSIToCSS;
	}
}
