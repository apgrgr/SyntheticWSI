package fr.unistra.wsi.synthetic;

import static imj2.tools.CommonSwingTools.center;
import static imj2.tools.CommonSwingTools.property;
import static imj2.tools.CommonSwingTools.showEditDialog;
import static net.sourceforge.aprog.swing.SwingTools.show;
import static net.sourceforge.aprog.swing.SwingTools.verticalBox;
import static net.sourceforge.aprog.tools.Tools.array;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.sourceforge.aprog.swing.SwingTools;
import net.sourceforge.aprog.tools.IllegalInstantiationException;

/**
 * @author greg (creation 2015-01-27)
 */
public final class Launcher {
	
	private Launcher() {
		throw new IllegalInstantiationException();
	}
	
	static final int QUIT = -1;
	
	static final int EXTRACT_EXAMPLE = 0;
	
	static final int MODEL_MAKER = 1;
	
	static final int GENERATE_WSI = 2;
	
	static final int VIEW_WSI = 3;
	
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
						action(actions, window, "Extract example", EXTRACT_EXAMPLE),
						action(actions, window, "ModelMaker", MODEL_MAKER),
						action(actions, window, "GenerateWSI", GENERATE_WSI),
						action(actions, window, "ViewWSI", VIEW_WSI)), "SyntheticWSI", false);
				
				window[0].addWindowListener(new WindowAdapter() {
					
					@Override
					public final void windowClosing(final WindowEvent event) {
						actions.offer(QUIT);
					}
					
				});
			}
			
		});
		
		switch (actions.take()) {
		case QUIT:
			break;
		case EXTRACT_EXAMPLE:
			// TODO
			break;
		case MODEL_MAKER:
			ModelMaker.main(array());
			break;
		case GENERATE_WSI:
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
				GenerateWSI.main(array("model", arguments.getModelPath(), "renderer", arguments.getRendererPath()));
				break;
			}
			
			break;
		case VIEW_WSI:
			// TODO
			break;
		}
	}
	
	public static final String pathOrEmpty(final String path) {
		return new File(path).exists() ? path : "";
	}
	
	public static final JPanel action(final Queue<Integer> actions, final Window[] window, final String name, final int action) {
		return center(new JButton(new AbstractAction(name) {
			
			@Override
			public final void actionPerformed(final ActionEvent event) {
				window[0].dispose();
				
				actions.offer(action);
			}
			
			private static final long serialVersionUID = 7297715238510494272L;
			
		}));
	}
	
	/**
	 * @author greg (creation 2015-01-27)
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
