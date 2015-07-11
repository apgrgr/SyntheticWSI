package imj2.pixel3d;

import java.awt.image.BufferedImage;
import java.io.Serializable;

/**
 * @author codistmonk (creation 2014-04-29)
 */
public interface Renderer extends Serializable {
	
	public abstract Renderer setCanvas(BufferedImage canvas);
	
	public abstract void clear();
	
	public abstract Renderer addPixel(double x, double y, double z, int argb);
	
	public abstract void render();
	
}