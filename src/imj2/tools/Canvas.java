package imj2.tools;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;

/**
 * @author codistmonk (creation 2014-02-15)
 */
public final class Canvas implements Serializable {
	
	private transient BufferedImage image;
	
	private transient Graphics2D graphics;
	
	public final int getWidth() {
		return this.getImage() == null ? 0 : this.getImage().getWidth();
	}
	
	public final int getHeight() {
		return this.getImage() == null ? 0 : this.getImage().getHeight();
	}
	
	public final BufferedImage getImage() {
		return this.image;
	}
	
	public final Graphics2D getGraphics() {
		return this.graphics;
	}
	
	public final Canvas setFormat(final int width, final int height, final int bufferedImageType) {
		if (this.getImage() == null || this.getImage().getWidth() != width || this.getImage().getHeight() != height ||
				this.getImage().getType() != bufferedImageType) {
			if (this.getGraphics() != null) {
				this.getGraphics().dispose();
			}
			
			this.image = new BufferedImage(width, height, bufferedImageType);
			this.graphics = this.getImage().createGraphics();
		}
		
		return this;
	}
	
	public final Canvas clear(final Color color) {
		this.getGraphics().setColor(color);
		this.getGraphics().fillRect(0, 0, this.getWidth(), this.getHeight());
		
		return this;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = -299324620065690574L;
	
}
