package fr.unistra.wsi.synthetic2;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sqrt;
import static javax.swing.SwingUtilities.invokeLater;
import static joints2.JointsEditorPanel.middle;
import static multij.swing.SwingTools.show;
import static multij.swing.SwingTools.useSystemLookAndFeel;
import static multij.tools.MathTools.square;
import static multij.tools.Tools.*;

import java.io.Serializable;
import java.util.Arrays;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import joints2.JointsEditorPanel;
import multij.swing.ScriptingPanel;
import multij.tools.IllegalInstantiationException;

/**
 * @author ga (creation 2015-09-01)
 */
public final class StructureViewer {
	
	private StructureViewer() {
		throw new IllegalInstantiationException();
	}
	
	public static final JointsEditorPanel[] debugEditor = { null };
	
	/**
	 * @param commandLineArguments
	 * <br>Unused
	 */
	public static void main(final String[] commandLineArguments) {
		useSystemLookAndFeel();
		
		ScriptingPanel.openScriptingPanelOnCtrlF2();
		
		invokeLater(() -> {
			final JointsEditorPanel editor = new JointsEditorPanel();
			debugEditor[0] = editor;
			
			final int[] base = new int[2];
			base[0] = editor.addJoint(new Point3f(-1F, 0F, 0F));
			base[1] = editor.addJoint(new Point3f(1F, 0F, 0F));
			final Vector3f duct0Direction = new Vector3f(0F, 4F, 0F);
			final Vector3f[] duct1Directions = { new Vector3f(-2F, 2F, 0F), new Vector3f(2F, 2F, 0F) };
			final int duct1Count = duct1Directions.length;
			final int d0 = 0;
			final int d1 = 1;
			final int d2 = 2;
			
			final float duct0Diameter = editor.point(base[0]).distance(editor.point(base[1]));
			final float duct1Diameter = duct0Diameter * 0.8F;
			final float duct2Diameter = duct1Diameter * 0.8F;
			
			final int[] duct0End = addGirder(base[0], base[1], duct0Direction, "duct" + d0, "" + duct0Diameter, editor);
			final int[][] duct1Ends = addBranches(editor, duct0End, d0, duct0Diameter, d1, duct1Diameter, duct1Directions);
			
			for (int i = 0; i < duct1Count; ++i) {
				final Vector3f[] duct2Directions = Arrays.stream(duct1Directions).map(v -> rotated(v, new AxisAngle4f(0F, 1F, 0F, (float) (PI / 2.0)))).toArray(Vector3f[]::new);
				addBranches(editor, duct1Ends[i], d1, duct1Diameter, d2, duct2Diameter, duct2Directions);
			}
			
			show(editor, StructureViewer.class.getName(), false);
		});
	}
	
	public static final Vector3f rotated(final Vector3f v, final AxisAngle4f axisAngle) {
		final Vector3f result = new Vector3f();
		final Matrix4f transform = new Matrix4f();
		
		transform.set(axisAngle);
		transform.transform(v, result);
		
		return result;
	}
	
	public static final int[] addBranch(final JointsEditorPanel editor, final int[] duct0End, final int d0, final float duct0Diameter, final int d1,
			final float duct1Diameter, final Vector3f duct1Direction) {
		final int[] duct1Start = addUJoint(duct0End[0], duct0End[1], duct1Direction, editor);
		
		setupUJointConstraints(editor, d0, d1, duct0Diameter, duct0End, duct1Start, duct1Diameter, (float) (Math.PI / 2.0));
		
		return addGirder(duct1Start[0], duct1Start[1], duct1Direction, "duct" + d1, "" + duct1Diameter, editor);
	}
	
	public static final int[][] addBranches(final JointsEditorPanel editor, final int[] duct0End, final int d0, final float duct0Diameter,
			final int d1, final float newDuctsDiameter, final Vector3f... newDuctDirections) {
		final Vector3f jointDirection = new Vector3f();
		
		Arrays.stream(newDuctDirections).forEach(jointDirection::add);
		
		final int[] duct1Start = addUJoint(duct0End[0], duct0End[1], jointDirection, editor);
		
		setupUJointConstraints(editor, d0, d1, duct0Diameter, duct0End, duct1Start, newDuctsDiameter, (float) (Math.PI / 2.0));
		
		final int n = newDuctDirections.length;
		final int[][] result = new int[n][];
		
		for (int i = 0; i < n; ++i) {
			result[i] = addGirder(duct1Start[0], duct1Start[1], newDuctDirections[i], "duct" + d1, "" + newDuctsDiameter, editor);
		}
		
		return result;
	}
	
	public static final void setupUJointConstraints(final JointsEditorPanel editor, final int d0, final int d1,
			final float duct0Diameter, final int[] duct0End, final int[] duct1Start, final float duct1Diameter, final float torsion) {
		editor.segment(duct1Start[0], duct1Start[1]).setConstraint("duct" + d1 + "Diameter=" + duct1Diameter);
		
		/*
		 * duct0End[0] -> duct1Start[0] -> duct0End[1] -> duct1Start[1]
		 */
		
		final float duct0Duct1Torsion = (float) sqrt(square(duct0Diameter) + square(duct1Diameter) - 2F * duct0Diameter * duct1Diameter * (float) cos(torsion)) / 2F;
		
		editor.segment(duct0End[0], duct1Start[0]).setConstraint("duct" + d0 + "Duct" + d1 + "Torsion=" + duct0Duct1Torsion);
		{
			final String a = "(duct" + d0 + "Diameter/2)";
			final String a2 = a + "*" + a;
			final String b = "(duct" + d1 + "Diameter/2)";
			final String b2 = b + "*" + b;
			final String c = "duct" + d0 + "Duct" + d1 + "Torsion";
			final String c2 = c + "*" + c;
			final String gamma = join("", "Math.acos((", a2, "+", b2, "-", c2, ")/(", "2*", a, "*", b, "))");
			
			editor.segment(duct1Start[1], duct0End[0]).setConstraint("duct" + d0 + "Duct" + d1 + "TorsionComplement="
					+ join("", "Math.sqrt(", a2, "+", b2, "-", "2*", a, "*", b, "*Math.cos(Math.PI-", gamma, "))"));
		}
		editor.segment(duct0End[1], duct1Start[1]).setConstraint("duct" + d0 + "Duct" + d1 + "Torsion");
		editor.segment(duct1Start[0], duct0End[1]).setConstraint("duct" + d0 + "Duct" + d1 + "TorsionComplement");
	}
	
	public static final int[] addGirder(final int id1, final int id2, final Vector3f direction,
			final String objectName, final String baseConstraint, final JointsEditorPanel editor) {
		final Point3f start1 = editor.point(id1);
		final Point3f start2 = editor.point(id2);
		final Point3f end1 = new Point3f(start1);
		final Point3f end2 = new Point3f(start2);
		
		end1.add(direction);
		end2.add(direction);
		
		final int[] result = new int[2];
		final float length = direction.length();
		final String b2 = join("", "(", baseConstraint, ")*(", baseConstraint, ")");
		final String l2 = join("", length, "*", length);
		final String diagonal = join("", "Math.sqrt(", b2, "+", l2, ")");
		
		result[0] = editor.addJoint(end1);
		result[1] = editor.addJoint(end2);
		
		editor.addSegmentIfAbsent(id1, id2).setConstraint(objectName + "Diameter=" + baseConstraint);
		editor.addSegmentIfAbsent(id1, result[0]).setConstraint("" + length);
		editor.addSegmentIfAbsent(id2, result[1]).setConstraint("" + length);
		editor.addSegmentIfAbsent(result[0], result[1]).setConstraint(objectName + "Diameter");
		editor.addSegmentIfAbsent(id1, result[1]).setConstraint("" + diagonal);
		editor.addSegmentIfAbsent(id2, result[0]).setConstraint("" + diagonal);
		
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
		
		editor.addSegmentIfAbsent(id1, result[0]).setConstraint("" + edgeLength);
		editor.addSegmentIfAbsent(id2, result[1]).setConstraint("" + edgeLength);
		editor.addSegmentIfAbsent(result[0], result[1]).setConstraint("" + axleLength);
		editor.addSegmentIfAbsent(id1, result[1]).setConstraint("" + edgeLength);
		editor.addSegmentIfAbsent(id2, result[0]).setConstraint("" + edgeLength);
		
		return result;
	}

}
