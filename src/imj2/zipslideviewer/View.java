package imj2.zipslideviewer;

import static java.lang.Math.atan2;
import static java.lang.Math.ceil;
import static java.lang.Math.cos;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static net.sourceforge.aprog.tools.MathTools.square;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import imj2.core.TiledImage2D;
import imj2.pixel3d.MouseHandler;
import imj2.tools.IMJTools;
import imj2.tools.MultiThreadTools;
import imj2.tools.Image2DComponent.Painter;

/**
 * @author codistmonk (creation 2014-11-23)
 */
public final class View extends JComponent {
	
	private final List<Painter<View>> painters;
	
	private final TiledImage2D image;
	
	private final TiledImage2D initialLODImage;
	
	private final Map<Integer, TiledImage2D> lodImages;
	
	private final Map<Integer, Map<Point, BufferedImage>> cache;
	
	private final AtomicBoolean updateNeeded;
	
	private final Point2D.Double center;
	
	private double scale;
	
	private double angle;
	
	public View(final TiledImage2D image) {
		this.painters = new ArrayList<>();
		this.image = image;
		this.lodImages = Collections.synchronizedMap(new HashMap<>());
		this.cache = Collections.synchronizedMap(new HashMap<>());
		this.updateNeeded = new AtomicBoolean(true);
		this.center = new Point2D.Double(image.getWidth() / 2.0, image.getHeight() / 2.0);
		this.scale = min((double) image.getOptimalTileWidth() / image.getWidth(),
				(double) image.getOptimalTileHeight() / image.getHeight());
		this.initialLODImage = this.getLODImage();
		
		this.setFocusable(true);
		
		new MouseHandler(null) {
			
			private final Point mouse = new Point();
			
			@Override
			public final void mousePressed(final MouseEvent event) {
				this.mouse.setLocation(event.getX(), event.getY());
			}
			
			@Override
			public final void mouseDragged(final MouseEvent event) {
				
				if (event.isAltDown()) {
					final double x0 = getWidth() / 2.0;
					final double y0 = getHeight() / 2.0;
					
					updateAngle(atan2(event.getY() - y0, event.getX() - x0) - atan2(this.mouse.y - y0, this.mouse.x - x0));
				} else {
					final double dx = (event.getX() - this.mouse.x) / getScale();
					final double dy = (event.getY() - this.mouse.y) / getScale();
					
					updateCenter(-dx, -dy);
				}
				
				this.mouse.setLocation(event.getX(), event.getY());
			}
			
			@Override
			public final void mouseWheelMoved(final MouseWheelEvent event) {
				if (event.getWheelRotation() < 0) {
					updateScale(5.0 / 4.0);
				} else {
					updateScale(4.0 / 5.0);
				}
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = -6649302685423143601L;
			
		}.addTo(this);
	}
	
	public final List<Painter<View>> getPainters() {
		return this.painters;
	}
	
	public final TiledImage2D getImage() {
		return this.image;
	}
	
	public final Point2D.Double getCenter() {
		return this.center;
	}
	
	public final double getScale() {
		return this.scale;
	}
	
	public final double getAngle() {
		return this.angle;
	}
	
	@Override
	protected final void paintComponent(final Graphics g) {
		if (this.updateNeeded.getAndSet(false)) {
			MultiThreadTools.getExecutor().submit(this::updateCache);
		}
		
		this.drawTiles((Graphics2D) g, this.initialLODImage);
		
		TiledImage2D lodImage = this.getLODImageOrNull(this.computeLOD() + 2);
		
		if (lodImage != null) {
			this.drawTiles((Graphics2D) g, lodImage);
		}
		
		lodImage = this.getLODImageOrNull();
		
		if (lodImage != null) {
			this.drawTiles((Graphics2D) g, lodImage);
		}
		
		for (final Painter<View> painter : this.getPainters()) {
			painter.paint((Graphics2D) g, this, this.getWidth(), this.getHeight());
		}
	}
	
	public final void updateCache() {
		synchronized (this.image) {
			final double x0 = this.getWidth() / 2.0;
			final double y0 = this.getHeight() / 2.0;
			final double r = sqrt(square(x0) + square(y0));
			
			final TiledImage2D lodImage = this.getLODImage();
			final int lod = lodImage.getLOD();
			final Map<Point, BufferedImage> sharedCache = this.getCache(lod);
			final Map<Point, BufferedImage> cache = new HashMap<>();
			
			synchronized (sharedCache) {
				cache.putAll(sharedCache);
			}
			
			final int lodImageWidth = lodImage.getWidth();
			final int lodImageHeight = lodImage.getHeight();
			final double lodScale = this.getScale() / pow(2.0, -lod);
			final double lodCenterX = this.getCenter().x * pow(2.0, -lod);
			final double lodCenterY = this.getCenter().y * pow(2.0, -lod);
			final double visibleLeft = max(0.0, lodCenterX - r / lodScale);
			final double visibleTop = max(0.0, lodCenterY - r / lodScale);
			final double visibleWidth = min(lodImageWidth, lodCenterX + r / lodScale) - visibleLeft;
			final double visibleHeight = min(lodImageHeight, lodCenterY + r / lodScale) - visibleTop;
			final int optimalTileWidth = lodImage.getOptimalTileWidth();
			final int optimalTileHeight = lodImage.getOptimalTileHeight();
			final Map<Point, BufferedImage> tmp = new HashMap<>();
			
			for (double y = quantize(visibleTop, optimalTileHeight); y < visibleTop + visibleHeight; y += optimalTileHeight) {
				for (double x = quantize(visibleLeft, optimalTileWidth); x < visibleLeft + visibleWidth; x += optimalTileWidth) {
					if (this.updateNeeded.get()) {
						return;
					}
					
					final Point tileXY = new Point((int) x, (int) y);
					final int actualTileWidth = min(lodImageWidth - tileXY.x, optimalTileWidth);
					final int actualTileHeight = min(lodImageHeight - tileXY.y, optimalTileHeight);
					BufferedImage tile = cache.get(tileXY);
					
					if (tile != null) {
						tmp.put(tileXY, tile);
					} else {
						tmp.put(tileXY, tile = IMJTools.awtImage(lodImage, tileXY.x, tileXY.y,
								actualTileWidth, actualTileHeight));
					}
				}
			}
			
			synchronized (sharedCache) {
				sharedCache.clear();
				sharedCache.putAll(tmp);
			}
			
			this.repaint();
		}
	}
	
	final TiledImage2D getLODImageOrNull() {
		return this.getLODImageOrNull(this.computeLOD());
	}
	
	final int computeLOD() {
		return max(0, (int) (-log(this.getScale()) / log(2.0)));
	}
	
	final TiledImage2D getLODImageOrNull(final int lod) {
		if (this.lodImages.containsKey(lod)) {
			return this.lodImages.get(lod);
		}
		
		new Thread(this::getLODImage).start();
		
		return null;
	}
	
	final TiledImage2D getLODImage() {
		return this.getLODImage(this.computeLOD());
	}
	
	final TiledImage2D getLODImage(final int lod) {
		return this.lodImages.computeIfAbsent(lod, l -> ZipSlideViewer.tiled(this.image.getLODImage(lod)));
	}
	
	final void drawTiles(final Graphics2D g, final TiledImage2D lodImage) {
		final int lod = lodImage.getLOD();
		final Map<Point, BufferedImage> tiles = this.getCacheCopy(lod);
		final double x0 = this.getWidth() / 2.0;
		final double y0 = this.getHeight() / 2.0;
		final double r = sqrt(square(x0) + square(y0));
		final int lodImageWidth = lodImage.getWidth();
		final int lodImageHeight = lodImage.getHeight();
		final double lodScale = this.getScale() / pow(2.0, -lod);
		final double lodCenterX = this.getCenter().x * pow(2.0, -lod);
		final double lodCenterY = this.getCenter().y * pow(2.0, -lod);
		final double visibleLeft = max(0.0, lodCenterX - r / lodScale);
		final double visibleTop = max(0.0, lodCenterY - r / lodScale);
		final double visibleWidth = min(lodImageWidth, lodCenterX + r / lodScale) - visibleLeft;
		final double visibleHeight = min(lodImageHeight, lodCenterY + r / lodScale) - visibleTop;
		final int optimalTileWidth = lodImage.getOptimalTileWidth();
		final int optimalTileHeight = lodImage.getOptimalTileHeight();
		final AffineTransform savedTransform = g.getTransform();
		
		g.rotate(this.getAngle(), x0, y0);
		
		for (double y = quantize(visibleTop, optimalTileHeight); y < visibleTop + visibleHeight; y += optimalTileHeight) {
			for (double x = quantize(visibleLeft, optimalTileWidth); x < visibleLeft + visibleWidth; x += optimalTileWidth) {
				final Point tileXY = new Point((int) x, (int) y);
				final int actualTileWidth = min(lodImageWidth - tileXY.x, optimalTileWidth);
				final int actualTileHeight = min(lodImageHeight - tileXY.y, optimalTileHeight);
				final BufferedImage tile = tiles.get(tileXY);
				
				if (tile != null) {
					g.drawImage(tile,
							(int) (x0 - lodCenterX * lodScale + x * lodScale),
							(int) (y0 - lodCenterY * lodScale + y * lodScale),
							(int) ceil(actualTileWidth * lodScale),
							(int) ceil(actualTileHeight * lodScale),
							null);
				}
			}
		}
		
		g.setTransform(savedTransform);
	}
	
	final Map<Point, BufferedImage> getCache(final int lod) {
		return this.cache.computeIfAbsent(lod, l -> new HashMap<>());
	}
	
	final Map<Point, BufferedImage> getCacheCopy(final int lod) {
		final Map<Point, BufferedImage> cache = this.getCache(lod);
		
		synchronized (cache) {
			return new HashMap<>(cache);
		}
	}
	
	final void updateCenter(final double deltaX, final double deltaY) {
		/*
		 * M = [cos(a) -sin(a)]
		 *     [sin(a)  cos(a)]
		 * 
		 * M^-1 = [cos(a)  sin(a)]
		 *        [-sin(a) cos(a)]
		 * 
		 * M^-1 * [dx] = [dx * cos(a) + dy * sin(a)] 
		 *        [dy]   [-dx * sin(a) + dy * cos(a)]
		 */
		this.center.x += deltaX * cos(this.angle) + deltaY * sin(this.angle);
		this.center.y += -deltaX * sin(this.angle) + deltaY * cos(this.angle);
		this.updateNeeded.set(true);
		this.repaint();
	}
	
	final void setScale(final double scale) {
		this.scale = scale;
		this.updateNeeded.set(true);
	}
	
	final void updateScale(final double factor) {
		this.scale *= factor;
		this.updateNeeded.set(true);
		this.repaint();
	}
	
	final void setAngle(final double angle) {
		this.angle = angle;
		this.updateNeeded.set(true);
	}
	
	final void updateAngle(final double deltaRadians) {
		this.angle += deltaRadians;
		this.updateNeeded.set(true);
		this.repaint();
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -8101322100984141200L;
	
	public static final double quantize(final double value, final double q) {
		return q * (int) (value / q);
	}
	
}
