package org.eclipse.debug.jdi.tests;

/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

import java.util.ArrayList;

import com.sun.jdi.BooleanValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.event.AccessWatchpointEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.event.WatchpointEvent;
import com.sun.jdi.request.EventRequestManager;

/**
 * Tests for JDI com.sun.jdi.event.WatchpointEvent.
 */
public class WatchpointEventTest extends AbstractJDITest {

	private WatchpointEvent fAccessWatchpointEvent,
		fStaticAccessWatchpointEvent,
		fModificationWatchpointEvent;
	// NB: Static modification watchpoint event is tested in ModificationWatchpointTest
	/**
	 * Creates a new test.
	 */
	public WatchpointEventTest() {
		super();
	}
	/**
	 * Init the fields that are used by this test only.
	 */
	public void localSetUp() {
		// Trigger an access watchpoint event
		fAccessWatchpointEvent =
			(AccessWatchpointEvent) triggerAndWait(getAccessWatchpointRequest(),
				"AccessWatchpointEvent",
				true);
		assertTrue("Got access watchpoint event", fAccessWatchpointEvent != null);

		// Trigger a static access watchpoint event
		fStaticAccessWatchpointEvent =
			(AccessWatchpointEvent) triggerAndWait(
				getStaticAccessWatchpointRequest(),
				"StaticAccessWatchpointEvent",
				true);
		assertTrue(
			"Got static access watchpoint event",
			fStaticAccessWatchpointEvent != null);

		// Trigger a modification watchpoint event
		fModificationWatchpointEvent =
			(ModificationWatchpointEvent) triggerAndWait(
				getModificationWatchpointRequest(),
				"ModificationWatchpointEvent",
				false);
		// Interrupt the VM so that we can test valueCurrent()
		assertTrue(
			"Got modification watchpoint event",
			fModificationWatchpointEvent != null);

	}
	/**
	 * Make sure the test leaves the VM in the same state it found it.
	 */
	public void localTearDown() {
		// Ensure that the modification of the "fBool" field has completed
		fVM.resume();
		waitUntilReady();

		// Delete the event requests we created in this test
		EventRequestManager requestManager = fVM.eventRequestManager();
		requestManager.deleteEventRequests(
			new ArrayList(requestManager.accessWatchpointRequests()));
		requestManager.deleteEventRequests(
			new ArrayList(requestManager.modificationWatchpointRequests()));

		// Set the value of the "fBool" field back to its original value
		resetField();
	}
	/**
	 * Run all tests and output to standard output.
	 */
	public static void main(java.lang.String[] args) {
		new WatchpointEventTest().runSuite(args);
	}
	/**
	 * Gets the name of the test case.
	 */
	public String getName() {
		return "com.sun.jdi.event.WatchpointEvent";
	}
	/**
	 * Test JDI field().
	 */
	public void testJDIField() {
		assertEquals("1", getField("fBool"), fAccessWatchpointEvent.field());
		assertEquals(
			"2",
			getField("fString"),
			fStaticAccessWatchpointEvent.field());
		assertEquals(
			"3",
			getField("fBool"),
			fModificationWatchpointEvent.field());
	}
	/**
	 * Test JDI object().
	 */
	public void testJDIObject() {
		assertEquals(
			"1",
			getObjectReference(),
			fAccessWatchpointEvent.object());
		assertTrue("2", fStaticAccessWatchpointEvent.object() == null);
		assertEquals(
			"3",
			getObjectReference(),
			fModificationWatchpointEvent.object());
	}
	/**
	 * Test JDI valueCurrent().
	 */
	public void testJDIValueCurrent() {
		assertTrue(
			"1",
			false
				== ((BooleanValue) fAccessWatchpointEvent.valueCurrent()).value());

		assertEquals(
			"2",
			"Hello World",
			((StringReference) fStaticAccessWatchpointEvent.valueCurrent())
				.value());

		assertTrue(
			"3",
			false
				== ((BooleanValue) fModificationWatchpointEvent.valueCurrent())
					.value());
	}
}