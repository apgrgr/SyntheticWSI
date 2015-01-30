package fr.unistra.wsi.synthetic;

import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.lang.Math.min;
import static net.sourceforge.aprog.swing.SwingTools.show;
import static net.sourceforge.aprog.swing.SwingTools.useSystemLookAndFeel;
import static net.sourceforge.aprog.tools.Tools.array;
import static net.sourceforge.aprog.tools.Tools.baseName;
import static net.sourceforge.aprog.tools.Tools.invoke;
import static net.sourceforge.aprog.tools.Tools.readObject;
import static net.sourceforge.aprog.tools.Tools.unchecked;

import imj2.core.Image2D;
import imj2.pixel3d.MouseHandler;
import imj2.tools.Image2DComponent.Painter;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.CompositeContext;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.sourceforge.aprog.tools.IllegalInstantiationException;
import net.sourceforge.aprog.tools.Tools;
import net.sourceforge.aprog.xml.XMLTools;

/**
 * @author greg (creation 2014-09-05)
 */
public final class ModelMaker {
	
	private ModelMaker() {
		throw new IllegalInstantiationException();
	}
	
	/**
	 * {@value}.
	 */
	public static final String MODEL_FILE_KEY = "modelFile";
	
	/**
	 * {@value}.
	 */
	public static final String MODEL_FILE_DEFAULT_PATH = "model.xml";
	
	static final Map<String, Color> labelColors = new HashMap<>();
	
	static final Preferences preferences = Preferences.userNodeForPackage(ModelMaker.class);
	
	static {
		labelColors.put("", Color.BLACK);
		labelColors.put("fat", Color.YELLOW);
		labelColors.put("stroma", Color.PINK);
		labelColors.put("loose stroma", Color.MAGENTA);
		labelColors.put("lobule", Color.BLUE);
		labelColors.put("infiltration", Color.ORANGE);
		labelColors.put("disruption", Color.CYAN);
		labelColors.put("acinus", Color.GREEN);
		labelColors.put("immune cell", Color.DARK_GRAY);
		labelColors.put("epithelial cell", Color.LIGHT_GRAY);
		labelColors.put("cancer", Color.RED);
		labelColors.put("lumen", Color.WHITE);
	}
	
	/**
	 * @param commandLineArguments
	 * <br>Unused
	 */
	public static final void main(final String[] commandLineArguments) {
		useSystemLookAndFeel();
		toneDownBioFormatsLogger();
		
		SwingUtilities.invokeLater(new Runnable() {
			
			@Override
			public void run() {
				final Model model = newModel();
				final VirtualCanvasComponent view = new VirtualCanvasComponent();
				
				setupControllers(model, view);
				
				show(view, ModelMaker.class.getSimpleName(), false);
				
				boundsChanged(model, view);
			}
			
		});
	}
	
	public static final Class<?> classForName(final String className) {
		try {
			return Class.forName(className);
		} catch (final ClassNotFoundException exception) {
			throw unchecked(exception);
		}
	}
	
	public static final Object fieldValue(final Object objectOrClass, final String fieldName) {
		final Class<?> cls = objectOrClass instanceof Class<?> ? (Class<?>) objectOrClass : objectOrClass.getClass();
		
		try {
			return cls.getField(fieldName).get(objectOrClass);
		} catch (final Exception exception) {
			throw unchecked(exception);
		}
	}
	
	public static final void toneDownBioFormatsLogger() {
		try {
			final Class<?> TIFF_PARSER_CLASS = classForName("loci.formats.tiff.TiffParser");
			final Class<?> TIFF_COMPRESSION_CLASS = classForName("loci.formats.tiff.TiffCompression");
			
			final Class<?> loggerFactory = classForName("org.slf4j.LoggerFactory");
			final Object logLevel = fieldValue(classForName("ch.qos.logback.classic.Level"), "INFO");
			
			invoke(invoke(loggerFactory, "getLogger", TIFF_PARSER_CLASS), "setLevel", logLevel);
			invoke(invoke(loggerFactory, "getLogger", TIFF_COMPRESSION_CLASS), "setLevel", logLevel);
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}
	
	public static final boolean testModifier(final MouseEvent event, final int modifierMask) {
		return (event.getModifiersEx() & modifierMask) == modifierMask;
	}
	
	public static final void setupControllers(final Model model, final VirtualCanvasComponent view) {
		final AtomicBoolean showBackground = new AtomicBoolean(true);
		final AtomicBoolean showGraph = new AtomicBoolean(false);
		
		view.getPainters().add(new Painter<VirtualCanvasComponent>() {
			
			@Override
			public final void paint(final Graphics2D g, final VirtualCanvasComponent component,
					final int width, final int height) {
				final Image2D image = model.getImage(view.getScale());
				
				if (image == null || !showBackground.get()) {
					return;
				}
				
				final int lod = image.getLOD();
				
				for (int y = 0; y < view.getViewportHeight(); ++y) {
					final int yInImage = view.unscale(view.getViewportY() + y) >> lod;
					
					if (image.getHeight() <= yInImage) {
						continue;
					}
					
					for (int x = 0; x < view.getViewportWidth(); ++x) {
						final int xInImage = view.unscale(view.getViewportX() + x) >> lod;
						
						if (image.getWidth() <= xInImage) {
							break;
						}
						
						view.getViewportImage().setRGB(x, y, 0xFF000000 | image.getPixelValue(xInImage, yInImage));
					}
				}
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 8542204917701561299L;
			
		});
		
		view.getPainters().add(new Painter<VirtualCanvasComponent>() {
			
			@Override
			public final void paint(final Graphics2D g, final VirtualCanvasComponent component,
					final int width, final int height) {
				final Graphics2D viewportGraphics = component.getViewportGraphics();
				
				{
					final double scale = view.getScale();
					final List<Region> regions = model.getRegions(scale);
					final Region activeRegion = model.getActiveRegionIndex().get() < 0 ? null : regions.get(model.getActiveRegionIndex().get());
					final int tx = component.getViewportX();
					final int ty = component.getViewportY();
					final Stroke saved = viewportGraphics.getStroke();
					
					viewportGraphics.translate(-tx, -ty);
					viewportGraphics.scale(scale, scale);
					
					viewportGraphics.setStroke(new BasicStroke(
							(float) (3.0 / scale),
							BasicStroke.CAP_BUTT,
							BasicStroke.JOIN_BEVEL,
							1F,
							new float[] { 4F, 2F },
							0F));
					viewportGraphics.setColor(new Color(0x80000000, true));
					
					if (showGraph.get()) {
						for (final Region region : model.getRegions(1.0)) {
							final Region parent = region.getParent();
							
							if (parent != null) {
								final Rectangle regionBounds = region.getGeometry().getBounds();
								final Rectangle parentBounds = parent.getGeometry().getBounds();
								
								viewportGraphics.drawLine(
										(int) parentBounds.getCenterX(),
										(int) parentBounds.getCenterY(),
										(int) regionBounds.getCenterX(),
										(int) regionBounds.getCenterY());
							}
						}
					}
					
					viewportGraphics.setStroke(new BasicStroke((float) (4.0 / scale)));
					
					for (final Region region : regions) {
						final Color color = labelColors.get(region.getLabel());
						
						if (region == activeRegion) {
							viewportGraphics.setColor(new Color(0x40000000 | (0x00FFFFFF & color.getRGB()), true));
							viewportGraphics.fill(region.getGeometry());
						} else {
							viewportGraphics.setColor(new Color(0x80000000 | (0x00FFFFFF & color.getRGB()), true));
							viewportGraphics.draw(region.getGeometry());
						}
					}
					
					viewportGraphics.scale(1.0 / scale, 1.0 / scale);
					viewportGraphics.translate(tx, ty);
					viewportGraphics.setStroke(saved);
				}
				
				if (model.getToolVisible().get()) {
					final java.awt.Composite saved = viewportGraphics.getComposite();
					viewportGraphics.setComposite(new ColorInversionComposite());
					viewportGraphics.setColor(Color.WHITE);
					viewportGraphics.draw(model.getTool());
					viewportGraphics.setComposite(saved);
				}
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 7760147174365686463L;
			
		});
		
		view.addKeyListener(new KeyAdapter() {
			
			@Override
			public final void keyPressed(final KeyEvent event) {
				final Component component = event.getComponent();
				
				if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
					model.getActiveRegionIndex().set(-1);
					view.refreshViewport();
				} else if (event.getKeyCode() == KeyEvent.VK_B) {
					showBackground.set(!showBackground.get());
					view.refreshViewport();
				} else if (event.getKeyCode() == KeyEvent.VK_G) {
					showGraph.set(!showGraph.get());
					view.refreshViewport();
				} else if (isMetaDown(event) && event.getKeyCode() == KeyEvent.VK_C) {
					final BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
					
					{
						final Graphics2D g = image.createGraphics();
						
						component.paint(g);
						
						g.dispose();
					}
					
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
						
						@Override
						public final boolean isDataFlavorSupported(final DataFlavor flavor) {
							return Arrays.asList(this.getTransferDataFlavors()).contains(flavor);
						}
						
						@Override
						public final DataFlavor[] getTransferDataFlavors() {
							return array(DataFlavor.imageFlavor);
						}
						
						@Override
						public final Object getTransferData(final DataFlavor flavor)
								throws UnsupportedFlavorException, IOException {
							if (this.isDataFlavorSupported(flavor)) {
								Tools.debugPrint();
								return image;
							}
							
							throw new UnsupportedFlavorException(flavor);
						}
						
					}, null);
				} else if (isMetaDown(event) && event.getKeyCode() == KeyEvent.VK_S) {
					final File modelFile = getModelFile();
					final JFileChooser fileChooser = new JFileChooser(modelFile.getAbsoluteFile().getParent());
					
					fileChooser.setSelectedFile(modelFile);
					
					if (JFileChooser.APPROVE_OPTION == fileChooser.showSaveDialog(event.getComponent())) {
						final File file = fileChooser.getSelectedFile();
						
						try {
							preferences.put(MODEL_FILE_KEY, file.getPath());
							preferences.sync();
						} catch (final BackingStoreException exception) {
							exception.printStackTrace();
						}
						
						model.setFile(file).save();
						
						final Frame frame = (Frame) view.getRootPane().getParent();
						
						if (frame.getTitle().endsWith("*")) {
							frame.setTitle(frame.getTitle().substring(0, frame.getTitle().length() - 1));
						}
					}
				} else if (isMetaDown(event) && event.getKeyCode() == KeyEvent.VK_N) {
					model.clearCache();
					model.getRegions().clear();
					model.setImageFile(null);
					model.getActiveRegionIndex().set(-1);
					view.setVirtualSize(view.getClientWidth(), view.getClientHeight());
				} else if (isMetaDown(event) && event.getKeyCode() == KeyEvent.VK_O) {
					final File modelFile = model.getFile();
					final JFileChooser fileChooser = new JFileChooser(modelFile.getAbsoluteFile().getParent());
					
					fileChooser.setSelectedFile(modelFile);
					
					if (JFileChooser.APPROVE_OPTION == fileChooser.showOpenDialog(event.getComponent())) {
						final File file = fileChooser.getSelectedFile();
						
						if (file.getName().endsWith(".jo") || file.getName().endsWith(".xml")) {
							try {
								model.open(file);
								
								preferences.put(MODEL_FILE_KEY, file.getPath());
								preferences.sync();
							} catch (final BackingStoreException exception) {
								exception.printStackTrace();
							}
							
							final Frame frame = (Frame) view.getRootPane().getParent();
							
							if (frame.getTitle().endsWith("*")) {
								frame.setTitle(frame.getTitle().substring(0, frame.getTitle().length() - 1));
							}
						} else {
							model.setImageFile(file);
							
						}
						
						boundsChanged(model, view);
					}
				} else if (event.getKeyCode() == KeyEvent.VK_ADD) {
					view.setScale(view.getScale() * 1.25);
				} else if (event.getKeyCode() == KeyEvent.VK_SUBTRACT) {
					view.setScale(view.getScale() * 0.8);
				}
			}
			
		});
		view.setFocusable(true);
		view.requestFocusInWindow();
		
		new MouseHandler(null) {
			
			@Override
			public final void mouseMoved(final MouseEvent event) {
				this.updateToolLocation(event);
				this.updateActiveCountourIndex(event);
				view.refreshViewport();
			}
			
			@Override
			public final void mouseExited(final MouseEvent event) {
				model.getToolVisible().set(false);
				
				view.refreshViewport();
			}
			
			@Override
			public final void mouseWheelMoved(final MouseWheelEvent event) {
				final AffineTransform transform = new AffineTransform();
				final Rectangle toolBounds = model.getTool().getBounds();
				final double tx = toolBounds.getCenterX();
				final double ty = toolBounds.getCenterY();
				
				transform.translate(tx, ty);
				
				if (event.getWheelRotation() < 0) {
					transform.scale(1.25, 1.25);
				} else {
					transform.scale(0.8, 0.8);
				}
				
				transform.translate(-tx, -ty);
				
				model.getTool().transform(transform);
				
				view.refreshViewport();
			}
			
			@Override
			public final void mouseClicked(final MouseEvent event) {
				this.popupTriggered(event);
			}
			
			@Override
			public final void mouseReleased(final MouseEvent event) {
				this.popupTriggered(event);
			}
			
			@Override
			public final void mousePressed(final MouseEvent event) {
				if (this.popupTriggered(event)) {
					return;
				}
				
				if (event.getButton() != MouseEvent.BUTTON1) {
					return;
				}
				
				if (isMetaDown(event)) {
					final Area newArea = new Area(model.getTool());
					final int viewportX = view.getViewportX();
					final int viewportY = view.getViewportY();
					final AffineTransform transform = new AffineTransform();
					
					transform.setToScale(1.0 / view.getScale(), 1.0 / view.getScale());
					transform.translate(viewportX, viewportY);
					newArea.transform(transform);
					
					model.getRegions().add(new Region(newArea, model.getNewRegionLabel(), 1)
						.setParent(model.getActiveRegion()));
					model.getActiveRegionIndex().set(model.getRegions().size() - 1);
					
					this.documentModified();
				} else {
					this.updateRegions(event);
				}
				
				this.simplifyRegions();
				this.updateVirtualSize();
				this.sortRegions();
				view.refreshViewport();
			}
			
			@Override
			public final void mouseDragged(final MouseEvent event) {
				this.updateToolLocation(event);
				this.updateRegions(event);
				this.simplifyRegions();
				this.updateVirtualSize();
				this.sortRegions();
				view.refreshViewport();
			}
			
			private final void simplifyRegions() {
				model.getRegions().stream().forEach(c -> c.simplify(3F));
			}
			
			private final void sortRegions() {
				final Region activeRegion = model.getActiveRegion();
				
				Collections.sort(model.getRegions(), new Comparator<Region>() {
					
					@Override
					public final int compare(final Region c1, final Region c2) {
						final Rectangle bounds1 = c1.getGeometry().getBounds();
						final Rectangle bounds2 = c2.getGeometry().getBounds();
						
						return -Double.compare((double) bounds1.width * bounds1.height,
								(double) bounds2.width * bounds2.height);
					}
					
				});
				
				model.getActiveRegionIndex().set(model.getRegions().indexOf(activeRegion));
			}
			
			private final boolean popupTriggered(final MouseEvent event) {
				if (event.isPopupTrigger()) {
					final int regionIndex = model.getActiveRegionIndex().get();
					
					if (0 <= regionIndex) {
						final Region region = model.getRegions().get(regionIndex);
						final Object[] options = labelColors.keySet().toArray();
						final Object newLabel = JOptionPane.showInputDialog(
								event.getComponent(),
								"Label:",
								ModelMaker.class.getSimpleName(),
								JOptionPane.PLAIN_MESSAGE,
								null,
								options,
								options[0]
						);
						
						if (newLabel != null) {
							model.setNewRegionLabel(newLabel.toString());
							region.setLabel(newLabel.toString());
							view.refreshViewport();
							this.documentModified();
						}
					}
					
					return true;
				}
				
				return false;
			}
			
			private final void updateVirtualSize() {
				final Rectangle bounds = new Rectangle();
				
				for (final Region region : model.getRegions()) {
					bounds.add(region.getGeometry().getBounds());
				}
				
				{
					final AffineTransform transform = new AffineTransform();
					
					transform.setToTranslation(-min(0, bounds.x), -min(0, bounds.y));
					
					for (final Region region : model.getRegions()) {
						region.getGeometry().transform(transform);
					}
				}
				
				bounds.x *= view.getScale();
				bounds.y *= view.getScale();
				
				view.addVirtualRectangle(bounds);
			}
			
			private final void updateToolLocation(final MouseEvent event) {
				final Rectangle toolBounds = model.getTool().getBounds();
				final double tx = event.getX() - toolBounds.getCenterX();
				final double ty = event.getY() - toolBounds.getCenterY();
				final AffineTransform transform = new AffineTransform();
				
				transform.setToTranslation(tx, ty);
				
				model.getTool().transform(transform);
				model.getToolVisible().set(true);
			}
			
			private final void updateRegions(final MouseEvent event) {
				final int regionIndex = model.getActiveRegionIndex().get();
				
				if (regionIndex < 0) {
					return;
				}
				
				final Area tmp = new Area(model.getTool());
				final AffineTransform transform = new AffineTransform();
				final int viewportX = view.getViewportX();
				final int viewportY = view.getViewportY();
				
				transform.setToScale(1.0 / view.getScale(), 1.0 / view.getScale());
				transform.translate(viewportX, viewportY);
				tmp.transform(transform);
				
				final Region region = model.getRegions().get(regionIndex);
				final Area area = region.getGeometry();
				
				if ((event.getModifiersEx() & SHIFT_DOWN_MASK) == SHIFT_DOWN_MASK) {
					area.subtract(tmp);
					
					if (area.isEmpty()) {
						region.setParent(null);
						model.getRegions().remove(regionIndex);
						model.getActiveRegionIndex().set(-1);
					}
				} else {
					area.add(tmp);
				}
				
				this.documentModified();
			}
			
			private final int updateActiveCountourIndex(final MouseEvent event) {
				final int viewportX = view.getViewportX();
				final int viewportY = view.getViewportY();
				final double x = (viewportX + event.getX()) / view.getScale();
				final double y = (viewportY + event.getY()) / view.getScale();
				int result = model.getRegions().size() - 1;
				
				for (; 0 <= result; --result) {
					if (model.getRegions().get(result).getGeometry().contains(x, y)) {
						model.getActiveRegionIndex().set(result);
						break;
					}
				}
				
				return result;
			}
			
			private final void documentModified() {
				final Frame frame = (Frame) view.getRootPane().getParent();
				
				if (!frame.getTitle().endsWith("*")) {
					frame.setTitle(frame.getTitle() + "*");
				}
				
				model.clearCache();
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 6281865351854503L;
			
		}.addTo(view);
	}
	
	public static final void boundsChanged(final Model model, final VirtualCanvasComponent view) {
		final Rectangle bounds = model.getBounds();
		
		view.setVirtualSize(bounds.width, bounds.height);
		
		view.refreshViewport();
	}
	
	public static final Model newModel() {
		final File modelFile = getModelFile();
		final File modelXMLFile = new File(baseName(modelFile.getPath()) + ".xml");
		final Model result = readModel(modelFile);
		
		return result != null ? result : new Model().setFile(modelXMLFile);
	}
	
	public static final Model readModel(final File modelFile) {
		final File modelXMLFile = new File(baseName(modelFile.getPath()) + ".xml");
		
		try {
			if (modelFile.exists() && !modelXMLFile.exists()) {
				final Model model = ((Model) readObject(modelFile.getPath())).setFile(modelFile);
				
				XMLTools.write(Model.toXML(model), modelXMLFile, 0);
			}
			
			if (modelXMLFile.exists()) {
				try (final InputStream input = new FileInputStream(modelXMLFile)) {
					return Model.fromXML(XMLTools.parse(input)).setFile(modelXMLFile);
				}
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
		
		return null;
	}
	
	public static final BufferedImage fill(final BufferedImage image, final Color color) {
		final Graphics2D g = image.createGraphics();
		
		g.setColor(color);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.dispose();
		
		return image;
	}
	
	public static final File getModelFile() {
		return new File(preferences.get(MODEL_FILE_KEY, MODEL_FILE_DEFAULT_PATH));
	}
	
	public static final boolean isMetaDown(final InputEvent event) {
		return event.isControlDown() || event.isMetaDown();
	}
	
	/**
	 * @author greg (creation 2014-09-06)
	 */
	public static final class ColorInversionComposite implements java.awt.Composite {
		
		@Override
		public final CompositeContext createContext(final ColorModel srcColorModel,
				final ColorModel dstColorModel, final RenderingHints hints) {
			return new CompositeContext() {
				
				@Override
				public final void dispose() {
					// NOP
				}
				
				@Override
				public final void compose(final Raster src, final Raster dstIn, final WritableRaster dstOut) {
					final Rectangle inBounds = dstIn.getBounds();
					final Rectangle outBounds = dstOut.getBounds();
					
					for (int yIn = inBounds.y, yOut = outBounds.y; yIn < inBounds.y + inBounds.height; ++yIn, ++yOut) {
						for (int xIn = inBounds.x, xOut = outBounds.x; xIn < inBounds.x + inBounds.width; ++xIn, ++xOut) {
							final int[] datum = (int[]) dstIn.getDataElements(xIn, yIn, null);
							datum[0] = (datum[0] & 0xFF000000) | (~datum[0] & 0x00FFFFFF);
							dstOut.setDataElements(xOut, yOut, datum);
						}
					}
				}
				
			};
		}
		
	}
	
}
