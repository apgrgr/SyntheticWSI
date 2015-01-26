package imj2.tools;

import static imj2.core.ConcreteImage2D.getX;
import static imj2.core.ConcreteImage2D.getY;

import imj2.core.ConcreteImage2D;
import imj2.core.IMJCoreTools;
import imj2.core.Image;
import imj2.core.Image2D;
import imj2.core.SubsampledImage2D;

import java.awt.image.BufferedImage;

/**
 * @author codistmonk (creation 2013-08-07)
 */
public final class AwtBackedImage extends Image.Abstract implements Image2D {
	
	private final String id;
	
	private final BufferedImage awtImage;
	
	public AwtBackedImage(final String id, final BufferedImage awtImage) {
		this.id = id;
		this.awtImage = awtImage;
	}
	
	public final BufferedImage getAwtImage() {
		return this.awtImage;
	}
	
	@Override
	public final Image getSource() {
		return null;
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
		
		return new SubsampledImage2D(this).getLODImage(lod);
	}
	
	@Override
	public final String getId() {
		return this.id;
	}
	
	@Override
	public final long getPixelCount() {
		return (long) this.getWidth() * this.getHeight();
	}
	
	@Override
	public final Channels getChannels() {
		return IMJTools.predefinedChannelsFor(this.getAwtImage());
	}
	
	@Override
	public final int getPixelValue(final long pixelIndex) {
		return this.getPixelValue(getX(this, pixelIndex), getY(this, pixelIndex));
	}
	
	@Override
	public final void setPixelValue(final long pixelIndex, final int pixelValue) {
		this.setPixelValue(getX(this, pixelIndex), getY(this, pixelIndex), pixelValue);
	}
	
	@Override
	public final int getWidth() {
		return this.getAwtImage().getWidth();
	}
	
	@Override
	public final int getHeight() {
		return this.getAwtImage().getHeight();
	}
	
	@Override
	public final int getPixelValue(final int x, final int y) {
		return this.getAwtImage().getRGB(x, y);
	}
	
	@Override
	public final void setPixelValue(final int x, final int y, final int value) {
		this.getAwtImage().setRGB(x, y, value);
	}
	
	@Override
	public final void forEachPixelInBox(final int left, final int top, final int width, final int height,
			final Image2D.Process process) {
		ConcreteImage2D.forEachPixelInBox(this, left, top, width, height, process);
	}
	
	@Override
	public final void copyPixelValues(final int left, final int top, final int width, final int height,
			final int[] result) {
		ConcreteImage2D.copyEachPixelValue(this, left, top, width, height, result);
	}
	
	@Override
	public final AwtBackedImage[] newParallelViews(final int n) {
		return IMJCoreTools.newParallelViews(this, n);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 7888698824812027392L;
	
}
