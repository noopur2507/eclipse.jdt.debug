/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.core;

 
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IWatchpoint;

/**
 * A breakpoint on a field. If a watchpoint is an access watchpoint,
 * it will suspend execution when its field is accessed. If a watchpoint
 * is a modification watchpoint, it will suspend execution when its field
 * is modified.
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @since 2.0
 */
public interface IJavaWatchpoint extends IJavaLineBreakpoint, IWatchpoint {	
	/**
	 * Returns the name of the field associated with this watchpoint
	 * 
	 * @return field the name of the field on which this watchpoint is installed
	 * @exception CoreException if unable to access the property on
	 * 	this breakpoint's underlying marker
	 */
	public String getFieldName() throws CoreException;	
	/**
	 * Returns whether this breakpoint last suspended in this target due to an access
	 * (<code>true</code>) or modification (<code>false</code>).
	 * 
	 * @return <code>true</code> if this watchpoint last suspended the given
	 *  target due to a field access; <code>false</code> if this watchpoint last
	 *  suspended the given target due to a modification access or if this
	 *  watchpoint hasn't suspended the given target.
	 */
	public boolean isAccessSuspend(IDebugTarget target);
}
