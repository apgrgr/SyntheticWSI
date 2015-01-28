package imj2.zipslideviewer;

import static java.lang.Math.cos;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static net.sourceforge.aprog.swing.SwingTools.scrollable;
import static net.sourceforge.aprog.swing.SwingTools.useSystemLookAndFeel;
import static net.sourceforge.aprog.tools.Tools.cast;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

import imj2.core.Image2D;
import imj2.core.RetiledImage2D;
import imj2.core.TiledImage2D;
import imj2.tools.AwtBackedImage;
import imj2.tools.IMJTools;
import imj2.tools.LociBackedImage;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.CellEditorListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import net.sourceforge.aprog.events.EventManager;
import net.sourceforge.aprog.events.EventManager.AbstractEvent;
import net.sourceforge.aprog.swing.SwingTools;
import net.sourceforge.aprog.tools.CommandLineArgumentsParser;
import net.sourceforge.aprog.tools.IllegalInstantiationException;
import net.sourceforge.aprog.tools.Tools;

/**
 * @author codistmonk (creation 2014-11-04)
 */
public final class ZipSlideViewer {
	
	private ZipSlideViewer() {
		throw new IllegalInstantiationException();
	}
	
	static final Preferences preferences = Preferences.userNodeForPackage(ZipSlideViewer.class);
	
	static final int MAXIMUM_TILE_SIZE = 512;
	
	/**
	 * @param commandLineArguments
	 * <br>Unused
	 */
	public static final void main(final String[] commandLineArguments) {
		final CommandLineArgumentsParser arguments = new CommandLineArgumentsParser(commandLineArguments);
		
		useSystemLookAndFeel();
		IMJTools.toneDownBioFormatsLogger();
		
		SwingUtilities.invokeLater(() -> {
			final JFrame mainFrame = newMainFrame();
			final File imageFile = new File(arguments.get("file", ""));
			
			if (imageFile.isFile()) {
				openImages(mainFrame, Arrays.asList(imageFile));
			}
		});
	}
	
	public static final void openImage(final ImageItem item, final Component[] view,
			final JFrame mainFrame, final DefaultMutableTreeNode parent) {
		final File file = item.getFile();
		
		preferences.put("lastDirectory", file.getParent());
		
		final Component oldComponent = view[0];
		final View oldView = cast(View.class, oldComponent);
		final ImageGroupItem groupItem = parent == null ? null
				: cast(ImageGroupItem.class, parent.getUserObject());
		
		view[0] = item.getImageView();
		
		if (oldComponent != null) {
			mainFrame.remove(oldComponent);
		}
		
		mainFrame.add(view[0], BorderLayout.CENTER);
		mainFrame.setTitle(file.getName());
		mainFrame.validate();
		
		view[0].requestFocus();
		
		preferences.put("lastImage", file.getPath());
		
		SharedProperties.getInstance().set(mainFrame, "view", view[0]);
		
		if (groupItem != null && false) {
			groupItem.update(oldView, item.getImageView(), item.getLockView());
		}
		
		mainFrame.repaint();
		
		EventManager.getInstance().dispatch(new ImageChangedEvent(mainFrame, ((View) view[0]).getImage()));
	}
	
	public static final TiledImage2D tiled(final Image2D image) {
		return tiled(image, MAXIMUM_TILE_SIZE);
	}
	
	public static final TiledImage2D tiled(final Image2D image, final int maximumTileSize) {
		final TiledImage2D tiledImage = Tools.cast(TiledImage2D.class, image);
		
		if (tiledImage != null && min(tiledImage.getOptimalTileWidth(), tiledImage.getWidth()) <= maximumTileSize
				&&  min(tiledImage.getOptimalTileHeight(), tiledImage.getHeight()) <= maximumTileSize) {
			return tiledImage;
		}
		
		return new RetiledImage2D(image, maximumTileSize);
	}
	
	public static final DefaultMutableTreeNode openSession() {
		if (false) {
			try {
				return Tools.readObject("session.jo");
			} catch (final Exception exception) {
				Tools.debugError(exception);
				return new DefaultMutableTreeNode(new ImageGroupItem().setName("images"));
			}
		} else {
			return new DefaultMutableTreeNode(new ImageGroupItem().setName("images"));
		}
	}
	
	public static final JFrame newMainFrame() {
		final JFrame result = new JFrame();
		final Component[] view = { new JLabel("Drop your file here (zip, svs, jpg, png)") };
		
		result.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		result.setPreferredSize(new Dimension(512, 512));
		
		final JTree tree = new JTree(openSession());
		
		tree.setCellRenderer(new TreeCellRenderer() {
			
			private final TreeCellRenderer defaultRenderer = tree.getCellRenderer();
			
			@Override
			public final Component getTreeCellRendererComponent(final JTree tree, final Object value,
					final boolean selected, final boolean expanded, final boolean leaf, final int row,
					final boolean hasFocus) {
				final DefaultMutableTreeNode node = cast(DefaultMutableTreeNode.class, value);
				final ImageItem component = cast(ImageItem.class, node.getUserObject());
				final Component result = component != null ? component.getItemView()
						: this.defaultRenderer.getTreeCellRendererComponent(
								tree, value, selected, expanded, leaf, row, hasFocus);
				final int preferredHeight = result.getPreferredSize().height;
				
				if (tree.getRowHeight() < preferredHeight) {
					tree.setRowHeight(preferredHeight);
				}
				
				if (component != null) {
					component.getItemView().setBackground(selected ? Color.LIGHT_GRAY : null);
				}
				
				return result;
			}
			
		});
		
		tree.setEditable(true);
		
		tree.setCellEditor(new TreeCellEditor() {
			
			private final TreeCellEditor defaultRenderer = tree.getCellEditor();
			
			private ImageItem item;
			
			@Override
			public final Component getTreeCellEditorComponent(final JTree tree,
					final Object value, final boolean isSelected, final boolean expanded,
					final boolean leaf, final int row) {
				this.item.getItemView().setBackground(Color.LIGHT_GRAY);
				
				return this.item.getItemView();
			}
			
			@Override
			public final Object getCellEditorValue() {
				return this.item;
			}
			
			@Override
			public final boolean isCellEditable(final EventObject event) {
				this.item = null;
				
				final MouseEvent mouseEvent = cast(MouseEvent.class, event);
				
				if (mouseEvent != null) {
					final TreePath path = tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());
					final DefaultMutableTreeNode node = cast(DefaultMutableTreeNode.class, path.getLastPathComponent());
					this.item = node == null ? null : cast(ImageItem.class, node.getUserObject());
					
					if (this.item != null) {
						return true;
					}
				}
				
				return false;
			}
			
			@Override
			public final boolean shouldSelectCell(final EventObject event) {
				return true;
			}
			
			@Override
			public final boolean stopCellEditing() {
				return this.defaultRenderer.stopCellEditing();
			}
			
			@Override
			public final void cancelCellEditing() {
				this.defaultRenderer.cancelCellEditing();
			}
			
			@Override
			public final void addCellEditorListener(final CellEditorListener l) {
				this.defaultRenderer.addCellEditorListener(l);
			}
			
			@Override
			public final void removeCellEditorListener(final CellEditorListener l) {
				this.defaultRenderer.removeCellEditorListener(l);
			}
			
		});
		
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			
			@Override
			public final void valueChanged(final TreeSelectionEvent event) {
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
				final ImageItem imageItem = cast(ImageItem.class, node.getUserObject());
				final File file = imageItem != null ? imageItem.getFile() : null;
				
				Tools.debugPrint(file);
				
				if (file != null) {
					openImage(imageItem, view, result, (DefaultMutableTreeNode) node.getParent());
				}
			}
			
		});
		
		SharedProperties.getInstance().set(result, "tree", tree);
		
		result.add(scrollable(tree), BorderLayout.WEST);
		result.add(view[0], BorderLayout.CENTER);
		
		result.setDropTarget(new DropTarget() {
			
			@Override
			public final synchronized void drop(final DropTargetDropEvent event) {
				final List<File> files = SwingTools.getFiles(event);
				
				openImages(result, files);
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = -2485213741517692527L;
			
		});
		
		tree.addTreeExpansionListener(new TreeExpansionListener() {
			
			@Override
			public final void treeExpanded(final TreeExpansionEvent event) {
				tree.getRootPane().validate();
			}
			
			@Override
			public final void treeCollapsed(final TreeExpansionEvent event) {
				tree.getRootPane().validate();
			}
			
		});
		
		SwingTools.packAndCenter(result).setVisible(true);
		
		final Timer timer = new Timer(30_000, e -> {
			saveSession(tree);
		});
		
		result.addWindowListener(new WindowAdapter() {
			
			@Override
			public final void windowClosing(final WindowEvent event) {
				timer.stop();
				saveSession(tree);
			}
			
		});
		
		timer.setRepeats(true);
		timer.start();
		
		return result;
	}
	
	public static final void saveSession(final JTree tree) {
		if (true) {
			return;
		}
		
		try {
			Tools.debugPrint("Saving session...");
			Tools.writeObject((Serializable) tree.getModel().getRoot(), "session.jo");
			Tools.debugPrint("Session saved");
		} catch (final Exception exception) {
			exception.printStackTrace();
		}
	}
	
	public static final void openImages(final JFrame result, final List<File> files) {
		final JTree tree = SharedProperties.getInstance().get(result, "tree");
		TreePath toSelect = null;
		
		for (final File file : files) {
			final DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
			final DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
			final DefaultMutableTreeNode node = new DefaultMutableTreeNode(new ImageItem(file.getPath()));
			
			if (toSelect == null) {
				toSelect = new TreePath(treeModel.getPathToRoot(node));
			}
			
			treeModel.insertNodeInto(node, root, treeModel.getChildCount(root));
		}
		
		tree.setSelectionPath(toSelect);
		
		tree.getRootPane().validate();
	}

	/**
	 * @author codistmonk (creation 2014-11-26)
	 */
	public static final class ImageGroupItem implements Serializable {
		
		private String name;
		
		private final Point2D.Double centerOffset = new Point2D.Double();
		
		private final Point2D.Double imageItemCenterSaved = new Point2D.Double();
		
		private double scale = 1.0;
		
		private double angleOffset;
		
		private transient JCheckBox viewLock;
		
		public final String getName() {
			return this.name;
		}
		
		public final ImageGroupItem setName(final String name) {
			this.name = name;
			
			return this;
		}
		
		public final double getScale() {
			return this.scale;
		}
		
		public final ImageGroupItem setScale(final double scale) {
			this.scale = scale;
			
			return this;
		}
		
		public final double getAngleOffset() {
			return this.angleOffset;
		}
		
		public final ImageGroupItem setAngleOffset(final double angleOffset) {
			this.angleOffset = angleOffset;
			
			return this;
		}
		
		public final Point2D.Double getCenterOffset() {
			return this.centerOffset;
		}
		
		public final Point2D.Double getImageItemCenterSaved() {
			return this.imageItemCenterSaved;
		}
		
		public final ImageGroupItem update(final View oldView, final View newView, final JCheckBox viewLock) {
			Tools.debugPrint(oldView, newView);
			
			if (oldView != null) {
				if (this.viewLock.isSelected()) {
					final double dX = oldView.getCenter().x - this.getImageItemCenterSaved().x;
					final double dY = oldView.getCenter().y - this.getImageItemCenterSaved().y;
					final double oldAngle = oldView.getAngle();
					this.getCenterOffset().x = dX * cos(oldAngle) - dY * sin(oldAngle);
					this.getCenterOffset().y = dX * sin(oldAngle) + dY * cos(oldAngle);
					
					oldView.getCenter().setLocation(this.getImageItemCenterSaved());
				} else {
					oldView.updateCenter(-this.getCenterOffset().x, -this.getCenterOffset().y);
					this.viewLock.setSelected(true);
				}
				
				this.setScale(oldView.getScale());
			}
			
			this.viewLock = viewLock;
			
			this.getImageItemCenterSaved().setLocation(newView.getCenter());
			
			newView.setScale(this.getScale());
			newView.updateCenter(this.getCenterOffset().x, this.getCenterOffset().y);
			
			return this;
		}
		
		@Override
		public final String toString() {
			return this.getName();
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -127899985304845515L;
		
	}
	
	/**
	 * @author codistmonk (creation 2014-11-25)
	 */
	public static final class ImageItem implements Serializable {
		
		private final String path;
		
		private Point2D.Double center;
		
		private double scale;
		
		private double angle;
		
		private transient File file;
		
		private transient JComponent itemView;
		
		private transient JCheckBox lockView;
		
		private transient View view;
		
		public ImageItem(final String path) {
			Tools.debugPrint(path);
			this.path = path;
		}
		
		public final JCheckBox getLockView() {
			if (this.lockView == null) {
				this.lockView = noBackground(new JCheckBox((String) null, true));
			}
			
			return this.lockView;
		}
		
		public final JComponent getItemView() {
			if (this.itemView == null) {
				this.itemView = noBackground(new JPanel());
				
				if (false) {
					this.itemView.add(this.getLockView());
				}
				this.itemView.add(noBackground(new JLabel(this.getFile().getName())));
			}
			
			return this.itemView;
		}
		
		public final File getFile() {
			if (this.file == null) {
				this.file = new File(this.path);
			}
			
			return this.file;
		}
		
		public final View getImageView() {
			if (this.view == null) {
				final File file = this.getFile();
				
				final Image2D image;
				
				if (file.getName().endsWith(".zip")) {
					image = new MultiFileImage2D(file.getPath());
				} else if (file.getName().endsWith(".jpg") || file.getName().endsWith(".png")) {
					try {
						image = new AwtBackedImage(file.getPath(), ImageIO.read(file));
					} catch (final IOException exception) {
						throw new UncheckedIOException(exception);
					}
				} else {
					image = new LociBackedImage(file.getPath());
				}
				
				final TiledImage2D tiled = tiled(image);
				
				this.view = new View(tiled);
				
				this.view.getPainters().add((g, v, w, h) -> {
					g.drawString((int) ((40.0 * v.getScale()) * 100.0) / 100.0 + "X", 1, h - 1);
				});
				
				if (this.center != null) {
					this.view.getCenter().setLocation(this.center);
					this.view.setScale(this.scale);
					this.view.setAngle(this.angle);
					this.view.repaint();
				}
			}
			
			return this.view;
		}
		
		private final Object writeReplace() throws ObjectStreamException {
			if (this.view != null) {
				this.center = this.view.getCenter();
				this.scale = this.view.getScale();
				this.angle = this.view.getAngle();
			}
			
			return this;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 8449846928874675190L;
		
		public static final <C extends JComponent> C notOpaque(final C component) {
			component.setOpaque(false);
			
			return component;
		}
		
		public static final <C extends Component> C noBackground(final C component) {
			component.setBackground(null);
			
			return component;
		}
		
	}
	
	/**
	 * @author codistmonk (creation 2014-11-24)
	 */
	public static final class ImageChangedEvent extends AbstractEvent<JFrame> {
		
		private final TiledImage2D newImage;
		
		public ImageChangedEvent(final JFrame source, final TiledImage2D newImage) {
			super(source);
			this.newImage = newImage;
		}
		
		public final TiledImage2D getNewImage() {
			return this.newImage;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 5378247296797275116L;
		
	}
	
	/**
	 * @author codistmonk (creation 2014-11-24)
	 */
	public static final class SharedProperties implements Serializable {
		
		private final Map<Object, Map<String, Object>> properties = new WeakHashMap<>();
		
		public final SharedProperties set(final Object object, final String key, final Object value) {
			this.properties.computeIfAbsent(object, o -> new HashMap<>()).put(key, value);
			
			return this;
		}
		
		@SuppressWarnings("unchecked")
		public final <T> T get(final Object object, final String key) {
			return (T) this.properties.getOrDefault(object, Collections.emptyMap()).get(key);
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 1961721962084887786L;
		
		private static final SharedProperties instance = new SharedProperties();
		
		public static final SharedProperties getInstance() {
			return instance;
		}
		
	}
	
}
