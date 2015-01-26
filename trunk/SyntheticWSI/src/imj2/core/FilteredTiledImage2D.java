package imj2.core;

import static imj2.core.IMJCoreTools.cache;
import static java.util.Arrays.asList;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * @author codistmonk (creation 2013-08-26)
 */
public abstract class FilteredTiledImage2D extends TiledImage2D {
	
	private final Image2D source;
	
	private ConcreteImage2D<LinearIntImage> tile;
	
	protected FilteredTiledImage2D(final String id, final Image2D source) {
		super(id);
		this.source = source;
		
		this.getFromCache(true);
	}
	
	final FilteredTiledImage2D getFromCache(final boolean refresh) {
		return cache(this.getId(), new Callable<FilteredTiledImage2D>() {
			
			@Override
			public final FilteredTiledImage2D call() throws Exception {
				return FilteredTiledImage2D.this;
			}
			
		}, refresh);
	}
	
	@Override
	public final Image2D getSource() {
		return this.source;
	}
	
	public final Object getTileKey(final int tileX, final int tileY) {
		return asList(this.getId(), tileX, tileY);
	}
	
	@Override
	public final ConcreteImage2D<LinearIntImage> updateTile() {
		final int tileX = this.getTileX();
		final int tileY = this.getTileY();
		final int tileWidth = this.getTileWidth();
		final int tileHeight = this.getTileHeight();
		final Object key = this.getTileKey(tileX, tileY);
		final Callable<TimestampedValue<ConcreteImage2D<LinearIntImage>>> valueFactory = new Callable<TimestampedValue<ConcreteImage2D<LinearIntImage>>>() {
			
			@Override
			public final TimestampedValue<ConcreteImage2D<LinearIntImage>> call() throws Exception {
				// FIXME Potential issue: each factory retains its own enclosing instance;
				//       as a result, tiles of the same imageId may behave inconsistently;
				//       possible fix: refactor as static class to update image reference
				final FilteredTiledImage2D image = FilteredTiledImage2D.this.getFromCache(false);
				
				return new TimestampedValue<ConcreteImage2D<LinearIntImage>>(image.getTimestamp().get(),
						image.updateTile(tileX, tileY, image.newTile(tileWidth, tileHeight)));
			}
			
		};
		
		// XXX Use while loop ?
		this.tile = cache(key, valueFactory,
				cache(key, valueFactory).getTimestamp() != this.getTimestamp().get()).getValue();
		this.setTileTimestamp(this.getTimestamp().get());
		
		return this.tile;
	}
	
	public final int[] getTileData(final int x, final int y) {
		this.ensureTileContains(x, y);
		
		return this.updateTile().getSource().getData();
	}
	
	@Override
	protected final int getPixelValueFromTile(final int x, final int y, final int xInTile, final int yInTile) {
		return this.tile.getPixelValue(xInTile, yInTile);
	}
	
	@Override
	protected void setTilePixelValue(final int x, final int y, final int xInTile, final int yInTile,
			final int value) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected final boolean makeNewTile() {
		return this.tile == null || this.getTimestamp().get() != this.getTileTimestamp();
	}
	
	protected abstract ConcreteImage2D<LinearIntImage> updateTile(int tileX, int tileY, ConcreteImage2D<LinearIntImage> tile);
	
	final ConcreteImage2D<LinearIntImage> newTile(final int tileWidth, final int tileHeight) {
		return new ConcreteImage2D<LinearIntImage>(
				new LinearIntImage("", (long) tileWidth * tileHeight, this.getChannels()), tileWidth, tileHeight);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -8955541468755019991L;
	
	/**
	 * @author codistmonk (creation 2013-11-10)
	 */
	public static final class TimestampedValue<V> implements Serializable {
		
		private final long timestamp;
		
		private final V value;
		
		public TimestampedValue(final long timestamp, final V value) {
			this.timestamp = timestamp;
			this.value = value;
		}
		
		public final long getTimestamp() {
			return this.timestamp;
		}
		
		public final V getValue() {
			return this.value;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 7822086437658548820L;
		
	}
	
}
