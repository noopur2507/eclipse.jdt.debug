/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.debug.ui.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTargetExtension;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaWatchpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.JavaDebugUtils;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;
import org.eclipse.jdt.internal.debug.ui.DebugWorkingCopyManager;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.ui.IWorkingCopyManager;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IEditorStatusLine;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Toggles a line breakpoint in a Java editor.
 * 
 * @since 3.0
 */
public class ToggleBreakpointAdapter implements IToggleBreakpointsTargetExtension {
	
	private static final String EMPTY_STRING = ""; //$NON-NLS-1$
	
	/**
	 * Constructor
	 */
	public ToggleBreakpointAdapter() {
		// initialize helper in UI thread
		ActionDelegateHelper.getDefault();
	}

    /**
     * Convenience method for printing messages to the status line
     * @param message the message to be displayed
     * @param part the currently active workbench part
     */
    protected void report(final String message, final IWorkbenchPart part) {
        JDIDebugUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
            public void run() {
                IEditorStatusLine statusLine = (IEditorStatusLine) part.getAdapter(IEditorStatusLine.class);
                if (statusLine != null) {
                    if (message != null) {
                        statusLine.setMessage(true, message, null);
                    } else {
                        statusLine.setMessage(true, null, null);
                    }
                }
                if (message != null && JDIDebugUIPlugin.getActiveWorkbenchShell() != null) {
                    JDIDebugUIPlugin.getActiveWorkbenchShell().getDisplay().beep();
                }
            }
        });
    }

    /**
     * Returns the <code>IType</code> for the given selection
     * @param selection the current text selection
     * @return the <code>IType</code> for the text selection or <code>null</code>
     */
    protected IType getType(ITextSelection selection) {
        IMember member = ActionDelegateHelper.getDefault().getCurrentMember(selection);
        IType type = null;
        if (member instanceof IType) {
            type = (IType) member;
        } else if (member != null) {
            type = member.getDeclaringType();
        }
        // bug 52385: we don't want local and anonymous types from compilation
        // unit,
        // we are getting 'not-always-correct' names for them.
        try {
            while (type != null && !type.isBinary() && type.isLocal()) {
                type = type.getDeclaringType();
            }
        } catch (JavaModelException e) {
            JDIDebugUIPlugin.log(e);
        }
        return type;
    }

    /**
     * Returns the IType associated with the <code>IJavaElement</code> passed in
     * @param element the <code>IJavaElement</code> to get the type from
     * @return the corresponding <code>IType</code> for the <code>IJavaElement</code>, or <code>null</code> if there is not one.
     * @since 3.3
     */
    protected IType getType(IJavaElement element) {
    	switch(element.getElementType()) {
	    	case IJavaElement.FIELD: {
	    		return ((IField)element).getDeclaringType();
	    	}	
	    	case IJavaElement.METHOD: {
	    		return ((IMethod)element).getDeclaringType();
	    	}
	    	case IJavaElement.TYPE: {
	    		return (IType)element;
	    	}
	    	default: {
	    		return null;
	    	}
    	}
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#toggleLineBreakpoints(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
     */
    public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
    	toggleLineBreakpoints(part, selection, false);
    }
    
    /**
     * Toggles a line breakpoint.
     * @param part the currently active workbench part 
     * @param selection the current selection
     * @param bestMatch if we should make a best match or not
     */
    public void toggleLineBreakpoints(final IWorkbenchPart part, final ISelection selection, final boolean bestMatch) {
        Job job = new Job("Toggle Line Breakpoint") { //$NON-NLS-1$
            protected IStatus run(IProgressMonitor monitor) {
            	ITextEditor editor = getTextEditor(part);
                if (editor != null && selection instanceof ITextSelection) {
                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }
                    try {
	                    report(null, part);
	                    ISelection sel = selection;
	                	if(!(selection instanceof IStructuredSelection)) {
	                		sel = translateToMembers(part, selection);
	                	}
	                	if(isInterface(sel, part)) {
	                		report(ActionMessages.ToggleBreakpointAdapter_6, part);
	                    	return Status.OK_STATUS;
	                	}
	                    if(sel instanceof IStructuredSelection) {
	                    	IMember member = (IMember) ((IStructuredSelection)sel).getFirstElement();
	                    	IType type = null;
	                    	if(member.getElementType() == IJavaElement.TYPE) {
	                    		type = (IType) member;
	                    	}
	                    	else {
	                    		type = member.getDeclaringType();
	                    	}
	                    	String tname = createQualifiedTypeName(type);
	                    	IResource resource = BreakpointUtils.getBreakpointResource(type);
							int lnumber = ((ITextSelection) selection).getStartLine() + 1;
							IJavaLineBreakpoint existingBreakpoint = JDIDebugModel.lineBreakpointExists(resource, tname, lnumber);
							if (existingBreakpoint != null) {
								DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(existingBreakpoint, true);
								return Status.OK_STATUS;
							}
							Map attributes = new HashMap(10);
							IDocumentProvider documentProvider = editor.getDocumentProvider();
							if (documentProvider == null) {
							    return Status.CANCEL_STATUS;
							}
							IDocument document = documentProvider.getDocument(editor.getEditorInput());
							try {
								IRegion line = document.getLineInformation(lnumber - 1);
								int start = line.getOffset();
								int end = start + line.getLength() - 1;
								BreakpointUtils.addJavaBreakpointAttributesWithMemberDetails(attributes, type, start, end);
							} 	
							catch (BadLocationException ble) {JDIDebugUIPlugin.log(ble);}
							IJavaLineBreakpoint breakpoint = JDIDebugModel.createLineBreakpoint(resource, tname, lnumber, -1, -1, 0, true, attributes);
							new BreakpointLocationVerifierJob(document, breakpoint, lnumber, bestMatch, tname, type, resource, editor).schedule();
	                    }
	                    else {
	                    	report(ActionMessages.ToggleBreakpointAdapter_3, part);
	                    	return Status.OK_STATUS;
	                    }
                    } 
                    catch (CoreException ce) {return ce.getStatus();}
                }
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#canToggleLineBreakpoints(IWorkbenchPart,
     *      ISelection)
     */
    public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
    	if (isRemote(part, selection)) {
    		return false;
    	}
        return selection instanceof ITextSelection;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#toggleMethodBreakpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public void toggleMethodBreakpoints(final IWorkbenchPart part, final ISelection finalSelection) {
        Job job = new Job("Toggle Method Breakpoints") { //$NON-NLS-1$
            protected IStatus run(IProgressMonitor monitor) {
                if (monitor.isCanceled()) {
                    return Status.CANCEL_STATUS;
                }
                try {
                    report(null, part);
                    ISelection selection = finalSelection;
                    if(!(selection instanceof IStructuredSelection)) {
                    	selection = translateToMembers(part, selection);
                    }
                    if(isInterface(selection, part)) {
                    	report(ActionMessages.ToggleBreakpointAdapter_7, part);
                    	return Status.OK_STATUS;
                    }
                    if (selection instanceof IStructuredSelection) {
                        IMethod[] members = getMethods((IStructuredSelection) selection);
                        if (members.length == 0) {
                            report(ActionMessages.ToggleBreakpointAdapter_9, part); 
                            return Status.OK_STATUS;
                        }
                        IJavaBreakpoint breakpoint = null;
                        ISourceRange range = null;
                        Map attributes = null;
                        IType type = null;
                        String signature = null;
                        String mname = null;
                        for (int i = 0, length = members.length; i < length; i++) {
                            breakpoint = getMethodBreakpoint(members[i]);
                            if (breakpoint == null) {
                                int start = -1;
                                int end = -1;
                                range = members[i].getNameRange();
                                if (range != null) {
                                    start = range.getOffset();
                                    end = start + range.getLength();
                                }
                                attributes = new HashMap(10);
                                BreakpointUtils.addJavaBreakpointAttributes(attributes, members[i]);
                                type = members[i].getDeclaringType();
                                signature = members[i].getSignature();
                                mname = members[i].getElementName();
                                if (members[i].isConstructor()) {
                                	mname = "<init>"; //$NON-NLS-1$
                                    if (type.isEnum()) {
                                    	signature = "(Ljava.lang.String;I" + signature.substring(1); //$NON-NLS-1$
                                    }
                                }
                                if (!type.isBinary()) {
                                	signature = resolveMethodSignature(type, signature);
                                    if (signature == null) {
                                    	report(ActionMessages.ManageMethodBreakpointActionDelegate_methodNonAvailable, part); 
                                        return Status.OK_STATUS;
                                    }
                                }
                                JDIDebugModel.createMethodBreakpoint(BreakpointUtils.getBreakpointResource(members[i]), createQualifiedTypeName(type), mname, signature, true, false, false, -1, start, end, 0, true, attributes);
                            } else {
                            	DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(breakpoint, true);
                            }
                        }
                    }
                    else {
                    	report(ActionMessages.ToggleBreakpointAdapter_4, part);
                    	return Status.OK_STATUS;
                    }
                } catch (CoreException e) {
                    return e.getStatus();
                }
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }
    
    /**
     * Toggles a class load breakpoint
     * @param part the part
     * @param selection the current selection
     * @since 3.3
     */
    public void toggleClassBreakpoints(final IWorkbenchPart part, final ISelection selection) {
    	Job job = new Job("Toggle Class Load Breakpoints") { //$NON-NLS-1$
			protected IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled()) {
                    return Status.CANCEL_STATUS;
                }
                try {
                	report(null, part);
                	ISelection sel = selection;
                	if(!(selection instanceof IStructuredSelection)) {
                		sel = translateToMembers(part, selection);
                	}
                	if(isInterface(sel, part)) {
                    	report(ActionMessages.ToggleBreakpointAdapter_1, part);
                    	return Status.OK_STATUS;
                    }
					if(sel instanceof IStructuredSelection) {
						IMember member = (IMember)((IStructuredSelection)sel).getFirstElement();
						IType type = (IType) member;
						IBreakpoint existing = getClassLoadBreakpoint(type);
						if (existing != null) {
							existing.delete(); 
						}
						else {
							HashMap map = new HashMap(10);
							BreakpointUtils.addJavaBreakpointAttributes(map, type);
							ISourceRange range= type.getNameRange();
							int start = -1;
							int end = -1;
							if (range != null) {
								start = range.getOffset();
								end = start + range.getLength();
							}
							JDIDebugModel.createClassPrepareBreakpoint(BreakpointUtils.getBreakpointResource(member), createQualifiedTypeName(type), IJavaClassPrepareBreakpoint.TYPE_CLASS, start, end, true, map);
						}
					}
					else {
						report(ActionMessages.ToggleBreakpointAdapter_0, part);
						return Status.OK_STATUS;
					}
				} 
                catch (CoreException e) {
					return e.getStatus();
				}
				return Status.OK_STATUS;
			}
    	};
    	job.setSystem(true);
    	job.schedule();
    }
    
    /**
     * Returns the class load breakpoint for the specified type or null if none found
     * @param type the type to search for a class load breakpoint for
     * @return the existing class load breakpoint, or null if none
     * @throws CoreException
     * @since 3.3
     */
    protected IBreakpoint getClassLoadBreakpoint(IType type) throws CoreException {
    	IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(JDIDebugModel.getPluginIdentifier());
    	IBreakpoint existing = null;
    	IJavaBreakpoint breakpoint = null;
    	for (int i = 0; i < breakpoints.length; i++) {
			breakpoint = (IJavaBreakpoint) breakpoints[i];
			if (breakpoint instanceof IJavaClassPrepareBreakpoint && createQualifiedTypeName(type).equals(breakpoint.getTypeName())) {
				existing = breakpoint;
				break;
			}
		}
    	return existing;
    }
    	
    /**
     * Returns the package qualified name, while accounting for the fact that a source file might
     * not have a project
     * @param type the type to ensure the package qualified name is created for
     * @return the package qualified name
     * @since 3.3
     */
    private String createQualifiedTypeName(IType type) {
    	String tname = type.getFullyQualifiedName();
    	try {
	    	if(!type.getJavaProject().exists()) {
	    		String packName = null;
	    		if (type.isBinary()) {
	    			packName = type.getPackageFragment().getElementName();
	    		} else {
	    			IPackageDeclaration[] pd = type.getCompilationUnit().getPackageDeclarations();
					if(pd.length > 0) {
						packName = pd[0].getElementName();
					}
	    		}
				if(packName != null && !packName.equals(EMPTY_STRING)) {
					tname =  packName+"."+tname; //$NON-NLS-1$
				}
			}
	    	if(type.isAnonymous()) {
				//prune the $# from the name
				int idx = tname.indexOf('$');
				if(idx > -1) {
					tname = tname.substring(0, idx);
				}
	    	}
    	} 
    	catch (JavaModelException e) {}
    	return tname;
    }
    
    /**
     * gets the <code>IJavaElement</code> from the editor input
     * @param input the current editor input
     * @return the corresponding <code>IJavaElement</code>
     * @since 3.3
     */
    private IJavaElement getJavaElement(IEditorInput input) {
    	IJavaElement je = JavaUI.getEditorInputJavaElement(input);
    	if(je != null) {
    		return je;
    	}
    	//try to get from the working copy manager
    	return DebugWorkingCopyManager.getWorkingCopy(input, false);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#canToggleMethodBreakpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
    	if (isRemote(part, selection)) {
    		return false;
    	}
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection ss = (IStructuredSelection) selection;
            return getMethods(ss).length > 0;
        }
        return (selection instanceof ITextSelection) && isMethod((ITextSelection) selection, part);
    }
    
    /**
     * Returns whether the given part/selection is remote (viewing a repository)
     * 
     * @param part
     * @param selection
     * @return
     */
    protected boolean isRemote(IWorkbenchPart part, ISelection selection) {
    	if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			Object element = ss.getFirstElement();
			if(element instanceof IMember) {
				IMember member = (IMember) element;
				return !member.getJavaProject().getProject().exists();
			}
		}
    	ITextEditor editor = getTextEditor(part);
    	if (editor != null) {
    		IEditorInput input = editor.getEditorInput();
    		Object adapter = Platform.getAdapterManager().getAdapter(input, "org.eclipse.team.core.history.IFileRevision"); //$NON-NLS-1$
    		return adapter != null;
    	} 
    	return false;
    }
    
    /**
     * Returns the text editor associated with the given part or <code>null</code>
     * if none. In case of a multi-page editor, this method should be used to retrieve
     * the correct editor to perform the breakpoint operation on.
     * 
     * @param part workbench part
     * @return text editor part or <code>null</code>
     */
    protected ITextEditor getTextEditor(IWorkbenchPart part) {
    	if (part instanceof ITextEditor) {
    		return (ITextEditor) part;
    	}
    	return (ITextEditor) part.getAdapter(ITextEditor.class);
    }

    /**
     * Returns the methods from the selection, or an empty array
     * @param selection the selection to get the methods from
     * @return an array of the methods from the selection or an empty array
     */
    protected IMethod[] getMethods(IStructuredSelection selection) {
        if (selection.isEmpty()) {
            return new IMethod[0];
        }
        List methods = new ArrayList(selection.size());
        Iterator iterator = selection.iterator();
        while (iterator.hasNext()) {
            Object thing = iterator.next();
            try {
                if (thing instanceof IMethod) {
                	IMethod method = (IMethod) thing;
                	if (!Flags.isAbstract(method.getFlags())) {
                		methods.add(method);
                	}
                }
            } 
            catch (JavaModelException e) {}
        }
        return (IMethod[]) methods.toArray(new IMethod[methods.size()]);
    }

    /**
     * Returns if the text selection is a valid method or not
     * @param selection the text selection
     * @param part the associated workbench part
     * @return true if the selection is a valid method, false otherwise
     */
    private boolean isMethod(ITextSelection selection, IWorkbenchPart part) {
		ITextEditor editor = getTextEditor(part);
		if(editor != null) {
			IJavaElement element = getJavaElement(editor.getEditorInput());
			if(element != null) {
				try {
					if(element instanceof ICompilationUnit) {
						element = ((ICompilationUnit) element).getElementAt(selection.getOffset());
					}
					else if(element instanceof IClassFile) {
						element = ((IClassFile) element).getElementAt(selection.getOffset());
					}
					return element != null && element.getElementType() == IJavaElement.METHOD;
				} 
    			catch (JavaModelException e) {return false;}
			}
		}
    	return false;
    }
    
    /**
     * Returns a list of <code>IField</code> and <code>IJavaFieldVariable</code> in the given selection.
     * When an <code>IField</code> can be resolved for an <code>IJavaFieldVariable</code>, it is
     * returned in favour of the variable.
     *
     * @param selection
     * @return list of <code>IField</code> and <code>IJavaFieldVariable</code>, possibly empty
     * @throws CoreException
     */
    protected List getFields(IStructuredSelection selection) throws CoreException {
        if (selection.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        List fields = new ArrayList(selection.size());
        Iterator iterator = selection.iterator();
        while (iterator.hasNext()) {
            Object thing = iterator.next();
            if (thing instanceof IField) {
                fields.add(thing);
            } else if (thing instanceof IJavaFieldVariable) {
                IField field = getField((IJavaFieldVariable) thing);
                if (field == null) {
                	fields.add(thing);
                } else {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * Returns if the structured selection is itself or is part of an interface
     * @param selection the current selection
     * @return true if the selection is part of an interface, false otherwise
     * @since 3.2
     */
    private boolean isInterface(ISelection selection, IWorkbenchPart part) {
		try {
			ISelection sel = selection;
			if(!(sel instanceof IStructuredSelection)) {
				sel = translateToMembers(part, selection);
			}
			if(sel instanceof IStructuredSelection) {
				Object obj = ((IStructuredSelection)sel).getFirstElement();
				if(obj instanceof IMember) {
					IMember member = (IMember) ((IStructuredSelection)sel).getFirstElement();
					if(member.getElementType() == IJavaElement.TYPE) {
						return ((IType)member).isInterface();
					}
					return member.getDeclaringType().isInterface();
				}
				else if(obj instanceof IJavaFieldVariable) {
					IJavaFieldVariable var = (IJavaFieldVariable) obj;
					IType type = JavaDebugUtils.resolveType(var.getDeclaringType());
					return type != null && type.isInterface();
				}
			}
		} 
		catch (CoreException e1) {}
    	return false;
    }
    
    /**
     * Returns if the text selection is a field selection or not
     * @param selection the text selection
     * @param part the associated workbench part
     * @return true if the text selection is a valid field for a watchpoint, false otherwise
     * @since 3.3
     */
    private boolean isField(ITextSelection selection, IWorkbenchPart part) {
    	ITextEditor editor = getTextEditor(part);
    	if(editor != null) {
    		IJavaElement element = getJavaElement(editor.getEditorInput());
    		if(element != null) {
    			try {
	    			if(element instanceof ICompilationUnit) {
						element = ((ICompilationUnit) element).getElementAt(selection.getOffset());
	    			}
	    			else if(element instanceof IClassFile) {
	    				element = ((IClassFile) element).getElementAt(selection.getOffset());
	    			}
	    			return element != null && element.getElementType() == IJavaElement.FIELD;
				} 
    			catch (JavaModelException e) {return false;}		
    		}
    	}
    	return false;
    }
    
    /**
     * Determines if the selection is a field or not
     * @param selection the current selection
     * @return true if the selection is a field false otherwise
     */
    private boolean isFields(IStructuredSelection selection) {
        if (!selection.isEmpty()) {
        	try {
	            Iterator iterator = selection.iterator();
	            while (iterator.hasNext()) {
	                Object thing = iterator.next();
	                if (thing instanceof IField) {
	                	int flags = ((IField)thing).getFlags();
	                	return !Flags.isFinal(flags) & !(Flags.isFinal(flags) & Flags.isStatic(flags));
	                }
	                else if(thing instanceof IJavaFieldVariable) {
	                	IJavaFieldVariable fv = (IJavaFieldVariable)thing;
	                	return !fv.isFinal() & !(fv.isFinal() & fv.isStatic());
	                }
	            }
        	}
        	catch(JavaModelException e) {return false;}
        	catch(DebugException de) {return false;}
        }
        return false;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#toggleWatchpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public void toggleWatchpoints(final IWorkbenchPart part, final ISelection finalSelection) {
        Job job = new Job("Toggle Watchpoints") { //$NON-NLS-1$
            protected IStatus run(IProgressMonitor monitor) {
                if (monitor.isCanceled()) {
                    return Status.CANCEL_STATUS;
                }
                try {
                    report(null, part);
                    ISelection selection = finalSelection;
                    if(!(selection instanceof IStructuredSelection)) {
                    	selection = translateToMembers(part, finalSelection);
                    }
                    if(isInterface(selection, part)) {
                		report(ActionMessages.ToggleBreakpointAdapter_5, part);
                		return Status.OK_STATUS;
                	}
                    boolean allowed = false;
	                if (selection instanceof IStructuredSelection) {
	                	List fields = getFields((IStructuredSelection) selection);
	                    if (fields.isEmpty()) {
	                        report(ActionMessages.ToggleBreakpointAdapter_10, part); 
	                        return Status.OK_STATUS;
	                    }
	                    Iterator theFields = fields.iterator();
	                    IField javaField = null;
	                    IResource resource = null;
                        String typeName = null;
                        String fieldName = null;
                        Object element = null;
                        Map attributes = null;
                        IJavaBreakpoint breakpoint = null;
	                    while (theFields.hasNext()) {
	                        element = theFields.next();
	                        if (element instanceof IField) {
								javaField = (IField) element;
								IType type = javaField.getDeclaringType();
								typeName = createQualifiedTypeName(type);
								fieldName = javaField.getElementName();
								int f = javaField.getFlags();
								boolean fin = Flags.isFinal(f);
								allowed = !(fin) & !(Flags.isStatic(f) & fin);
							}
	                        breakpoint = getWatchpoint(typeName, fieldName);
	                        if (breakpoint == null) {
	                        	if(!allowed) {
	                        		toggleLineBreakpoints(part, finalSelection);
	                        		return Status.OK_STATUS;
	                        	}
	                        	int start = -1;
	                            int end = -1;
	                            attributes = new HashMap(10);
                                IType type = javaField.getDeclaringType();
                                ISourceRange range = javaField.getNameRange();
                                if (range != null) {
                                    start = range.getOffset();
                                    end = start + range.getLength();
                                }
                                BreakpointUtils.addJavaBreakpointAttributes(attributes, javaField);
                                resource = BreakpointUtils.getBreakpointResource(type);
	                        	JDIDebugModel.createWatchpoint(resource, typeName, fieldName, -1, start, end, 0, true, attributes);
	                        } else {
	                            DebugPlugin.getDefault().getBreakpointManager().removeBreakpoint(breakpoint, true);
	                        }
	                    }
                    }
                    else {
                    	report(ActionMessages.ToggleBreakpointAdapter_2, part);
                    	return Status.OK_STATUS;
                    }
                } catch (CoreException e) {return e.getStatus();}
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        job.schedule();
    }
    
    /**
     * Returns any existing watchpoint for the given field, or <code>null</code> if none.
     * 
     * @param typeName fully qualified type name on which watchpoint may exist
     * @param fieldName field name
     * @return any existing watchpoint for the given field, or <code>null</code> if none
     * @throws CoreException
     */
    private IJavaWatchpoint getWatchpoint(String typeName, String fieldName) throws CoreException {
        IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
        IBreakpoint[] breakpoints = breakpointManager.getBreakpoints(JDIDebugModel.getPluginIdentifier());
        for (int i = 0; i < breakpoints.length; i++) {
            IBreakpoint breakpoint = breakpoints[i];
            if (breakpoint instanceof IJavaWatchpoint) {
                IJavaWatchpoint watchpoint = (IJavaWatchpoint) breakpoint;
                if (typeName.equals(watchpoint.getTypeName()) && fieldName.equals(watchpoint.getFieldName())) {
                    return watchpoint;
                }
            }
        }
        return null;
    }

    /**
     * Returns the resolved method signature for the specified type
     * @param type the declaring type the method is contained in
     * @param methodSignature the method signature to resolve
     * @return the resolved method signature
     * @throws JavaModelException
     */
    public static String resolveMethodSignature(IType type, String methodSignature) throws JavaModelException {
        String[] parameterTypes = Signature.getParameterTypes(methodSignature);
        int length = parameterTypes.length;
        String[] resolvedParameterTypes = new String[length];
        for (int i = 0; i < length; i++) {
            resolvedParameterTypes[i] = resolveType(type, parameterTypes[i]);
            if (resolvedParameterTypes[i] == null) {
                return null;
            }
        }
        String resolvedReturnType = resolveType(type, Signature.getReturnType(methodSignature));
        if (resolvedReturnType == null) {
            return null;
        }
        return Signature.createMethodSignature(resolvedParameterTypes, resolvedReturnType);
    }

    /**
     * Resolves the the type for its given signature
     * @param type the type
     * @param typeSignature the types signature
     * @return the resolved type name
     * @throws JavaModelException
     */
    private static String resolveType(IType type, String typeSignature) throws JavaModelException {
        int count = Signature.getArrayCount(typeSignature);
        String elementTypeSignature = Signature.getElementType(typeSignature);
        if (elementTypeSignature.length() == 1) {
            // no need to resolve primitive types
            return typeSignature;
        }
        String elementTypeName = Signature.toString(elementTypeSignature);
        String[][] resolvedElementTypeNames = type.resolveType(elementTypeName);
        if (resolvedElementTypeNames == null || resolvedElementTypeNames.length != 1) {
        	// check if type parameter
            ITypeParameter[] typeParameters = type.getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
    			ITypeParameter parameter = typeParameters[i];
    			if (parameter.getElementName().equals(elementTypeName)) {
    				String[] bounds = parameter.getBounds();
    				if (bounds.length == 0) {
    					return "Ljava/lang/Object;"; //$NON-NLS-1$
    				} else {
						String bound = Signature.createTypeSignature(bounds[0], false);
						return resolveType(type, bound);
    				}
    			}
    		}
            // the type name cannot be resolved
            return null;
        }

        String[] types = resolvedElementTypeNames[0];
        types[1] = types[1].replace('.', '$');
        
        String resolvedElementTypeName = Signature.toQualifiedName(types);
        String resolvedElementTypeSignature = EMPTY_STRING;
        if(types[0].equals(EMPTY_STRING)) {
        	resolvedElementTypeName = resolvedElementTypeName.substring(1);
        	resolvedElementTypeSignature = Signature.createTypeSignature(resolvedElementTypeName, true);
        }
        else {
        	resolvedElementTypeSignature = Signature.createTypeSignature(resolvedElementTypeName, true).replace('.', '/');
        }

        return Signature.createArraySignature(resolvedElementTypeSignature, count);
    }

    /**
     * Returns the resource associated with the specified editor part
     * @param editor the currently active editor part
     * @return the corresponding <code>IResource</code> from the editor part
     */
    protected static IResource getResource(IEditorPart editor) {
        IEditorInput editorInput = editor.getEditorInput();
        IResource resource = (IResource) editorInput.getAdapter(IFile.class);
        if (resource == null) {
            resource = ResourcesPlugin.getWorkspace().getRoot();
        }
        return resource;
    }

    /**
     * Returns a handle to the specified method or <code>null</code> if none.
     * 
     * @param editorPart
     *            the editor containing the method
     * @param typeName
     * @param methodName
     * @param signature
     * @return handle or <code>null</code>
     */
    protected IMethod getMethodHandle(IEditorPart editorPart, String typeName, String methodName, String signature) throws CoreException {
        IJavaElement element = (IJavaElement) editorPart.getEditorInput().getAdapter(IJavaElement.class);
        IType type = null;
        if (element instanceof ICompilationUnit) {
            IType[] types = ((ICompilationUnit) element).getAllTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i].getFullyQualifiedName().equals(typeName)) {
                    type = types[i];
                    break;
                }
            }
        } else if (element instanceof IClassFile) {
            type = ((IClassFile) element).getType();
        }
        if (type != null) {
            String[] sigs = Signature.getParameterTypes(signature);
            return type.getMethod(methodName, sigs);
        }
        return null;
    }

    /**
     * Returns the <code>IJavaBreakpoint</code> from the specified <code>IMember</code>
     * @param element the element to get the breakpoint from
     * @return the current breakpoint from the element or <code>null</code>
     */
    protected IJavaBreakpoint getMethodBreakpoint(IMember element) {
        IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
        IBreakpoint[] breakpoints = breakpointManager.getBreakpoints(JDIDebugModel.getPluginIdentifier());
        if (element instanceof IMethod) {
            IMethod method = (IMethod) element;
            for (int i = 0; i < breakpoints.length; i++) {
                IBreakpoint breakpoint = breakpoints[i];
                if (breakpoint instanceof IJavaMethodBreakpoint) {
                    IJavaMethodBreakpoint methodBreakpoint = (IJavaMethodBreakpoint) breakpoint;
                    IMember container = null;
                    try {
                        container = BreakpointUtils.getMember(methodBreakpoint);
                    } catch (CoreException e) {
                        JDIDebugUIPlugin.log(e);
                        return null;
                    }
                    if (container == null) {
                        try {
                            if (method.getDeclaringType().getFullyQualifiedName().equals(methodBreakpoint.getTypeName()) && 
                            		method.getElementName().equals(methodBreakpoint.getMethodName()) && 
                            		methodBreakpoint.getMethodSignature().equals(resolveMethodSignature(method.getDeclaringType(), method.getSignature()))) {
                                return methodBreakpoint;
                            }
                        } catch (CoreException e) {
                            JDIDebugUIPlugin.log(e);
                        }
                    } else {
                        if (container instanceof IMethod) {
                        	if(method.getDeclaringType().equals(container.getDeclaringType())) {
	                            if (method.getDeclaringType().getFullyQualifiedName().equals(container.getDeclaringType().getFullyQualifiedName())) {
	                                if (method.isSimilar((IMethod) container)) {
	                                    return methodBreakpoint;
	                                }
	                            }
                        	}
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the compilation unit from the editor
     * @param editor the editor to get the compilation unit from
     * @return the compilation unit or <code>null</code>
     * @throws CoreException
     */
    protected CompilationUnit parseCompilationUnit(ITextEditor editor) throws CoreException {
        IEditorInput editorInput = editor.getEditorInput();
        IDocumentProvider documentProvider = editor.getDocumentProvider();
        if (documentProvider == null) {
            throw new CoreException(Status.CANCEL_STATUS);
        }
        IDocument document = documentProvider.getDocument(editorInput);
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(document.get().toCharArray());
        return (CompilationUnit) parser.createAST(null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTarget#canToggleWatchpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
    	if (isRemote(part, selection)) {
    		return false;
    	}
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection ss = (IStructuredSelection) selection;
            return isFields(ss);
        }
        return (selection instanceof ITextSelection) && isField((ITextSelection) selection, part);
    }
    
    /**
     * Returns a selection of the member in the given text selection, or the
     * original selection if none.
     * 
     * @param part
     * @param selection
     * @return a structured selection of the member in the given text selection,
     *         or the original selection if none
     * @exception CoreException
     *                if an exception occurs
     */
    protected ISelection translateToMembers(IWorkbenchPart part, ISelection selection) throws CoreException {
    	ITextEditor textEditor = getTextEditor(part);
        if (textEditor != null && selection instanceof ITextSelection) {
            ITextSelection textSelection = (ITextSelection) selection;
            IEditorInput editorInput = textEditor.getEditorInput();
            IDocumentProvider documentProvider = textEditor.getDocumentProvider();
            if (documentProvider == null) {
                throw new CoreException(Status.CANCEL_STATUS);
            }
            IDocument document = documentProvider.getDocument(editorInput);
            int offset = textSelection.getOffset();
            if (document != null) {
                try {
                    IRegion region = document.getLineInformationOfOffset(offset);
                    int end = region.getOffset() + region.getLength();
                    while (Character.isWhitespace(document.getChar(offset)) && offset < end) {
                        offset++;
                    }
                } catch (BadLocationException e) {}
            }
            IMember m = null;
            IClassFile classFile = (IClassFile) editorInput.getAdapter(IClassFile.class);
            if (classFile != null) {
                IJavaElement e = classFile.getElementAt(offset);
                if (e instanceof IMember) {
                    m = (IMember) e;
                }
            } else {
                IWorkingCopyManager manager = JavaUI.getWorkingCopyManager();
                ICompilationUnit unit = manager.getWorkingCopy(editorInput);
                if (unit != null) {
                    synchronized (unit) {
                        unit.reconcile(ICompilationUnit.NO_AST , false, null, null);
                    }
                }
                else {
                	unit = DebugWorkingCopyManager.getWorkingCopy(editorInput, false);
                	if(unit != null) {
	                	synchronized (unit) {
	                		unit.reconcile(ICompilationUnit.NO_AST, false, null, null);
	                	}
                	}
                }
                IJavaElement e = unit.getElementAt(offset);
                if (e instanceof IMember) {
                    m = (IMember) e;
                }
            }
            if (m != null) {
                return new StructuredSelection(m);
            }
        }
        return selection;
    }

    /**
     * Return the associated IField (Java model) for the given
     * IJavaFieldVariable (JDI model)
     */
    private IField getField(IJavaFieldVariable variable) throws CoreException {
        String varName = null;
        try {
            varName = variable.getName();
        } catch (DebugException x) {
            JDIDebugUIPlugin.log(x);
            return null;
        }
        IField field;
        IJavaType declaringType = variable.getDeclaringType(); 
        IType type = JavaDebugUtils.resolveType(declaringType);
        if (type != null) {
            field = type.getField(varName);
            if (field.exists()) {
                return field;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTargetExtension#toggleBreakpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public void toggleBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
    	ISelection sel = translateToMembers(part, selection);
    	if(sel instanceof IStructuredSelection) {
    		IMember member = (IMember) ((IStructuredSelection)sel).getFirstElement();
    		int mtype = member.getElementType();
    		if(mtype == IJavaElement.FIELD || mtype == IJavaElement.METHOD) {
    			// remove line breakpoint if present first
    	    	if (selection instanceof ITextSelection) {
    				ITextSelection ts = (ITextSelection) selection;
    				IJavaLineBreakpoint breakpoint = JDIDebugModel.lineBreakpointExists(createQualifiedTypeName(member.getDeclaringType()), ts.getStartLine() + 1);
    				if (breakpoint != null) {
    					breakpoint.delete();
    					return;
    				}
    				CompilationUnit unit = parseCompilationUnit(getTextEditor(part));
        			ValidBreakpointLocationLocator loc = new ValidBreakpointLocationLocator(unit, ts.getStartLine()+1, true, true);
        			unit.accept(loc);
        			if(loc.getLocationType() == ValidBreakpointLocationLocator.LOCATION_METHOD) {
        				toggleMethodBreakpoints(part, sel);
        			}
        			else if(loc.getLocationType() == ValidBreakpointLocationLocator.LOCATION_FIELD) {
        				toggleWatchpoints(part, ts);
        			}
        			else if(loc.getLocationType() == ValidBreakpointLocationLocator.LOCATION_LINE) {
        				toggleLineBreakpoints(part, ts);
        			}
    			} 
    		}
    		else if(member.getElementType() == IJavaElement.TYPE) {
    			toggleClassBreakpoints(part, sel);
    		}
    		else {
    			//fall back to old behavior, always create a line breakpoint
    			toggleLineBreakpoints(part, selection, true);
    		}
    	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.debug.ui.actions.IToggleBreakpointsTargetExtension#canToggleBreakpoints(org.eclipse.ui.IWorkbenchPart,
     *      org.eclipse.jface.viewers.ISelection)
     */
    public boolean canToggleBreakpoints(IWorkbenchPart part, ISelection selection) {
    	if (isRemote(part, selection)) {
    		return false;
    	}    	
        return canToggleLineBreakpoints(part, selection);
    }
}
