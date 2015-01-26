package imj2.pixel3d;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author codistmonk (creation 2014-03-15)
 */
public abstract class MouseHandler extends MouseAdapter implements Serializable {
	
	private final AtomicBoolean updateNeeded;
	
	protected MouseHandler(final AtomicBoolean sharedUpdateFlag) {
		this.updateNeeded = sharedUpdateFlag != null ? sharedUpdateFlag : new AtomicBoolean(true);
	}
	
	@SuppressWarnings("unchecked")
	public final <T extends MouseHandler> T addTo(final Component component) {
		component.addMouseListener(this);
		component.addMouseMotionListener(this);
		component.addMouseWheelListener(this);
		
		return (T) this;
	}
	
	@SuppressWarnings("unchecked")
	public final <T extends MouseHandler> T removeFrom(final Component component) {
		component.removeMouseListener(this);
		component.removeMouseMotionListener(this);
		component.removeMouseWheelListener(this);
		
		return (T) this;
	}
	
	public final AtomicBoolean getUpdateNeeded() {
		return this.updateNeeded;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 2247894840683764453L;
	
}

