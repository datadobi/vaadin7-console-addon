package org.vaadin8.console.client;

import com.vaadin.shared.annotations.DelegateToWidget;

/**
 * @author indvdum (gotoindvdum[at]gmail[dot]com)
 * @since 16.06.2014 17:44:05
 * 
 */
public class ConsoleState extends com.vaadin.shared.AbstractComponentState {

	private static final long serialVersionUID = -5576147144891328552L;

	@DelegateToWidget
	public String greeting;
	@DelegateToWidget
	public boolean wrap;
	@DelegateToWidget
	public boolean scrollLock;
	@DelegateToWidget
	public String ps;
	@DelegateToWidget
	public int maxBufferSize;

	public ConsoleState() {
		maxBufferSize = 256;
		ps = "}> ";
		scrollLock = false;
		wrap = true;
		greeting = "Console ready.";
		primaryStyleName = "v-console";
	}
}