package imj2.core;

import static java.lang.Integer.parseInt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author codistmonk (creation 2013-08-25)
 */
public final class SubsampledImage2D extends FilteredTiledImage2D {
	
	private final int width;
	
	private final int height;
	
	public SubsampledImage2D(final Image2D source) {
		super(subId(source.getId() + "_subsampled"), source);
		this.width = source.getWidth() / 2;
		this.height = source.getHeight() / 2;
		
		this.useOptimalTileDimensionsOf(source, 256, 256);
	}
	
	public SubsampledImage2D(final Image2D source, final int tileWidth, final int tileHeight) {
		super(subId(source.getId()), source);
		this.width = source.getWidth() / 2;
		this.height = source.getHeight() / 2;
		
		this.setOptimalTileDimensions(tileWidth, tileHeight);
	}
	
	@Override
	public final int getLOD() {
		return this.getSource().getLOD() + 1;
	}
	
	@Override
	public final Image2D getLODImage(final int lod) {
		final int thisLOD = this.getLOD();
		
		if (lod == thisLOD) {
			return this;
		}
		
		if (lod < thisLOD) {
			return this.getSource().getLODImage(lod);
		}
		
		return new SubsampledImage2D(this).getLODImage(lod);
	}
	
	@Override
	public final int getWidth() {
		return this.width;
	}
	
	@Override
	public final int getHeight() {
		return this.height;
	}
	
	@Override
	public final SubsampledImage2D[] newParallelViews(final int n) {
		return IMJCoreTools.newParallelViews(this, n);
	}
	
	@Override
	public final Channels getChannels() {
		return this.getSource().getChannels();
	}
	
	@Override
	protected final ConcreteImage2D<LinearIntImage> updateTile(final int tileX, final int tileY, final ConcreteImage2D<LinearIntImage> tile) {
		final Image2D source = this.getSource();
		final DefaultColorModel color = new DefaultColorModel(source.getChannels());
		
		tile.forEachPixelInBox(tileX, tileY, tile.getWidth(), tile.getHeight(), new MonopatchProcess() {
			
			@Override
			public final void pixel(final int x, final int y) {
				final int rgba00 = source.getPixelValue(x * 2 + 0, y * 2 + 0);
				final int rgba10 = source.getPixelValue(x * 2 + 1, y * 2 + 0);
				final int rgba01 = source.getPixelValue(x * 2 + 0, y * 2 + 1);
				final int rgba11 = source.getPixelValue(x * 2 + 1, y * 2 + 1);
				final int red = (color.red(rgba00) + color.red(rgba10) + color.red(rgba01) + color.red(rgba11)) / 4;
				final int green = (color.green(rgba00) + color.green(rgba10) + color.green(rgba01) + color.green(rgba11)) / 4;
				final int blue = (color.blue(rgba00) + color.blue(rgba10) + color.blue(rgba01) + color.blue(rgba11)) / 4;
				final int alpha = (color.alpha(rgba00) + color.alpha(rgba10) + color.alpha(rgba01) + color.alpha(rgba11)) / 4;
				
				tile.setPixelValue(x - tileX, y - tileY, DefaultColorModel.argb(red, green, blue, alpha));
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 8092549882515884605L;
			
		});
		
		return tile;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 5611507539390575756L;
	
	private static final Pattern lodPattern = Pattern.compile("[^A-Za-z0-9]lod(\\d+)");
	
	public static final String subId(final String id) {
		final Matcher matcher = lodPattern.matcher(id);
		String prefix = ".lod";
		int lod = 0;
		int start = id.length();
		int end = start;
		
		while (matcher.find()) {
			lod = parseInt(matcher.group(1));
			start = matcher.start();
			end = matcher.end();
			prefix = id.substring(start, start + 4);
		}
		
		return id.substring(0, start) + prefix + (lod + 1) + id.substring(end);
	}
	
}
