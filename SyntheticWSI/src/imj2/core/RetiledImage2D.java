package imj2.core;

import imj2.core.FilteredTiledImage2D;
import imj2.core.IMJCoreTools;
import imj2.core.Image2D;

/**
 * @author codistmonk (creation 2013-11-16)
 */
public class RetiledImage2D extends FilteredTiledImage2D {
	
	public RetiledImage2D(final Image2D source, final int optimalTileSize) {
		super(source.getId() + "_retiled" + optimalTileSize, source);
		this.setOptimalTileDimensions(optimalTileSize, optimalTileSize);
	}
	
	@Override
	public final int getWidth() {
		return this.getSource().getWidth();
	}

	@Override
	public final int getHeight() {
		return this.getSource().getHeight();
	}
	
	@Override
	public final int getLOD() {
		return this.getSource().getLOD();
	}
	
	@Override
	public final RetiledImage2D getLODImage(final int lod) {
		final Image2D newSource = this.getSource().getLODImage(lod);
		
		return newSource == this.getSource() ? this : new RetiledImage2D(newSource, this.getOptimalTileWidth());
	}
	
	@Override
	public final Image2D[] newParallelViews(final int n) {
		return IMJCoreTools.newParallelViews(this, n);
	}
	
	@Override
	public final Channels getChannels() {
		return this.getSource().getChannels();
	}
	
	@Override
	protected final ConcreteImage2D<LinearIntImage> updateTile(final int tileX, final int tileY, final ConcreteImage2D<LinearIntImage> tile) {
		tile.forEachPixelInBox(0, 0, tile.getWidth(), tile.getHeight(), new MonopatchProcess() {
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = -3578482683595517563L;
			
			@Override
			public final void pixel(final int x, final int y) {
				tile.setPixelValue(x, y, RetiledImage2D.this.getSource().getPixelValue(tileX + x, tileY + y));
			}
			
		});
		
		return tile;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -8320894712878155634L;
	
}
