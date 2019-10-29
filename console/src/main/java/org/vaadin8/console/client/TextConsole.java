package org.vaadin8.console.client;

import com.google.gwt.dom.client.*;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.FocusWidget;
import com.vaadin.client.Util;

import java.util.*;
import java.util.logging.Logger;

public class TextConsole extends FocusWidget {
    private static final String DEFAULT_TABS = "    ";
    private static final int BIG_NUMBER = 100000;

    private boolean scrollLock;

    private final DivElement term;
    private TextConsoleHandler handler;
    private final Element buffer;
    private final TableElement prompt;
    private final Element ps;
    private final InputElement input;
    private List<HandlerRegistration> handlers = new ArrayList<>();
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
        if (!handlers.isEmpty()) {
            return;
        }

        MouseClickHandler mouseHandler = new MouseClickHandler(event -> {
            getLogger().info("clicked()");
            setFocus(true);
        });

        handlers.add(addDomHandler(mouseHandler, ClickEvent.getType()));
        handlers.add(addDomHandler(mouseHandler, MouseDownEvent.getType()));
        handlers.add(addDomHandler(mouseHandler, MouseUpEvent.getType()));
        handlers.add(addDomHandler(mouseHandler, MouseMoveEvent.getType()));
        handlers.add(addDomHandler(mouseHandler, MouseOutEvent.getType()));

        handlers.add(addDomHandler(new KeyHandler(), KeyDownEvent.getType()));
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
            return (Element) last;
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

    private void setCursorPosition(int position) {
        setSelectionRange(input, position, position);
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

    private native String getKey(NativeEvent evt) /*-{
        return evt.key;
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
        getLogger().info("setFocus(focused: " + this.focused + " -> " + focused + ")");
        this.focused = focused;
        ensureFocus();
    }

    private void ensureFocus() {
        getLogger().info("ensureFocus(focused: " + focused + ")");
        if (focused) {
            if (isPromptActive()) {
                getLogger().info("focus input");
                input.focus();

                // Focus to end
                final String s = getInput();
                if (s != null && s.length() > 0) {
                    setCursorPosition(s.length());
                }
            } else {
                getLogger().info("focus term");
                super.setFocus(true);
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

        for (HandlerRegistration h : handlers) {
            h.removeHandler();
        }
        handlers.clear();
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

    private class MouseClickHandler implements MouseDownHandler, MouseUpHandler, MouseMoveHandler, MouseOutHandler, ClickHandler {
        private final int mouseButton;
        private final ClickHandler delegate;

        private boolean down;
        private boolean ignoreClick;
        private int downX;
        private int downY;
        private int distThreshold;

        private MouseClickHandler(ClickHandler delegate) {
            mouseButton = NativeEvent.BUTTON_LEFT;
            distThreshold = 3 * 3;
            this.delegate = delegate;
        }

        @Override
        public void onClick(ClickEvent clickEvent) {
            if (clickEvent.getNativeButton() != mouseButton) {
                return;
            }

            if (ignoreClick) {
                return;
            }

            delegate.onClick(clickEvent);
        }

        @Override
        public void onMouseDown(MouseDownEvent mouseDownEvent) {
            if (mouseDownEvent.getNativeButton() != mouseButton) {
                return;
            }

            down = true;
            ignoreClick = false;
            downX = mouseDownEvent.getClientX();
            downY = mouseDownEvent.getClientY();
        }

        @Override
        public void onMouseUp(MouseUpEvent mouseUpEvent) {
            if (mouseUpEvent.getNativeButton() != mouseButton) {
                return;
            }

            down = false;
        }

        @Override
        public void onMouseMove(MouseMoveEvent mouseMoveEvent) {
            if (!down) {
                return;
            }

            int x = mouseMoveEvent.getClientX();
            int y = mouseMoveEvent.getClientY();
            int dx = x - downX;
            int dy = y - downY;
            int distSquared = dx * dx + dy * dy;
            if (distSquared > distThreshold) {
                ignoreClick = true;
            }
        }

        @Override
        public void onMouseOut(MouseOutEvent mouseOutEvent) {
            if (down) {
                ignoreClick = true;
            }
        }
    }

    private static final Set<String> SPECIAL_KEYS = new HashSet<>(Arrays.asList(
            "Esc", "Escape", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19"
    ));

    private class KeyHandler implements KeyDownHandler {

        @Override
        public void onKeyDown(KeyDownEvent event) {
            String key = TextConsole.this.getKey(event.getNativeEvent());
            boolean ctrl = event.isControlKeyDown();
            boolean alt = event.isAltKeyDown();
            boolean shift = event.isShiftKeyDown();
            boolean meta = event.isMetaKeyDown();
            getLogger().info("keyEvent(" + key + ", ctrl: " + ctrl + ", alt: " + alt + ", meta: " + meta + ")");
            if (ctrl) {
                /* Control characters in https://en.wikipedia.org/wiki/C0_and_C1_control_codes */
                switch (key) {
                    case "a":
                        event.preventDefault();
                        setCursorPosition(0);
                        return;
                    case "e":
                        event.preventDefault();
                        setCursorPosition(TextConsole.this.getInputLength());
                        return;
                    case "h":
                    case "?":
                    case "g":
                        bell();
                        return;
                    case "m":
                    case "j":
                    case "k":
                        carriageReturn();
                        return;
                    case "l":
                        clearBuffer();
                        return;
                    case "i":
                        tab();
                        return;
                    case "[":
                        handler.controlChar("Escape", 0);
                        return;
                }
            }

            switch (key) {
                case "Enter":
                    event.preventDefault();
                    TextConsole.this.carriageReturn();
                    break;
                case "Up":
                case "ArrowUp":
                    event.preventDefault();
                    handler.previousCommand();
                    break;
                case "Down":
                case "ArrowDown":
                    event.preventDefault();
                    handler.nextCommand();
                    break;
                case "Tab":
                    event.preventDefault();
                    handler.suggest(TextConsole.this.getInput());
                    break;
                case "Backspace":
                    if (getInputLength() == 0) {
                        bell();
                    }
                    break;
                default:
                    if (SPECIAL_KEYS.contains(key)) {
                        event.preventDefault();
                        int modifier = 0;
                        if (shift) modifier |= TextConsoleHandler.SHIFT;
                        if (ctrl) modifier |= TextConsoleHandler.CTRL;
                        if (alt) modifier |= TextConsoleHandler.ALT;
                        if (meta) modifier |= TextConsoleHandler.META;
                        handler.controlChar(key, modifier);
                    }
            }
        }
    }
}
