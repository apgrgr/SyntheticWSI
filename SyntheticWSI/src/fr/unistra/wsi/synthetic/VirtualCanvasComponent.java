package fr.unistra.wsi.synthetic;

import static java.lang.Math.max;

import imj2.tools.Canvas;
import imj2.tools.Image2DComponent.Painter;

import java.awt.Adjustable;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JScrollBar;

/**
 * @author ga (creation 2014-09-05)
 */
public final class VirtualCanvasComponent extends JComponent {
	
	private final Dimension virtualSize;
	
	private final Point viewportLocation;
	
	private final Canvas viewport;
	
	private final List<Painter<VirtualCanvasComponent>> painters;
	
	private final JScrollBar horizontalScrollBar;
	
	private final JScrollBar verticalScrollBar;
	
	private double scale;
	
	public VirtualCanvasComponent() {
		this.virtualSize = new Dimension(512, 512);
		this.viewportLocation = new Point();
		this.viewport = new Canvas().setFormat(this.virtualSize.width, this.virtualSize.height, BufferedImage.TYPE_INT_ARGB);
		this.painters = new ArrayList<>();
		this.horizontalScrollBar = new JScrollBar(Adjustable.HORIZONTAL, 0, 1, 0, 1);
		this.verticalScrollBar = new JScrollBar(Adjustable.VERTICAL, 0, 1, 0, 1);
		this.scale = 1.0;
		
		this.setPreferredSize(this.virtualSize);
		
		{
			this.setLayout(new GridBagLayout());
			
			final GridBagConstraints constraints = new GridBagConstraints();
			
			{
				constraints.anchor = GridBagConstraints.SOUTHWEST;
				constraints.gridx = 0;
				constraints.gridy = 1;
				constraints.fill = GridBagConstraints.HORIZONTAL;
				constraints.weightx = 1.0;
				constraints.weighty = 0.0;
				
				this.add(this.horizontalScrollBar, constraints);
			}
			
			{
				constraints.anchor = GridBagConstraints.NORTHEAST;
				constraints.gridx = 1;
				constraints.gridy = 0;
				constraints.fill = GridBagConstraints.VERTICAL;
				constraints.weightx = 0.0;
				constraints.weighty = 1.0;
				
				this.add(this.verticalScrollBar, constraints);
			}
		}
		
		this.addComponentListener(new ComponentAdapter() {
			
			@Override
			public final void componentResized(final ComponentEvent event) {
				VirtualCanvasComponent.this.resized();
			}
			
		});
		final AdjustmentListener adjustmentListener = new AdjustmentListener() {
			
			@Override
			public final void adjustmentValueChanged(final AdjustmentEvent event) {
				VirtualCanvasComponent.this.scrolled();
			}
			
		};
		this.getHorizontalScrollBar().addAdjustmentListener(adjustmentListener);
		this.getVerticalScrollBar().addAdjustmentListener(adjustmentListener);
	}
	
	public final double getScale() {
		return this.scale;
	}
	
	public final void setScale(final double scale) {
		if (scale <= 0.0) {
			throw new IllegalArgumentException();
		}
		
		if (scale != this.getScale()) {
			final double viewportCenterUnscaledX1 = this.getViewportCenterUnscaledX();
			final double viewportCenterUnscaledY1 = this.getViewportCenterUnscaledY();
			this.scale = scale;
			
			this.resized();
			
			
			this.getHorizontalScrollBar().setValue((int) (this.scale(viewportCenterUnscaledX1) - this.getViewportWidth() / 2.0));
			this.getVerticalScrollBar().setValue((int) (this.scale(viewportCenterUnscaledY1) - this.getViewportHeight() / 2.0));
		}
	}
	
	public final double getViewportCenterUnscaledX() {
		return this.unscale(this.getViewportX() + this.getViewportWidth() / 2.0);
	}
	
	public final double getViewportCenterUnscaledY() {
		return this.unscale(this.getViewportY() + this.getViewportHeight() / 2.0);
	}
	
	public final int getViewportWidth() {
		return this.getViewportImage().getWidth();
	}
	
	public final int getViewportHeight() {
		return this.getViewportImage().getHeight();
	}
	
	public final double scale(final double value) {
		return value * this.getScale();
	}
	
	public final int scale(final int value) {
		return (int) (value * this.getScale());
	}
	
	public final double unscale(final double value) {
		return value / this.getScale();
	}
	
	public final int unscale(final int value) {
		return (int) (value / this.getScale());
	}
	
	public final void setVirtualSize(final int virtualWidth, final int virtualHeight) {
		this.resized(virtualWidth, virtualHeight);
		this.refreshViewport();
	}
	
	public final void addVirtualRectangle(final Rectangle bounds) {
		boolean refreshViewport = false;
		
		if (this.getVirtualWidth() < bounds.x + bounds.width) {
			this.resized(bounds.x + bounds.width, this.getVirtualHeight());
			refreshViewport = true;
		}
		if (this.getVirtualHeight() < bounds.y + bounds.height) {
			this.resized(this.getVirtualWidth(), bounds.y + bounds.height);
			refreshViewport = true;
		}
		if (bounds.x < 0) {
			this.resized(this.getVirtualWidth() - bounds.x, this.getVirtualHeight());
			this.getHorizontalScrollBar().setValue(this.viewportLocation.x - bounds.x);
			refreshViewport = true;
		}
		if (bounds.y < 0) {
			this.viewportLocation.y -= bounds.y;
			this.resized(this.getVirtualWidth(), this.getVirtualHeight() - bounds.y);
			this.getVerticalScrollBar().setValue(this.viewportLocation.y - bounds.y);
			refreshViewport = true;
		}
		
		if (refreshViewport) {
			this.refreshViewport();
		}
	}
	
	public final JScrollBar getHorizontalScrollBar() {
		return this.horizontalScrollBar;
	}
	
	public final JScrollBar getVerticalScrollBar() {
		return this.verticalScrollBar;
	}
	
	public final int getClientWidth() {
		return this.getVerticalScrollBar().getX();
	}
	
	public final int getClientHeight() {
		return this.getHorizontalScrollBar().getY();
	}
	
	public final List<Painter<VirtualCanvasComponent>> getPainters() {
		return this.painters;
	}
	
	public final int getVirtualWidth() {
		return this.virtualSize.width;
	}
	
	public final int getVirtualHeight() {
		return this.virtualSize.height;
	}
	
	public final int getViewportX() {
		return this.viewportLocation.x;
	}
	
	public final int getViewportY() {
		return this.viewportLocation.y;
	}
	
	public final BufferedImage getViewportImage() {
		return this.viewport.getImage();
	}
	
	public final Graphics2D getViewportGraphics() {
		return this.viewport.getGraphics();
	}
	
	public final void refreshViewport() {
		final Graphics2D bufferGraphics = this.getViewportGraphics();
		final int clientWidth = this.getClientWidth();
		final int clientHeight = this.getClientHeight();
		
		bufferGraphics.setColor(this.getBackground());
		bufferGraphics.fillRect(0, 0, clientWidth, clientHeight);
		
		final Graphics2D graphics = (Graphics2D) this.getGraphics();
		
		for (final Painter<VirtualCanvasComponent> painter : this.getPainters()) {
			painter.paint(graphics, this, clientWidth, clientHeight);
		}
		
		this.repaint();
	}
	
	@Override
	protected final void paintComponent(final Graphics g) {
		g.drawImage(this.getViewportImage(), 0, 0, null);
	}
	
	final void scrolled() {
		this.viewportLocation.setLocation(this.getHorizontalScrollBar().getValue(), this.getVerticalScrollBar().getValue());
		this.refreshViewport();
	}
	
	final void resized() {
		this.resized(this.virtualSize.width, this.virtualSize.height);
		this.refreshViewport();
	}
	
	private final void resized(final int virtualWidth, final int virtualHeight) {
		final int clientWidth = this.getClientWidth();
		final int clientHeight = this.getClientHeight();
		this.virtualSize.width = max(virtualWidth, clientWidth);
		this.virtualSize.height = max(virtualHeight, clientHeight);
		this.viewport.setFormat(clientWidth, clientHeight, BufferedImage.TYPE_INT_ARGB);
		this.getHorizontalScrollBar().setMaximum(max(1, this.scale(virtualWidth)));
		this.getHorizontalScrollBar().setVisibleAmount(clientWidth);
		this.getVerticalScrollBar().setMaximum(max(1, this.scale(virtualHeight)));
		this.getVerticalScrollBar().setVisibleAmount(clientHeight);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -161764318379463769L;
	
}