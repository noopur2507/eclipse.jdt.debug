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
package org.eclipse.jdt.internal.debug.ui.actions;


import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.jdt.internal.debug.ui.IJDIPreferencesConstants;
import org.eclipse.jdt.internal.debug.ui.JDIModelPresentation;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.ui.IViewSite;

/**
 * Allows setting of primitive display options for java variables
 */
public class PrimitiveOptionsAction extends AbstractDisplayOptionsAction {

	public PrimitiveOptionsAction() {
		super();
	}
	
	protected String[][] getPreferenceInfo() {
	    return new String[][] {
	            {IJDIPreferencesConstants.PREF_SHOW_HEX, JDIModelPresentation.SHOW_HEX_VALUES},
	            {IJDIPreferencesConstants.PREF_SHOW_CHAR, JDIModelPresentation.SHOW_CHAR_VALUES},
	            {IJDIPreferencesConstants.PREF_SHOW_UNSIGNED, JDIModelPresentation.SHOW_UNSIGNED_VALUES},
	    };
	}
	
	protected void applyPreference(String preference, String attribute, IDebugModelPresentation presentation) {
		boolean on = getBooleanPreferenceValue(getView().getSite().getId(), preference);
		presentation.setAttribute(attribute, (on ? Boolean.TRUE : Boolean.FALSE));
	}

    /* (non-Javadoc)
     * @see org.eclipse.jdt.internal.debug.ui.actions.AbstractDisplayOptionsAction#getDialog()
     */
    protected Dialog getDialog() {
        IViewSite viewSite = getView().getViewSite();
        return new PrimitiveOptionsDialog(viewSite.getShell(), viewSite.getId());
    }
}