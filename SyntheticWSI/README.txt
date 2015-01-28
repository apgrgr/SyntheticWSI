SyntheticWSI v201501281540

	Requirements
	
		Java 8

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
		
		* GenerateWSI
			Start the tool to generate a WSI (format = zipped JPG tiles)
		
		* ViewWSI
			Start the tool to visualize WSIs (drag and drop images from OS to open them)
