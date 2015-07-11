package fr.unistra.wsi.synthetic;

import static multij.tools.Tools.*;
import static multij.xml.XMLTools.parse;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import multij.tools.CommandLineArgumentsParser;
import multij.tools.IllegalInstantiationException;
import multij.tools.Tools;
import multij.xml.XMLTools;

/**
 * @author ga (creation 2015-07-11)
 */
public final class Model2SVG {
	
	private Model2SVG() {
		throw new IllegalInstantiationException();
	}
	
	private static final AffineTransform IDENTITY = new AffineTransform();
	
	/**
	 * @param commandLineArguments
	 * <br>Must not be null
	 */
	public static final void main(final String[] commandLineArguments) {
		final CommandLineArgumentsParser arguments = new CommandLineArgumentsParser(commandLineArguments);
		final File modelFile = new File(arguments.get("model",
				ModelMaker.preferences.get(ModelMaker.MODEL_FILE_KEY, ModelMaker.MODEL_FILE_DEFAULT_PATH)));
		final String outputBase = arguments.get("output", baseName(modelFile.getPath()));
		final Model model = ModelMaker.readModel(modelFile);
		final File outputFile = new File(outputBase + ".svg");
		final String[] labels = Arrays.stream(arguments.get("labels", "").split(",")).filter(s -> !s.trim().isEmpty()).toArray(String[]::new);
		final String[] classIds = Arrays.stream(arguments.get("classIds", Tools.join(",", intRange(labels.length))).split(",")).filter(s -> !s.trim().isEmpty()).toArray(String[]::new);
		
		debugPrint(modelFile, "->", outputFile);
		debugPrint("regionCount:", model.getRegions().size());
		debugPrint("availableLabels:", model.getRegions().stream().map(Region::getLabel).collect(Collectors.toSet()));
		
		{
			final int[] indices = intRange(labels.length);
			
			sort(indices, (i1, i2) -> labels[i1].compareTo(labels[i2]));
			
			final String[] labelsCopy = Arrays.copyOf(labels, labels.length);
			final String[] classIdsCopy = Arrays.copyOf(classIds, labels.length);
			
			for (int i = 0; i < labels.length; ++i) {
				labels[i] = labelsCopy[indices[i]];
				classIds[i] = classIdsCopy[indices[i]];
			}
		}
		
		final Rectangle bounds = model.getBounds();
		debugPrint("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:imj=\"IMJ\" width=\"" + (bounds.width + 1) + "\" height=\"" + (bounds.height + 1) + "\">");
		final Document svg = parse("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:imj=\"IMJ\" width=\"" + (bounds.width + 1) + "\" height=\"" + (bounds.height + 1) + "\"/>");
		
		for (final Region region : model.getRegions()) {
			final int i = Arrays.binarySearch(labels, region.getLabel());
			
			if (labels.length == 0 || 0 <= i) {
				addTo(svg, region.getGeometry(), ModelMaker.labelColors.get(region.getLabel()).getRGB(), 0 <= i ? classIds[i] : region.getLabel());
			}
		}
		
		debugPrint("Writing", outputFile);
		XMLTools.write(svg, outputFile, 1);
		
		debugPrint("Done");
	}
	
	public static final void addTo(final Document svg, final Shape s, final int rgb, final String classId) {
		final Element svgRoot = svg.getDocumentElement();
		final StringBuilder pathData = new StringBuilder();
		final double[] segment = new double[6];
		
		for (final PathIterator pathIterator = s.getPathIterator(IDENTITY);
				!pathIterator.isDone(); pathIterator.next()) {
			final int segmentType = pathIterator.currentSegment(segment);
			
			switch (segmentType) {
			case PathIterator.SEG_CLOSE:
				pathData.append('Z');
				break;
			case PathIterator.SEG_CUBICTO:
				pathData.append('C');
				pathData.append(join(" ", segment, 6));
				break;
			case PathIterator.SEG_LINETO:
				pathData.append('L');
				pathData.append(join(" ", segment, 2));
				break;
			case PathIterator.SEG_MOVETO:
				pathData.append('M');
				pathData.append(join(" ", segment, 2));
				break;
			case PathIterator.SEG_QUADTO:
				pathData.append('Q');
				pathData.append(join(" ", segment, 4));
				break;
			default:
				debugError("Unhandled segment type:", segmentType);
			}
		}
		
		final Element svgRegion = (Element) svgRoot.appendChild(svg.createElement("path"));
		
		svgRegion.setAttribute("d", pathData.toString());
		svgRegion.setAttribute("style", "fill:" + formatColor(rgb));
		svgRegion.setAttribute("imj:classId", classId);
	}
	
	public static final String formatColor(final long color) {
		return "#" + String.format("%06X", color & 0x00FFFFFF);
	}
	
	public static final String join(final String separator, final double[] array, final int n) {
		final StringBuilder resultBuilder = new StringBuilder();
		
		if (0 < n) {
			resultBuilder.append(array[0]);
			
			for (int i = 1; i < n; ++i) {
				resultBuilder.append(separator);
				resultBuilder.append(array[i]);
			}
		}
		
		return resultBuilder.toString();
	}
	
}
