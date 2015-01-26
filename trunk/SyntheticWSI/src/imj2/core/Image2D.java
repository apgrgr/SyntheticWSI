package imj2.core;

/**
 * @author codistmonk (creation 2013-08-04)
 */
public abstract interface Image2D extends Image {
	
	public abstract int getWidth();
	
	public abstract int getHeight();
	
	public abstract int getLOD();
	
	public abstract Image2D getLODImage(int lod);
	
	public abstract int getPixelValue(int x, int y);
	
	public abstract void setPixelValue(int x, int y, int value);
	
	public abstract void forEachPixelInBox(int left, int top, int width, int height, Process process);
	
	public abstract void copyPixelValues(int left, int top, int width, int height, int[] result);
	
	@Override
	public abstract Image2D[] newParallelViews(int n);
	
	/**
	 * @author codistmonk (creation 2013-08-07)
	 */
	public static abstract interface Process extends Image.Process {
		
		public abstract void pixel(int x, int y);
		
	}
	
	/**
	 * @author codistmonk (creation 2013-08-08)
	 */
	public static abstract class MonopatchProcess implements Process {
		
		@Override
		public final void endOfPatch() {
			// NOP
		}
		
		/**
		 * {@value}.
		 */
		private static final long serialVersionUID = 4975211754834419344L;
		
	}
	
	/**
	 * @author codistmonk (creation 2013-08-07)
	 */
	public static enum Traversing implements Image.Traversing<Image2D, Process> {
		
		ALL {
			
			@Override
			public final void forEachPixelIn(final Image2D image, final Process process) {
				final int width = image.getWidth();
				final int height = image.getHeight();
				
				for (int y = 0; y < height; ++y) {
					for (int x = 0; x < width; ++x) {
						process.pixel(x, y);
					}
				}
				
				process.endOfPatch();
			}
			
		};
		
	}
	
}
