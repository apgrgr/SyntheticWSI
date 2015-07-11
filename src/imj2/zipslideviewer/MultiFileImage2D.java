package imj2.zipslideviewer;

import static multij.tools.Tools.baseName;
import static multij.tools.Tools.unchecked;
import static multij.xml.XMLTools.getNode;
import static multij.xml.XMLTools.getNumber;
import static multij.xml.XMLTools.parse;

import imj2.core.IMJCoreTools;
import imj2.core.Image;
import imj2.core.Image2D;
import imj2.core.SubsampledImage2D;
import imj2.core.TiledImage2D;
import imj2.tools.IMJTools;
import imj2.tools.InputSource;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import multij.xml.XMLTools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author codistmonk (creation 2014-09-09)
 */
public final class MultiFileImage2D extends TiledImage2D {
	
	private final InputSource inputSource;
	
	private final Document metadata;
	
	private final int lod;
	
	private final int width;
	
	private final int height;
	
	private final String tileBase;
	
	private BufferedImage tile;
	
	private final Channels channels;
	
	private TreeMap<Integer, Image2D> lodImages;
	
	public MultiFileImage2D(final String id) {
		this(id, 0);
	}
	
	public MultiFileImage2D(final String id, final int lod) {
		this(id, lod, lod == 0 ? new TreeMap<>() : null);
	}
	
	private MultiFileImage2D(final String id, final int lod, final TreeMap<Integer, Image2D> lodImages) {
		super(id + "_lod" + lod);
		this.inputSource = new InputSource(id);
		this.lod = lod;
		this.tileBase = baseName(new File(id).getName()) + "_svs" + lod + "_";
		this.lodImages = lodImages;
		
		try (final InputStream xml = this.inputSource.open("metadata.xml");
				final InputStream tile = this.inputSource.open(this.tileBase + "0_0.jpg")) {
			this.metadata = parse(xml);
			final BufferedImage tile00 = ImageIO.read(tile);
			final Element image = (Element) getNode(this.metadata, "image/subimage[@id='" + lod +"']");
			this.width = getNumber(image, "@width").intValue();
			this.height = getNumber(image, "@height").intValue();
			this.tile = tile00;
			this.channels = IMJTools.predefinedChannelsFor(tile00);
			
			this.setOptimalTileDimensions(getNumber(image, "@tileWidth").intValue(),
					getNumber(image, "@tileHeight").intValue());
			
			if (0 == lod) {
				this.lodImages.put(lod, this);
				
				for (final Node subimageNode : XMLTools.getNodes(this.metadata, "image/subimage")) {
					final int subimageWidth = getNumber(subimageNode, "@width").intValue();
					final int subimageHeight = getNumber(subimageNode, "@height").intValue();
					final int subimageLOD = bitSize(this.width) - bitSize(subimageWidth);
					
					if (0 < subimageLOD && this.width >> subimageLOD == subimageWidth
							&& this.height >> subimageLOD == subimageHeight) {
						this.lodImages.put(subimageLOD,
								new MultiFileImage2D(id, subimageLOD, this.lodImages));
					}
				}
			}
		} catch (final IOException exception) {
			throw unchecked(exception);
		}
	}
	
	public final Document getMetadata() {
		return this.metadata;
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
	public final int getLOD() {
		return this.lod;
	}
	
	@Override
	public final Image2D getLODImage(final int lod) {
		if (lod == this.getLOD()) {
			return this;
		}
		
		Image2D protoresult = this;
		
		for (final Map.Entry<Integer, Image2D> entry : this.lodImages.entrySet()) {
			if (entry.getKey() == lod) {
				return entry.getValue();
			}
			
			if (entry.getKey() < lod) {
				protoresult = entry.getValue();
			}
		}
		
		return new SubsampledImage2D(protoresult).getLODImage(lod);
	}
	
	@Override
	public final MultiFileImage2D[] newParallelViews(final int n) {
		final MultiFileImage2D[] result = new MultiFileImage2D[n];
		
		if (0 < n) {
			result[0] = this;
			
			for (int i = 1; i < n; ++i) {
				result[i] = new MultiFileImage2D(this.getId());
			}
		}
		
		return result;
	}
	
	@Override
	public final Channels getChannels() {
		return this.channels;
	}
	
	@Override
	public final Image getSource() {
		return null;
	}
	
	@Override
	protected final int getPixelValueFromTile(final int x, final int y, final int xInTile, final int yInTile) {
		return this.tile.getRGB(xInTile, yInTile);
	}
	
	@Override
	protected final void setTilePixelValue(final int x, final int y, final int xInTile, final int yInTile, final int value) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public final BufferedImage updateTile() {
		final String tileName = this.tileBase + this.getTileX() + "_" + this.getTileY() + ".jpg";
		final String tileKey = this.getId() + " " + tileName;
		final InputSource inputSource = this.inputSource;
		
		this.tile = IMJCoreTools.cache(tileKey, new Callable<BufferedImage>() {
			
			@Override
			public final BufferedImage call() throws Exception {
				try (final InputStream input = inputSource.open(tileName)) {
					return ImageIO.read(input);
				}
			}
			
		});
		
		return this.tile;
	}
	
	@Override
	protected final boolean makeNewTile() {
		return this.tile.getWidth() != this.getTileWidth() || this.tile.getHeight() != this.getTileHeight();
	}
	
	@Override
	protected final void finalize() throws Throwable {
		try {
			this.inputSource.close();
		} finally {
			super.finalize();
		}
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 85144910520620110L;
	
	public static final int bitSize(final int value) {
		return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(value);
	}
	
}
