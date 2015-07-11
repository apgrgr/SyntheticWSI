package imj2.tools;

import java.io.Serializable;

import multij.primitivelists.LongList;

/**
 * @author codistmonk (creation 2014-09-16)
 */
public final class BigBitSet implements Serializable {
	
	private final LongList data;
	
	public BigBitSet() {
		this(0L);
	}
	
	public BigBitSet(final long n) {
		this.data = new LongList((int) (n / Long.SIZE) + 1);
	}
	
	public final boolean get(final long index) {
		final long datumIndex = index / Long.SIZE;
		
		if (this.data.size() <= datumIndex) {
			return false;
		}
		
		final long datum = this.data.get((int) datumIndex);
		final long mask = 1L << index;
		
		return (datum & mask) != 0L;
	}
	
	public final void set(final long index, final boolean value) {
		final long datumIndex = index / Long.SIZE;
		
		if (this.data.size() <= datumIndex) {
			this.data.resize((int) datumIndex + 1);
		}
		
		final long datum = this.data.get((int) datumIndex);
		final long mask = 1L << index;
		
		this.data.set((int) datumIndex, value ? datum | mask : datum & ~mask);
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 2879590469152973114L;
	
}
