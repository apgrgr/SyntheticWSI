package imj2.tools;

import static net.sourceforge.aprog.tools.Tools.unchecked;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author codistmonk (creation 2014-09-09)
 */
public final class OutputSource implements Serializable, Closeable {
	
	private final File file;
	
	private final ZipOutputStream zip;
	
	public OutputSource(final String id) {
		this.file = new File(id);
		
		if (id.endsWith(".zip")) {
			try {
				this.zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(id)));
				this.zip.setLevel(ZipOutputStream.STORED);
			} catch (final IOException exception) {
				throw unchecked(exception);
			}
		} else {
			this.zip = null;
			
			if (!this.file.mkdirs() && !this.file.isDirectory()) {
				throw unchecked(new IOException());
			}
		}
	}
	
	public final OutputStream open(final String key) {
		try {
			final ZipOutputStream zip = this.zip;
			
			if (zip != null) {
				zip.putNextEntry(new ZipEntry(key));
				
				return new OutputStream() {
					
					@Override
					public final void write(final int b) throws IOException {
						zip.write(b);
					}
					
					@Override
					public final void write(final byte[] b) throws IOException {
						zip.write(b);
					}
					
					@Override
					public final void write(final byte[] b, final int off, final int len)
							throws IOException {
						zip.write(b, off, len);
					}
					
					@Override
					public final void flush() throws IOException {
						zip.flush();
					}
					
					@Override
					public final void close() throws IOException {
						zip.closeEntry();
					}
					
				};
			}
			
			return new BufferedOutputStream(new FileOutputStream(new File(this.file, key)));
		} catch (final IOException exception) {
			throw unchecked(exception);
		}
	}
	
	@Override
	public final void close() throws IOException {
		if (this.zip != null) {
			this.zip.close();
		}
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 115519844836294165L;
	
}