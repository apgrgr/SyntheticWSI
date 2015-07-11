package imj2.tools;

import static imj2.core.IMJCoreTools.cache;
import static java.lang.Math.min;
import static multij.tools.Tools.unchecked;
import imj2.core.ConcreteImage2D;
import imj2.core.DefaultColorModel;
import imj2.core.FilteredTiledImage2D;
import imj2.core.Image;
import imj2.core.Image2D;
import imj2.core.LinearIntImage;
import imj2.core.TiledImage2D;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import multij.tools.Tools;

/**
 * @author codistmonk (creation 2013-08-07)
 */
public final class LociBackedImage extends TiledImage2D {
	
	private final String imageId;
	
	private int seriesCount;
	
	private final int seriesIndex;
	
	private transient IFormatReader reader;
	
	private final int bytesPerPixel;
	
	private transient byte[] tile;
	
	private final Dimension[] seriesDimensions;
	
	private final Map<Integer, Image2D> subsampleds;
	
	public LociBackedImage(final String id) {
		this(id, 0);
	}
	
	public LociBackedImage(final String id, final int seriesIndex) {
		super(id + "_series" + seriesIndex);
		this.imageId = id;
		
		this.seriesIndex = seriesIndex;
		
		this.setupReader();
		
		this.bytesPerPixel = FormatTools.getBytesPerPixel(this.reader.getPixelType()) * this.reader.getRGBChannelCount();
		
		if (4 < this.bytesPerPixel) {
			throw new IllegalArgumentException();
		}
		
		this.seriesDimensions = new Dimension[this.getSeriesCount()];
		
		for (int i = this.getSeriesCount() - 1; 0 <= i; --i) {
			this.reader.setSeries(i);
			this.seriesDimensions[i] = new Dimension(this.reader.getSizeX(), this.reader.getSizeY());
		}
		
		this.reader.setSeries(seriesIndex);
		
		this.subsampleds = new HashMap<>();
	}
	
	public final String getImageId() {
		return this.imageId;
	}
	
	public final IFormatReader getReader() {
		if (this.reader == null) {
			this.setupReader();
		}
		
		return this.reader;
	}
	
	public final int getSeriesIndex() {
		return this.seriesIndex;
	}
	
	public final int getSeriesCount() {
		return this.seriesCount;
	}
	
	public final Dimension getSeriesDimension(final int seriesIndex) {
		return this.seriesDimensions[seriesIndex];
	}
	
	@Override
	public final Image getSource() {
		return null;
	}
	
	@Override
	public final int getLOD() {
		return 0;
	}
	
	@Override
	public final Image2D getLODImage(final int lod) {
		if (lod <= 0) {
			return this;
		}
		
		return this.subsampleds.compute(lod, (k, v) -> {
			if (v != null) {
				return v;
			}
			
			final LociBackedImage source = this.getSource(lod);
			
			return new Subsampled(this, source, lod).getLODImage(lod);
		});
	}
	
	@Override
	public final Channels getChannels() {
		return predefinedChannelsFor(this.getReader());
	}
	
	@Override
	public final int getWidth() {
		return this.getReader().getSizeX();
	}
	
	@Override
	public final int getHeight() {
		return this.getReader().getSizeY();
	}
	
	@Override
	public final LociBackedImage[] newParallelViews(final int n) {
		final LociBackedImage[] result = new LociBackedImage[n];
		
		result[0] = this;
		
		for (int i = 1; i < n; ++i) {
			result[i] = new LociBackedImage(this.getId());
		}
		
		return result;
	}
	
	@Override
	public final byte[] updateTile() {
		final int tileWidth = this.getTileWidth();
		final int tileHeight = this.getTileHeight();
		
		this.tile = cache(Arrays.asList(this.getId(), this.getTileX(), this.getTileY(), this.getReader().getSeries()), new Callable<byte[]>() {
			
			@Override
			public final byte[] call() throws Exception {
				return LociBackedImage.this.updateTile(LociBackedImage.this.newTile(tileWidth, tileHeight));
			}
			
		});
		
		return this.tile;
	}
	
	@Override
	protected final int getPixelValueFromTile(final int x, final int y, final int xInTile, final int yInTile) {
		final int channelCount = this.getChannels().getChannelCount();
		final int bytesPerChannel = FormatTools.getBytesPerPixel(this.reader.getPixelType());
		int result = 0;
		
		if (this.getReader().isIndexed()) {
			if (!this.getReader().isInterleaved()) {
				throw new IllegalArgumentException();
			}
			
			final int pixelFirstByteIndex = (yInTile * this.getTileWidth() + xInTile) * bytesPerChannel;
			
			try {
				switch (bytesPerChannel) {
				case 1:
					return packPixelValue(this.getReader().get8BitLookupTable(),
							this.tile[pixelFirstByteIndex] & 0x000000FF);
				case 2:
					return packPixelValue(this.getReader().get16BitLookupTable(),
							((this.tile[pixelFirstByteIndex] & 0x000000FF) << 8) | (this.tile[pixelFirstByteIndex + 1] & 0x000000FF));
				default:
					throw new IllegalArgumentException();
				}
			} catch (final Exception exception) {
				throw unchecked(exception);
			}
		} else if (this.getReader().isInterleaved()) {
			final int pixelFirstByteIndex = (yInTile * this.getTileWidth() + xInTile) * bytesPerChannel * channelCount;
			
			for (int i = 0; i < channelCount; ++i) {
				result = (result << 8) | (this.tile[pixelFirstByteIndex + i] & 0x000000FF);
			}
		} else {
			final int tileChannelByteCount = this.getTileWidth() * this.getTileHeight() * bytesPerChannel;
			final int pixelFirstByteIndex = (yInTile * this.getTileWidth() + xInTile) * bytesPerChannel;
			
			for (int i = 0; i < channelCount; ++i) {
				result = (result << 8) | (this.tile[pixelFirstByteIndex + i * tileChannelByteCount] & 0x000000FF);
			}
		}
		
		// XXX Is it always ok to assume RGBA and convert to ARGB if channelCount == 4?
		return channelCount == 4 ? (result >> 8) | (result << 24) : result;
	}
	
	@Override
	protected final void setTilePixelValue(final int x, final int y, final int xInTile, final int yInTile,
			final int value) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected final boolean makeNewTile() {
		return this.tile == null;
	}
	
	final byte[] updateTile(final byte[] tile) {
		try {
			this.getReader().openBytes(0, tile, this.getTileX(), this.getTileY(),
					this.getTileWidth(), this.getTileHeight());
			
			return tile;
		} catch (final Exception exception) {
			throw unchecked(exception);
		}
	}
	
	final byte[] newTile(final int tileWidth, final int tileHeight) {
		return new byte[tileWidth * tileHeight * this.bytesPerPixel];
	}
	
	private final void setupReader() {
		this.reader = newImageReader(this.imageId);
		
		for (this.seriesCount = 0; this.seriesCount < this.reader.getSeriesCount(); ++this.seriesCount) {
			this.reader.setSeries(this.seriesCount);
			
			if (this.reader.getSeriesMetadata().isEmpty()) {
				++this.seriesCount;
				break;
			}
		}
		
		this.reader.setSeries(this.seriesIndex);
		
		this.setOptimalTileDimensions(this.reader.getOptimalTileWidth(), this.reader.getOptimalTileHeight());
	}
	
	private final LociBackedImage getSource(final int lod) {
		final int w = this.getWidth() >> lod;
		final int h = this.getHeight() >> lod;
		final int n = this.getSeriesCount();
		
		for (int i = n - 1; 0 <= i; --i) {
			final Dimension d = this.getSeriesDimension(i);
			
			Tools.debugPrint(i, d, w, h);
			
			if (w <= d.getWidth() && h <= d.getHeight()) {
				return new LociBackedImage(this.getImageId(), i);
			}
		}
		
		throw new IllegalArgumentException();
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -3770386453405162843L;
	
	public static final IFormatReader newImageReader(final String id) {
		final IFormatReader reader = new ImageReader();
		
		try {
			reader.setId(id);
		} catch (final Exception exception) {
			throw unchecked(exception);
		}
		
		if ("portable gray map".equals(reader.getFormat().toLowerCase(Locale.ENGLISH))) {
			// XXX This fixes a defect in Bio-Formats PPM loading, but is it always OK?
			reader.getCoreMetadata()[0].interleaved = true;
		}
		
		reader.setSeries(0);
		
		return reader;
	}
	
	public static final int packPixelValue(final byte[][] channelTables, final int colorIndex) {
		int result = 0;
		
		for (final byte[] channelTable : channelTables) {
			result = (result << 8) | (channelTable[colorIndex] & 0x000000FF);
		}
		
		return result;
	}
	
	public static final int packPixelValue(final short[][] channelTables, final int colorIndex) {
		int result = 0;
		
		for (final short[] channelTable : channelTables) {
			result = (result << 16) | (channelTable[colorIndex] & 0x0000FFFF);
		}
		
		return result;
	}
	
	public static final Channels predefinedChannelsFor(final IFormatReader lociImage) {
		if (lociImage.isIndexed()) {
			return PredefinedChannels.C3_U8;
		}
		
		switch (lociImage.getRGBChannelCount()) {
		case 1:
			switch (FormatTools.getBytesPerPixel(lociImage.getPixelType()) * lociImage.getRGBChannelCount()) {
			case 1:
				return 1 == lociImage.getBitsPerPixel() ?
						PredefinedChannels.C1_U1 : PredefinedChannels.C1_U8;
			case 2:
				return PredefinedChannels.C1_U16;
			default:
				return PredefinedChannels.C1_S32;
			}
		case 2:
			return PredefinedChannels.C2_U16;
		case 3:
			return PredefinedChannels.C3_U8;
		case 4:
			return PredefinedChannels.C4_U8;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @author codistmonk (creation 2014-09-08)
	 */
	public static final class Subsampled extends FilteredTiledImage2D {
		
		private transient LociBackedImage source0;
		
		private final int lod;
		
		Subsampled(final LociBackedImage source0, final LociBackedImage source, final int lod) {
			super(source.getId() + "_lod" + lod, source);
			this.source0 = source0;
			this.lod = lod;
			
			this.setOptimalTileDimensions(source.getOptimalTileWidth(), source.getOptimalTileHeight());
		}
		
		@Override
		public final int getWidth() {
			return this.source0.getWidth() >> this.getLOD();
		}
		
		@Override
		public final int getHeight() {
			return this.source0.getHeight() >> this.getLOD();
		}
		
		@Override
		public final int getLOD() {
			return this.lod;
		}
		
		@Override
		public final Image2D getLODImage(final int lod) {
			return lod == this.getLOD() ? this : this.source0.getLODImage(lod);
		}
		
		@Override
		public final Subsampled[] newParallelViews(final int n) {
			final Subsampled[] result = new Subsampled[n];
			
			result[0] = this;
			
			for (int i = 1; i < n; ++i) {
				result[i] = new Subsampled(this.source0, (LociBackedImage) this.getSource(), this.getLOD());
			}
			
			return result;
		}
		
		@Override
		public final Channels getChannels() {
			return this.getSource().getChannels();
		}
		
		@Override
		protected final ConcreteImage2D<LinearIntImage> updateTile(
				final int tileX, final int tileY, final ConcreteImage2D<LinearIntImage> tile) {
			final Image2D source = this.getSource();
			final DefaultColorModel color = new DefaultColorModel(source.getChannels());
			final double stride = min((double) this.getSource().getWidth() / this.getWidth(),
					(double) this.getSource().getHeight() / this.getHeight());
			
			tile.forEachPixelInBox(tileX, tileY, tile.getWidth(), tile.getHeight(), new MonopatchProcess() {
				
				@Override
				public final void pixel(final int x, final int y) {
					int red = 0;
					int green = 0;
					int blue = 0;
					int alpha = 0;
					int n = 0;
					
					for (double yy = y * stride; yy < (y + 1.0) * stride; ++yy) {
						for (double xx = x * stride; xx < (x + 1.0) * stride; ++xx, ++n) {
							final int rgba = source.getPixelValue((int) xx, (int) yy);
							red += color.red(rgba);
							green += color.green(rgba);
							blue += color.blue(rgba);
							alpha += color.alpha(rgba);
						}
					}
					
					red /= n;
					green /= n;
					blue /= n;
					alpha /= n;
					
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
		private static final long serialVersionUID = -4860842602765514046L;
		
	}
	
}
