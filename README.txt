SyntheticWSI v201501300946

	Requirements
	
		Java 8+

	Quick start
	
		1. Start the graphical interface (double-click or left-click and find "run as Java application" or "open with Java")
		2. Click "Extract Example" (check that it generates a directory data/ with images and XML files)
		3. Click "GenerateWSI"; the options are the paths of the model file and the renderer specification file
		   (default values should point to files inside data/); click "Ok"
		4. Wait... (depending on the hardware, it can be less than 30 min or more than 2h)
		5. If all goes well, the previous step should:
			* generate a temporary directory with synthetic tiles and a corresponding ZIP archive:
			  the directory can be deleted, the WSI is the ZIP file
			* generate a temporary file named renderers.jo: this contains the WSI description ("perfect ground truth")
			  but is currently only used as an intermediate step in the WSI generation
			* display the generated WSI

	Possible issues
	
		* Not enough memory allocated to the program
		  Solution: use the command line to start the application with more memory:
		            java -Xmx16g -jar syntheticwsi.jar

	Graphical interface
	
		* README
			Show this file.
		
		* Extract Example
			Extract example files into the subdirectory data/ in the current directory
		
		* ModelMaker
			Start the tool to make "models" = 2D digital phantoms
			Ctrl+N: new texture/model
			Ctrl+O: open a texture/model file (XML) or load a background image (JPG, PNG, SVS)
			Ctrl+S: save the current texture/model
			Ctrl+C: copy current view to OS clipboard
			B: show/hide background image
			+/-: zoom in/out
			Ctrl+Left click: start new region
			Drag motion with left mouse button: extend current region
			Shift+Drag motion with left mouse button: reduce current region
			Right click: select texture
			(Mac OS X: use Cmd instead of Ctrl)
		
		* GenerateWSI
			Start the tool to generate a WSI (format = zipped JPG tiles)
		
		* ViewWSI
			Start the tool to visualize WSIs (drag and drop images from OS to open them)

	Command line interface
	
		* ModelMaker
			java -cp syntheticwsi.jar fr.unistra.wsi.synthetic.ModelMaker
		
		* GenerateWSI
			java -cp syntheticwsi.jar fr.unistra.wsi.synthetic.GenerateWSI
				[model <file path>] [renderer <file path>] [output <file path>]
				[tileWidth <integer>] [tileHeight <integer>] [show <0|1>]
		
		* ViewWSI
			java -cp syntheticwsi.jar imj2.zipslideviewer.ZipSlideViewer [file <file path>]
