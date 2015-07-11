package imj2.tools;

import static multij.tools.Tools.iterable;
import static multij.tools.Tools.unchecked;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author codistmonk (creation 2014-09-09)
 */
public final class InputSource implements Serializable, Closeable {
	
	private final File file;
	
	private final ZipFile zip;
	
	private final Map<String, ZipEntry> zipEntries;
	
	public InputSource(final String id) {
		this.file = new File(id);
		
		if (id.endsWith(".zip")) {
			try {
				this.zip = new ZipFile(id);
				this.zipEntries = new HashMap<>();
				
				for (final ZipEntry entry : iterable(this.zip.entries())) {
					if (null != this.zipEntries.put(entry.getName(), entry)) {
						throw new IllegalArgumentException();
					}
				}
			} catch (final IOException exception) {
				throw unchecked(exception);
			}
		} else {
			this.zip = null;
			this.zipEntries = null;
		}
	}
	
	public final InputStream open(final String key) {
		try {
			if (this.zip != null) {
				if (this.zipEntries.get(key) == null) {
					new FileNotFoundException(key).printStackTrace();
					System.exit(-1);
				}
				
				return this.zip.getInputStream(this.zipEntries.get(key));
			}
			
			return new FileInputStream(new File(this.file, key));
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