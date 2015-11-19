package com.paulwithers.openLog;

/*

 <!--
 Copyright 2013 Paul Withers
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and limitations under the License
 -->

 */

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.logging.Level;

import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.el.PropertyNotFoundException;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;

import com.ibm.jscript.InterpretException;
import com.ibm.jscript.parser.ParseException;
import com.ibm.xsp.FacesExceptionEx;
import com.ibm.xsp.exception.EvaluationExceptionEx;
import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.paulwithers.openLog.OpenLogErrorHolder.EventError;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;

/**
 * @author withersp
 * @since 1.0.0
 *
 */
public class OpenLogPhaseListener implements PhaseListener {
	private static final long serialVersionUID = 1L;
	private static final int RENDER_RESPONSE = 6;
	private static Boolean suppressControlIdsForEvents;

	@SuppressWarnings("unchecked")
	public void beforePhase(PhaseEvent event) {
		// Add FacesContext messages for anything captured so far
		if (RENDER_RESPONSE == event.getPhaseId().getOrdinal()) {
			final Map<String, Object> r = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
			final Map<String, Object> sessScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
			if (null == r.get("error")) {
				OpenLogItem.setThisAgent(true);
			}
			if (null != sessScope.get("openLogBean")) {
				// sessionScope.openLogBean is not null, the developer has
				// called openLogBean.addError(e,this)
				final OpenLogErrorHolder errList = (OpenLogErrorHolder) sessScope.get("openLogBean");
				errList.setLoggedErrors(new LinkedHashSet<EventError>());
				// loop through the ArrayList of EventError objects and add any
				// errors already captured as a facesMessage
				if (null != errList.getErrors()) {
					for (final EventError error : errList.getErrors()) {
						errList.addFacesMessageForError(error);
					}
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * javax.faces.event.PhaseListener#afterPhase(javax.faces.event.PhaseEvent)
	 */
	@SuppressWarnings("unchecked")
	public void afterPhase(PhaseEvent event) {
		try {
			if (RENDER_RESPONSE == event.getPhaseId().getOrdinal()) {
				final Map<String, Object> r = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
				final Map<String, Object> sessScope = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
				if (null != r.get("error")) {
					processUncaughtException(r);

				} else if (null != sessScope.get("openLogBean")) {
					// sessionScope.openLogBean is not null, the developer has
					// called openLogBean.addError(e,this)
					final OpenLogErrorHolder errList = (OpenLogErrorHolder) sessScope.get("openLogBean");
					// loop through the ArrayList of EventError objects
					if (null != errList.getErrors()) {
						for (final EventError error : errList.getErrors()) {
							String msg = "";
							if (!"".equals(error.getMsg())) {
								msg = msg + error.getMsg();
							}
							msg = msg + "Error on ";
							if (null != error.getControl()) {
								msg = msg + error.getControl().getId();
							}
							if (null != error.getError()) {
								msg = msg + ":\n\n" + error.getError().getLocalizedMessage() + "\n\n" + error.getError().getExpressionText();
							}
							final Level severity = convertSeverity(error.getSeverity());
							Document passedDoc = null;
							if (!"".equals(error.getUnid())) {
								try {
									final Database currDb = ExtLibUtil.getCurrentDatabase();
									passedDoc = currDb.getDocumentByUNID(error.getUnid());
								} catch (final Exception e) {
									msg = msg + "\n\nCould not retrieve document but UNID was passed: " + error.getUnid();
								}
							}
							OpenLogItem.logErrorEx(error.getError(), msg, severity, passedDoc);
							try {
								passedDoc.recycle();
							} catch (final Throwable e) {
								// nothing to recycle here, move along
							}
						}
					}
					// loop through the ArrayList of EventError objects
					if (null != errList.getEvents()) {
						for (final EventError eventObj : errList.getEvents()) {
							String msg = "";
							if (null != eventObj.getControl() && !isSuppressControlIdsForEvents()) {
								msg = msg + "Event logged for " + eventObj.getControl().getId() + " ";
							}
							msg = msg + eventObj.getMsg();
							final Level severity = convertSeverity(eventObj.getSeverity());
							Document passedDoc = null;
							if (!"".equals(eventObj.getUnid())) {
								try {
									final Database currDb = ExtLibUtil.getCurrentDatabase();
									passedDoc = currDb.getDocumentByUNID(eventObj.getUnid());
								} catch (final Exception e) {
									msg = msg + "\n\nCould not retrieve document but UNID was passed: " + eventObj.getUnid();
								}
							}
							OpenLogItem.logEvent(null, msg, severity, passedDoc);
							try {
								passedDoc.recycle();
							} catch (final Throwable e) {
								// nothing to recycle here, move along
							}
						}
					}
					sessScope.put("openLogBean", null);
				}
			}
		} catch (final Throwable e) {
			// We've hit an error in our code here, log the error
			OpenLogItem.logError(e);
		}
	}

	/**
	 * This is getting VERY complex because of the variety of exceptions and
	 * tracking up the stack trace to find the right class to get as much info
	 * as possible. Extracted into a separate method to make it more readable.
	 *
	 * @param r
	 *            requestScope map
	 */
	private void processUncaughtException(Map<String, Object> r) {
		// requestScope.error is not null, we're on the custom error page.
		final Object error = r.get("error");

		// Set the agent (page we're on) to the *previous* page
		OpenLogItem.setThisAgent(false);

		String msg = "";
		if ("com.ibm.xsp.exception.EvaluationExceptionEx".equals(error.getClass().getName())) {
			// EvaluationExceptionEx, so SSJS error is on a component property.
			// Hit by ErrorOnLoad.xsp
			final EvaluationExceptionEx ee = (EvaluationExceptionEx) error;
			if ("com.ibm.jscript.InterpretException".equals(ee.getCause().getClass().getName())) {
				final InterpretException ie = (InterpretException) ee.getCause();
				msg = "Error on " + ee.getErrorComponentId() + " " + ee.getErrorPropertyId() + " property/event, line " + Integer.toString(ie.getErrorLine()) + ":\n\n"
						+ ie.getLocalizedMessage() + "\n\n" + ie.getExpressionText();
			} else if ("com.ibm.jscript.parser.ParseException".equals(ee.getCause().getClass().getName())) {
				final ParseException ie = (ParseException) ee.getCause();
				msg = "Error on " + ee.getErrorComponentId() + " " + ee.getErrorPropertyId() + " property/event " + ":\n\n" + ie.getLocalizedMessage();

			}
			OpenLogItem.logErrorEx(ee, msg, null, null);

		} else if ("javax.faces.FacesException".equals(error.getClass().getName())) {
			// FacesException, so error is on event or method in EL
			final FacesException fe = (FacesException) error;
			InterpretException ie = null;
			EvaluationExceptionEx ee = null;
			msg = "Error on ";
			try {
				// javax.faces.el.MethodNotFoundException hit by
				// ErrorOnMethod.xsp
				if (!"javax.faces.el.MethodNotFoundException".equals(fe.getCause().getClass().getName())) {
					if ("com.ibm.xsp.exception.EvaluationExceptionEx".equals(fe.getCause().getClass().getName())) {
						// Hit by ErrorOnClick.xsp
						ee = (EvaluationExceptionEx) fe.getCause();
					} else if ("javax.faces.el.PropertyNotFoundException".equals(fe.getCause().getClass().getName())) {
						// Property not found exception, so error is on a
						// component property
						msg = "PropertyNotFoundException Error, cannot locate component:\n\n";
					} else if ("com.ibm.xsp.exception.EvaluationExceptionEx".equals(fe.getCause().getCause().getClass().getName())) {
						// Hit by using e.g. currentDocument.isNewDoc()
						// i.e. using a Variable that relates to a valid Java
						// object but a method that doesn't exist
						ee = (EvaluationExceptionEx) fe.getCause().getCause();
					}
					if (null != ee) {
						msg = msg + ee.getErrorComponentId() + " " + ee.getErrorPropertyId() + " property/event:\n\n";
						if ("com.ibm.jscript.InterpretException".equals(ee.getCause().getClass().getName())) {
							ie = (InterpretException) ee.getCause();
						}
					}
				}
			} catch (final Throwable t) {
				msg = "Unexpected error class: " + fe.getCause().getClass().getName() + "\n Message recorded is: ";
			}
			if (null != ie) {
				msg = msg + Integer.toString(ie.getErrorLine()) + ":\n\n" + ie.getLocalizedMessage() + "\n\n" + ie.getExpressionText();
			} else {
				msg = msg + fe.getCause().getLocalizedMessage();
			}
			OpenLogItem.logErrorEx(fe.getCause(), msg, null, null);
		} else if ("com.ibm.xsp.FacesExceptionEx".equals(error.getClass().getName())) {
			// FacesException, so error is on event - doesn't get hit in
			// examples. Can this still get hit??
			final FacesExceptionEx fe = (FacesExceptionEx) error;
			try {
				if ("lotus.domino.NotesException".equals(fe.getCause().getClass().getName())) {
					// sometimes the cause is a NotesException
					final NotesException ne = (NotesException) fe.getCause();

					msg = msg + "NotesException - " + Integer.toString(ne.id) + " " + ne.text;
				} else if ("java.io.IOException".equals(error.getClass().getName())) {
					final IOException e = (IOException) error;

					msg = "Java IO:" + error.toString();
					OpenLogItem.logErrorEx(e.getCause(), msg, null, null);
				} else {
					final EvaluationExceptionEx ee = (EvaluationExceptionEx) fe.getCause();
					final InterpretException ie = (InterpretException) ee.getCause();

					msg = "Error on " + ee.getErrorComponentId() + " " + ee.getErrorPropertyId() + " property/event:\n\n" + Integer.toString(ie.getErrorLine()) + ":\n\n"
							+ ie.getLocalizedMessage() + "\n\n" + ie.getExpressionText();
				}
			} catch (final Throwable t) {
				try {
					msg = "Unexpected error class: " + fe.getCause().getClass().getName() + "\n Message recorded is: " + fe.getCause().getLocalizedMessage();
				} catch (final Throwable ee) {
					msg = fe.getLocalizedMessage();
				}
			}
			OpenLogItem.logErrorEx(fe.getCause(), msg, null, null);

		} else if ("javax.faces.el.PropertyNotFoundException".equals(error.getClass().getName())) {
			// Hit by ErrorOnProperty.xsp
			// Property not found exception, so error is on a component property
			final PropertyNotFoundException pe = (PropertyNotFoundException) error;
			msg = "PropertyNotFoundException Error, cannot locate component:\n\n" + pe.getLocalizedMessage();
			OpenLogItem.logErrorEx(pe, msg, null, null);
		} else {
			try {
				System.out.println("Error type not found:" + error.getClass().getName());
				msg = error.toString();
				OpenLogItem.logErrorEx((Throwable) error, msg, null, null);
			} catch (final Throwable t) {
				t.printStackTrace();
			}
		}
	}

	private Level convertSeverity(int severity) {
		Level internalLevel = null;
		switch (severity) {
		case 1:
			internalLevel = Level.SEVERE;
			break;
		case 2:
			internalLevel = Level.WARNING;
			break;
		case 3:
			internalLevel = Level.INFO;
			break;
		case 5:
			internalLevel = Level.FINE;
			break;
		case 6:
			internalLevel = Level.FINER;
			break;
		case 7:
			internalLevel = Level.FINEST;
			break;
		default:
			internalLevel = Level.CONFIG;
		}
		return internalLevel;
	}

	public PhaseId getPhaseId() {
		return PhaseId.ANY_PHASE;
	}

	/**
	 * @return the includeControlIdsForEvents
	 */
	public static Boolean isSuppressControlIdsForEvents() {
		if (null == suppressControlIdsForEvents) {
			setSuppressControlIdsForEvents();
		}
		return suppressControlIdsForEvents;
	}

	/**
	 * @param includeControlIdsForEvents
	 *            the includeControlIdsForEvents to set
	 */
	public static void setSuppressControlIdsForEvents() {
		suppressControlIdsForEvents = false;
		final String retVal = OpenLogUtil.getXspProperty("xsp.openlog.suppressEventControl", "");
		if (!"".equals(retVal)) {
			suppressControlIdsForEvents = true;
		}
	}
}
