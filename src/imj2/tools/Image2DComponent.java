package imj2.tools;

import static imj2.tools.IMJTools.newC4U8ConcreteImage2D;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.synchronizedMap;
import static multij.swing.SwingTools.horizontalBox;
import static multij.tools.Tools.unchecked;
import imj2.core.Image2D;
import imj2.core.Image2D.MonopatchProcess;
import imj2.core.ScaledImage2D;

import java.awt.Adjustable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;

import multij.swing.SwingTools;
import multij.tools.Tools;

/**
 * @author codistmonk (creation 2013-08-05)
 */
public final class Image2DComponent extends JComponent {
	
	private ScaledImage2D scaledImage;
	
	private BufferedImage backBuffer;
	
	private Graphics2D backBufferGraphics;
	
	private BufferedImage frontBuffer;
	
	private Graphics2D frontBufferGraphics;
	
	private final Rectangle scaledImageVisibleRectangle;
	
	private final JScrollBar horizontalScrollBar;
	
	private final JScrollBar verticalScrollBar;
	
	private final List<Painter<Image2DComponent>> painters; 
	
	private final Map<Object, Object> documentData;
	
	public Image2DComponent() {
		this.scaledImageVisibleRectangle = new Rectangle();
		this.horizontalScrollBar = new JScrollBar(Adjustable.HORIZONTAL);
		this.verticalScrollBar = new JScrollBar(Adjustable.VERTICAL);
		this.painters = new ArrayList<Painter<Image2DComponent>>();
		this.documentData = synchronizedMap(new LinkedHashMap<Object, Object>());
		this.setDoubleBuffered(false);
		this.setLayout(new BorderLayout());
		this.add(horizontalBox(this.getHorizontalScrollBar(), Box.createHorizontalStrut(this.getVerticalScrollBar().getPreferredSize().width)), BorderLayout.SOUTH);
		this.add(this.getVerticalScrollBar(), BorderLayout.EAST);
		
		this.addComponentListener(new ComponentAdapter() {
			
			@Override
			public final void componentResized(final ComponentEvent event) {
				Image2DComponent.this.setScrollBarsVisibleAmounts();
			}
			
		});
		
		final AdjustmentListener bufferPositionAdjuster = new AdjustmentListener() {
			
			@Override
			public final void adjustmentValueChanged(final AdjustmentEvent event) {
				Image2DComponent.this.updateBufferAccordingToScrollBars(false);
				Image2DComponent.this.repaint();
			}
			
		};
		
		this.getHorizontalScrollBar().addAdjustmentListener(bufferPositionAdjuster);
		this.getVerticalScrollBar().addAdjustmentListener(bufferPositionAdjuster);
		
		this.setBackground(Color.BLACK);
		
		this.setFocusable(true);
		
		this.addKeyListener(new KeyAdapter() {
			
			@Override
			public final void keyTyped(final KeyEvent event) {
				switch (event.getKeyChar()) {
				case '*':
					Image2DComponent.this.increaseZoom();
					break;
				case '/':
					Image2DComponent.this.decreaseZoom();
					break;
				case '+':
					Image2DComponent.this.increaseLOD();
					break;
				case '-':
					Image2DComponent.this.decreaseLOD();
					break;
				default:
					return;
				}
			}
			
		});
		
		final MouseAdapter mouseHandler = new MouseAdapter() {
			
			private int horizontalScrollBarValue;
			
			private int verticalScrollBarValue;
			
			private int x;
			
			private int y;
			
			@Override
			public final void mousePressed(final MouseEvent event) {
				if (!Image2DComponent.this.requestFocusInWindow()) {
					Image2DComponent.this.requestFocus();
				}
				
				this.horizontalScrollBarValue = Image2DComponent.this.getHorizontalScrollBar().getValue();
				this.verticalScrollBarValue = Image2DComponent.this.getVerticalScrollBar().getValue();
				this.x = event.getX();
				this.y = event.getY();
			}
			
			@Override
			public final void mouseDragged(final MouseEvent event) {
				Image2DComponent.this.getHorizontalScrollBar().setValue(this.horizontalScrollBarValue - (event.getX() - this.x));
				Image2DComponent.this.getVerticalScrollBar().setValue(this.verticalScrollBarValue - (event.getY() - this.y));
			}
			
		};
		
		this.addMouseListener(mouseHandler);
		this.addMouseMotionListener(mouseHandler);
		this.addMouseWheelListener(mouseHandler);
	}
	
	public Image2DComponent(final Image2D image) {
		this();
		this.scaledImage = new ScaledImage2D(image);
		this.getHorizontalScrollBar().setMaximum(image.getWidth());
		this.getVerticalScrollBar().setMaximum(image.getHeight());
		
		final Dimension preferredSize = new Dimension(Toolkit.getDefaultToolkit().getScreenSize());
		preferredSize.width = min(preferredSize.width / 2, image.getWidth() + this.getVerticalScrollBar().getPreferredSize().width);
		preferredSize.height = min(preferredSize.height / 2, image.getHeight() + this.getHorizontalScrollBar().getPreferredSize().height);
		
		this.setPreferredSize(preferredSize);
	}
	
	public final Map<Object, Object> getDocumentData() {
		return this.documentData;
	}
	
	public final BufferedImage getFrontBuffer() {
		return this.frontBuffer;
	}
	
	public void decreaseLOD() {
		final Image2D image = this.getImage();
		
		if (image.getWidth() <= 1 || image.getHeight() <= 1) {
			return;
		}
		
		this.setScaledImage(this.getScaledImage().getLODImage(image.getLOD() + 1));
		this.updateView();
	}

	public void increaseLOD() {
		final Image2D image = this.getImage();
		
		if (image.getLOD() <= 0) {
			return;
		}
		
		this.setScaledImage(this.getScaledImage().getLODImage(image.getLOD() - 1));
		this.updateView();
	}

	public final void decreaseZoom() {
		final int zoom = this.getZoom();
		
		if (zoom <= 1) {
			return;
		}
		
		this.setZoom(zoom / 2);
		this.updateView();
	}

	public final void increaseZoom() {
		final int zoom = this.getZoom();
		
		if (256 <= zoom) {
			return;
		}
		
		this.setZoom(zoom * 2);
		this.updateView();
	}
	
	public final void updateView() {
		final JScrollBar horizontalScrollBar = this.getHorizontalScrollBar();
		final JScrollBar verticalScrollBar = this.getVerticalScrollBar();
		final int oldHV = horizontalScrollBar.getValue();
		final int oldHA = horizontalScrollBar.getVisibleAmount();
		final int oldHM = horizontalScrollBar.getMaximum();
		final int oldVV = verticalScrollBar.getValue();
		final int oldVA = verticalScrollBar.getVisibleAmount();
		final int oldVM = verticalScrollBar.getMaximum();
		
		this.setScrollBarsVisibleAmounts();
		this.updateBufferAccordingToScrollBars(true);
		
		final int newHA = horizontalScrollBar.getVisibleAmount();
		final int newHM = horizontalScrollBar.getMaximum();
		final int newVA = verticalScrollBar.getVisibleAmount();
		final int newVM = verticalScrollBar.getMaximum();
		
		// oldC / oldM = newC / newM
		// -> newC = oldC * newM / oldM
		// -> newV + newA / 2 = (oldV + oldA / 2) * newM / oldM
		// -> newV = (oldV + oldA / 2) * newM / oldM - newA / 2
		// -> newV = ((2 * oldV + oldA) * newM - newA * oldM) / (2 * oldM)
		horizontalScrollBar.setValue((int) (((2L * oldHV + oldHA) * newHM - (long) newHA * oldHM) / (2L * oldHM)));
		verticalScrollBar.setValue((int) (((2L * oldVV + oldVA) * newVM - (long) newVA * oldVM) / (2L * oldVM)));
		
		this.updateBuffer();
	}
	
	public final List<Painter<Image2DComponent>> getPainters() {
		return this.painters;
	}
	
	public final int getZoom() {
		return this.getScaledImage().getZoom();
	}
	
	public final void setZoom(final int zoom) {
		if (0 < zoom && zoom != this.getZoom()) {
			this.getScaledImage().setZoom(zoom);
			
			this.updateBuffer();
		}
	}
	
	public final Image2D getImage() {
		return this.getScaledImage() == null ? null : this.getScaledImage().getSource();
	}
	
	public final void setImage(final Image2D image) {
		final int zoom = this.getZoom();
		this.setScaledImage(new ScaledImage2D(image));
		this.setZoom(zoom);
	}
	
	public final boolean setBuffer() {
		final int width = min(this.getScaledImageWidth(), max(1, this.getUsableWidth()));
		final int height = min(this.getScaledImageHeight(), max(1, this.getUsableHeight()));
		final boolean createBuffer;
		
		if (this.frontBuffer == null) {
			createBuffer = true;
		} else if (this.frontBuffer.getWidth() != width || this.frontBuffer.getHeight() != height) {
			this.frontBufferGraphics.dispose();
			this.frontBufferGraphics = null;
			this.backBufferGraphics.dispose();
			this.backBufferGraphics = null;
			createBuffer = true;
		} else {
			createBuffer = false;
		}
		
		if (createBuffer) {
			final BufferedImage oldBuffer = this.frontBuffer;
			this.frontBuffer = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
			this.frontBufferGraphics = this.frontBuffer.createGraphics();
			this.backBuffer = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
			this.backBufferGraphics = this.backBuffer.createGraphics();
			
			this.setScaledImageVisibleRectangle(new Rectangle(
					min(this.scaledImageVisibleRectangle.x, this.getScaledImageWidth() - width),
					min(this.scaledImageVisibleRectangle.y, this.getScaledImageHeight() - height),
					width, height), oldBuffer);
		}
		
		return createBuffer;
	}
	
	public final void updateBuffer() {
		if (!this.setBuffer()) {
			this.updateBuffer(this.scaledImageVisibleRectangle.x, this.scaledImageVisibleRectangle.y,
					this.scaledImageVisibleRectangle.width, this.scaledImageVisibleRectangle.height);
		}
		
		this.repaint();
	}
	
	public final Rectangle getVisibleBoxInImage() {
		final int x = this.getXInImage(this.getCenteringOffsetX());
		final int y = this.getYInImage(this.getCenteringOffsetY());
		final int endX = this.getXInImage(this.getCenteringOffsetX() + this.frontBuffer.getWidth());
		final int endY = this.getYInImage(this.getCenteringOffsetY() + this.frontBuffer.getHeight());
		
		return new Rectangle(x, y, endX - x, endY - y);
	}
	
	public final int getXInImage(final int xInComponent) {
		return this.getXInScaledImage(xInComponent) / this.getZoom();
	}
	
	public final int getYInImage(final int yInComponent) {
		return this.getYInScaledImage(yInComponent) / this.getZoom();
	}
	
	public final int getXInScaledImage(final int xInComponent) {
		return xInComponent - this.getCenteringOffsetX() + this.scaledImageVisibleRectangle.x;
	}
	
	public final int getYInScaledImage(final int yInComponent) {
		return yInComponent - this.getCenteringOffsetY() + this.scaledImageVisibleRectangle.y;
	}
	
	public final int getCenteringOffsetX() {
		return max(0, (this.getUsableWidth() - this.frontBuffer.getWidth()) / 2);
	}
	
	public final int getCenteringOffsetY() {
		return max(0, (this.getUsableHeight() - this.frontBuffer.getHeight()) / 2);
	}
	
	public final int getUsableHeight() {
		return this.getHeight() - this.getHorizontalScrollBar().getHeight();
	}
	
	public final int getUsableWidth() {
		return this.getWidth() - this.getVerticalScrollBar().getWidth();
	}
	
	@Override
	protected final void paintComponent(final Graphics g) {
		this.setBuffer();
		
		g.drawImage(this.frontBuffer, this.getCenteringOffsetX(), this.getCenteringOffsetY(), null);
		
		for (final Painter<Image2DComponent> painter : this.getPainters()) {
			painter.paint((Graphics2D) g, this, this.getWidth(), this.getHeight());
		}
	}
	
	final JScrollBar getHorizontalScrollBar() {
		return this.horizontalScrollBar;
	}
	
	final JScrollBar getVerticalScrollBar() {
		return this.verticalScrollBar;
	}
	
	final synchronized void updateBufferAccordingToScrollBars(final boolean forceRepaint) {
		if (this.frontBuffer == null) {
			return;
		}
		
		final int width = min(this.getScaledImageWidth(), this.frontBuffer.getWidth());
		final int height = min(this.getScaledImageHeight(), this.frontBuffer.getHeight());
		final int left = width < this.getScaledImageWidth() ? min(this.getScaledImageWidth() - width, this.getHorizontalScrollBar().getValue()) : 0;
		final int top = height < this.getScaledImageHeight() ? min(this.getScaledImageHeight() - height, this.getVerticalScrollBar().getValue()) : 0;
		
		this.setScaledImageVisibleRectangle(new Rectangle(left, top, width, height), forceRepaint ? null : this.frontBuffer);
	}
	
	final void setScrollBarsVisibleAmounts() {
		this.getHorizontalScrollBar().setMaximum(this.getScaledImageWidth());
		this.getVerticalScrollBar().setMaximum(this.getScaledImageHeight());
		
		final int usableWidth = max(0, this.getUsableWidth());
		final int usableHeight = max(0, this.getUsableHeight());
		
		if (this.getHorizontalScrollBar().getMaximum() <= this.getHorizontalScrollBar().getValue() + usableWidth) {
			this.getHorizontalScrollBar().setValue(max(0, this.getHorizontalScrollBar().getMaximum() - usableWidth));
		}
		
		this.getHorizontalScrollBar().setVisibleAmount(usableWidth);
		
		if (this.getVerticalScrollBar().getMaximum() <= this.getVerticalScrollBar().getValue() + usableHeight) {
			this.getVerticalScrollBar().setValue(max(0, this.getVerticalScrollBar().getMaximum() - usableHeight));
		}
		
		this.getVerticalScrollBar().setVisibleAmount(usableHeight);
	}
	
	final void copyImagePixelsToBuffer(final int left, final int top, final int width, final int height) {
		this.getScaledImage().forEachPixelInBox(left, top, width, height, new MonopatchProcess() {
			
			@Override
			public final void pixel(final int x, final int y) {
				Image2DComponent.this.copyImagePixelToBuffer(x, y);
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 1810623847473680066L;
			
		});
	}
	
	final void clearBuffer(final int left, final int top, final int width, final int height) {
		this.frontBufferGraphics.clearRect(left - this.scaledImageVisibleRectangle.x,
				top - this.scaledImageVisibleRectangle.y, width, height);
	}
	
	final boolean copyImagePixelToBuffer(final int xInScaledImage, final int yInScaledImage) {
		try {
			this.frontBuffer.setRGB(xInScaledImage - this.scaledImageVisibleRectangle.x, yInScaledImage - this.scaledImageVisibleRectangle.y,
					this.getScaledImage().getPixelValue(xInScaledImage, yInScaledImage));
			
			return true;
		} catch (final Exception exception) {
			System.err.println(Tools.debug(Tools.DEBUG_STACK_OFFSET, exception));
//			exception.printStackTrace();
//			debugPrint(this.frontBuffer.getWidth(), this.frontBuffer.getHeight());
//			debugPrint(xInScaledImage, yInScaledImage, xInScaledImage - this.scaledImageVisibleRectangle.x,
//					yInScaledImage - this.scaledImageVisibleRectangle.y);
			return false;
		}
	}
	
	final ScaledImage2D getScaledImage() {
		return this.scaledImage;
	}
	
	final void setScaledImage(final ScaledImage2D scaledImage) {
		this.scaledImage = scaledImage;
	}
	
	private final void updateBuffer(final int left, final int top, final int width, final int height) {
		if (this.getScaledImage() != null && 0 < width && 0 < height) {
			this.copyImagePixelsToBuffer(left, top, width, height);
		}
	}
	
	private final void setScaledImageVisibleRectangle(final Rectangle rectangle, final BufferedImage oldBuffer) {
		if (this.getScaledImageWidth() < rectangle.x + rectangle.width || this.getScaledImageHeight() < rectangle.y + rectangle.height ||
				this.frontBuffer.getWidth() < rectangle.width || this.frontBuffer.getHeight() < rectangle.height) {
			throw new IllegalArgumentException(rectangle + " " + new Rectangle(this.getScaledImageWidth(), this.getScaledImageHeight()) +
					" " + new Rectangle(this.frontBuffer.getWidth(),  this.frontBuffer.getHeight()));
		}
		
		if (oldBuffer == null) {
			this.scaledImageVisibleRectangle.setBounds(rectangle);
			this.updateBuffer(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
		} else {
			final Rectangle intersection = this.scaledImageVisibleRectangle.intersection(rectangle);
			
			if (intersection.isEmpty()) {
				this.scaledImageVisibleRectangle.setBounds(rectangle);
				this.updateBuffer(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
			} else {
				final int intersectionRight = intersection.x + intersection.width;
				final int intersectionBottom = intersection.y + intersection.height;
				
				this.backBufferGraphics.drawImage(oldBuffer,
						intersection.x - rectangle.x, intersection.y - rectangle.y,
						intersectionRight - rectangle.x, intersectionBottom - rectangle.y,
						intersection.x - this.scaledImageVisibleRectangle.x, intersection.y - this.scaledImageVisibleRectangle.y,
						intersectionRight - this.scaledImageVisibleRectangle.x, intersectionBottom - this.scaledImageVisibleRectangle.y
						, null);
				this.swapBuffers();
				
				this.scaledImageVisibleRectangle.setBounds(rectangle);
				
				// Update top
				this.updateBuffer(rectangle.x, rectangle.y, rectangle.width, intersection.y - rectangle.y);
				// Update left
				this.updateBuffer(rectangle.x, intersection.y, intersection.x - rectangle.x, intersection.height);
				// Update right
				this.updateBuffer(intersectionRight, intersection.y, rectangle.x + rectangle.width - intersectionRight, intersection.height);
				// Update bottom
				this.updateBuffer(rectangle.x, intersectionBottom, rectangle.width, rectangle.y + rectangle.height - intersectionBottom);
			}
		}
		
		if (this.frontBuffer.getWidth() < this.scaledImageVisibleRectangle.width || this.frontBuffer.getHeight() < this.scaledImageVisibleRectangle.getHeight()) {
			throw new IllegalStateException();
		}
	}
	
	private final void swapBuffers() {
		final BufferedImage tmpBuffer = this.frontBuffer;
		final Graphics2D tmpGraphics = this.frontBufferGraphics;
		this.frontBuffer = this.backBuffer;
		this.frontBufferGraphics = this.backBufferGraphics;
		this.backBuffer = tmpBuffer;
		this.backBufferGraphics = tmpGraphics;
	}
	
	private final int getScaledImageWidth() {
		return this.getScaledImage().getWidth();
	}
	
	private final int getScaledImageHeight() {
		return this.getScaledImage().getHeight();
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 4189273248039238064L;
	
	public static final Window showDefaultImage() {
		return show(newC4U8ConcreteImage2D("(1x1)", 1, 1));
	}
	
	public static final Window show(final Image2D image) {
		final Image2DComponent[] component = { null };
		
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				
				@Override
				public final void run() {
					component[0] = new Image2DComponent(image);
					component[0].setDropTarget(new DropTarget() {
						
						@Override
						public final synchronized void drop(final DropTargetDropEvent event) {
							component[0].setImage(new LociBackedImage(SwingTools.getFiles(event).get(0).toString()));
							component[0].updateView();
						}
						
						/**
						 * {@value}.
						 */
						private static final long serialVersionUID = 1282630245529197515L;
						
					});
				}
				
			});
		} catch (final Exception exception) {
			throw unchecked(exception);
		}
		
		return SwingTools.show(component[0], image.getId(), false);
	}
	
	/**
	 * @author codistmonk (creation 2013-11-10)
	 *
	 * @param <T>
	 */
	public static abstract interface Painter<T> extends Serializable {

		public abstract void paint(Graphics2D g, T component, int width, int height);
		
	}
	
}
