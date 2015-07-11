package fr.unistra.wsi.synthetic;

import static imj2.tools.CommonSwingTools.center;
import static imj2.tools.CommonSwingTools.property;
import static imj2.tools.CommonSwingTools.showEditDialog;
import static net.sourceforge.aprog.swing.SwingTools.scrollable;
import static net.sourceforge.aprog.swing.SwingTools.show;
import static net.sourceforge.aprog.swing.SwingTools.verticalBox;
import static net.sourceforge.aprog.tools.Tools.array;
import static net.sourceforge.aprog.tools.Tools.getResourceAsStream;
import static net.sourceforge.aprog.tools.Tools.writeAndClose;
import imj2.zipslideviewer.ZipSlideViewer;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import net.sourceforge.aprog.swing.SwingTools;
import net.sourceforge.aprog.tools.IllegalInstantiationException;
import net.sourceforge.aprog.tools.Tools;

/**
 * @author ga (creation 2015-01-27)
 */
public final class Launcher {
	
	private Launcher() {
		throw new IllegalInstantiationException();
	}
	
	static final int QUIT = -1;
	
	static final int SHOW_README = 0;
	
	static final int EXTRACT_EXAMPLE = 1;
	
	static final int MODEL_MAKER = 2;
	
	static final int GENERATE_WSI = 3;
	
	static final int VIEW_WSI = 4;
	
	static final Preferences preferences = Preferences.userNodeForPackage(Launcher.class);
	
	/**
	 * @param commandLineArguments
	 * <br>Unused
	 * @throws InterruptedException 
	 * @throws IOException 
	 */
	public static final void main(final String[] commandLineArguments) throws InterruptedException, IOException {
		SwingTools.useSystemLookAndFeel();
		
		final Window[] window = { null };
		final BlockingQueue<Integer> actions = new ArrayBlockingQueue<>(1);
		
		SwingUtilities.invokeLater(new Runnable() {
			
			@Override
			public final void run() {
				window[0] = show(verticalBox(
						action(actions, window, "README", SHOW_README),
						action(actions, window, "Extract example", EXTRACT_EXAMPLE),
						action(actions, window, "ModelMaker", MODEL_MAKER),
						action(actions, window, "GenerateWSI", GENERATE_WSI),
						action(actions, window, "ViewWSI", VIEW_WSI)
						), "SyntheticWSI", false);
				
				window[0].addWindowListener(new WindowAdapter() {
					
					@Override
					public final void windowClosing(final WindowEvent event) {
						actions.offer(QUIT);
					}
					
				});
			}
			
		});
		
		int action;
		
		try {
			do {
				action = actions.take();
				
				switch (action) {
				case SHOW_README:
					final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
					
					writeAndClose(getResourceAsStream("README.txt"), true, buffer, true);
					SwingUtilities.invokeLater(() -> show(scrollable(new JTextArea(buffer.toString())), "README", false));
					
					break;
				case EXTRACT_EXAMPLE:
					final File applicationFile = Tools.getApplicationFile();
					
					if (applicationFile.isDirectory()) {
						final File sourceData = new File(applicationFile.getParentFile(), "lib/data");
						final File targetData = new File("data");
						
						process(sourceData, new FileProcessor() {
							
							@Override
							public final Control file(final File file) {
								if (file.isFile()) {
									final File target = new File(targetData, file.getPath().substring(sourceData.getPath().length()));
									
									target.getParentFile().mkdirs();
									
									try {
										Files.copy(file.toPath(), target.toPath());
									} catch (final IOException exception) {
										exception.printStackTrace();
									}
								}
								
								return file.isDirectory() ? Control.ENTER : Control.CONTINUE;
							}
							
							private static final long serialVersionUID = 4487138895065647165L;
							
						});
					} else {
						try (final JarFile jarFile = new JarFile(applicationFile)) {
							for (final JarEntry entry : Tools.iterable(jarFile.entries())) {
								if (entry.getName().startsWith("data/") && !entry.isDirectory()) {
									final File target = new File(entry.getName());
									
									target.getParentFile().mkdirs();
									
									try (final InputStream input = jarFile.getInputStream(entry);
											final OutputStream output = new FileOutputStream(target)) {
										Tools.writeAndClose(input, false, output, false);
									}
								}
							}
						}
					}
					
					break;
				case MODEL_MAKER:
					window[0].dispose();
					ModelMaker.main(array());
					action = QUIT;
					break;
				case GENERATE_WSI:
					window[0].dispose();
					
					final GenerateWSIArguments arguments = new GenerateWSIArguments()
						.setModelPath(preferences.get("modelPath", pathOrEmpty("data/SYN_NB_01_001.xml")))
						.setRendererPath(preferences.get("rendererPath", pathOrEmpty("data/textures/he_renderer.xml")));
					
					SwingUtilities.invokeLater(() -> showEditDialog("GenerateWSI",
							() ->  actions.offer(GENERATE_WSI),
							() ->  actions.offer(QUIT),
							property("Model path:", arguments::getModelPath, arguments::setModelPath),
							property("Renderer path:", arguments::getRendererPath, arguments::setRendererPath)));
					
					switch (actions.take()) {
					case GENERATE_WSI:
						preferences.put("modelPath", arguments.getModelPath());
						preferences.put("rendererPath", arguments.getRendererPath());
						final AtomicBoolean abort = new AtomicBoolean(true);
						final JProgressBar progress = new JProgressBar();
						final Window abortWindow = show(progress, "GenerateWSI: close this window to abort", false);
						
						SwingUtilities.invokeLater(() -> {
							progress.setIndeterminate(true);
							
							abortWindow.addWindowListener(new WindowAdapter() {
								
								@Override
								public final void windowClosing(final WindowEvent event) {
									if (abort.get()) {
										System.exit(-1);
									}
								}
								
							});
						});
						
						GenerateWSI.main(array("model", arguments.getModelPath(), "renderer", arguments.getRendererPath()));
						
						abort.set(false);
						SwingUtilities.invokeLater(abortWindow::dispose);
						
						break;
					}
					
					action = QUIT;
					break;
				case VIEW_WSI:
					window[0].dispose();
					ZipSlideViewer.main(array());
					action = QUIT;
					break;
				}
			} while (action != QUIT);
			
			if (window[0] != null) {
				SwingUtilities.invokeLater(window[0]::dispose);
			}
		} catch (final Exception exception) {
			exception.printStackTrace();
			System.exit(-1);
		}
	}
	
	public static final String pathOrEmpty(final String path) {
		return new File(path).exists() ? path : "";
	}
	
	public static final JPanel action(final Queue<Integer> actions, final Window[] window, final String name, final int action) {
		return center(new JButton(new AbstractAction(name) {
			
			@Override
			public final void actionPerformed(final ActionEvent event) {
				actions.offer(action);
			}
			
			private static final long serialVersionUID = 7297715238510494272L;
			
		}));
	}
	
	public static final FileProcessor.Control process(final File file, final FileProcessor processor) {
		final FileProcessor.Control result = processor.file(file);
		
		if (FileProcessor.Control.ENTER.equals(result)) {
			final File[] subfiles = file.listFiles();
			
			if (subfiles != null) {
				for (final File subfile : subfiles) {
					if (!FileProcessor.Control.CONTINUE.equals(process(subfile, processor))) {
						return FileProcessor.Control.STOP;
					}
				}
			}
			
			return FileProcessor.Control.CONTINUE;
		}
		
		return result;
	}
	
	/**
	 * @author ga (creation 2015-01-28)
	 */
	public static abstract interface FileProcessor extends Serializable {
		
		public abstract Control file(File file);
		
		/**
		 * @author ga (creation 2015-01-28)
		 */
		public static enum Control {
			
			CONTINUE, ENTER, STOP; 
			
		}
		
	}
	
	/**
	 * @author ga (creation 2015-01-27)
	 */
	public static final class GenerateWSIArguments implements Serializable {
		
		private String modelPath = "";
		
		private String rendererPath = "";
		
		public final String getModelPath() {
			return this.modelPath;
		}
		
		public final GenerateWSIArguments setModelPath(final String modelPath) {
			this.modelPath = modelPath;
			
			return this;
		}
		
		public final String getRendererPath() {
			return this.rendererPath;
		}
		
		public final GenerateWSIArguments setRendererPath(final String rendererPath) {
			this.rendererPath = rendererPath;
			
			return this;
		}
		
		private static final long serialVersionUID = -7978128284612068612L;
		
	}
	
}
