/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package imj2.pixel3d;

import imj2.pixel3d.OrthographicRenderer.IntComparator;

/**
 * This class implements the Dual-Pivot Quicksort algorithm by
 * Vladimir Yaroslavskiy, Jon Bentley, and Josh Bloch. The algorithm
 * offers O(n log(n)) performance on many data sets that cause other
 * quicksorts to degrade to quadratic performance, and is typically
 * faster than traditional (one-pivot) Quicksort implementations.
 *
 * All exposed methods are package-private, designed to be invoked
 * from public methods (in class Arrays) after performing any
 * necessary array bounds checks and expanding parameters into the
 * required forms.
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 *
 * @version 2011.02.11 m765.827.12i:5\7pm
 * @since 1.7
 */
final class DualPivotQuicksort {

    /**
     * Prevents instantiation.
     */
    private DualPivotQuicksort() {}

    /*
     * Tuning parameters.
     */

    /**
     * The maximum number of runs in merge sort.
     */
    private static final int MAX_RUN_COUNT = 67;

    /**
     * The maximum length of run in merge sort.
     */
    private static final int MAX_RUN_LENGTH = 33;

    /**
     * If the length of an array to be sorted is less than this
     * constant, Quicksort is used in preference to merge sort.
     */
    private static final int QUICKSORT_THRESHOLD = 286;

    /**
     * If the length of an array to be sorted is less than this
     * constant, insertion sort is used in preference to Quicksort.
     */
    private static final int INSERTION_SORT_THRESHOLD = 47;

    public static final void swap(final int[] array, final int i, final int j) {
    	final int tmp = array[i];
    	array[i] = array[j];
    	array[j] = tmp;
    }
    
    /**
     * Sorts the specified range of the array using the given
     * workspace array slice if possible for merging
     *
     * @param array the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param work a workspace array (slice)
     * @param workBase origin of usable space in work array
     * @param workLength usable size of work array
     */
    static final void sort(final int[] array, final int left, int right
    		, int[] work, int workBase, int workLength
    		, final IntComparator comparator) {
        // Use Quicksort on small arrays
        if (right - left < QUICKSORT_THRESHOLD) {
            sort(array, left, right, true, comparator);
            return;
        }

        /*
         * Index run[i] is the start of i-th run
         * (ascending or descending sequence).
         */
        final int[] run = new int[MAX_RUN_COUNT + 1];
        int count = 0;
        run[0] = left;

        // Check if the array is nearly sorted
        for (int k = left; k < right; run[count] = k) {
            if (comparator.compare(array[k], array[k + 1]) < 0) { // ascending
                while (++k <= right && comparator.compare(array[k - 1], array[k]) <= 0);
            } else if (comparator.compare(array[k], array[k + 1]) > 0) { // descending
                while (++k <= right && comparator.compare(array[k - 1], array[k]) >= 0);
                
                for (int lo = run[count] - 1, hi = k; ++lo < --hi; ) {
                	swap(array, lo, hi);
                }
            } else { // equal
                for (int m = MAX_RUN_LENGTH; ++k <= right && comparator.compare(array[k - 1], array[k]) == 0; ) {
                    if (--m == 0) {
                        sort(array, left, right, true, comparator);
                        return;
                    }
                }
            }

            /*
             * The array is not highly structured,
             * use Quicksort instead of merge sort.
             */
            if (++count == MAX_RUN_COUNT) {
                sort(array, left, right, true, comparator);
                return;
            }
        }

        // Check special cases
        // Implementation note: variable "right" is increased by 1.
        if (run[count] == right++) { // The last run contains one element
            run[++count] = right;
        } else if (count == 1) { // The array is already sorted
            return;
        }

    	int[] a = array;
        // Determine alternation base for merge
        boolean odd = false;
        for (int n = 1; (n <<= 1) < count; odd ^= true);
        
        // Use or create temporary array b for merging
        int[] b;                 // temp array; alternates with a
        int aOffset, bOffset;              // array offsets from 'left'
        int bLength = right - left; // space needed for b
        if (work == null || workLength < bLength || workBase + bLength > work.length) {
            work = new int[bLength];
            workBase = 0;
        }
        if (!odd) {
            System.arraycopy(a, left, work, workBase, bLength);
            b = a;
            bOffset = 0;
            a = work;
            aOffset = workBase - left;
        } else {
            b = work;
            aOffset = 0;
            bOffset = workBase - left;
        }

        // Merging
        for (int last; count > 1; count = last) {
            for (int k = (last = 0) + 2; k <= count; k += 2) {
                int hi = run[k], mi = run[k - 1];
                for (int i = run[k - 2], p = i, q = mi; i < hi; ++i) {
                    if (q >= hi || p < mi && comparator.compare(a[p + aOffset], a[q + aOffset]) <= 0) {
                        b[i + bOffset] = a[p++ + aOffset];
                    } else {
                        b[i + bOffset] = a[q++ + aOffset];
                    }
                }
                run[++last] = hi;
            }
            if ((count & 1) != 0) {
                for (int i = right, lo = run[count - 1]; --i >= lo;
                    b[i + bOffset] = a[i + aOffset]
                );
                run[++last] = right;
            }
            {
            	final int[] t = a;
            	a = b;
            	b = t;
            }
            {
            	final int o = aOffset;
            	aOffset = bOffset;
            	bOffset = o;
            }
        }
    }

    /**
     * Sorts the specified range of the array by Dual-Pivot Quicksort.
     *
     * @param array the array to be sorted
     * @param left the index of the first element, inclusive, to be sorted
     * @param right the index of the last element, inclusive, to be sorted
     * @param leftmost indicates if this part is the leftmost in the range
     */
    private static void sort(final int[] array, int left, int right, final boolean leftmost, final IntComparator comparator) {
        int length = right - left + 1;

        // Use insertion sort on tiny arrays
        if (length < INSERTION_SORT_THRESHOLD) {
            if (leftmost) {
                /*
                 * Traditional (without sentinel) insertion sort,
                 * optimized for server VM, is used in case of
                 * the leftmost part.
                 */
                for (int i = left, j = i; i < right; j = ++i) {
                    int ai = array[i + 1];
                    while (comparator.compare(ai, array[j]) < 0) {
                        array[j + 1] = array[j];
                        if (j-- == left) {
                            break;
                        }
                    }
                    array[j + 1] = ai;
                }
            } else {
                /*
                 * Skip the longest ascending sequence.
                 */
                do {
                    if (left >= right) {
                        return;
                    }
                } while (comparator.compare(array[++left], array[left - 1]) >= 0);

                /*
                 * Every element from adjoining part plays the role
                 * of sentinel, therefore this allows us to avoid the
                 * left range check on each iteration. Moreover, we use
                 * the more optimized algorithm, so called pair insertion
                 * sort, which is faster (in the context of Quicksort)
                 * than traditional implementation of insertion sort.
                 */
                for (int k = left; ++left <= right; k = ++left) {
                    int a1 = array[k], a2 = array[left];

                    if (comparator.compare(a1, a2) < 0) {
                        a2 = a1; a1 = array[left];
                    }
                    while (comparator.compare(a1, array[--k]) < 0) {
                        array[k + 2] = array[k];
                    }
                    array[++k + 1] = a1;

                    while (comparator.compare(a2, array[--k]) < 0) {
                        array[k + 1] = array[k];
                    }
                    array[k + 1] = a2;
                }
                int last = array[right];

                while (comparator.compare(last, array[--right]) < 0) {
                    array[right + 1] = array[right];
                }
                array[right + 1] = last;
            }
            return;
        }

        // Inexpensive approximation of length / 7
        final int seventh = (length >> 3) + (length >> 6) + 1;

        /*
         * Sort five evenly spaced elements around (and including) the
         * center element in the range. These elements will be used for
         * pivot selection as described below. The choice for spacing
         * these elements was empirically determined to work well on
         * a wide variety of inputs.
         */
        final int e3 = (left + right) >>> 1; // The midpoint
        final int e2 = e3 - seventh;
        final int e1 = e2 - seventh;
        final int e4 = e3 + seventh;
        final int e5 = e4 + seventh;

        insertionSort(array, e3, e2, e1, e4, e5, comparator);

        // Pointers
        int less  = left;  // The index of the first element of center part
        int great = right; // The index before the first element of right part

        if (comparator.compare(array[e1], array[e2]) != 0 && comparator.compare(array[e2], array[e3]) != 0
        		&& comparator.compare(array[e3], array[e4]) != 0 && comparator.compare(array[e4], array[e5]) != 0) {
            /*
             * Use the second and fourth of the five sorted elements as pivots.
             * These values are inexpensive approximations of the first and
             * second terciles of the array. Note that pivot1 <= pivot2.
             */
            final int pivot1 = array[e2];
            final int pivot2 = array[e4];

            /*
             * The first and the last elements to be sorted are moved to the
             * locations formerly occupied by the pivots. When partitioning
             * is complete, the pivots are swapped back into their final
             * positions, and excluded from subsequent sorting.
             */
            array[e2] = array[left];
            array[e4] = array[right];

            /*
             * Skip elements, which are less or greater than pivot values.
             */
            while (comparator.compare(array[++less], pivot1) < 0);
            while (comparator.compare(array[--great], pivot2) > 0);

            /*
             * Partitioning:
             *
             *   left part           center part                   right part
             * +--------------------------------------------------------------+
             * |  < pivot1  |  pivot1 <= && <= pivot2  |    ?    |  > pivot2  |
             * +--------------------------------------------------------------+
             *               ^                          ^       ^
             *               |                          |       |
             *              less                        k     great
             *
             * Invariants:
             *
             *              all in (left, less)   < pivot1
             *    pivot1 <= all in [less, k)     <= pivot2
             *              all in (great, right) > pivot2
             *
             * Pointer k is the first index of ?-part.
             */
            outer:
            for (int k = less - 1; ++k <= great; ) {
                int ak = array[k];
                if (comparator.compare(ak, pivot1) < 0) { // Move a[k] to left part
                    array[k] = array[less];
                    /*
                     * Here and below we use "a[i] = b; i++;" instead
                     * of "a[i++] = b;" due to performance issue.
                     */
                    array[less] = ak;
                    ++less;
                } else if (comparator.compare(ak, pivot2) > 0) { // Move a[k] to right part
                    while (comparator.compare(array[great], pivot2) > 0) {
                        if (great-- == k) {
                            break outer;
                        }
                    }
                    if (comparator.compare(array[great], pivot1) < 0) { // a[great] <= pivot2
                        array[k] = array[less];
                        array[less] = array[great];
                        ++less;
                    } else { // pivot1 <= a[great] <= pivot2
                        array[k] = array[great];
                    }
                    /*
                     * Here and below we use "a[i] = b; i--;" instead
                     * of "a[i--] = b;" due to performance issue.
                     */
                    array[great] = ak;
                    --great;
                }
            }

            // Swap pivots into their final positions
            array[left]  = array[less  - 1]; array[less  - 1] = pivot1;
            array[right] = array[great + 1]; array[great + 1] = pivot2;

            // Sort left and right parts recursively, excluding known pivots
            sort(array, left, less - 2, leftmost, comparator);
            sort(array, great + 2, right, false, comparator);

            /*
             * If center part is too large (comprises > 4/7 of the array),
             * swap internal pivot values to ends.
             */
            if (less < e1 && e5 < great) {
                /*
                 * Skip elements, which are equal to pivot values.
                 */
                while (comparator.compare(array[less], pivot1) == 0) {
                    ++less;
                }

                while (comparator.compare(array[great], pivot2) == 0) {
                    --great;
                }

                /*
                 * Partitioning:
                 *
                 *   left part         center part                  right part
                 * +----------------------------------------------------------+
                 * | == pivot1 |  pivot1 < && < pivot2  |    ?    | == pivot2 |
                 * +----------------------------------------------------------+
                 *              ^                        ^       ^
                 *              |                        |       |
                 *             less                      k     great
                 *
                 * Invariants:
                 *
                 *              all in (*,  less) == pivot1
                 *     pivot1 < all in [less,  k)  < pivot2
                 *              all in (great, *) == pivot2
                 *
                 * Pointer k is the first index of ?-part.
                 */
                outer:
                for (int k = less - 1; ++k <= great; ) {
                    int ak = array[k];
                    if (comparator.compare(ak, pivot1) == 0) { // Move a[k] to left part
                        array[k] = array[less];
                        array[less] = ak;
                        ++less;
                    } else if (ak == pivot2) { // Move a[k] to right part
                        while (comparator.compare(array[great], pivot2) == 0) {
                            if (great-- == k) {
                                break outer;
                            }
                        }
                        if (comparator.compare(array[great], pivot1) == 0) { // a[great] < pivot2
                            array[k] = array[less];
                            /*
                             * Even though a[great] equals to pivot1, the
                             * assignment a[less] = pivot1 may be incorrect,
                             * if a[great] and pivot1 are floating-point zeros
                             * of different signs. Therefore in float and
                             * double sorting methods we have to use more
                             * accurate assignment a[less] = a[great].
                             */
                            array[less] = pivot1;
                            ++less;
                        } else { // pivot1 < a[great] < pivot2
                            array[k] = array[great];
                        }
                        array[great] = ak;
                        --great;
                    }
                }
            }

            // Sort center part recursively
            sort(array, less, great, false, comparator);

        } else { // Partitioning with one pivot
            /*
             * Use the third of the five sorted elements as pivot.
             * This value is inexpensive approximation of the median.
             */
        	final int pivot = array[e3];

            /*
             * Partitioning degenerates to the traditional 3-way
             * (or "Dutch National Flag") schema:
             *
             *   left part    center part              right part
             * +-------------------------------------------------+
             * |  < pivot  |   == pivot   |     ?    |  > pivot  |
             * +-------------------------------------------------+
             *              ^              ^        ^
             *              |              |        |
             *             less            k      great
             *
             * Invariants:
             *
             *   all in (left, less)   < pivot
             *   all in [less, k)     == pivot
             *   all in (great, right) > pivot
             *
             * Pointer k is the first index of ?-part.
             */
            for (int k = less; k <= great; ++k) {
                if (comparator.compare(array[k], pivot) == 0) {
                    continue;
                }
                int ak = array[k];
                if (comparator.compare(ak, pivot) < 0) { // Move a[k] to left part
                    array[k] = array[less];
                    array[less] = ak;
                    ++less;
                } else { // a[k] > pivot - Move a[k] to right part
                    while (comparator.compare(array[great], pivot) > 0) {
                        --great;
                    }
                    if (comparator.compare(array[great], pivot) < 0) { // a[great] <= pivot
                        array[k] = array[less];
                        array[less] = array[great];
                        ++less;
                    } else { // a[great] == pivot
                        /*
                         * Even though a[great] equals to pivot, the
                         * assignment a[k] = pivot may be incorrect,
                         * if a[great] and pivot are floating-point
                         * zeros of different signs. Therefore in float
                         * and double sorting methods we have to use
                         * more accurate assignment a[k] = a[great].
                         */
                        array[k] = pivot;
                    }
                    array[great] = ak;
                    --great;
                }
            }

            /*
             * Sort left and right parts recursively.
             * All elements from center part are equal
             * and, therefore, already sorted.
             */
            sort(array, left, less - 1, leftmost, comparator);
            sort(array, great + 1, right, false, comparator);
        }
    }
    
	private static final void insertionSort(final int[] array, final int e3, final int e2,
			final int e1, final int e4, final int e5, final IntComparator comparator) {
		if (comparator.compare(array[e2], array[e1]) < 0) {
        	swap(array, e1, e2);
        }

        if (comparator.compare(array[e3], array[e2]) < 0) {
        	final int t = array[e3];
        	array[e3] = array[e2];
        	array[e2] = t;
        	
			if (comparator.compare(t, array[e1]) < 0) {
				array[e2] = array[e1];
				array[e1] = t;
			}
        }
        
		if (comparator.compare(array[e4], array[e3]) < 0) {
			final int t = array[e4];
			array[e4] = array[e3];
			array[e3] = t;
			
			if (comparator.compare(t, array[e2]) < 0) {
				array[e3] = array[e2];
				array[e2] = t;
				
				if (comparator.compare(t, array[e1]) < 0) {
					array[e2] = array[e1];
					array[e1] = t;
				}
			}
		}
		
		if (comparator.compare(array[e5], array[e4]) < 0) {
			final int t = array[e5];
			array[e5] = array[e4];
			array[e4] = t;
			
			if (comparator.compare(t, array[e3]) < 0) {
				array[e4] = array[e3];
				array[e3] = t;
				
				if (comparator.compare(t, array[e2]) < 0) {
					array[e3] = array[e2];
					array[e2] = t;
					
					if (comparator.compare(t, array[e1]) < 0) {
						array[e2] = array[e1];
						array[e1] = t;
					}
				}
			}
		}
	}

}
