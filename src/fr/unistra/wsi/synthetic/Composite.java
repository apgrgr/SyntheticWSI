package fr.unistra.wsi.synthetic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ga (creation 2014-09-07)
 * 
 * @param <T>
 */
public final class Composite<T> implements Serializable {
	
	private final T object;
	
	private Composite<T> parent;
	
	private final List<T> children;
	
	public Composite(final T object) {
		this.object = object;
		this.children = new ArrayList<>();
	}
	
	public final T getObject() {
		return this.object;
	}
	
	public final Composite<T> getParent() {
		return this.parent;
	}
	
	public final Composite<T> setParent(final Composite<T> parent) {
		if (parent == this.getParent()) {
			return this;
		}
		
		if (parent == null) {
			this.getParent().getChildren().remove(this.getObject());
		}
		
		this.parent = parent;
		
		if (parent != null) {
			parent.getChildren().add(this.getObject());
		}
		
		return this;
	}
	
	public final List<T> getChildren() {
		return this.children;
	}
	
	/**
	 * {@value}.
	 */
	private static final long serialVersionUID = 3611363794729269384L;
	
}