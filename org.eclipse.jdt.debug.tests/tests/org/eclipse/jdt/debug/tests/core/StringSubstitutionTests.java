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
package org.eclipse.jdt.debug.tests.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.internal.variables.StringVariableManager;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.variables.IDynamicVariable;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.IValueVariable;
import org.eclipse.core.variables.IValueVariableListener;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.stringsubstitution.SelectedResourceManager;
import org.eclipse.jdt.debug.tests.AbstractDebugTest;
import org.eclipse.jdt.internal.launching.JavaLocalApplicationLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;


/**
 * Tests string substitutions
 */
public class StringSubstitutionTests extends AbstractDebugTest implements IValueVariableListener {
	
	// change notification
	public IValueVariable[] fAdded;
	public IValueVariable[] fChanged;
	public IValueVariable[] fRemoved;
	
	public StringSubstitutionTests(String name) {
		super(name);
	}

	/**
	 * Returns the value variable with the given name or <code>null</code>
	 * if none.
	 * 
	 * @param name variable name 
	 * @return value variable with the given name or <code>null</code>
	 * if none
	 */
	protected IValueVariable getValueVariable(String name) {
		return VariablesPlugin.getDefault().getStringVariableManager().getValueVariable(name);
	}
	
	/**
	 * Returns the context variable with the given name or <code>null</code>
	 * if none.
	 * 
	 * @param name variable name 
	 * @return context variable with the given name or <code>null</code>
	 * if none
	 */
	protected IDynamicVariable getContextVariable(String name) {
		return VariablesPlugin.getDefault().getStringVariableManager().getDynamicVariable(name);
	}	
	
	/**
	 * Tests value variable initializer
	 * 
	 * @throws Exception
	 */
	public void testValueInitializer() throws Exception {
		IValueVariable variable = getValueVariable("VALUE_VAR_WITH_INITIALIZER");
		assertNotNull("Missing VALUE_VAR_WITH_INITIALIZER", variable);
		String value = variable.getValue();
		assertEquals("value should be 'initialized-value'", "initialized-value", value);
	}	
	
	/**
	 * Tests value variable with an initial value
	 */
	public void testValueSupplied() throws Exception {
		IValueVariable variable = getValueVariable("VALUE_VAR_WITH_VALUE");
		assertNotNull("Missing VALUE_VAR_WITH_VALUE", variable);
		String value = variable.getValue();
		assertEquals("initial-value", value);		
	}
	
	/**
	 * Tests a context variable with an argument
	 */
	public void testContextWithArg() throws CoreException {
		IDynamicVariable variable = getContextVariable("SAMPLE_DYNAMIC_VAR");
		assertNotNull("Missing SAMPLE_DYNAMIC_VAR", variable);
		String value = variable.getValue("ONE");
		assertEquals("the arg is ONE", value);
	}
	
	/**
	 * Tests a context variable with no argument
	 */	
	public void testContextWithoutArg() throws CoreException {
		IDynamicVariable variable = getContextVariable("SAMPLE_DYNAMIC_VAR");
		assertNotNull("Missing SAMPLE_DYNAMIC_VAR", variable);
		String value = variable.getValue(null);
		assertEquals("no arg", value);		
	}
	
	/**
	 * Test an expression with no variable references
	 */
	public void testNoReferences() throws CoreException {
		String expression = "no references";
		String result = doSubs(expression);
		assertEquals(expression, result);
	}
	
	/**
	 * Test an expression with a value variable reference
	 */
	public void testValueVarReference() throws CoreException {
		String expression = "something ${VALUE_VAR_WITH_INITIALIZER} else";
		String result = doSubs(expression);
		assertEquals("something initialized-value else", result);
	}
	
	/**
	 * Test an expression with a context variable reference
	 */
	public void testContextVarReferenceNoArgs() throws CoreException {
		String expression = "something ${SAMPLE_DYNAMIC_VAR} else";
		String result = doSubs(expression);
		assertEquals("something no arg else", result);
	}
	
	/**
	 * Test an expression with a context variable reference and arg
	 */
	public void testContextVarReferenceWithArg() throws CoreException {
		String expression = "something ${SAMPLE_DYNAMIC_VAR:TWO} else";
		String result = doSubs(expression);
		assertEquals("something the arg is TWO else", result);
	}	
	
	/**
	 * Test an expression with multiple references
	 */
	public void testMultipleReferences() throws CoreException {
		String expression = "${SAMPLE_DYNAMIC_VAR:TWO} ${VALUE_VAR_WITH_INITIALIZER} ${VALUE_VAR_WITH_VALUE}";
		String result = doSubs(expression);
		assertEquals("the arg is TWO initialized-value initial-value", result);
	}	
		
	/**
	 * Perfrom substitutions on the given expression.
	 * 
	 * @param expression source expression
	 * @return the result after performing substitutions
	 */
	protected String doSubs(String expression) throws CoreException {
		IStringVariableManager manager = StringVariableManager.getDefault();
		return manager.performStringSubstitution(expression);
	}

	/**
	 * Test nested variables
	 */
	public void testNestedReferences() throws CoreException {
		String expression = "${SAMPLE_DYNAMIC_VAR:${VALUE_VAR_WITH_VALUE}}";
		String result = doSubs(expression);
		assertEquals("the arg is initial-value", result);
	}
	
	/**
	 * Test recursive resolution
	 */
	public void testRecursiveReferences() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable variable = manager.newValueVariable("my_var", null);
		try {
			manager.addVariables(new IValueVariable[]{variable});
			variable.setValue("${SAMPLE_DYNAMIC_VAR:recurse}");
			String expression = "something ${my_var} else";
			String result = doSubs(expression);
			assertEquals("something the arg is recurse else", result);
		} finally {
			manager.removeVariables(new IValueVariable[]{variable});
		}
	}
	
	/**
	 * Test for simple cycling references like ${A} -> ${B} -> ${A}
	 */
	public void testSimpleReferenceCycle() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable var1 = manager.newValueVariable("var_A", null);
		IValueVariable var2 = manager.newValueVariable("var_B", null);
		try {
			manager.addVariables(new IValueVariable[]{var1, var2});
			var1.setValue("${var_B}");
			var2.setValue("${var_A}");
			String expression = "something ${var_A} else";
			try {
				doSubs(expression);
				assertTrue("Expected cycle to cause exception", false);
			} catch (CoreException ce){
				IStatus status = ce.getStatus();
				if (status.getSeverity() != IStatus.ERROR || status.getCode() != VariablesPlugin.REFERENCE_CYCLE_ERROR)
					throw ce;				
			}
		} finally {
			manager.removeVariables(new IValueVariable[]{var1, var2});
		}
	}
	
	/**
	 * Test for cycling references in cases where original expression is never duplicated
	 * eg. ${A} -> ${B}z -> ${A}z -> ${B}zz
	 */
	public void testReferenceCycleWithUniqueExpression() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable var1 = manager.newValueVariable("var_A", null);
		IValueVariable var2 = manager.newValueVariable("var_B", null);
		try {
			manager.addVariables(new IValueVariable[]{var1, var2});
			var1.setValue("${var_B} plus foo");
			var2.setValue("${var_A}");
			String expression = "something ${var_A} else";
			try {
				doSubs(expression);
				assertTrue("Expected cycle to cause exception", false);
			} catch (CoreException ce){
				IStatus status = ce.getStatus();
				if (status.getSeverity() != IStatus.ERROR || status.getCode() != VariablesPlugin.REFERENCE_CYCLE_ERROR)
					throw ce;				
			}
		} finally {
			manager.removeVariables(new IValueVariable[]{var1, var2});
		}
	}
	
	/**
	 * Test for proper handling of infinite variable reference cycles in cases where
	 * resolving two variables results in a third
	 * eg 	${A} = "${"
	 * 		${B} = "C}
	 * 		${C} = ${A}${B}
	 * 		In this case any references to ${C} or ${A}${B} cannot be resolved 
	 */
	public void testDividedRecursiveReferenceCycles() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable var1 = manager.newValueVariable("var_A", null);
		IValueVariable var2 = manager.newValueVariable("var_B", null);
		IValueVariable var3 = manager.newValueVariable("var_C", null);
		try {
			manager.addVariables(new IValueVariable[]{var1, var2, var3});
			var1.setValue("${");
			var2.setValue("var_C}");
			var3.setValue("${var_A}${var_B}");
			String expression = "${var_A}${var_B}";
			try {
				doSubs(expression);
				assertTrue("Expected cycle to cause exception", false);
			} catch (CoreException ce){
				IStatus status = ce.getStatus();
				if (status.getSeverity() != IStatus.ERROR || status.getCode() != VariablesPlugin.REFERENCE_CYCLE_ERROR)
					throw ce;				
			}
		} finally {
			manager.removeVariables(new IValueVariable[]{var1, var2, var3});
		}		
	}
	
	/**
	 * Test a string with an open ended reference. The open ended expression
	 * will not be translated.
	 */
	public void testOpenEndedBrace() throws CoreException {
		String expression = "${SAMPLE_DYNAMIC_VAR:${VALUE_VAR_WITH_VALUE}";
		String result = doSubs(expression);
		assertEquals("${SAMPLE_DYNAMIC_VAR:initial-value", result);
	}
	
	/**
	 * Test that we receive proper add notification.
	 * 
	 * @throws CoreException
	 */
	public void testAddNotificaiton() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		List vars = new ArrayList();
		IValueVariable one = manager.newValueVariable("var_one", null);
		IValueVariable two = manager.newValueVariable("var_two", null);
		vars.add(one);
		vars.add(two);
		try {
			manager.addVariables(new IValueVariable[]{one, two});
			assertNotNull("no add notifications", fAdded);
			for (int i = 0; i < fAdded.length; i++) {
				vars.remove(fAdded[i]);
			}
			assertEquals("collection should be empty", 0, vars.size());
		} finally {
			manager.removeVariables(new IValueVariable[]{one, two});
		}
	}
	
	/**
	 * Test that we receive proper change notification.
	 * 
	 * @throws CoreException
	 */
	public void testChangeNotificaiton() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		IValueVariable one = manager.newValueVariable("var_one", null);
		IValueVariable two = manager.newValueVariable("var_two", null);
		try {
			manager.addVariables(new IValueVariable[]{one, two});
			one.setValue("1");
			assertNotNull("no change notifications", fChanged);
			assertEquals("should be 1 change notification", 1, fChanged.length);
			assertEquals(one, fChanged[0]);
			two.setValue("2");
			assertNotNull("no change notifications", fChanged);
			assertEquals("should be 1 change notification", 1, fChanged.length);
			assertEquals(two, fChanged[0]);			
		} finally {
			manager.removeVariables(new IValueVariable[]{one, two});
		}
	}	
	
	/**
	 * Test that we receive proper remove notification.
	 * 
	 * @throws CoreException
	 */
	public void testRemoveNotificaiton() throws CoreException {
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		List vars = new ArrayList();
		IValueVariable one = manager.newValueVariable("var_one", null);
		IValueVariable two = manager.newValueVariable("var_two", null);
		vars.add(one);
		vars.add(two);
		try {
			manager.addVariables(new IValueVariable[]{one, two});
			manager.removeVariables(new IValueVariable[]{one, two});
			assertNotNull("no remove notifications", fRemoved);
			for (int i = 0; i < fRemoved.length; i++) {
				vars.remove(fRemoved[i]);
			}
			assertEquals("collection should be empty", 0, vars.size());
		} finally {
			manager.removeVariables(new IValueVariable[]{one, two});
		}
	}	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		fAdded = null;
		fChanged = null;
		fRemoved = null;
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		manager.addValueVariableListener(this);
		SelectedResourceManager.setDebug(true);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
		manager.removeValueVariableListener(this);
		fAdded = null;
		fChanged = null;
		fRemoved = null;
		SelectedResourceManager.setDebug(false);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.stringsubstitution.IValueVariableListener#variablesAdded(org.eclipse.debug.internal.core.stringsubstitution.IValueVariable[])
	 */
	public void variablesAdded(IValueVariable[] variables) {
		fAdded = variables;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.stringsubstitution.IValueVariableListener#variablesChanged(org.eclipse.debug.internal.core.stringsubstitution.IValueVariable[])
	 */
	public void variablesChanged(IValueVariable[] variables) {
		fChanged = variables;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.core.stringsubstitution.IValueVariableListener#variablesRemoved(org.eclipse.debug.internal.core.stringsubstitution.IValueVariable[])
	 */
	public void variablesRemoved(IValueVariable[] variables) {
		fRemoved = variables;
	}
	
	/**
	 * Test the <code>${workspace_loc}</code> variable.
	 */
	public void testWorkspaceLoc() throws CoreException {
		String expression = "${workspace_loc}";
		String result = doSubs(expression);
		assertEquals(ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString(), result);
	}

	/**
	 * Test the <code>${workspace_loc}</code> variable with an argument
	 */
	public void testWorkspaceLocArg() throws CoreException {
		String expression = "${workspace_loc:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFolder("src").getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${project_loc}</code> variable with a project name argument
	 */
	public void testProjectLocArgProjectName() throws CoreException {
		String expression = "${project_loc:DebugTests}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${project_loc}</code> variable with a folder path argument
	 */
	public void testProjectLocArgFolderPath() throws CoreException {
		String expression = "${project_loc:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${project_loc}</code> variable with a folder selected
	 */
	public void testProjectLocSelectFolder() throws CoreException {
		String expression = "${project_loc}";
		IResource resource = getJavaProject().getProject().getFolder("src");
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getProject().getLocation().toOSString(), result);
	}
		
	/**
	 * Test the <code>${project_path}</code> variable with a project name argument
	 */
	public void testProjectPathArgProjectName() throws CoreException {
		String expression = "${project_path:DebugTests}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFullPath().toOSString(), result);
	}
	
	/**
	 * Test the <code>${project_path}</code> variable with a file selected
	 */
	public void testProjectPathSelectFile() throws CoreException {
		String expression = "${project_path}";
		IResource resource = getJavaProject().getProject().getFile(".classpath");
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getProject().getFullPath().toOSString(), result);
	}				
	
	/**
	 * Test the <code>${project_path}</code> variable with a folder path argument
	 */
	public void testProjectPathArgFolderPath() throws CoreException {
		String expression = "${project_path:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFullPath().toOSString(), result);
	}
	
	/**
	 * Test the <code>${project_name}</code> variable with a project name argument
	 */
	public void testProjectNameArgProjectName() throws CoreException {
		String expression = "${project_name:DebugTests}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getName(), result);
	}
	
	/**
	 * Test the <code>${project_name}</code> variable with a project selected
	 */
	public void testProjectNameSelectProject() throws CoreException {
		String expression = "${project_name}";
		IResource resource = getJavaProject().getProject();
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getProject().getName(), result);
	}				
	
	/**
	 * Test the <code>${project_name}</code> variable with a folder path argument
	 */
	public void testProjectNameArgFolderPath() throws CoreException {
		String expression = "${project_name:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getName(), result);
	}
	
	/**
	 * Test the <code>${container_loc}</code> variable with a folder name argument.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerLocArgFolderName() throws CoreException {
		String expression = "${container_loc:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${container_loc}</code> variable with a folder selected.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerLocSelectFolder() throws CoreException {
		String expression = "${container_loc}";
		IResource resource = getJavaProject().getProject().getFolder("src");
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getParent().getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${container_path}</code> variable with a folder name argument.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerPathArgFolderName() throws CoreException {
		String expression = "${container_path:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFullPath().toOSString(), result);
	}
	
	/**
	 * Test the <code>${container_path}</code> variable with a folder selected.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerPathSelectFolder() throws CoreException {
		String expression = "${container_path}";
		IResource resource = getJavaProject().getProject().getFolder("src"); 
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getParent().getFullPath().toOSString(), result);
	}
	
	/**
	 * Test the <code>${container_name}</code> variable with a folder name argument.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerNameArgFolderName() throws CoreException {
		String expression = "${container_name:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getName(), result);
	}
	
	/**
	 * Test the <code>${container_name}</code> variable with a folder selected.
	 * Will resolve to the container of the specified folder.
	 */
	public void testContainerNameSelectFolder() throws CoreException {
		String expression = "${container_name}";
		IResource resource = getJavaProject().getProject().getFolder("src"); 
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getParent().getName(), result);
	}
	
	/**
	 * Test the <code>${resource_loc}</code> variable with a folder name argument.
	 */
	public void testResourceLocArgFolderName() throws CoreException {
		String expression = "${resource_loc:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFolder("src").getLocation().toOSString(), result);
	}
	
	/**
	 * Test the <code>${resource_loc}</code> variable with a folder selected.
	 */
	public void testResourceLocSelectFolder() throws CoreException {
		String expression = "${resource_loc}";
		IResource resource = getJavaProject().getProject().getFolder("src");
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getLocation().toOSString(), result);
	}	
		
	/**
	 * Test the <code>${resource_path}</code> variable with a folder name argument.
	 */
	public void testResourcePathArgFolderName() throws CoreException {
		String expression = "${resource_path:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFolder("src").getFullPath().toOSString(), result);
	}

	/**
	 * Test the <code>${resource_path}</code> variable with a file selected.
	 */
	public void testResourcePathSelectFile() throws CoreException {
		String expression = "${resource_path}";
		IResource resource = getJavaProject().getProject().getFile(".classpath"); 
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getFullPath().toOSString(), result);
	}	
	
	/**
	 * Test the <code>${resource_name}</code> variable with a folder name argument.
	 */
	public void testResourceNameArgFolderName() throws CoreException {
		String expression = "${resource_name:DebugTests/src}";
		String result = doSubs(expression);
		assertEquals(getJavaProject().getProject().getFolder("src").getName(), result);
	}	

	/**
	 * Test the <code>${resource_name}</code> variable with a file selected.
	 */
	public void testResourceNameSelectFile() throws CoreException {
		String expression = "${resource_name}";
		IResource resource = getJavaProject().getProject().getFile(".classpath");
		setSelection(resource);
		String result = doSubs(expression);
		assertEquals(resource.getName(), result);
	}

	/**
	 * Tests that variables work as parameters to various fields in local Java
	 * application configurations.
	 */
	public void testLocalJavaApplicationParameters() throws CoreException {
		IResource resource = getJavaProject().getProject().getFile(".classpath");
		setSelection(resource);
		
		ILaunchConfiguration config = getLaunchConfiguration("Breakpoints");
		ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, "${project_name}");
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "${resource_name}");
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_WORKING_DIRECTORY, "${project_loc}");
		
		JavaLocalApplicationLaunchConfigurationDelegate delegate = new JavaLocalApplicationLaunchConfigurationDelegate();
		
		assertEquals(resource.getProject().getName(), delegate.getProgramArguments(wc));
		assertEquals(resource.getName(), delegate.getVMArguments(wc));
		File workingDir = delegate.getWorkingDirectory(wc);
		assertNotNull(workingDir);
		assertEquals(resource.getProject().getLocation().toOSString(), workingDir.toString());
	}
	
	/**
	 * Sets the selected resource in the navigator view.
	 * 
	 * @param resource resource to select
	 */
	protected void setSelection(final IResource resource) {
		Runnable r = new Runnable() {
			public void run() {
				IWorkbenchPage page = DebugUIPlugin.getActiveWorkbenchWindow().getActivePage();
				IViewPart part;
				try {
					part = page.showView("org.eclipse.ui.views.ResourceNavigator");
					part.getSite().getSelectionProvider().setSelection(new StructuredSelection(resource));
				} catch (PartInitException e) {
					assertNotNull("Failed to open navigator view", null);
				}
				
			}
		};
		DebugUIPlugin.getStandardDisplay().syncExec(r);
	}
	
}
