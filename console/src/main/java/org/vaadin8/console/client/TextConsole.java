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

import java.util.logging.Logger;

public class TextConsole extends FocusWidget {
    /* Control characters in http://en.wikipedia.org/wiki/Control_character */

    private static final char CTRL_END_OF_TEXT = 'C';
    private static final char CTRL_BELL = 'G';
    private static final char CTRL_BACKSPACE = 'H';
    private static final char CTRL_TAB = 'I';
    private static final char CTRL_LINE_FEED = 'J';
    private static final char CTRL_FORM_FEED = 'L';
    private static final char CTRL_CARRIAGE_RETURN = 'M';
    private static final char CTRL_ESCAPE = '[';
    private static final char CTRL_DELETE = '?';

    private static final char[] CTRL = {CTRL_BELL, CTRL_BACKSPACE, CTRL_TAB, CTRL_LINE_FEED, CTRL_FORM_FEED, CTRL_CARRIAGE_RETURN, CTRL_ESCAPE, CTRL_DELETE, CTRL_END_OF_TEXT};

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

    private boolean scrollLock;

    private final DivElement term;
    private TextConsoleHandler handler;
    private final Element buffer;
    private final TableElement prompt;
    private final Element ps;
    private final InputElement input;
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

        setBackgroundReversed(false);

        registerHandlersIfNeeded();

        updateFontDimensions();
    }

    private void registerHandlersIfNeeded() {
        if (clickHandler == null) {
            clickHandler = addDomHandler(event -> setFocus(true), ClickEvent.getType());
        }

        if (keyHandler == null) {
            keyHandler = addDomHandler(event -> {
                getLogger().info("keyEvent(" + event.getNativeKeyCode()
                        + ", ctrl: " + event.isControlKeyDown()
                        + ", alt: " + event.isAltKeyDown()
                        + ", meta: " + event.isMetaKeyDown()
                        + ")");
                if (event.getNativeEvent().getCtrlKey()) {
                    final char ctrlChar = getControlKey(event.getNativeKeyCode());
                    if (ctrlChar > 0) {
                        event.preventDefault();
                        handleControlChar(ctrlChar);
                    }
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    event.preventDefault();
                    handleControlChar(CTRL_ESCAPE);
                } else if (!isPromptActive()) {
                    event.preventDefault();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    event.preventDefault();
                    carriageReturn();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_UP || event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
                    event.preventDefault();
                    handleCommandHistoryBrowse(event.getNativeKeyCode());
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_TAB) {
					event.preventDefault();
                    handleSuggest();
                } else if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE && getInputLength() == 0) {
                    bell();
                }
            }, KeyDownEvent.getType());
        }
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
            case TextConsole.CTRL_LINE_FEED:
                carriageReturn();
                break;
            case TextConsole.CTRL_DELETE:
            case TextConsole.CTRL_FORM_FEED:
                bell();
                break;
            case TextConsole.CTRL_TAB:
                tab();
                break;
            default:
                controlChar(c);
                break;
        }
    }

    protected void controlChar(char c) {
        handler.controlChar(c);
    }

    private void handleCommandHistoryBrowse(final int i) {
        if (i == KeyCodes.KEY_UP) {
            handler.previousCommand();
        } else {
            handler.nextCommand();
        }
    }

    private void handleSuggest() {
        handler.suggest(getInput());
    }

    public String getInput() {
        return input.getValue();
    }

    private int getInputLength() {
        final String v = getInput();
        return v == null ? 0 : v.length();
    }

    public void setInput(final String inputText) {
        if (inputText != null) {
            input.setValue(inputText);
        } else {
            input.setValue("");
        }
    }

    protected void tab() {
        setInput(getInput() + "\t");
    }

    private void backspace() {
        bell();
    }

    void carriageReturn() {
        String lineBuffer = getInput();
		handler.terminalInput(this, lineBuffer);
        setFocus(true);
    }

    private boolean bufferIsEmpty() {
        return !buffer.hasChildNodes();
    }

    void setPromptVisible(final boolean active) {
        getLogger().info("setPromptActive(" + active + ")");
        if (active && !isPromptActive()) {
            prompt.getStyle().setDisplay(Display.BLOCK);
            ensureFocus();
        } else if (!active && isPromptActive()) {
            prompt.getStyle().setDisplay(Display.NONE);
            ensureFocus();
        }
    }

    void setInputEditable(boolean editable) {
        input.setReadOnly(!editable);
    }

    private boolean isPromptActive() {
        return !Display.NONE.getCssName().equals(prompt.getStyle().getDisplay());
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
		Node last = getLastBufferChild();
		return last != null && "br".equals(last.getNodeName().toLowerCase());
    }

	private Element getCurrentSpan() {
		Node last = getLastBufferChild();
		if (last != null && last.getNodeType() == Node.ELEMENT_NODE && "span".equals(last.getNodeName().toLowerCase())) {
			return (Element)last;
		} else {
			return null;
		}
	}

	private Node getLastBufferChild() {
		Node last = buffer != null ? buffer.getLastChild() : null;
		while (last != null && last.getLastChild() != null) {
			last = last.getLastChild();
		}
		return last;
	}

	private void focusPrompt() {
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
        } else if (input.createTextRange) {
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
        return w1 - w2;
    }-*/;

    public void newLine() {
        getLogger().info("newline");
        beforeChangeTerminal();
		appendNewLine();
		checkBufferLimit();
		scrollToEnd();
    }

	private void appendNewLine() {
		buffer.appendChild(Document.get().createBRElement());
	}

	public void newLineIfNotEndsWithNewLine() {
        if (!bufferIsEmpty() && !bufferEndsWithNewLine()) {
            newLine();
        }
    }

    public void setPs(final String string) {
        getLogger().info("setPs: ps=" + string);
        cleanPs = Util.escapeHTML(string);
        cleanPs = cleanPs.replaceAll(" ", "&nbsp;");
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

    public void print(String string) {
		printWithClass(string, null);
    }

    public void printWithClass(String string, String className) {
    	if (string == null) {
    		return;
		}

        beforeChangeTerminal();

    	string = string.replaceAll("\t", tabs);

		Element span = getCurrentSpan();
		if (span == null || !isSameClass(span.getClassName(), className)) {
			span = Document.get().createElement("span");
			span.appendChild(Document.get().createTextNode(""));
			if (className != null) {
				span.addClassName(className);
			}
            buffer.appendChild(span);
		}

		Text text = (Text) span.getChild(0);
		text.insertData(text.getLength(), string);

		scrollToEnd();
    }

	private boolean isSameClass(String class1, String class2) {
    	if (class1 == null) {
    		class1 = "";
		}
		if (class2 == null) {
			class2 = "";
		}
		return class1.equals(class2);
	}

	private String getCurrentPromptContent() {
        return prompt.getInnerText() + getInput();
    }

    private void checkBufferLimit() {
        // Buffer means only offscreen lines
        final int maxb = maxBufferSize + rows;
        while (getBufferSize() > maxb && buffer.hasChildNodes()) {
            buffer.removeChild(buffer.getFirstChild());
        }

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
        ensureFocus();
    }

    private void ensureFocus() {
        if (focused) {
            if (isPromptActive()) {
                input.focus();

                // Focus to end
                final String s = getInput();
                if (s != null && s.length() > 0) {
                    setSelectionRange(input, s.length(), s.length());
                }
            } else {
                term.focus();
            }
        }
    }

    public void setHandler(final TextConsoleHandler handler) {
        this.handler = handler;
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
        }
        // Add styles and start the timer
        setBackgroundReversed(true);
        timer = new Timer() {

            @Override
            public void run() {
                setBackgroundReversed(false);
            }
        };
        timer.schedule(150);
    }

    private void setBackgroundReversed(boolean reversed) {
        if (reversed) {
            term.addClassName("term-bg-rev");
            term.removeClassName("term-bg");
            input.addClassName("term-bg-rev");
            input.removeClassName("term-bg");
        } else {
            term.addClassName("term-bg");
            term.removeClassName("term-bg-rev");
            input.addClassName("term-bg");
            input.removeClassName("term-bg-rev");
        }
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
