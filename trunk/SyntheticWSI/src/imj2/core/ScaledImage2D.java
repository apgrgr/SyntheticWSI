package imj2.core;

import static java.lang.Math.min;
import static java.lang.Math.pow;

/**
 * @author codistmonk (creation 2013-08-12)
 */
public final class ScaledImage2D extends TiledImage2D {
	
	private final Image2D source;
	
	private int zoom;
	
	public ScaledImage2D(final Image2D source) {
		super(source.getId() + "_scaled");
		this.source = source;
		this.setZoom(1);
	}
	
	public final int getZoom() {
		return this.zoom;
	}
	
	public final void setZoom(final int zoom) {
		if (0 < zoom) {
			this.zoom = zoom;
			
			if (this.getSource() instanceof TiledImage2D) {
				this.setOptimalTileWidth(((TiledImage2D) this.getSource()).getOptimalTileWidth() * zoom);
				this.setOptimalTileHeight(((TiledImage2D) this.getSource()).getOptimalTileHeight() * zoom);
			} else {
				this.setOptimalTileWidth(this.getSource().getWidth() * zoom);
				this.setOptimalTileHeight(this.getSource().getHeight() * zoom);
			}
		}
	}
	
	@Override
	public final Image2D getSource() {
		return this.source;
	}
	
	@Override
	public final int getLOD() {
		return this.getSource().getLOD();
	}
	
	@Override
	public final ScaledImage2D getLODImage(final int lod) {
		final int thisLOD = this.getLOD();
		
		if (lod == thisLOD) {
			return this;
		}
		
		final ScaledImage2D result = new ScaledImage2D(this.getSource().getLODImage(lod));
		final int deltaLOD = lod - thisLOD;
		
		result.setZoom((int) (this.getZoom() * pow(2.0, min(0, deltaLOD))));
		
		return result;
	}
	
	@Override
	public final int getWidth() {
		return this.getSource().getWidth() * this.getZoom();
	}
	
	@Override
	public final int getHeight() {
		return this.getSource().getHeight() * this.getZoom();
	}
	
	@Override
	public final Channels getChannels() {
		return this.getSource().getChannels();
	}
	
	@Override
	public final ScaledImage2D[] newParallelViews(final int n) {
		final ScaledImage2D[] result = new ScaledImage2D[n];
		
		result[0] = this;
		
		if (1 < n) {
			final Image2D[] sources = this.getSource().newParallelViews(n);
			
			for (int i = 1; i < n; ++i) {
				result[i] = new ScaledImage2D(sources[i]);
				result[i].setZoom(this.getZoom());
			}
		}
		
		return result;
	}
	
	@Override
	public final Object updateTile() {
		return null;
	}
	
	@Override
	protected final int getPixelValueFromTile(final int x, final int y, final int xInTile, final int yInTile) {
		return this.getSource().getPixelValue(x / this.getZoom(), y / this.getZoom());
	}
	
	@Override
	protected final void setTilePixelValue(final int x, final int y, final int xInTile, final int yInTile,
			final int value) {
		this.getSource().setPixelValue(x / this.getZoom(), y / this.getZoom(), value);
	}
	
	@Override
	protected final boolean makeNewTile() {
		return false;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -7082323074031564968L;
	
}
