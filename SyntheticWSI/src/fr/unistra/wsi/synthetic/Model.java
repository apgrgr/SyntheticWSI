package fr.unistra.wsi.synthetic;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.util.stream.Collectors.toList;
import static net.sourceforge.aprog.tools.Tools.readObject;
import static net.sourceforge.aprog.tools.Tools.unchecked;
import static net.sourceforge.aprog.tools.Tools.writeObject;
import imj2.core.Image2D;
import imj2.tools.LociBackedImage;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.sourceforge.aprog.tools.Tools;
import net.sourceforge.aprog.xml.XMLTools;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import fr.unistra.wsi.synthetic.Region.PathElement;

/**
 * @author ga (creation 2014-09-06)
 */
public final class Model implements Serializable {
	
	private String filePath;
	
	private String imageRelativePath;
	
	private transient Image2D image;
	
	private transient Area tool;
	
	private String newRegionLabel = "H&E stroma";
	
	private final AtomicBoolean toolVisible = new AtomicBoolean();
	
	private final AtomicInteger activeRegionIndex = new AtomicInteger(-1);
	
	private final List<Region> regions = new ArrayList<>();
	
	public final File getFile() {
		return new File(this.filePath);
	}
	
	public final Model open(final File file) {
		final Model model;
		
		if (file.getName().endsWith(".xml")) {
			try (final InputStream input = new FileInputStream(file)) {
				model = fromXML(XMLTools.parse(input));
			} catch (Exception exception) {
				Tools.debugError(file);
				throw unchecked(exception);
			}
		} else {
			model = readObject(file.getPath());
		}
		
		this.filePath = file.getPath();
		this.setImageRelativePath(model.getImageRelativePath());
		this.image = null;
		this.getActiveRegionIndex().set(-1);
		this.getRegions().clear();
		this.getRegions().addAll(model.getRegions());
		
		return this;
	}
	
	public final Rectangle getBounds() {
		final Rectangle result = new Rectangle();
		final Image2D image = this.getImage(1.0);
		
		if (image != null) {
			result.width = image.getWidth();
			result.height = image.getHeight();
		}
		
		this.getRegions().forEach(c -> result.add(c.getGeometry().getBounds()));
		
		return result;
	}
	
	public final Model sortRegions() {
		Collections.sort(this.getRegions(), (r1, r2) -> Double.compare(r2.computeArea(), r1.computeArea()));
		
		return this;
	}
	
	public final Model save() {
		this.setImageFile(this.getImageFile());
		
		this.sortRegions();
		
		if (this.getFile().getName().endsWith(".xml")) {
			XMLTools.write(toXML(this), this.getFile(), 0);
		} else {
			writeObject(this, this.getFile().getPath());
		}
		
		return this;
	}
	
	public final Model setFile(final File file) {
		if (this.filePath != null && this.imageRelativePath != null) {
			final File imageFile = this.getImageFile();
			
			this.filePath = file.getPath();
			
			this.setImageFile(imageFile);
		} else {
			this.filePath = file.getPath();
		}
		
		return this;
	}
	
	public final String getImageRelativePath() {
		return this.imageRelativePath == null ? "" : this.imageRelativePath;
	}
	
	public final void setImageRelativePath(final String imageRelativePath) {
		this.imageRelativePath = imageRelativePath == null ? "" : imageRelativePath;
	}
	
	public final Model setImageFile(final File imageFile) {
		try {
			final File oldImageFile = this.getImageFile();
			
			if (imageFile != null) {
				final Path modelPath = Paths.get(this.getFile().getCanonicalFile().getParentFile().toURI());
				final Path imagePath = Paths.get(imageFile.getCanonicalFile().toURI());
				this.setImageRelativePath(modelPath.relativize(imagePath).toString());
				Tools.debugPrint(modelPath);
				Tools.debugPrint(imageFile);
				Tools.debugPrint(imagePath);
				Tools.debugPrint(this.imageRelativePath);
			} else {
				this.setImageRelativePath("");
			}
			
			if (!Tools.equals(imageFile, oldImageFile)) {
				this.image = null;
			}
		} catch (final IOException exception) {
			throw unchecked(exception);
		}
		
		return this;
	}
	
	public final File getImageFile() {
		return this.getImageRelativePath().isEmpty() ? null : new File(this.getFile().getParent(), this.getImageRelativePath());
	}
	
	public final Image2D getImage(final double scale) {
		try {
			if (this.image == null && !this.getImageRelativePath().isEmpty()) {
				this.image = new LociBackedImage(this.getImageFile().getPath());
				
				for (int lod = 0; lod < 7; ++lod) {
					this.image.getLODImage(lod);
				}
			}
		} catch (final Exception exception) {
			if (getRootCause(exception) instanceof FileNotFoundException) {
				Tools.debugError(getRootCause(exception));
			} else {
				exception.printStackTrace();
			}
		}
		
		if (this.image != null) {
			final int lod = max(0, (int) (-log(scale) / log(2.0)));
			
			if (this.image.getLOD() != lod) {
				this.image = this.image.getLODImage(lod);
			}
		}
		
		return this.image;
	}
	
	public final Area getTool() {
		if (this.tool == null) {
			this.tool = new Area(new Ellipse2D.Float(0F, 0F, 64F, 64F));
		}
		
		return this.tool;
	}
	
	public final String getNewRegionLabel() {
		return this.newRegionLabel;
	}
	
	public final void setNewRegionLabel(final String newRegionLabel) {
		this.newRegionLabel = newRegionLabel;
	}
	
	public final AtomicBoolean getToolVisible() {
		return this.toolVisible;
	}
	
	public final AtomicInteger getActiveRegionIndex() {
		return this.activeRegionIndex;
	}
	
	public final List<Region> getRegions() {
		return this.regions;
	}
	
	private transient Map<Float, List<Region>> cache;
	
	public final List<Region> getRegions(final double scale) {
		if (scale == 1.0) {
			return this.getRegions();
		}
		
		if (this.cache == null) {
			this.cache = new HashMap<>();
		}
		
		final float minimumArcLength = 1F / (float) scale;
		List<Region> result = this.cache.get(minimumArcLength);
		
		if (result == null) {
			result = this.getRegions().stream().map(r ->  new Region(new Area(r.getGeometry()), r.getLabel(), r.getOccurences()).simplify(minimumArcLength)).collect(toList());
			this.cache.put(minimumArcLength, result);
		}
		
		return result;
	}
	
	public final Region getActiveRegion() {
		final int index = this.getActiveRegionIndex().get();
		
		return index < 0 ? null : this.getRegions().get(index);
	}
	
	public final void clearCache() {
		if (this.cache != null) {
			this.cache.clear();
		}
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -4459968950008736912L;
	
	/**
	 * @author ga (creation 2014-09-11)
	 */
	public static final class DOMBuilder implements Serializable {
		
		private final Document result;
		
		private Element context;
		
		public DOMBuilder(final String documentElementName) {
			this.result = XMLTools.parse("<" + documentElementName + "/>");
			this.context = this.result.getDocumentElement();
		}
		
		public final Model.DOMBuilder begin(final String elementName) {
			final Element newElement = this.result.createElement(elementName);
			
			this.context.appendChild(newElement);
			
			this.context = newElement;
			
			return this;
		}
		
		public final Model.DOMBuilder text(final Object text) {
			this.context.appendChild(this.result.createTextNode("" + text));
			
			return this;
		}
		
		public final Model.DOMBuilder attribute(final String name, final Object value) {
			this.context.setAttribute(name, "" + value);
			
			return this;
		}
		
		public final Model.DOMBuilder end() {
			this.context = (Element) this.context.getParentNode();
			
			return this;
		}
		
		public final Document build() {
			return this.result;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -949763478948677339L;
		
	}
	
	public static final Document toXML(final Model model) {
		final Map<Object, Integer> ids = new IdentityHashMap<>();
		final Model.DOMBuilder resultBuilder = new DOMBuilder("model");
		
		ids.put(null, -1);
		
		resultBuilder.attribute("imageRelativePath", model.getImageRelativePath());
		resultBuilder.attribute("newRegionLabel", model.getNewRegionLabel());
		resultBuilder.attribute("toolVisible", model.getToolVisible());
		resultBuilder.attribute("activeRegionIndex", model.getActiveRegionIndex());
		resultBuilder.begin("regions");
		
		for (final Region region : model.getRegions()) {
			ids.put(region, ids.size());
		}
		
		for (final Region region : model.getRegions()) {
			final int id = ids.get(region);
			
			resultBuilder.begin("region");
			
			resultBuilder.attribute("id", id);
			resultBuilder.attribute("parent", ids.get(region.getParent()));
			resultBuilder.attribute("label", region.getLabel());
			
			final PathIterator pathIterator = region.getGeometry().getPathIterator(null);
			
			resultBuilder.attribute("windingRule", pathIterator.getWindingRule());
			
			for (final PathElement pathElement : Region.iterable(pathIterator)) {
				resultBuilder.begin(pathElement.getType());
				
				final String parameters = join(" ", pathElement.getParameters());
				
				if (!parameters.isEmpty()) {
					resultBuilder.attribute("to", parameters);
				}
				
				resultBuilder.end();
			}
			
			resultBuilder.end();
		}
		
		resultBuilder.end();
		
		return resultBuilder.build();
	}
	
	public static final String join(final String separator, final float... values) {
		final int n = values.length;
		final StringBuilder resultBuilder = new StringBuilder();
		
		if (0 < n) {
			resultBuilder.append(values[0]);
			
			for (int i = 1; i < n; ++i) {
				resultBuilder.append(separator).append(values[i]);
			}
		}
		
		return resultBuilder.toString();
	}
	
	public static final Model fromXML(final Document xml) {
		final Map<Integer, Object> ids = new HashMap<>();
		final Model result = new Model();
		final Element modelNode = xml.getDocumentElement();
		final float[] segment = new float[6];
		
		result.setImageRelativePath(modelNode.getAttribute("imageRelativePath"));
		result.setNewRegionLabel(modelNode.getAttribute("newRegionLabel"));
		result.getToolVisible().set(parseBoolean(modelNode.getAttribute("toolVisible")));
		result.getActiveRegionIndex().set(parseInt(modelNode.getAttribute("activeRegionIndex")));
		
		for (final Node regionNode : XMLTools.getNodes(modelNode, "regions/region")) {
			final String idString = ((Element) regionNode).getAttribute("id");
			final int id = idString.isEmpty() ? -1 : parseInt(idString);
			final int windingRule = parseInt(((Element) regionNode).getAttribute("windingRule"));
			final String label = ((Element) regionNode).getAttribute("label");
			final String occurencesString = ((Element) regionNode).getAttribute("occurences");
			final int occurences = occurencesString.isEmpty() ? 1 : parseInt(occurencesString);
			final Path2D.Float path = new Path2D.Float(windingRule);
			
			for (final Node pathElementNode : XMLTools.getNodes(regionNode, "*")) {
				final String type = pathElementNode.getNodeName();
				final String[] values = ((Element) pathElementNode).getAttribute("to").split("\\s+");
				final int n = values.length;
				
				for (int i = 0; i < n; ++i) {
					if (!values[i].isEmpty()) {
						segment[i] = parseFloat(values[i]);
					}
				}
				
				Region.newPathElement(type, segment).update(path);
			}
			
			final Region region = new Region(new Area(path), label, occurences);
			
			result.getRegions().add(region);
			
			if (!idString.isEmpty()) {
				ids.put(id, region);
			}
		}
		
		if (!ids.isEmpty()) {
			for (final Node regionNode : XMLTools.getNodes(modelNode, "regions/region")) {
				final int id = parseInt(((Element) regionNode).getAttribute("id"));
				final int parentId = parseInt(((Element) regionNode).getAttribute("parent"));
				
				final Region region = (Region) ids.get(id);
				final Region parent = (Region) ids.get(parentId);
				
				region.setParent(parent);
			}
		}
		
		return result.sortRegions();
	}
	
	public static final Throwable getRootCause(final Throwable throwable) {
		return throwable.getCause() == null ? throwable : getRootCause(throwable.getCause());
	}
	
}