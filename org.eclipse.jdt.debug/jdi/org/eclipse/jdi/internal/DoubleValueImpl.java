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
package org.eclipse.jdi.internal;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.jdi.internal.jdwp.JdwpID;

import com.sun.jdi.DoubleValue;
import com.sun.jdi.Type;

/**
 * this class implements the corresponding interfaces
 * declared by the JDI specification. See the com.sun.jdi package
 * for more information.
 *
 */
public class DoubleValueImpl extends PrimitiveValueImpl implements DoubleValue {
	/** JDWP Tag. */
	public static final byte tag = JdwpID.DOUBLE_TAG;

	/**
	 * Creates new instance.
	 */
	public DoubleValueImpl(VirtualMachineImpl vmImpl, Double value) {
		super("DoubleValue", vmImpl, value); //$NON-NLS-1$
	}
	
	/**
	 * @returns tag.
	 */
	public byte getTag() {
		return tag;
	}

	/**
	 * @returns type of value.
   	 */
	public Type type() {
		return virtualMachineImpl().getDoubleType();
	}

	/**
	 * @returns Value.
	 */
	public double value() {
		return doubleValue();
	}
	
	/**
	 * @return Reads and returns new instance.
	 */
	public static DoubleValueImpl read(MirrorImpl target, DataInputStream in) throws IOException {
		VirtualMachineImpl vmImpl = target.virtualMachineImpl();
		double value = target.readDouble("doubleValue", in); //$NON-NLS-1$
		return new DoubleValueImpl(vmImpl, new Double(value));
	}
	
	/**
	 * Writes value without value tag.
	 */
	public void write(MirrorImpl target, DataOutputStream out) throws IOException {
		target.writeDouble(((Double)fValue).doubleValue(), "doubleValue", out); //$NON-NLS-1$
	}
}