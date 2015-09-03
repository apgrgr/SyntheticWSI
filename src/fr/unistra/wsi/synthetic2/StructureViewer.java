package fr.unistra.wsi.synthetic2;

import static java.lang.Math.sqrt;
import static javax.swing.SwingUtilities.invokeLater;
import static joints2.JointsEditorPanel.middle;
import static multij.swing.SwingTools.show;
import static multij.swing.SwingTools.useSystemLookAndFeel;
import static multij.tools.MathTools.square;

import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import joints2.JointsEditorPanel;
import multij.tools.IllegalInstantiationException;
import multij.tools.MathTools;
import multij.tools.Tools;

/**
 * @author ga (creation 2015-09-01)
 */
public final class StructureViewer {
	
	private StructureViewer() {
		throw new IllegalInstantiationException();
	}
	
	/**
	 * @param commandLineArguments
	 * <br>Unused
	 */
	public static void main(final String[] commandLineArguments) {
		useSystemLookAndFeel();
		invokeLater(() -> {
			final JointsEditorPanel editor = new JointsEditorPanel();
			
			final int p1 = editor.addJoint(new Point3f(0F, 0F, 0F));
			final int p2 = editor.addJoint(new Point3f(1F, 0F, 0F));
			
			editor.addSegmentIfAbsent(p1, p2);
			
			final int[] girderTop = addGirder(p1, p2, new Vector3f(0F, 4F, 0F), editor);
			final int[] uJointAxle = addUJoint(girderTop[0], girderTop[1], new Vector3f(0F, 1F, 0F), editor);
			
			addGirder(uJointAxle[0], uJointAxle[1], new Vector3f(0F, 4F, 0F), editor);
			
			show(editor, StructureViewer.class.getName(), false);
		});
	}
	
	public static final int[] addGirder(final int id1, final int id2, final Vector3f direction, final JointsEditorPanel editor) {
		final Point3f start1 = editor.point(id1);
		final Point3f start2 = editor.point(id2);
		final Point3f end1 = new Point3f(start1);
		final Point3f end2 = new Point3f(start2);
		
		end1.add(direction);
		end2.add(direction);
		
		final int[] result = new int[2];
		final float baseLength = start1.distance(start2);
		final float length = direction.length();
		final float diagonal = (float) sqrt(square(length) + square(baseLength));
		
		result[0] = editor.addJoint(end1);
		result[1] = editor.addJoint(end2);
		
		editor.addSegmentIfAbsent(id1, result[0]).setConstraint(length);
		editor.addSegmentIfAbsent(id2, result[1]).setConstraint(length);
		editor.addSegmentIfAbsent(result[0], result[1]).setConstraint(baseLength);
		editor.addSegmentIfAbsent(id1, result[1]).setConstraint(diagonal);
		editor.addSegmentIfAbsent(id2, result[0]).setConstraint(diagonal);
		
		return result;
	}
	
	public static final int[] addUJoint(final int id1, final int id2, final Vector3f direction, final JointsEditorPanel editor) {
		final Point3f start1 = editor.point(id1);
		final Point3f start2 = editor.point(id2);
		final Point3f middle = middle(start1, start2);
		final Vector3f axle = new Vector3f(start2);
		
		axle.sub(start1);
		
		final float axleLength = axle.length();
		final float edgeLength = (float) (axleLength / sqrt(2.0));
		
		axle.cross(direction, axle);
		axle.normalize();
		axle.scale(axleLength / 2F);
		
		final Point3f end1 = new Point3f(middle);
		final Point3f end2 = new Point3f(middle);
		
		end1.sub(axle);
		end2.add(axle);
		
		final int[] result = new int[2];
		
		result[0] = editor.addJoint(end1);
		result[1] = editor.addJoint(end2);
		
		editor.addSegmentIfAbsent(id1, result[0]).setConstraint(edgeLength);
		editor.addSegmentIfAbsent(id2, result[1]).setConstraint(edgeLength);
		editor.addSegmentIfAbsent(result[0], result[1]).setConstraint(axleLength);
		editor.addSegmentIfAbsent(id1, result[1]).setConstraint(edgeLength);
		editor.addSegmentIfAbsent(id2, result[0]).setConstraint(edgeLength);
		
		return result;
	}

}
