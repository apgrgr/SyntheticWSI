package fr.unistra.wsi.synthetic;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;
import static net.sourceforge.aprog.tools.MathTools.square;
import static net.sourceforge.aprog.tools.Tools.cast;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * @author greg (creation 2014)
 */
public final class Region implements Serializable {
	
	private final Composite<Region> composite;
	
	private transient Area geometry;
	
	private Shape serializableGeometry;
	
	private String label;
	
	private int occurences;
	
	public Region(final Area geometry, final String label, final int occurences) {
		this.composite = new Composite<>(this);
		this.geometry = geometry;
		this.setLabel(label);
		this.setOccurences(occurences);
	}
	
	public final Region getParent() {
		final Composite<Region> parentComposite = this.composite.getParent();
		
		return parentComposite == null ? null : parentComposite.getObject();
	}
	
	public final Region setParent(final Region parent) {
		final Composite<Region> parentComposite = parent == null ? null : parent.composite;
		
		return this.composite.setParent(parentComposite).getObject();
	}
	
	public final List<Region> getChildren() {
		return this.composite.getChildren();
	}
	
	public final Area getGeometry() {
		return this.geometry;
	}
	
	public final double computeArea() {
		PathElement first = null;
		PathElement previous = null;
		double area = 0.0;
		
		for (final PathElement pathElement : Region.iterable(this.getGeometry().getPathIterator(null, 3.0))) {
			if (pathElement instanceof MoveTo) {
				previous = first = pathElement;
			} else if (pathElement instanceof ClosePath) {
				area += determinant(previous.getEndX(), previous.getEndY(), first.getEndX(), first.getEndY());
			} else {
				area += determinant(previous.getEndX(), previous.getEndY(), pathElement.getEndX(), pathElement.getEndY());
				
				previous = pathElement;
			}
		}
		
		return abs(area) / 2.0;
	}
	
	public final Point2D computeCenter() {
		double x = 0.0;
		double y = 0.0;
		double sum = 0.0;
		
		for (final PathElement pathElement : Region.iterable(this.getGeometry().getPathIterator(null, 3.0))) {
			final float endX = pathElement.getEndX();
			final float endY = pathElement.getEndY();
			
			if (Double.isFinite(endX) && Double.isFinite(endY)) {
				x += endX;
				y += endY;
				++sum;
			}
		}
		
		if (sum == 0.0) {
			sum = 1.0;
		}
		
		return new Point2D.Double(x / sum, y / sum);
	}
	
	public final Region simplify(final float minimumArcLength) {
		final List<PathElement> pathElements = new ArrayList<>();
		final float[] segment = new float[6];
		final PathIterator pathIterator = this.getGeometry().getPathIterator(null);
		final Path2D.Float path = new Path2D.Float(pathIterator.getWindingRule());
		
		while (!pathIterator.isDone()) {
			pathElements.add(getPathElement(pathIterator, segment));
			pathIterator.next();
		}
		
		final int n = pathElements.size();
		
		if (n < 1) {
			new IllegalStateException().printStackTrace();
			
			return this;
		}
		
		final boolean[] removed = new boolean[n];
		final class IndexedElement implements Serializable, Comparable<IndexedElement> {
			
			private final int index;
			
			public IndexedElement(final int index) {
				this.index = index;
			}
			
			public final int getIndex() {
				return this.index;
			}
			
			public final float getLength() {
				int j = (n + this.getIndex() - 1) % n;
				
				while (removed[j]) {
					j = (n + j - 1) % n;
				}
				
				return pathElements.get(this.getIndex()).getLengthFrom(pathElements.get(j));
			}
			
			@Override
			public final int compareTo(final IndexedElement that) {
				return Float.compare(this.getLength(), that.getLength());
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = -3387465139530591036L;
			
		}
		
		final PriorityQueue<IndexedElement> queue = new PriorityQueue<>(n);
		
		for (int i = 0; i < n; ++i) {
			final PathElement element = pathElements.get(i);
			
			if (!(element instanceof ClosePath || element instanceof MoveTo)) {
				queue.add(new IndexedElement(i));
			}
		}
		
		while (3 < queue.size() && queue.peek().getLength() < minimumArcLength) {
			removed[queue.remove().getIndex()] = true;
		}
		
		for (int i = 0; i < n; ++i) {
			if (!removed[i]) {
				pathElements.get(i).update(path);
			}
		}
		
		this.geometry = new Area(path);
		
		return this;
	}
	
	public final String getLabel() {
		return this.label;
	}
	
	public final Region setLabel(final String label) {
		this.label = label == null ? "" : label;
		
		return this;
	}
	
	public final int getOccurences() {
		return this.occurences;
	}
	
	public final Region setOccurences(final int occurences) {
		this.occurences = occurences;
		
		return this;
	}
	
	@Override
	public final int hashCode() {
		return this.getGeometry().getBounds().hashCode() + this.getLabel().hashCode();
	}
	
	@Override
	public final boolean equals(final Object object) {
		final Region that = cast(this.getClass(), object);
		
		return that != null && this.getGeometry().equals(that.getGeometry()) && this.getLabel().equals(that.getLabel());
	}
	
	private final Object readResolve() throws ObjectStreamException {
		this.geometry = new Area(this.serializableGeometry);
		
		return this;
	}
	
	private final Object writeReplace() throws ObjectStreamException {
		this.serializableGeometry = new AffineTransform().createTransformedShape(this.getGeometry());
		
		return this;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 4928535227586298238L;
	
	public static final double determinant(final double a, final double b, final double c, final double d) {
		return a * d - b * c;
	}
	
	public static final float length(final float x1, final float y1, final float x2, final float y2) {
		return length(x2 - x1, y2 - y1);
	}
	
	public static final float length(final float x, final float y) {
		return (float) sqrt(square(x) + square(y));
	}
	
	public static final PathElement getPathElement(final PathIterator iterator, final float[] segment) {
		return newPathElement(iterator.currentSegment(segment), segment);
	}
	
	public static final PathElement newPathElement(final int type, final float[] segment) {
		switch (type) {
			case PathIterator.SEG_CLOSE:
				return new ClosePath();
			case PathIterator.SEG_CUBICTO:
				return new CurveTo(segment);
			case PathIterator.SEG_LINETO:
				return new LineTo(segment);
			case PathIterator.SEG_MOVETO:
				return new MoveTo(segment);
			case PathIterator.SEG_QUADTO:
				return new QuadTo(segment);
		}
		
		throw new IllegalArgumentException();
	}
	
	public static final PathElement newPathElement(final String type, final float[] segment) {
		switch (type) {
			case ClosePath.TYPE:
				return new ClosePath();
			case CurveTo.TYPE:
				return new CurveTo(segment);
			case LineTo.TYPE:
				return new LineTo(segment);
			case MoveTo.TYPE:
				return new MoveTo(segment);
			case QuadTo.TYPE:
				return new QuadTo(segment);
		}
		
		throw new IllegalArgumentException();
	}
	
	public static final Iterable<PathElement> iterable(final PathIterator iterator) {
		return new Iterable<PathElement>() {
			
			@Override
			public final Iterator<PathElement> iterator() {
				return new Iterator<PathElement>() {
					
					private final float[] segment = new float[6];
					
					@Override
					public final PathElement next() {
						final PathElement result = Region.getPathElement(iterator, this.segment);
						
						if (this.hasNext()) {
							iterator.next();
						}
						
						return result;
					}
					
					@Override
					public final boolean hasNext() {
						return !iterator.isDone();
					}
					
				};
			}
			
		};
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static abstract interface PathElement extends Serializable {
		
		public abstract void update(Path2D path);
		
		public abstract float getEndX();
		
		public abstract float getEndY();
		
		public abstract float getLengthFrom(PathElement previous);
		
		public abstract float[] getParameters();
		
		public abstract String getType();
		
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class ClosePath implements PathElement {
		
		@Override
		public final void update(final Path2D path) {
			path.closePath();
		}
		
		@Override
		public final float getEndX() {
			return Float.NaN;
		}
		
		@Override
		public final float getEndY() {
			return Float.NaN;
		}
		
		@Override
		public final float getLengthFrom(final PathElement previous) {
			return 0F;
		}
		
		@Override
		public final float[] getParameters() {
			return PARAMETERS;
		}
		
		@Override
		public final String getType() {
			return TYPE;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -8327249273581066144L;
		
		private static final float[] PARAMETERS = {};
		
		/**
		 * {@value}.
		 */
		public static final String TYPE = "closePath";
		
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class MoveTo implements PathElement {
		
		private final float x;
		
		private final float y;
		
		public MoveTo(final float[] segment) {
			this(segment[0], segment[1]);
		}
		
		public MoveTo(final float x, final float y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public final void update(final Path2D path) {
			path.moveTo(this.x, this.y);
		}
		
		@Override
		public final float getEndX() {
			return this.x;
		}
		
		@Override
		public final float getEndY() {
			return this.y;
		}
		
		@Override
		public final float getLengthFrom(final PathElement previous) {
			return 0F;
		}
		
		@Override
		public final float[] getParameters() {
			return new float[] { this.x, this.y };
		}
		
		@Override
		public final String getType() {
			return TYPE;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -5303924812398249561L;
		
		/**
		 * {@value}.
		 */
		public static final String TYPE = "move";
		
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class LineTo implements PathElement {
		
		private final float x;
		
		private final float y;
		
		public LineTo(final float[] segment) {
			this(segment[0], segment[1]);
		}
		
		public LineTo(final float x, final float y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public final void update(final Path2D path) {
			path.lineTo(this.x, this.y);
		}
		
		@Override
		public final float getEndX() {
			return this.x;
		}
		
		@Override
		public final float getEndY() {
			return this.y;
		}
		
		@Override
		public final float getLengthFrom(final PathElement previous) {
			return length(previous.getEndX(), previous.getEndY(), this.getEndX(), this.getEndY());
		}
		
		@Override
		public final float[] getParameters() {
			return new float[] { this.x, this.y };
		}
		
		@Override
		public final String getType() {
			return TYPE;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = -8913039760717772217L;
		
		/**
		 * {@value}.
		 */
		public static final String TYPE = "line";
		
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class QuadTo implements PathElement {
		
		private final float x1;
		
		private final float y1;
		
		private final float x2;
		
		private final float y2;
		
		public QuadTo(final float[] segment) {
			this(segment[0], segment[1], segment[2], segment[3]);
		}
		
		public QuadTo(final float x1, final float y1, final float x2, final float y2) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}
		
		@Override
		public final void update(final Path2D path) {
			path.quadTo(this.x1, this.y1, this.x2, this.y2);
		}
		
		@Override
		public final float getEndX() {
			return this.x2;
		}
		
		@Override
		public final float getEndY() {
			return this.y2;
		}
		
		@Override
		public final float getLengthFrom(final PathElement previous) {
			return 0.7F * (length(previous.getEndX(), previous.getEndY(), this.x1, this.y1) + length(this.x1, this.y1, this.getEndX(), this.getEndY()));
		}
		
		@Override
		public final float[] getParameters() {
			return new float[] { this.x1, this.y1, this.getEndX(), this.getEndY() };
		}
		
		@Override
		public final String getType() {
			return TYPE;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 2580429586679770153L;
		
		/**
		 * {@value}.
		 */
		public static final String TYPE = "quad";
		
	}
	
	/**
	 * @author greg (creation 2014-09-09)
	 */
	public static final class CurveTo implements PathElement {
		
		private final float x1;
		
		private final float y1;
		
		private final float x2;
		
		private final float y2;
		
		private final float x3;
		
		private final float y3;
		
		public CurveTo(final float[] segment) {
			this(segment[0], segment[1], segment[2], segment[3], segment[4], segment[5]);
		}
		
		public CurveTo(final float x1, final float y1, final float x2, final float y2, final float x3, final float y3) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.x3 = x3;
			this.y3 = y3;
		}
		
		@Override
		public final void update(final Path2D path) {
			path.curveTo(this.x1, this.y1, this.x2, this.y2, this.x3, this.y3);
		}
		
		@Override
		public final float getEndX() {
			return this.x3;
		}
		
		@Override
		public final float getEndY() {
			return this.y3;
		}
		
		@Override
		public final float getLengthFrom(final PathElement previous) {
			return 0.5F * (length(previous.getEndX(), previous.getEndY(), this.x1, this.y1)
					+ length(this.x1, this.y1, this.x2, this.y2)
					+ length(this.x2, this.y2, this.getEndX(), this.getEndY()));
		}
		
		@Override
		public final float[] getParameters() {
			return new float[] { this.x1, this.y1, this.x2, this.y2, this.getEndX(), this.getEndY() };
		}
		
		@Override
		public final String getType() {
			return TYPE;
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 3054151236275682347L;
		
		/**
		 * {@value}.
		 */
		public static final String TYPE = "curve";
		
	}
	
}