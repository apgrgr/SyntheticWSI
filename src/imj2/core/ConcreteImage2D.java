package imj2.core;

import static net.sourceforge.aprog.tools.Tools.cast;

/**
 * @author codistmonk (creation 2013-08-04)
 */
public final class ConcreteImage2D<Source extends Image> extends Image.Abstract implements Image2D {
	
	private final Source source;
	
	private final int width;
	
	private final int height;
	
	public ConcreteImage2D(final Source source, final int width, final int height) {
		if (source.getPixelCount() != (long) width * height) {
			throw new IllegalArgumentException();
		}
		
		this.source = source;
		this.width = width;
		this.height = height;
	}
	
	@Override
	public final Source getSource() {
		return this.source;
	}
	
	@Override
	public final int getLOD() {
		return 0;
	}
	
	@Override
	public final Image2D getLODImage(final int lod) {
		if (lod <= 0) {
			return this;
		}
		
		return new SubsampledImage2D(this);
	}
	
	@Override
	public final String getId() {
		return this.getSource().getId();
	}
	
	@Override
	public final long getPixelCount() {
		return this.getSource().getPixelCount();
	}
	
	@Override
	public final Channels getChannels() {
		return this.getSource().getChannels();
	}
	
	@Override
	public final int getPixelValue(final long pixelIndex) {
		return this.getSource().getPixelValue(pixelIndex);
	}
	
	@Override
	public final void setPixelValue(final long pixelIndex, final int pixelValue) {
		this.getSource().setPixelValue(pixelIndex, pixelValue);
	}
	
	@Override
	public final int getWidth() {
		return this.width;
	}
	
	@Override
	public final int getHeight() {
		return this.height;
	}
	
	@Override
	public final int getPixelValue(final int x, final int y) {
		return this.getPixelValue(getPixelIndex(this, x, y));
	}
	
	@Override
	public final void setPixelValue(final int x, final int y, final int value) {
		this.setPixelValue(getPixelIndex(this, x, y), value);
	}
	
	@Override
	public final void forEachPixelInBox(final int left, final int top,
			final int width, final int height, final Image2D.Process process) {
		forEachPixelInBox(this, left, top, width, height, process);
	}
	
	@Override
	public final void copyPixelValues(final int left, final int top, final int width, final int height,
			final int[] result) {
		final LinearIntImage ints = cast(LinearIntImage.class, this.getSource());
		
		if (ints != null) {
			if (left == 0 && top == 0 && width == this.getWidth() && height == this.getHeight()) {
				System.arraycopy(ints.getData(), 0, result, 0, width * height);
			} else {
				final int endY = top + height;
				
				for (int y = top, inIndex = y * this.getWidth() + left, outIndex = 0;
						y < endY; ++y, inIndex += this.getWidth(), outIndex += width) {
					System.arraycopy(ints.getData(), inIndex, result, outIndex, width);
				}
			}
		} else {
			copyEachPixelValue(this, left, top, width, height, result);
		}
	}
	
	@Override
	public final ConcreteImage2D<Source>[] newParallelViews(final int n) {
		return IMJCoreTools.newParallelViews(this, n);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -7130245486896515156L;
	
	public static final long getPixelIndex(final Image2D image, final int x, final int y) {
		return (long) y * image.getWidth() + x;
	}
	
	public static final int getX(final Image2D image, final long pixelIndex) {
		return (int) (pixelIndex % image.getWidth());
	}
	
	public static final int getY(final Image2D image, final long pixelIndex) {
		return (int) (pixelIndex / image.getWidth());
	}
	
	public static final void forEachPixelInBox(final Image2D image, final int left, final int top,
			final int width, final int height, final Image2D.Process process) {
		final int right = left + width;
		final int bottom = top + height;
		
		for (int y = top; y < bottom; ++y) {
			for (int x = left; x < right; ++x) {
				process.pixel(x, y);
			}
		}
		
		process.endOfPatch();
	}
	
	public static final void copyEachPixelValue(final Image2D image, final int left, final int top,
			final int width, final int height, final int[] result) {
		image.forEachPixelInBox(left, top, width, height, new MonopatchProcess() {
			
			private int i = 0;
			
			@Override
			public final void pixel(final int x, final int y) {
				result[this.i++] = image.getPixelValue(x, y);
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 3812041133305798530L;
			
		});
	}
	
}
