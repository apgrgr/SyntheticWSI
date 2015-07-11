package fr.unistra.wsi.synthetic;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.util.Collections.synchronizedMap;
import static java.util.stream.Collectors.toList;
import static net.sourceforge.aprog.tools.MathTools.square;
import static fr.unistra.wsi.synthetic.GenerateWSI.CONSTRAINT_SOLVER_MAXIMUM_MILLISECONDS;
import static fr.unistra.wsi.synthetic.GenerateWSI.RANDOM;

import fr.unistra.wsi.synthetic.Region.ClosePath;
import fr.unistra.wsi.synthetic.Region.PathElement;

import imj2.core.Image2D;
import imj2.core.Image2D.MonopatchProcess;
import imj2.tools.Canvas;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.sourceforge.aprog.tools.ConsoleMonitor;
import net.sourceforge.aprog.tools.TicToc;
import net.sourceforge.aprog.tools.Tools;

/**
 * @author ga (creation 2014-09-12)
 */
public final class AdjacentObjectsRenderer implements RegionRenderer {
	
	private final double collisionableRadius;
	
	private final Model[] textures;
	
	private transient List<TextureUnit> textureUnits;
	
	private final double[] unitAmounts;
	
	private final Map<Region, SphereSystem> systems;
	
	private final Map<Region, List<TextureObject>> objects;
	
	public AdjacentObjectsRenderer(final File... textureModelFiles) {
		this(1.0, textureModelFiles);
	}
	
	public AdjacentObjectsRenderer(final double collisionableRadius, final File... textureModelFiles) {
		this.collisionableRadius = collisionableRadius;
		this.textures = Arrays.stream(textureModelFiles).map(f -> new Model().open(f)).toArray(Model[]::new);
		this.systems = synchronizedMap(new HashMap<>());
		this.objects = synchronizedMap(new HashMap<>());
		this.unitAmounts = newProportions(this.getTextureUnits());
	}
	
	public final synchronized List<TextureUnit> getTextureUnits() {
		if (this.textureUnits == null) {
			Tools.debugPrint(Thread.currentThread());
			this.textureUnits = new ArrayList<>();
			final float collisionableRadius = (float) this.collisionableRadius;
			
			for (final Model texture : this.textures) {
				final Image2D image = texture.getImage(1.0);
				
				texture.getRegions().forEach(region -> {
					final Rectangle bounds = region.getGeometry().getBounds();
					final BufferedImage extracted = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
					final BufferedImage mask = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_BINARY);
					final float centerX = (float) bounds.getCenterX();
					final float centerY = (float) bounds.getCenterY();
					final float limit = max(bounds.width, bounds.height) / 2F;
					final float opacityLimit = collisionableRadius * limit;
					
					image.forEachPixelInBox(bounds.x, bounds.y, bounds.width, bounds.height, new MonopatchProcess() {
						
						@Override
						public final void pixel(final int x, final int y) {
							final float distanceFromCenter = Region.length(centerX, centerY, x, y);
							final int alpha = 0xFF & (int) ((1F - ratio(opacityLimit, limit, distanceFromCenter)) * 255F);
							
							extracted.setRGB(x - bounds.x, y - bounds.y, (alpha << 24) | image.getPixelValue(x, y));
						}
						
						/**
						 * {@value}.
						 */
						private static final long serialVersionUID = -8888976641104127138L;
						
					});
					
					{
						final Graphics2D g = mask.createGraphics();
						
						g.translate(-bounds.x, -bounds.y);
						g.fill(region.getGeometry());
						
						g.dispose();
					}
					
					for (int y = 0; y < extracted.getHeight(); ++y) {
						for (int x = 0; x < extracted.getWidth(); ++x) {
							if (0 == (0x00FFFFFF & mask.getRGB(x, y))) {
								extracted.setRGB(x, y, 0);
							}
						}
					}
					
					this.textureUnits.add(new TextureUnit(extracted, region));
				});
			}
			Tools.debugPrint(Thread.currentThread());
		}
		
		return this.textureUnits;
	}
	
	public static final float ratio(final float minimum, final float maximum, final float value) {
		return value < minimum ? 0F : maximum < value ? 1F : (value - minimum) / (maximum - minimum);
	}
	
	@Override
	public final boolean beforeRender(final Region region) {
		boolean result = false;
		SphereSystem system = this.systems.get(region);
		List<AdjacentObjectsRenderer.TextureObject> objects = this.objects.get(region);
		
		if (system == null) {
			Tools.debugPrint(Thread.currentThread());
			result = true;
			system = new SphereSystem();
			objects = new ArrayList<>();
			
			Tools.debugPrint(region.getGeometry().getBounds(), this.systems.size());
			
			final List<Point2D> regionVertices = new ArrayList<>();
			final Rectangle bounds = region.getGeometry().getBounds();
			final double area = region.computeArea();
			final int n = this.unitAmounts.length;
			final int[] unitCounts = new int[n];
			
			for (int i = 0; i < n; ++i) {
				unitCounts[i] = (int) (this.unitAmounts[i] * area /
						(square(this.collisionableRadius) * this.getTextureUnits().get(i).getRegion().computeArea()));
			}
			
			Tools.debugPrint("regionLabel:", region.getLabel(), "regionArea:", area);
			Tools.debugPrint("unitAmounts:", Arrays.toString(this.unitAmounts));
			Tools.debugPrint("unitCounts:", Arrays.toString(unitCounts));
			
			for (final PathElement pathElement : Region.iterable(region.getGeometry().getPathIterator(null, 2.0))) {
				if (!(pathElement instanceof ClosePath)) {
					regionVertices.add(new Point2D.Double(pathElement.getEndX(), pathElement.getEndY()));
				}
			}
			
			{
				final double[] collisionRadii = this.getTextureUnits().stream().mapToDouble(
						p -> this.collisionableRadius * sqrt(p.getRegion().computeArea() / PI)).toArray();
				final double maxR = sqrt(square(bounds.width) + square(bounds.height)) / 2.0;
				
				Tools.debugPrint("unitRadii:", Arrays.toString(collisionRadii));
				
				for (int i = 0; i < n; ++i) {
					final double radius = collisionRadii[i];
					
					for (int j = 0; j < unitCounts[i]; ++j) {
						final double centerR = maxR * sqrt(RANDOM.nextDouble());
						final double centerA = RANDOM.nextDouble() * 2.0 * PI;
						final int sphereId = system.newSphere(bounds.getCenterX() + centerR * cos(centerA),
								bounds.getCenterY() + centerR * sin(centerA), 0.0, radius, 0);
						objects.add(new TextureObject(i, sphereId, 2.0 * PI * RANDOM.nextDouble()));
					}
				}
			}
			
			Tools.debugPrint(Thread.currentThread(), "systemSize:", system.getSphereCount());
			
			final TicToc timer = new TicToc();
			final ConsoleMonitor monitor = new ConsoleMonitor(GenerateWSI.MONITOR_PERIOD_MILLISECONDS);
			final double goodEnough = 2.0;
			double d = 0.0;
			
			Tools.debugPrint(Thread.currentThread(), new Date(timer.tic()));
			
			wrangleSpheres(system, region, regionVertices);
			d = system.update2();
			
			Tools.debugPrint(Thread.currentThread(), d, timer.toc(), goodEnough);
			
			while (goodEnough < d && timer.toc() < CONSTRAINT_SOLVER_MAXIMUM_MILLISECONDS) {
				monitor.ping(d + "\r");
				wrangleSpheres(system, region, regionVertices);
				d = system.update2();
			}
			
			if (goodEnough < d) {
				Tools.debugPrint(Thread.currentThread(), "Constraint solver timed out");
			}
		}
		
		this.systems.put(region, system);
		final SphereSystem s = system;
		this.objects.put(region, objects.stream().filter(o -> {
			final double x = s.getSphereX(o.getSphereId());
			final double y = s.getSphereY(o.getSphereId());
			
			return region.getGeometry().contains(x, y);
		}).collect(toList()));
		
		return result;
	}
	
	@Override
	public final void render(final Region region, final Canvas buffer,
			final int tileX, final int tileY, final int optimalTileWidth, final int optimalTileHeight) {
		final SphereSystem system = this.systems.get(region);
		final List<TextureObject> objects = this.objects.get(region);
		
		if (this.textures.length == 0) {
			RegionRenderer.DEFAULT.render(region, buffer, tileX, tileY, optimalTileWidth, optimalTileHeight);
		} else {
			final List<TextureUnit> textureUnits = this.getTextureUnits();
			final AffineTransform transform = new AffineTransform();
			final Consumer<? super TextureObject> action = o -> {
				final double x = system.getSphereX(o.getSphereId());
				final double y = system.getSphereY(o.getSphereId());
				final TextureUnit unit = textureUnits.get(o.getUnitId());
				final BufferedImage image = unit.getImage();
				final double w = image.getWidth() / 2.0;
				final double h = image.getHeight() / 2.0;
				
				transform.setToTranslation(x - w, y - h);
				transform.rotate(o.getOrientation(), w, h);
				
				buffer.getGraphics().drawImage(image, transform, null);
			};
			
			objects.forEach(action);
		}
	}
	
	public final double wrangleSpheres(SphereSystem system, final Region region,
			final List<Point2D> regionVertices) {
		final int n = system.getSphereCount();
		double result = 0.0;
		final Point2D center = region.computeCenter();
		final Rectangle2D bounds = region.getGeometry().getBounds2D();
		final double maxR = sqrt(square(bounds.getWidth()) + square(bounds.getHeight())) / 2.0;
		
		for (int i = 0; i < n; ++i) {
			final double x = system.getSphereX(i);
			final double y = system.getSphereY(i);
			
			if (!region.getGeometry().contains(x, y)) {
				final Point2D nearestRegionVertex = regionVertices.stream().reduce((p1, p2) -> p1.distance(x, y) <= p2.distance(x, y) ? p1 : p2).get();
				result = max(result, nearestRegionVertex.distance(x, y));
				
				final double r = maxR * sqrt(RANDOM.nextDouble());
				final double a = RANDOM.nextDouble() * 2.0 * PI;
				
				system.setSphereX(i, center.getX() + r * cos(a));
				system.setSphereY(i, center.getY() + r * sin(a));
			}
		}
		
		return result;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 3370782935873969191L;
	
	public static final double[] newProportions(final List<TextureUnit> textureUnits) {
		final int n = textureUnits.size();
		final double[] result = new double[n];
		double sum = 0.0;
		
		for (int i = 0; i < n; ++i) {
			sum += result[i] = textureUnits.get(i).getRegion().getOccurences();
		}
		
		if (sum != 0.0) {
			for (int i = 0; i < n; ++i) {
				result[i] /= sum;
			}
		}
		
		return result;
	}
	
	public static final double[] newProportions(final int n) {
		final double[] result = new double[n];
		double sum = 0.0;
		
		for (int i = 0; i < n; ++i) {
			sum += result[i] = RANDOM.nextDouble();
		}
		
		if (sum != 0.0) {
			for (int i = 0; i < n; ++i) {
				result[i] /= sum;
			}
		}
		
		return result;
	}
	
	/**
	 * @author ga (creation 2014-09-12)
	 */
	public static final class TextureObject implements Serializable {
		
		private final int unitId;
		
		private final int sphereId;
		
		private final double orientation;
		
		public TextureObject(final int unitId, final int sphereId, final double orientation) {
			this.unitId = unitId;
			this.sphereId = sphereId;
			this.orientation = orientation;
		}
		
		public final int getUnitId() {
			return this.unitId;
		}
		
		public final int getSphereId() {
			return this.sphereId;
		}
		
		public final double getOrientation() {
			return this.orientation;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 1484826807507813768L;
		
	}
	
	/**
	 * @author ga (creation 2014-09-16)
	 */
	public static final class TextureUnit implements Serializable {
		
		private final BufferedImage image;
		
		private final Region region;
		
		public TextureUnit(final BufferedImage image, final Region region) {
			this.image = image;
			this.region = region;
		}
		
		public final BufferedImage getImage() {
			return this.image;
		}
		
		public final Region getRegion() {
			return this.region;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 3166790379474940192L;
		
	}
	
}