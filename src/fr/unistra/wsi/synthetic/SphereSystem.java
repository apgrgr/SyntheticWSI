package fr.unistra.wsi.synthetic;

import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static multij.tools.MathTools.square;
import static multij.tools.Tools.intRange;

import imj2.pixel3d.OrthographicRenderer;
import imj2.pixel3d.OrthographicRenderer.IntComparator;

import java.io.Serializable;
import java.util.Arrays;

import multij.primitivelists.DoubleList;
import multij.primitivelists.IntList;
import multij.primitivelists.LongList;

import multij.tools.Tools;

/**
 * @author ga (creation 2014-05-22)
 */
public final class SphereSystem implements Serializable {
	
	private final DoubleList spheres = new DoubleList();
	
	private final IntList sphereColors = new IntList();
	
	private final LongList explicitConstraints = new LongList();
	
	public final double[] getSpheres() {
		return this.spheres.toArray();
	}
	
	public final int[] getSphereColors() {
		return this.sphereColors.toArray();
	}
	
	public final int getSphereCount() {
		return this.spheres.size() / 4;
	}
	
	public final int getExplicitConstraintCount() {
		return this.explicitConstraints.size();
	}
	
	public final int newSphere() {
		return this.newSphere(0.0, 0.0, 0.0, 1.0, 0x60FFFFFF);
	}
	
	public final SphereSystem newExplicitConstraint(final int sphereId1, final int sphereId2) {
		final int first = min(sphereId1, sphereId2);
		final int second = max(sphereId1, sphereId2);
		final long constraint = ((long) first << Integer.SIZE) | (second & 0x00000000FFFFFFFFL);
		
		this.explicitConstraints.add(constraint);
		
		return this;
	}
	
	public final int newSphere(final double x, final double y, final double z, final double radius, final int color) {
		final int result = this.getSphereCount();
		
		this.spheres.addAll(x, y, z, radius);
		this.sphereColors.addAll(color);
		
		return result;
	}
	
	public final double getSphereX(final int sphereId) {
		return this.spheres.get(sphereId * 4 + 0);
	}
	
	public final SphereSystem setSphereX(final int sphereId, final double x) {
		this.spheres.set(sphereId * 4 + 0, x);
		
		return this;
	}
	
	public final SphereSystem updateSphereX(final int sphereId, final double dx) {
		return this.setSphereX(sphereId, this.getSphereX(sphereId) + dx);
	}
	
	public final double getSphereY(final int sphereId) {
		return this.spheres.get(sphereId * 4 + 1);
	}
	
	public final SphereSystem setSphereY(final int sphereId, final double y) {
		this.spheres.set(sphereId * 4 + 1, y);
		
		return this;
	}
	
	public final SphereSystem updateSphereY(final int sphereId, final double dy) {
		return this.setSphereY(sphereId, this.getSphereY(sphereId) + dy);
	}
	
	public final double getSphereZ(final int sphereId) {
		return this.spheres.get(sphereId * 4 + 2);
	}
	
	public final SphereSystem setSphereZ(final int sphereId, final double z) {
		this.spheres.set(sphereId * 4 + 2, z);
		
		return this;
	}
	
	public final SphereSystem updateSphereZ(final int sphereId, final double dz) {
		return this.setSphereZ(sphereId, this.getSphereZ(sphereId) + dz);
	}
	
	public final double getSphereRadius(final int sphereId) {
		return this.spheres.get(sphereId * 4 + 3);
	}
	
	public final SphereSystem setSphereRadius(final int sphereId, final double radius) {
		this.spheres.set(sphereId * 4 + 3, radius);
		
		return this;
	}
	
	public final SphereSystem updateSphereRadius(final int sphereId, final double dradius) {
		return this.setSphereRadius(sphereId, this.getSphereRadius(sphereId) + dradius);
	}
	
	public final int getSphereColor(final int sphereId) {
		return this.sphereColors.get(sphereId);
	}
	
	public final SphereSystem setSphereColor(final int sphereId, final int color) {
		this.sphereColors.set(sphereId, color);
		
		return this;
	}
	
	public final double[] computeCenter(final int[] ids) {
		final int n = ids.length;
		final double[] result = new double[3];
		
		for (int i = 0; i < n; ++i) {
			final int sphereId = ids[i];
			result[X] += this.getSphereX(sphereId);
			result[Y] += this.getSphereY(sphereId);
			result[Z] += this.getSphereZ(sphereId);
		}
		
		if (0 < n) {
			result[X] /= n;
			result[Y] /= n;
			result[Z] /= n;
		}
		
		return result;
	}
	
	public final double[] computeCenter() {
		final int n = this.getSphereCount();
		final double[] result = new double[3];
		
		for (int i = 0; i < n; ++i) {
			result[X] += this.getSphereX(i);
			result[Y] += this.getSphereY(i);
			result[Z] += this.getSphereZ(i);
		}
		
		if (0 < n) {
			result[X] /= n;
			result[Y] /= n;
			result[Z] /= n;
		}
		
		return result;
	}
	
	public final double update() {
		double result = 0.0;
		final double[] spheres = this.getSpheres();
		
		{
			final int n = spheres.length;
			
			for (int offsetI = 0; offsetI < n; offsetI += 4) {
				final double rI = spheres[offsetI + 3];
				
				for (int offsetJ = offsetI + 4; offsetJ < n; offsetJ += 4) {
					final double rJ = spheres[offsetJ + 3];
					final double d = distance(spheres, offsetI, offsetJ);
					final double touchingDistance = rI + rJ;
					
					if (d < touchingDistance) {
						result = max(result, touchingDistance - d);
						scaleDistance(spheres, offsetI, offsetJ, touchingDistance / (d == 0.0 ? 1.0 : d));
					}
				}
			}
		}
		
		for (final long explicitConstraint : this.explicitConstraints.toArray()) {
			final int id1 = (int) (explicitConstraint >> Integer.SIZE);
			final int id2 = (int) explicitConstraint;
			final int offset1 = id1 * 4;
			final int offset2 = id2 * 4;
			final double d = distance(spheres, offset1, offset2);
			final double rI = spheres[offset1 + 3];
			final double rJ = spheres[offset2 + 3];
			final double touchingDistance = rI + rJ;
			
			result = max(result, abs(touchingDistance - d));
			scaleDistance(spheres, offset1, offset2, touchingDistance / (d == 0.0 ? 1.0 : d));
		}
		
		return result;
	}
	
	public final double update2() {
		return this.update2(intRange(this.getSphereCount()));
	}
	
	private final IntList[] cut(final int[] ids, final double[] center) {
		final IntList[] result = Tools.instances(8, IntList.FACTORY);
		final double centerX = center[X];
		final double centerY = center[Y];
		final double centerZ = center[Z];
		
		for (final int sphereId : ids) {
			final boolean x0 = this.getSphereX(sphereId) < centerX;
			final boolean y0 = this.getSphereY(sphereId) < centerY;
			final boolean z0 = this.getSphereZ(sphereId) < centerZ;
			final int octant = ((x0 ? 0 : 1) << 2) | ((y0 ? 0 : 1) << 1) | ((z0 ? 0 : 1) << 0);
			
			result[octant].add(sphereId);
		}
		
		return result;
	}
	
	private final double update2(final int[] ids) {
		final int n = ids.length;
		
		if (4000 < n) {
			double result = 0.0;
			
			final IntList[] cut = this.cut(intRange(n), this.computeCenter(ids));
			
			update_recursively:
			{
				for (final IntList subIds : cut) {
					if (subIds.size() == n) {
						Tools.debugError("Failed to cut", n, "elements");
						Tools.debugError(Arrays.toString(this.computeCenter(ids)));
						
						break update_recursively;
					}
				}
				
				for (final IntList subIds : cut) {
					result = max(result, this.update2(subIds.toArray()));
				}
				
				return result;
			}
		}
		
		final double[] center = this.computeCenter(ids);
		final double[] spheres = this.getSpheres();
		
		OrthographicRenderer.dualPivotQuicksort(ids, 0, n, new IntComparator() {

			@Override
			public final int compare(final int id1, final int id2) {
				return Double.compare(distance(center, 0, spheres, id1 * STRIDE), distance(center, 0, spheres, id2 * STRIDE));
			}
			
			/**
			 * {@value}.
			 */
			private static final long serialVersionUID = 4546146040620923690L;
			
		});
		
		double result = 0.0;
		
		for (int i = 1; i < n; ++i) {
			final int offsetI = ids[i] * STRIDE;
			final double rI = spheres[offsetI + R];
			
			for (int j = 0; j < i; ++j) {
				final int offsetJ = ids[j] * STRIDE;
				final double rJ = spheres[offsetJ + R];
				final double touchingDistance = rI + rJ;
				final double distance = distance(spheres, offsetI, offsetJ);
				
				if (distance < touchingDistance) {
					result = max(result, touchingDistance - distance);
					
					scaleDistance2(spheres, offsetJ, offsetI, touchingDistance / (distance == 0.0 ? 1.0 : distance));
				}
			}
		}
		
		return result;
	}
	
	/**
	 * {@value}.
	 */
	public static final int X = 0;
	
	/**
	 * {@value}.
	 */
	public static final int Y = 1;
	
	/**
	 * {@value}.
	 */
	public static final int Z = 2;
	
	/**
	 * {@value}.
	 */
	public static final int R = 3;
	
	/**
	 * {@value}.
	 */
	public static final int STRIDE = 4;
	
	public static final void scaleDistance(final double[] data, final int offset1, final int offset2, final double scale) {
		final double x1 = data[offset1 + 0];
		final double y1 = data[offset1 + 1];
		final double z1 = data[offset1 + 2];
		final double x2 = data[offset2 + 0];
		final double y2 = data[offset2 + 1];
		final double z2 = data[offset2 + 2];
		final double middleX = (x1 + x2) / 2.0;
		final double middleY = (y1 + y2) / 2.0;
		final double middleZ = (z1 + z2) / 2.0;
		
		if (x1 == middleX && y1 == middleY && z1 == middleZ) {
			final double pseudoRandomTheta = offset1 + offset2;
			final double pseudoRandomPhi = offset1 - offset2;
			final double dx = scale * sin(pseudoRandomTheta) * cos(pseudoRandomPhi) / 2.0;
			final double dy = scale * sin(pseudoRandomTheta) * sin(pseudoRandomPhi) / 2.0;
			final double dz = scale * cos(pseudoRandomTheta) / 2.0;
			data[offset1 + 0] += dx;
			data[offset1 + 1] += dy;
			data[offset1 + 2] += dz;
			data[offset2 + 0] -= dx;
			data[offset2 + 1] -= dy;
			data[offset2 + 2] -= dz;
		} else {
			data[offset1 + 0] = middleX + scale * (x1 - middleX);
			data[offset1 + 1] = middleY + scale * (y1 - middleY);
			data[offset1 + 2] = middleZ + scale * (z1 - middleZ);
			data[offset2 + 0] = middleX + scale * (x2 - middleX);
			data[offset2 + 1] = middleY + scale * (y2 - middleY);
			data[offset2 + 2] = middleZ + scale * (z2 - middleZ);
		}
	}
	
	public static final void scaleDistance2(final double[] data, final int offset1, final int offset2, final double scale) {
		final double x1 = data[offset1 + X];
		final double y1 = data[offset1 + Y];
		final double z1 = data[offset1 + Z];
		final double x2 = data[offset2 + X];
		final double y2 = data[offset2 + Y];
		final double z2 = data[offset2 + Z];
		
		if (x1 == x2 && y1 == y2 && z1 == z2) {
			final double pseudoRandomTheta = offset1 + offset2;
			final double pseudoRandomPhi = offset1 - offset2;
			final double dx = scale * sin(pseudoRandomTheta) * cos(pseudoRandomPhi);
			final double dy = scale * sin(pseudoRandomTheta) * sin(pseudoRandomPhi);
			final double dz = scale * cos(pseudoRandomTheta);
			data[offset2 + X] += dx;
			data[offset2 + Y] += dy;
			data[offset2 + Z] += dz;
		} else {
			data[offset2 + X] = x1 + scale * (x2 - x1);
			data[offset2 + Y] = y1 + scale * (y2 - y1);
			data[offset2 + Z] = z1 + scale * (z2 - z1);
		}
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 2536182611751649583L;
	
	public static final double distance(final double[] data, final int offset1, final int offset2) {
		return distance(data, offset1, data, offset2);
	}
	
	public static final double distance(final double[] data1, final int offset1, final double[] data2, final int offset2) {
		double sum = 0.0;
		
		for (int k = 0; k < 3; ++k) {
			sum += square(data1[offset1 + k] - data2[offset2 + k]);
		}
		
		return sqrt(sum);
	}
	
}