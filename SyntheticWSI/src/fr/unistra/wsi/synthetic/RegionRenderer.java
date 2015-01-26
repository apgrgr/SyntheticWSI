package fr.unistra.wsi.synthetic;

import imj2.tools.Canvas;

import java.awt.Graphics2D;
import java.io.Serializable;

/**
 * @author greg (creation 2014-09-12)
 */
public abstract interface RegionRenderer extends Serializable {
	
	public default boolean beforeRender(final Region region) {
		return false;
	}
	
	public abstract void render(Region region, Canvas buffer, int tileX, int tileY, int optimalTileWidth, int optimalTileHeight);
	
	public static final RegionRenderer DEFAULT = new RegionRenderer() {
		
		@Override
		public final void render(final Region region, final Canvas buffer,
				final int tileX, final int tileY, final int optimalTileWidth, final int optimalTileHeight) {
			final Graphics2D g = buffer.getGraphics();
			
			g.setColor(ModelMaker.labelColors.get(region.getLabel()));
			
			g.fill(region.getGeometry());
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -6536238363391867574L;
		
	};
	
}