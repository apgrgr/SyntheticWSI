package fr.unistra.wsi.synthetic2;

import static java.lang.Math.cos;
import static java.lang.Math.sqrt;
import static javax.swing.SwingUtilities.invokeLater;
import static joints2.JointsEditorPanel.middle;
import static multij.swing.SwingTools.show;
import static multij.swing.SwingTools.useSystemLookAndFeel;
import static multij.tools.MathTools.square;
import static multij.tools.Tools.*;

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
			final Vector3f duct1Direction = new Vector3f(0F, 1F, 0F);
			final int d0 = 0;
			final int d1 = 1;
			
			final float duct0Diameter = editor.point(base[0]).distance(editor.point(base[1]));
			final float duct1Diameter = duct0Diameter * 0.5F;
			
			editor.addSegmentIfAbsent(base[0], base[1]).setConstraint("duct" + d0 + "Diameter=" + duct0Diameter);
			
			final int[] duct0End = addGirder(base[0], base[1], duct0Direction, editor);
			
			editor.segment(duct0End[0], duct0End[1]).setConstraint("duct" + d0 + "Diameter");
			
			addBranch(editor, d0, d1, duct0End, duct1Direction, duct0Diameter, duct1Diameter);
			
			show(editor, StructureViewer.class.getName(), false);
		});
	}

	public static void addBranch(final JointsEditorPanel editor, final int d0, final int d1, final int[] duct0End,
			final Vector3f duct1Direction, final float duct0Diameter, final float duct1Diameter) {
		final int[] duct1Start = addUJoint(duct0End[0], duct0End[1], duct1Direction, editor);
		
		setupUJointConstraints(editor, d0, d1, duct0Diameter, duct0End, duct1Start, duct1Diameter, (float) (Math.PI / 2.0));
		
		final int[] duct1End = addGirder(duct1Start[0], duct1Start[1], duct1Direction, editor);
		
		editor.segment(duct1End[0], duct1End[1]).setConstraint("duct" + d1 + "Diameter");
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
		
		editor.addSegmentIfAbsent(id1, result[0]).setConstraint("" + length);
		editor.addSegmentIfAbsent(id2, result[1]).setConstraint("" + length);
		editor.addSegmentIfAbsent(result[0], result[1]).setConstraint("" + baseLength);
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
