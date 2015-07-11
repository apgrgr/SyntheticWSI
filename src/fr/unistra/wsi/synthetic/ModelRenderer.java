package fr.unistra.wsi.synthetic;

import static net.sourceforge.aprog.tools.Tools.unchecked;
import static fr.unistra.wsi.synthetic.GenerateWSI.MAXIMUM_CPU_LOAD;
import static fr.unistra.wsi.synthetic.GenerateWSI.RENDERERS_FILE;

import imj2.tools.Canvas;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.io.Serializable;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.sourceforge.aprog.tools.TaskManager;
import net.sourceforge.aprog.tools.Tools;

/**
 * @author ga (creation 2014-09-16)
 */
public final class ModelRenderer implements Serializable {
	
	private final Model model;
	
	private final Map<String, RegionRenderer> regionRenderers;
	
	private final List<Region> subdividedRegions;
	
	public ModelRenderer(final Model model) {
		this.model = model;
		this.regionRenderers = new HashMap<>();
		this.subdividedRegions = new ArrayList<>();
	}
	
	public final Model getModel() {
		return this.model;
	}
	
	public final Map<String, RegionRenderer> getRegionRenderers() {
		return this.regionRenderers;
	}
	
	public final RegionRenderer getRegionRenderer(final String label) {
		return this.getRegionRenderers().getOrDefault(label, RegionRenderer.DEFAULT);
	}
	
	public final RegionRenderer setRegionRenderer(final String label, final RegionRenderer renderer) {
		return this.getRegionRenderers().put(label, renderer);
	}
	
	public final void beforeRender() {
		if (!this.subdividedRegions.isEmpty()) {
			return;
		}
		
		this.getModel().getRegions().forEach(c -> {
			subdivideRegion(c, SUBDIVISION_THRESHOLD, this.subdividedRegions);
		});
		
		{
			final TaskManager tasks = new TaskManager(MAXIMUM_CPU_LOAD);
			final int n = tasks.getWorkerCount();
			final int[] i = { 0 };
			final Semaphore semaphore = new Semaphore(n);
			final AtomicBoolean terminate = new AtomicBoolean();
			final AtomicInteger lastUpdate = new AtomicInteger();
			final Path renderersPath = FileSystems.getDefault().getPath(RENDERERS_FILE.getPath());
			final Path renderersBackupPath = FileSystems.getDefault().getPath(RENDERERS_FILE.getPath() + ".bak");
			final int subdividedRegionCount = this.subdividedRegions.size();
			
			this.subdividedRegions.forEach(c -> {
				final int taskId = ++i[0];
				
				tasks.submit(new Runnable() {
					
					@Override
					public final void run() {
						if (terminate.get()) {
							return;
						}
						
						Tools.debugPrint(taskId + " / " + subdividedRegionCount);
						
						try {
							semaphore.acquire();
							final boolean renderersUpdated = ModelRenderer.this.beforeRenderRegion(c);
							semaphore.release();
							
							if (renderersUpdated) {
								final int thisUpdate = lastUpdate.incrementAndGet();
								
								semaphore.acquire(n);
								if (thisUpdate == lastUpdate.get()) {
									Tools.debugPrint(Thread.currentThread(), thisUpdate);
									try {
										Tools.writeObject(ModelRenderer.this, RENDERERS_FILE.getPath() + ".bak");
										Files.move(renderersBackupPath, renderersPath, StandardCopyOption.REPLACE_EXISTING);
									} catch (final Exception exception) {
										exception.printStackTrace();
									}
								}
								semaphore.release(n);
							}
						} catch (final Exception exception) {
							exception.printStackTrace();
							terminate.set(true);
							semaphore.release(n);
							throw unchecked(exception);
						}
					}
					
				});
			});
			
			tasks.join();
		}
	}
	
	public final void renderTo(final Canvas buffer, final int tileX, final int tileY, final int optimalTileWidth, final int optimalTileHeight) {
		this.subdividedRegions.forEach(c -> {
			this.renderRegion(c, buffer, tileX, tileY, optimalTileWidth, optimalTileHeight);
		});
	}
	
	final boolean beforeRenderRegion(final Region region) {
		return this.getRegionRenderer(region.getLabel()).beforeRender(region);
	}
	
	final void renderRegion(final Region region, final Canvas buffer, final int tileX, final int tileY, final int optimalTileWidth, final int optimalTileHeight) {
		this.getRegionRenderer(region.getLabel()).render(region, buffer, tileX, tileY, optimalTileWidth, optimalTileHeight);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 1819426407264246241L;
	
	public static final int SUBDIVISION_THRESHOLD = 64000;
	
	public static final Collection<Region> subdivideRegion(final Region region, final int maximumDimension, final Collection<Region> result) {
		final Rectangle bounds = region.getGeometry().getBounds();
		
		if (maximumDimension <= bounds.height) {
			final int w = bounds.width;
			final int h = bounds.height / 2;
			final Area top = new Area(new Rectangle(bounds.x, bounds.y, w, h));
			final Area bottom = new Area(new Rectangle(bounds.x, bounds.y + h, w, h + (bounds.height & 1)));
			
			top.intersect(region.getGeometry());
			bottom.intersect(region.getGeometry());
			
			subdivideRegion(new Region(top, region.getLabel(), region.getOccurences()), maximumDimension, result);
			subdivideRegion(new Region(bottom, region.getLabel(), region.getOccurences()), maximumDimension, result);
		} else if (maximumDimension <= bounds.width) {
			final int w = bounds.width / 2;
			final int h = bounds.height;
			final Area left = new Area(new Rectangle(bounds.x, bounds.y, w, h));
			final Area right = new Area(new Rectangle(bounds.x + w, bounds.y, w + (bounds.width & 1), h));
			
			left.intersect(region.getGeometry());
			right.intersect(region.getGeometry());
			
			subdivideRegion(new Region(left, region.getLabel(), region.getOccurences()), maximumDimension, result);
			subdivideRegion(new Region(right, region.getLabel(), region.getOccurences()), maximumDimension, result);
		} else {
			result.add(region);
		}
		
		return result;
	}
	
}