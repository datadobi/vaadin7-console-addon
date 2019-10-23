package org.vaadin8.console.client;

import com.google.gwt.dom.client.*;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FocusWidget;
import com.vaadin.client.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TextConsole extends FocusWidget {
	/* Control characters in http://en.wikipedia.org/wiki/Control_character */

	private static final char CTRL_BELL = 'G';
	private static final char CTRL_BACKSPACE = 'H';
	private static final char CTRL_TAB = 'I';
	private static final char CTRL_LINE_FEED = 'J';
	private static final char CTRL_FORM_FEED = 'L';
	private static final char CTRL_CARRIAGE_RETURN = 'M';
	private static final char CTRL_ESCAPE = '[';
	private static final char CTRL_DELETE = '?';
	private static final char CTRL_C = 'C';

	private static final char[] CTRL = { CTRL_BELL, CTRL_BACKSPACE, CTRL_TAB, CTRL_LINE_FEED, CTRL_FORM_FEED, CTRL_CARRIAGE_RETURN, CTRL_ESCAPE, CTRL_DELETE, CTRL_C };

	private static char getControlKey(final int kc) {
		for (final char c : CTRL) {
			if (kc == c) {
				return c;
			}
		}
		return 0;
	}

	private static final String DEFAULT_TABS = "    ";
	private static final int BIG_NUMBER = 100000;

	private String greeting;
	private boolean scrollLock;

	private final DivElement term;
	private TextConsoleHandler handler;
	private final Element buffer;
	private final TableElement prompt;
	private final Element ps;
	private final InputElement input;
	private List<String> cmdHistory = new ArrayList<String>();
	private int cmdHistoryIndex = -1;
	private HandlerRegistration clickHandler;
	private HandlerRegistration keyHandler;
	private HandlerRegistration focusHandler;
	private int fontW = -1;
	private int fontH = -1;
	private int scrollbarW = -1;
	private int rows;
	private int cols;
	private String tabs = DEFAULT_TABS;
	private boolean focused;
	private int promptRows;
	private int padding;
	private final DivElement promptWrap;
	private Timer timer;
	private int maxBufferSize;
	private String cleanPs;
	private int paddingW;
	private boolean scrolledToEnd = true;

	public TextConsole() {
		setElement(Document.get().createDivElement());

		// Main element
		term = Document.get().createDivElement();
		term.addClassName("term");
		setTabIndex(0);
		getElement().appendChild(term);

		// Buffer
		buffer = Document.get().createElement("pre");
		buffer.addClassName("b");
		term.appendChild(buffer);

		// Prompt elements
		promptWrap = Document.get().createDivElement();
		promptWrap.addClassName("pw");
		term.appendChild(promptWrap);

		prompt = Document.get().createTableElement();
		promptWrap.appendChild(prompt);
		prompt.setAttribute("cellpadding", "0");
		prompt.setAttribute("cellspacing", "0");
		prompt.setAttribute("border", "0");
		prompt.addClassName("p");

		final TableSectionElement tbody = Document.get().createTBodyElement();
		prompt.appendChild(tbody);

		final TableRowElement tr = Document.get().createTRElement();
		tbody.appendChild(tr);

		final TableCellElement psTd = Document.get().createTDElement();
		psTd.addClassName("psw");
		tr.appendChild(psTd);

		ps = Document.get().createElement("nobr");
		ps.addClassName("ps");
		psTd.appendChild(ps);

		final TableCellElement inputTd = Document.get().createTDElement();
		inputTd.addClassName("iw");
		tr.appendChild(inputTd);

		input = (InputElement) Document.get().createElement("input");
		inputTd.appendChild(input);
		input.addClassName("i");
		input.setTabIndex(-1);
		input.setAttribute("spellcheck", "false");

		setPromptActive(false);

		registerHandlersIfNeeded();

		updateFontDimensions();
	}

	private void registerHandlersIfNeeded() {
		if (clickHandler == null) {
			clickHandler = addDomHandler(event -> setFocus(true), ClickEvent.getType());
		}

		if (keyHandler == null) {
			keyHandler = addDomHandler(event -> {
				if (event.getNativeEvent().getCtrlKey()) {
					final char ctrlChar = getControlKey(event.getNativeKeyCode());
					if (ctrlChar > 0) {
						event.preventDefault();
						handleControlChar(ctrlChar);
					}
				} else {
					boolean promptActive = isPromptActive();
					getLogger().info("handleKeyEvent(promptActive=" + promptActive + ")");

					if (!promptActive) {
						event.preventDefault();
					} else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
						event.preventDefault();
						carriageReturn();
					} else if (event.getNativeKeyCode() == KeyCodes.KEY_UP || event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
						event.preventDefault();
						handleCommandHistoryBrowse(event.getNativeKeyCode());
					} else if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
						event.preventDefault();
						suggest();
					} else if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE && getInputLenght() == 0) {
						bell();
					}
				}
			}, KeyDownEvent.getType());
		}
	}

	private int getInputLenght() {
		final String v = input.getValue();
		if (v != null) {
			return v.length();
		}
		return -1;
	}

	private void handleControlChar(final char c) {
		switch (c) {
		case TextConsole.CTRL_BACKSPACE:
			backspace();
			break;
		case TextConsole.CTRL_BELL:
			bell();
			break;
		case TextConsole.CTRL_CARRIAGE_RETURN:
			carriageReturn();
			break;
		case TextConsole.CTRL_DELETE:
			bell(); // TODO: not supported yet
			break;
		case TextConsole.CTRL_FORM_FEED:
			formFeed();
			break;
		case TextConsole.CTRL_LINE_FEED:
			lineFeed();
			break;
		case TextConsole.CTRL_TAB:
			tab();
			break;
		default:
			controlChar(c);
			break;
		}
	}

	protected void suggest() {
		handler.suggest(getInput());
	}

	protected void controlChar(char c) {
		handler.controlChar(c);
	}

	private void handleCommandHistoryBrowse(final int i) {
		cmdHistoryIndex = i == KeyCodes.KEY_UP ? cmdHistoryIndex - 1 : cmdHistoryIndex + 1;
		if (cmdHistoryIndex >= 0 && cmdHistoryIndex < cmdHistory.size()) {
			prompt(cmdHistory.get(cmdHistoryIndex));
		} else {
			prompt();
		}
	}

	public String getInput() {
		return input.getValue();
	}

	protected void setInput(final String inputText) {
		if (inputText != null) {
			input.setValue(inputText);
		} else {
			input.setValue("");
		}
		if (isFocused()) {
			focusPrompt();
		}
	}

	private void lineFeed() {
		carriageReturn();
	}

	protected void tab() {
		prompt(getInput() + "\t");
	}

	private void backspace() {
		bell();
	}

	void carriageReturn() {
		if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
			newLine();
			if (promptRows > 1) {
				reducePrompt(-1);
			}
		}
		String lineBuffer = getInput();
		lineBuffer = lineBuffer.trim();
		if (!"".equals(lineBuffer)) {
			cmdHistory.add(lineBuffer);
			cmdHistoryIndex = cmdHistory.size();
		}
		if (handler != null) {
			handler.terminalInput(this, lineBuffer);
		}
		setFocus(true);
	}

	private boolean bufferIsEmpty() {
		return !buffer.hasChildNodes();
	}

	private void setPromptActive(final boolean active) {
		getLogger().info("setPromptActive(" + active + ")");
		if (active && !isPromptActive()) {
			prompt.getStyle().setDisplay(Display.BLOCK);
		} else if (!active && isPromptActive()) {
			prompt.getStyle().setDisplay(Display.NONE);
		}
	}

	private boolean isPromptActive() {
		return !Display.NONE.getCssName().equals(prompt.getStyle().getDisplay());
	}

	private boolean isFocused() {
		return focused;
	}

	public void init() {
		registerHandlersIfNeeded();

		updateFontDimensions();

		scrollbarW = getScrollbarWidth();
		final String padStr = term.getStyle().getPadding();
		if (padStr != null && padStr.endsWith("px")) {
			padding = Integer.parseInt(padStr.substring(0, padStr.length() - 2));
		} else {
			getLogger().info("using default padding: 1x2");
			padding = 1;
			paddingW = 2;
		}

		getLogger().info("init: font=" + fontW + "x" + fontH + ";scrollbar="
		 + scrollbarW + ";size=" + getWidth() + "x" + getHeight());

		setPs(">");
		setMaxBufferSize(maxBufferSize);
	}

	private void updateFontDimensions() {

		// Test element for font size
		DivElement test = Document.get().createDivElement();
		test.setAttribute("style", "position: absolute;");
		test.setInnerHTML("X");
		term.appendChild(test);

		fontW = test.getClientWidth();
		fontH = test.getClientHeight();
		if (fontW <= 0 || fontW > 100) {
			fontW = test.getOffsetWidth();
		}
		if (fontH <= 0 || fontH > 100) {
			fontH = test.getOffsetHeight();
		}
		if (fontW <= 0 || fontW > 100) {
			fontW = 1;
		}
		if (fontH <= 0 || fontH > 100) {
			fontH = 1;
		}
		term.removeChild(test);
	}

	private boolean bufferEndsWithNewLine() {
		Node last = buffer != null ? buffer.getLastChild() : null;
		while (last != null && last.getLastChild() != null)
			last = last.getLastChild();
		getLogger().info("last node: " + (last != null ? last.getNodeName() : "<null>"));
		return last != null && "br".equals(last.getNodeName().toLowerCase());
	}

	private Node createTextNode(final String text) {
		return Document.get().createTextNode(text);
	}

	private Node createBr() {
		return Document.get().createBRElement();
	}

	private void focusPrompt() {
		focusPrompt(-1);
	}

	private void focusPrompt(final int cursorPos) {
		input.focus();

		// Focus to end
		final String s = getInput();
		if (s != null && s.length() > 0) {
			setSelectionRange(input, s.length(), s.length());
		}
	}

	private native void setSelectionRange(Element input, int selectionStart, int selectionEnd)/*-{
																								if (input.setSelectionRange) {
																								input.focus();
																								input.setSelectionRange(selectionStart, selectionEnd);
																								}
																								else if (input.createTextRange) {
																								var range = input.createTextRange();
																								range.collapse(true);
																								range.moveEnd('character', selectionEnd);
																								range.moveStart('character', selectionStart);
																								range.select();
																								}
																								}-*/;

	private native int getScrollbarWidth()/*-{

											var i = $doc.createElement('p');
											i.style.width = '100%';
											i.style.height = '200px';
											var o = $doc.createElement('div');
											o.style.position = 'absolute';
											o.style.top = '0px';
											o.style.left = '0px';
											o.style.visibility = 'hidden';
											o.style.width = '200px';
											o.style.height = '150px';
											o.style.overflow = 'hidden';
											o.appendChild(i);
											$doc.body.appendChild(o);
											var w1 = i.offsetWidth;
											var h1 = i.offsetHeight;
											o.style.overflow = 'scroll';
											var w2 = i.offsetWidth;
											var h2 = i.offsetHeight;
											if (w1 == w2) w2 = o.clientWidth;
											if (h1 == h2) h2 = o.clientWidth;
											$doc.body.removeChild(o);
											return w1-w2;
											}-*/;

	public void newLine() {
		getLogger().info("newline");
		beforeChangeTerminal();
		buffer.appendChild(createBr());
		checkBufferLimit();
		reducePrompt(1);
	}

	public void newLineIfNotEndsWithNewLine() {
		if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
			getLogger().info("newline");
			beforeChangeTerminal();
			buffer.appendChild(createBr());
			checkBufferLimit();
			reducePrompt(1);
		}
	}

	public void setPs(final String string) {
		getLogger().info("setPs: ps=" + string);
		cleanPs = Util.escapeHTML(string);
		cleanPs = cleanPs.replaceAll(" ", "&nbsp;");
		ps.setInnerHTML(cleanPs);
	}

	public void prompt(final String inputText) {
		setPromptActive(true);
		scrollToEnd();
		ps.setInnerHTML(cleanPs);
		setInput(inputText);
	}

	public void focusInput() {
		if (isFocused())
			setPromptActive(true);
		scrollToEnd();
		ps.setInnerHTML(cleanPs);
	}

	private boolean isCheckedScrollState = false;

	public void scrollToEnd() {
		if (scrollLock) {
			if (scrolledToEnd)
				term.setScrollTop(BIG_NUMBER);
		} else
			term.setScrollTop(BIG_NUMBER);
		isCheckedScrollState = false;
	}

	private void beforeChangeTerminal() {
		if (!isCheckedScrollState) {
			scrolledToEnd = term.getScrollTop() >= term.getScrollHeight() - term.getClientHeight();
			isCheckedScrollState = true;
		}
	}

	public void prompt() {
		prompt(null);
	}

	public void print(String string) {
		beforeChangeTerminal();
		if (string == null)
			string = "";
		if (isPromptActive()) {
			setPromptActive(false);
			if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
				newLine();
				reducePrompt(-1);
			}
			string = getCurrentPromptContent() + string;
		}
		String str = string.replaceAll("\t", tabs);

		// Continue to the last text node if available
		final Node last = getLastFirstLevelTextNode();
		int linesAdded = 0;
		if (last != null) {
			getLogger().info("print append to old node: '" + last.getNodeValue() + "'");
			str = last.getNodeValue() + str;
			buffer.removeChild(last);
			linesAdded--;
		}

		// Split by the newlines anyway
		int s = 0, e = str.indexOf('\n');
		while (e >= s) {
			final String line = str.substring(s, e);
			linesAdded += appendLine(buffer, line);
			buffer.appendChild(createBr());
			s = e + 1;
			e = str.indexOf('\n', s);
		}

		// Print the remaining string
		if (s < str.length()) {
			linesAdded += appendLine(buffer, str.substring(s));
		}

		reducePrompt(linesAdded);
	}

	public void printWithClass(String string, String className) {
		if (className == null) {
			print(string);
			return;
		}
		beforeChangeTerminal();
		if (string == null)
			string = "";
		if (isPromptActive()) {
			setPromptActive(false);
			if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
				newLine();
				reducePrompt(-1);
			}
			string = getCurrentPromptContent() + string;
		}
		String str = string.replaceAll("\t", tabs);
		int linesAdded = 0;

		Element classedChild = Document.get().createElement("span");
		classedChild.addClassName(className);
		buffer.appendChild(classedChild);
		// Split by the newlines anyway
		int s = 0, e = str.indexOf('\n');
		while (e >= s) {
			final String line = str.substring(s, e);
			linesAdded += appendLine(classedChild, line);
			classedChild.appendChild(createBr());
			s = e + 1;
			e = str.indexOf('\n', s);
		}

		// Print the remaining string
		if (s < str.length()) {
			linesAdded += appendLine(classedChild, str.substring(s));
		}

		reducePrompt(linesAdded);
	}

	public void append(String string) {
		getLogger().info("append = " + string);
		beforeChangeTerminal();
		if (string == null)
			string = "";
		String str = string.replaceAll("\t", tabs);

		// Continue to the last text node if available
		final Node last = getLastFirstLevelTextNode();
		int linesAdded = 0;
		if (last != null) {
			getLogger().info("print append to old node: '" + last.getNodeValue() + "'");
			str = last.getNodeValue() + str;
			buffer.removeChild(last);
			linesAdded--;
		}

		// Split by the newlines anyway
		int s = 0, e = str.indexOf('\n');
		while (e >= s) {
			final String line = str.substring(s, e);
			linesAdded += appendLine(buffer, line);
			buffer.appendChild(createBr());
			s = e + 1;
			e = str.indexOf('\n', s);
		}

		// Print the remaining string
		if (s < str.length()) {
			linesAdded += appendLine(buffer, str.substring(s));
		}

		reducePrompt(linesAdded);
	}

	public void appendWithClass(String string, String className) {
		if (className == null) {
			append(string);
			return;
		}
		getLogger().info("append = " + string + " classname = " + className);
		beforeChangeTerminal();
		if (string == null)
			string = "";
		String str = string.replaceAll("\t", tabs);
		int linesAdded = 0;

		Element classedChild = Document.get().createElement("span");
		classedChild.addClassName(className);
		buffer.appendChild(classedChild);
		// Split by the newlines anyway
		int s = 0, e = str.indexOf('\n');
		while (e >= s) {
			final String line = str.substring(s, e);
			linesAdded += appendLine(classedChild, line);
			classedChild.appendChild(createBr());
			s = e + 1;
			e = str.indexOf('\n', s);
		}

		// Print the remaining string
		if (s < str.length()) {
			linesAdded += appendLine(classedChild, str.substring(s));
		}

		reducePrompt(linesAdded);
	}

	private String getCurrentPromptContent() {
		return prompt.getInnerText() + getInput();
	}

	private void reducePrompt(final int rows) {
		int newRows = promptRows - rows;
		if (newRows < 1) {
			newRows = 1;
		}
		getLogger().info("prompt reduced from " + promptRows + " to " + newRows);
		setPromptHeight(newRows);
	}

	private void setPromptHeight(final int rows) {
		final int min = 1;
		final int max = rows;
		promptRows = rows < min ? min : (rows > max ? max : rows);
		final int newHeight = fontH * rows;
		getLogger().info("Prompt height=" + newHeight);
		promptWrap.getStyle().setHeight(newHeight, Unit.PX);
	}

	/**
	 * Split long text based on length.
	 * 
	 * @param parent
	 * @param str
	 * @return
	 */
	private int appendLine(final Node parent, String str) {
		int linesAdded = 0;
		parent.appendChild(createTextNode(str));
		getLogger().info("append: '" + str + "'");
		linesAdded++;

		// make sore we don't exceed the maximum buffer size
		checkBufferLimit();

		return linesAdded;
	}

	private void checkBufferLimit() {

		// Buffer means only offscreen lines
		final int maxb = maxBufferSize + (rows - promptRows);
		while (getBufferSize() > maxb && buffer.hasChildNodes()) {
			buffer.removeChild(buffer.getFirstChild());
		}

	}

	private Node getLastFirstLevelTextNode() {
		if (buffer == null) {
			return null;
		}
		final Node l = buffer.getLastChild();
		if (l != null && l.getNodeType() == Node.TEXT_NODE) {
			return l;
		}
		return null;
	}

	public void println(final String string) {
		print(string + "\n");
	}

	public void printlnWithClass(final String string, final String className) {
		printWithClass(string + "\n", className);
	}

	private void calculateRowsFromHeight() {
		final int h = term.getClientHeight() - (2 * padding);
		rows = h / fontH;

		getLogger().info("calculateRowsFromHeight: font=" + fontW + "x" + fontH
		 + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
		 + rows + ";size=" + getWidth() + "x" + getHeight());
	}

	private void calculateColsFromWidth() {
		final int w = term.getClientWidth();
		cols = (w - 2 * paddingW) / fontW;
		buffer.getStyle().setWidth((cols * fontW), Unit.PX);
		prompt.getStyle().setWidth((cols * fontW), Unit.PX);
		getLogger().info("calculateColsFromWidth: font=" + fontW + "x" + fontH
		 + ";scrollbar=" + scrollbarW + ";cols=" + cols + ";rows="
		 + rows + ";size=" + getWidth() + "x" + getHeight());
	}

	@Override
	public void setFocus(final boolean focused) {
		this.focused = focused;
		super.setFocus(focused);
		if (focused) {
			focusPrompt();
		}
	}

	public void setHandler(final TextConsoleHandler handler) {
		this.handler = handler;
	}

	public void reset() {
		beforeChangeTerminal();
		setPromptActive(false);
		clearBuffer();
		setPromptHeight(rows);
		print(greeting);
		prompt();
	}

	public int getBufferSize() {
		return (buffer.getClientHeight() / fontH);
	}

	public int getMaxBufferSize() {
		return maxBufferSize;
	}

	public void setMaxBufferSize(final int maxBuffer) {
		maxBufferSize = maxBuffer > 0 ? maxBuffer : 0;
		checkBufferLimit();
	}

	public void clearBuffer() {
		// Remove all children.
		while (buffer.hasChildNodes()) {
			buffer.removeChild(buffer.getFirstChild());
		}
	}

	void formFeed() {
		for (int i = 0; i < promptRows; i++) {
			newLine();
		}
		setPromptHeight(rows);
		scrollToEnd();

		checkBufferLimit();
	}

	void clearCommandHistory() {
		cmdHistory = new ArrayList<String>();
		cmdHistoryIndex = -1;
	}

	public String getHeight() {
		return (term.getClientHeight() - 2 * padding) + "px";
	}

	public String getWidth() {
		return (term.getClientWidth() + scrollbarW - 2 * paddingW) + "px";
	}

	protected void bell() {
		// Clear previous
		if (timer != null) {
			timer.cancel();
			timer = null;
			term.removeClassName("term-rev");
			input.removeClassName("term-rev");
		}
		// Add styles and start the timer
		input.addClassName("term-rev");
		term.addClassName("term-rev");
		timer = new Timer() {

			@Override
			public void run() {
				term.removeClassName("term-rev");
				input.removeClassName("term-rev");
			}
		};
		timer.schedule(150);
	}

	@Override
	protected void onUnload() {
		super.onUnload();

		if (clickHandler != null) {
			clickHandler.removeHandler();
			clickHandler = null;
		}
		if (focusHandler != null) {
			focusHandler.removeHandler();
			focusHandler = null;
		}
		if (keyHandler != null) {
			keyHandler.removeHandler();
			keyHandler = null;
		}
	}

	@Override
	protected void onLoad() {
		super.onLoad();
		init();
	}

	private static Logger getLogger() {
		return Logger.getLogger(TextConsole.class.getName());
	}

	public void resized() {
		calculateColsFromWidth();
		calculateRowsFromHeight();
		scrollToEnd();
	}

	public void setGreeting(String greeting) {
		getLogger().info("setGreeting: greeting=" + greeting);
		this.greeting = greeting;
	}

	public void setScrollLock(boolean scrollLock) {
		getLogger().info("setScrollLock: scrollLock=" + scrollLock);
		this.scrollLock = scrollLock;
	}

	public void setWrap(boolean wrap) {
		getLogger().info("setWrap: wrap=" + wrap);
		if (wrap) {
			buffer.addClassName("soft-wrap");
		} else {
			buffer.removeClassName("soft-wrap");
		}
	}
}
