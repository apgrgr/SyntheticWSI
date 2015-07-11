package imj2.core;

import imj2.core.Image.Channels;

/**
 * @author codistmonk (creation 2013-08-08)
 */
public final class DefaultColorModel {
	
	private final Channels channels;
	
	public DefaultColorModel(final Channels channels) {
		this.channels = channels;
	}
	
	public final Channels getChannels() {
		return this.channels;
	}
	
	public final int alpha(final int pixelValue) {
		return this.getChannels().getChannelValue(pixelValue, 3);
	}
	
	public final int red(final int pixelValue) {
		return this.getChannels().getChannelValue(pixelValue, 2);
	}
	
	public final int green(final int pixelValue) {
		return this.getChannels().getChannelValue(pixelValue, 1);
	}
	
	public final int blue(final int pixelValue) {
		return this.getChannels().getChannelValue(pixelValue, 0);
	}
	
	public final int gray(final int pixelValue) {
		return this.getChannels().getChannelCount() == 1 ? this.blue(pixelValue) :
			(2126 * this.red(pixelValue) + 7152 * this.green(pixelValue) + 722 * this.blue(pixelValue)) / 10000;
	}
	
	public final int binary(final int pixelValue) {
		return pixelValue == 0 ? 0 : 1;
	}
	
	public static final int argb(final int red, final int green, final int blue, final int alpha) {
		return (alpha << 24) | (red << 16) | (green << 8) | (blue << 0);
	}
	
}
