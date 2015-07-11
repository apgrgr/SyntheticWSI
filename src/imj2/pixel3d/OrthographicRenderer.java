package imj2.pixel3d;

import static java.lang.Math.min;
import static java.util.Arrays.copyOf;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.Serializable;

import multij.tools.Factory.DefaultFactory;

/**
 * @author codistmonk (creation 2014-04-27)
 */
public final class OrthographicRenderer implements Renderer {
	
	private final IntComparator comparator;
	
	private BufferedImage canvas;
	
	private int[] indices;
	
	private int[] pixels;
	
	private float[] zValues;
	
	private int[] colors;
	
	private int pixelCount;
	
	public OrthographicRenderer() {
		this.comparator = new IntComparator() {
			
			@Override
			public final int compare(final int index1, final int index2) {
				final float z1 = OrthographicRenderer.this.getZValue(index1);
				final float z2 = OrthographicRenderer.this.getZValue(index2);
				
				int result = Float.compare(z1, z2);
				
				if (result == 0) {
					result = index1 - index2;
				}
				
				return result;
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 1379706747335956894L;
			
		};
		this.indices = new int[1];
		this.pixels = new int[1];
		this.zValues = new float[1];
		this.colors = new int[1];
	}
	
	@Override
	public final OrthographicRenderer setCanvas(final BufferedImage canvas) {
		if (canvas.getType() != BufferedImage.TYPE_INT_ARGB && canvas.getType() != BufferedImage.TYPE_INT_RGB) {
			throw new IllegalArgumentException();
		}
		
		if (this.canvas != null) {
			final int oldW = this.canvas.getWidth();
			final int oldH = this.canvas.getHeight();
			final int newW = canvas.getWidth();
			final int newH = canvas.getHeight();
			
			if (oldW != newW || newH < oldH) {
				this.clear();
			}
		}
		
		this.canvas = canvas;
		
		return this;
	}
	
	public final OrthographicRenderer reserve(final int n) {
		if (this.pixels.length < n) {
			this.indices = copyOf(this.indices, n);
			this.pixels = copyOf(this.pixels, n);
			this.zValues = copyOf(this.zValues, n);
			this.colors = copyOf(this.colors, n);
		}
		
		return this;
	}
	
	public final void beforeAdd() {
		if (this.pixels.length <= this.pixelCount) {
			this.reserve((int) min(Integer.MAX_VALUE, 2L * (this.pixelCount + 1L)));
		}
	}
	
	@Override
	public final void clear() {
		this.pixelCount = 0;
	}
	
	@Override
	public final OrthographicRenderer addPixel(final double x, final double y, final double z, final int argb) {
		final int w = this.canvas.getWidth();
		final int h = this.canvas.getHeight();
		
		if (x < 0.0 || w <= x || y < 0.0 || h <= y) {
			return this;
		}
		
		this.beforeAdd();
		
		final int pixel = (h - 1 - (int) y) * w + (int) x;
		
		this.indices[this.pixelCount] = this.pixelCount;
		this.pixels[this.pixelCount] = pixel;
		this.zValues[this.pixelCount] = (float) z;
		this.colors[this.pixelCount] = argb;
		
		++this.pixelCount;
		
		return this;
	}
	
	@Override
	public final void render() {
		final int n = this.pixelCount;
		
		dualPivotQuicksort(this.indices, 0, n, this.comparator);
		
		final DataBuffer dataBuffer = this.canvas.getRaster().getDataBuffer();
		
		for (int i = 0; i < n; ++i) {
			final int index = this.indices[i];
			final int pixel = this.pixels[index];
			final int previousRGB = dataBuffer.getElem(pixel);
			final int argb = this.colors[index];
			
			dataBuffer.setElem(pixel, overlay(previousRGB, argb));
		}
	}
	
	final int getPixel(final int index) {
		return this.pixels[index];
	}
	
	final float getZValue(final int index) {
		return this.zValues[index];
	}
	
	final int getColor(final int index) {
		return this.colors[index];
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -6618739135450612928L;
	
	/**
	 * {@value}.
	 */
	public static final int A = 0xFF000000;
	
	/**
	 * {@value}.
	 */
	public static final int R = 0x00FF0000;
	
	/**
	 * {@value}.
	 */
	public static final int G = 0x0000FF00;
	
	/**
	 * {@value}.
	 */
	public static final int B = 0x000000FF;
	
	public static final DefaultFactory<OrthographicRenderer> FACTORY = DefaultFactory.forClass(OrthographicRenderer.class);
	
	public static final void checkSorted(final int[] values, final IntComparator comparator) {
		final int n = values.length - 1;
		
		for (int i = 0; i < n; ++i) {
			if (0 < comparator.compare(values[i], values[i + 1])) {
				throw new RuntimeException("element(" + i + ") > element(" + (i + 1) + ")");
			}
		}
	}
	
	public static final void dualPivotQuicksort(final int[] values, final int start, final int end, final IntComparator comparator) {
		DualPivotQuicksort.sort(values, start, end - 1, null, 0, 0, comparator);
	}
	
	public static final int overlay(final int previousRGB, final int argb) {
		final int alpha = argb >>> 24;
		final int beta = 255 - alpha;
		final int red = (((argb & R) * alpha + (previousRGB & R) * beta) / 255) & R;
		final int green = (((argb & G) * alpha + (previousRGB & G) * beta) / 255) & G;
		final int blue = (((argb & B) * alpha + (previousRGB & B) * beta) / 255) & B;
		
		return 0xFF000000 | red | green | blue;
	}
	
	/**
	 * @author codistmonk (creation 2014-04-29)
	 */
	public static abstract interface IntComparator extends Serializable {
		
		public abstract int compare(int value1, int value2);
		
		/**
		 * @author codistmonk (creation 2014-05-23)
		 */
		public static final class Default implements IntComparator {
			
			@Override
			public final int compare(final int value1, final int value2) {
				return value1 < value2 ? -1 : value1 == value2 ? 0 : 1;
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 2338544738825140920L;
			
			public static final Default INSTANCE = new Default();
			
		}
		
	}
	
}
