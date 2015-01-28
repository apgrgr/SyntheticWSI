package fr.unistra.wsi.synthetic;

import static imj2.tools.IMJTools.awtImage;
import static java.lang.Double.parseDouble;
import static java.lang.Math.min;
import static net.sourceforge.aprog.tools.Tools.array;
import static net.sourceforge.aprog.tools.Tools.baseName;
import static net.sourceforge.aprog.tools.Tools.debugPrint;
import static net.sourceforge.aprog.tools.Tools.unchecked;
import static net.sourceforge.aprog.xml.XMLTools.getNode;
import static net.sourceforge.aprog.xml.XMLTools.getNodes;
import static net.sourceforge.aprog.xml.XMLTools.getNumber;
import static net.sourceforge.aprog.xml.XMLTools.parse;

import imj2.core.ConcreteImage2D;
import imj2.core.IMJCoreTools;
import imj2.core.Image;
import imj2.core.Image2D;
import imj2.core.LinearIntImage;
import imj2.core.SubsampledImage2D;
import imj2.core.TiledImage2D;
import imj2.tools.Canvas;
import imj2.tools.IMJTools;
import imj2.tools.IMJTools.TileProcessor;
import imj2.tools.InputSource;
import imj2.tools.OutputSource;
import imj2.zipslideviewer.ZipSlideViewer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import net.sourceforge.aprog.tools.CommandLineArgumentsParser;
import net.sourceforge.aprog.tools.ConsoleMonitor;
import net.sourceforge.aprog.tools.IllegalInstantiationException;
import net.sourceforge.aprog.tools.TaskManager;
import net.sourceforge.aprog.tools.TicToc;
import net.sourceforge.aprog.tools.Tools;
import net.sourceforge.aprog.xml.XMLTools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author greg (creation 2014-09-09)
 */
public final class GenerateWSI {
	
	private GenerateWSI() {
		throw new IllegalInstantiationException();
	}
	
	static final Random RANDOM = new Random(1L);
	
	static final double SCALE = 1.0;
	
	static final long CONSTRAINT_SOLVER_MAXIMUM_MILLISECONDS = 120_000L;
	
	static final double MAXIMUM_CPU_LOAD = 0.75;
	
	static final long MONITOR_PERIOD_MILLISECONDS = 60_000L;
	
	static final File RENDERERS_FILE = new File("renderers.jo");
	
	/**
	 * @param commandLineArguments
	 * <br>Must not be null
	 */
	public static final void main(final String[] commandLineArguments) throws IOException {
		final CommandLineArgumentsParser arguments = new CommandLineArgumentsParser(commandLineArguments);
		final File modelFile = new File(arguments.get("model",
				ModelMaker.preferences.get(ModelMaker.MODEL_FILE_KEY, ModelMaker.MODEL_FILE_DEFAULT_PATH)));
		final String outputBase = arguments.get("output", baseName(modelFile.getPath()));
		final File temporaryDirectory = new File(outputBase);
		final File outputFile = new File(outputBase + ".zip");
		final String outputImageName = new File(outputBase).getName();
		final Model model = ModelMaker.readModel(modelFile);
		final int tileWidth = arguments.get("tileWidth", 512)[0];
		final int tileHeight = arguments.get("tileHeight", tileWidth)[0];
		final boolean showResult = arguments.get("show", 1)[0] != 0;
		final String rendererXMLPath = arguments.get("renderer", "");
		final TicToc timer = new TicToc();
		
		if (!outputFile.exists()) {
			System.out.println("Generating WSI... " + new Date(timer.tic()));
			process(model, tileWidth, tileHeight, temporaryDirectory, outputImageName, rendererXMLPath);
			subsample(temporaryDirectory, outputImageName);
			writeWSI(temporaryDirectory, outputFile);
			System.out.println("WSI generated in " + timer.toc() + " ms");
		}
		
		if (showResult) {
			ZipSlideViewer.main(array("file", outputFile.getPath()));
		}
	}
	
	public static final ModelRenderer newRenderer(final Model model, final String rendererXMLPath) {
		if (RENDERERS_FILE.exists()) {
			final ModelRenderer result = Tools.readObject(RENDERERS_FILE.getPath());
			
			if (model.getFile().equals(result.getModel().getFile())) {
				return result;
			}
		}
		
		final ModelRenderer result = new ModelRenderer(model);
		final File root = new File(rendererXMLPath).getParentFile();
		
		
		try (final InputStream xmlInputStream = new FileInputStream(rendererXMLPath)) {
			final Document xml = parse(xmlInputStream);
			
			for (final Node regionNode : getNodes(xml, "renderer/region")) {
				final Element regionElement = (Element) regionNode;
				final String label = regionElement.getAttribute("label");
				final Element regionRendererElement = (Element) regionElement.getElementsByTagName("*").item(0);
				
				Tools.debugPrint(label);
				
				if ("adjacentObjects".equals(regionRendererElement.getTagName())) {
					final double collisionableRadius = parseDouble(select(regionRendererElement.getAttribute("collisionableRadius"), "1.0"));
					final File[] textures = getNodes(regionRendererElement, "texture").stream().map(n -> new File(root, ((Element) n).getAttribute("file"))).toArray(File[]::new);
					result.setRegionRenderer(label, new AdjacentObjectsRenderer(collisionableRadius, textures));
				} else {
					Tools.debugError("Unknown region renderer:", regionElement.getTagName());
				}
			}
			
			return result;
		} catch (final IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
	
	public static final String select(final String string1, final String string2) {
		return string1.isEmpty() ? string2 : string1;
	}
	
	public static final void process(final Model model, final int tileWidth, final int tileHeight,
			final File temporaryDirectory, final String outputImageName, final String rendererXMLPath) throws IOException {
		final ConsoleMonitor monitor = new ConsoleMonitor(MONITOR_PERIOD_MILLISECONDS);
		final TicToc timer = new TicToc();
		
		System.out.println("Processing... " + new Date(timer.tic()));
		
		final ModelRenderer renderer = newRenderer(model, rendererXMLPath);
		final Rectangle bounds = model.getBounds();
		
		bounds.width *= SCALE;
		bounds.height *= SCALE;
		
		debugPrint(model.getRegions().size(), bounds);
		
		try (final OutputSource output = new OutputSource(temporaryDirectory.getPath())) {
			try (final OutputStream entryOutput = output.open("metadata.xml")) {
				final Document metadata = XMLTools.parse("<image micronsPerPixel=\"0.2525\"/>");
				final Element subImage = (Element) metadata.getDocumentElement().appendChild(metadata.createElement("subimage"));
				
				subImage.setAttribute("id", "0");
				subImage.setAttribute("type", "svs_slide_lod");
				subImage.setAttribute("width", "" + bounds.width);
				subImage.setAttribute("height", "" + bounds.height);
				subImage.setAttribute("tileWidth", "" + tileWidth);
				subImage.setAttribute("tileHeight", "" + tileHeight);
				
				XMLTools.write(metadata, entryOutput, 0);
			}
			
			{
				final TaskManager tasks = new TaskManager(MAXIMUM_CPU_LOAD);
				final Map<Thread, Canvas> buffers = new HashMap<>();
				
				for (int tileYVariable = 0; tileYVariable < bounds.height; tileYVariable += tileHeight) {
					final int tileY = tileYVariable;
					final int actualTileHeight = min(tileHeight, bounds.height - tileY);
					
					for (int tileXVariable = 0; tileXVariable < bounds.width; tileXVariable += tileWidth) {
						final int tileX = tileXVariable;
						final int actualTileWidth = min(tileWidth, bounds.width - tileX);
						final String entryName = outputImageName + "_svs0_" + tileX + "_" + tileY + ".jpg";
						final File entryFile = new File(temporaryDirectory, entryName);
						
						if (!entryFile.exists()) {
							renderer.beforeRender();
							
							tasks.submit(new Runnable() {
								
								@Override
								public final void run() {
									final Canvas buffer = buffers.compute(Thread.currentThread(), (k, v) -> v != null ? v : new Canvas());
									monitor.ping(tileX + " " + tileY + " / " + bounds.width + " " + bounds.height + " \r");
									buffer.setFormat(actualTileWidth, actualTileHeight, BufferedImage.TYPE_3BYTE_BGR);
									
									final Graphics2D g = buffer.getGraphics();
									final AffineTransform savedTransform = g.getTransform();
									
									g.setColor(new Color(0xFFF3F3F3));
									g.fillRect(0, 0, actualTileWidth, actualTileHeight);
									g.translate(-tileX, -tileY);
									g.scale(SCALE, SCALE);
									
									renderer.renderTo(buffer, tileX, tileY, tileWidth, tileHeight);
									addNoise(buffer.getImage());
									
									g.setTransform(savedTransform);
									
									try (final OutputStream entryOutput = output.open(entryName)) {
										ImageIO.write(buffer.getImage(), "jpg", entryOutput);
									} catch (final IOException exception) {
										throw unchecked(exception);
									}
								}
								
							});
						}
					}
				}
				
				tasks.join();
			}
		}
		
		monitor.pause();
		
		System.out.println("Processing done in " + timer.toc() + " ms");
	}
	
	public static final void addNoise(final BufferedImage image) {
		final int w = image.getWidth();
		final int h = image.getHeight();
		
		for (int y = 0; y < h; ++y) {
			for (int x = 0; x < w; ++x) {
				final int rgb = image.getRGB(x, y);
				final int alpha = IMJTools.alpha8(rgb);
				final int red, green, blue;
				
				{
					final int q = RANDOM.nextInt(4);
					final int mask = (~0) << q;
					red = (IMJTools.red8(rgb) & mask) | RANDOM.nextInt(1 << q);
				}
				{
					final int q = RANDOM.nextInt(4);
					final int mask = (~0) << q;
					green = (IMJTools.green8(rgb) & mask) | RANDOM.nextInt(1 << q);
				}
				{
					final int q = RANDOM.nextInt(4);
					final int mask = (~0) << q;
					blue = (IMJTools.blue8(rgb) & mask) | RANDOM.nextInt(1 << q);
				}
				
				image.setRGB(x, y, IMJTools.a8r8g8b8(alpha, red, green, blue));
			}
		}
	}
	
	public static final void writeWSI(final File temporaryDirectory,
			final File outputFile) throws IOException {
		final ConsoleMonitor monitor = new ConsoleMonitor(MONITOR_PERIOD_MILLISECONDS);
		final TicToc timer = new TicToc();
		
		if (!outputFile.exists()) {
			System.out.println("Writing " + outputFile + "... " + new Date(timer.tic()));
			
			try (final OutputSource output = new OutputSource(outputFile.getPath())) {
				for (final File file : temporaryDirectory.listFiles()) {
					monitor.ping(file.toString() + "\r");
					
					try (final OutputStream entryOutput = output.open(file.getName())) {
						Tools.writeAndClose(new FileInputStream(file), true, entryOutput, false);
					}
				}
			}
			
			monitor.pause();
			
			System.out.println("Writing " + outputFile + " done in " + timer.toc() + " ms");
		}
	}
	
	public static final void subsample(final File temporaryDirectory,
			final String outputImageName) throws IOException {
		final ConsoleMonitor monitor = new ConsoleMonitor(MONITOR_PERIOD_MILLISECONDS);
		final TicToc timer = new TicToc();
		
		System.out.println("Subsampling... " + new Date(timer.tic()));
		
		MultiFileImage2D image = new MultiFileImage2D(temporaryDirectory.getPath());
		
		try (final OutputSource output = new OutputSource(temporaryDirectory.getPath())) {
			for (int lodVariable = 1; lodVariable <= 7; ++lodVariable) {
				final int lod = lodVariable;
				final TiledImage2D lodImage = (TiledImage2D) image.getLODImage(lod);
				
				if (XMLTools.getNode(image.getMetadata(), "image/subimage[@id='" + lod + "']") != null) {
					continue;
				}
				
				Tools.debugPrint(lod, lodImage.getWidth(), lodImage.getHeight(), lodImage.getOptimalTileWidth(), lodImage.getOptimalTileHeight());
				
				IMJTools.forEachTileIn(lodImage, new TileProcessor() {
					
					@Override
					public final void pixel(final Info info) {
						final int tileX = info.getTileX();
						final int tileY = info.getTileY();
						
						monitor.ping(lod + " " + tileX + " " + tileY + " / " + lodImage.getWidth() + " " + lodImage.getHeight() + "\r");
						
						final String entryName = outputImageName + "_svs" + lod + "_" + tileX + "_" + tileY + ".jpg";
						
						if (new File(temporaryDirectory, entryName).exists()) {
							return;
						}
						
						@SuppressWarnings("unchecked")
						final ConcreteImage2D<LinearIntImage> tile = (ConcreteImage2D<LinearIntImage>) lodImage.ensureTileContains(tileX, tileY).updateTile();
						
						try (final OutputStream entryOutput = output.open(entryName)) {
							ImageIO.write(awtImage(tile), "jpg", entryOutput);
						} catch (final IOException exception) {
							throw unchecked(exception);
						}
					}
					
					@Override
					public final void endOfTile() {
						// NOP
					}
					
					/**
					 * {@value}.
					 */
					private static final long serialVersionUID = -8384016321780583170L;
					
				});
				
				XMLTools.getOrCreateNode(image.getMetadata(), "image/subimage["
						+ "@id='" + lod
						+ "' and @type='" + "svs_slide_lod"
						+ "' and @width='" + lodImage.getWidth()
						+ "' and @height='" + lodImage.getHeight()
						+ "' and @tileWidth='" + lodImage.getOptimalTileWidth()
						+ "' and @tileHeight='" + lodImage.getOptimalTileHeight()
						+ "']");
				
				try (final OutputStream entryOutput = output.open("metadata.xml")) {
					XMLTools.write(image.getMetadata(), entryOutput, 0);
				}
				
				image = new MultiFileImage2D(temporaryDirectory.getPath());
			}
		}
		
		monitor.pause();
		
		System.out.println("Subsampling done in " + timer.toc() + " ms");
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class MultiFileImage2D extends TiledImage2D {
		
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
	
	/**
	 * @author greg (creation 2014-09-14)
	 */
	public static final class IdManager<T> implements Serializable {
		
		private final Map<T, Integer> ids = new HashMap<>();
		
		public final synchronized int getId(final T object) {
			return this.ids.compute(object, (k, v) -> v != null ? v : this.ids.size());
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 2382386813868341771L;
		
	}
	
}
