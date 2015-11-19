package com.paulwithers.openLog;

/*

 <!--
 Copyright 2015 Paul Withers
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

import java.util.logging.Level;

import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.application.ApplicationEx;
import com.ibm.xsp.extlib.util.ExtLibUtil;

/**
 * @author Paul Withers
 * @since 6.0.0
 * 
 *        Utility class to allow access without OpenLogItem needing to have static methods
 * 
 */
public class OpenLogUtil {

	private transient static OpenLogItem oli_;

	/**
	 * 
	 */
	public OpenLogUtil() {

	}

	/**
	 * Helper method to give easy access to the OpenLogItem
	 * 
	 * @return OpenLogItem
	 * @since 6.0.0
	 */
	public static OpenLogItem getOpenLogItem() {
		if (null == oli_) {
			oli_ = new OpenLogItem();
		}
		return oli_;
	}

	/**
	 * Logs an error / throwable
	 * 
	 * @param ee
	 *            Throwable holding the error
	 * @return String error message logged
	 * @since 6.0.0
	 */
	public static String logError(final Throwable ee) {
		return getOpenLogItem().logError(ee);
	}

	/**
	 * Logs an error / throwable for a specific Session
	 * 
	 * @param s
	 *            Session to log the eror for
	 * @param ee
	 *            Throwable holding the error
	 * @since 6.0.0
	 */
	public static void logError(final Session s, final Throwable ee) {
		getOpenLogItem().logError(s, ee);
	}

	/**
	 * Logs an error / throwable for a specific Session at a specific severity for a specific document
	 * 
	 * @param s
	 *            Session to log the eror for
	 * @param ee
	 *            Throwable holding the error
	 * @param msg
	 *            String message to log
	 * @param severityType
	 *            Level to log at
	 * @param doc
	 *            Document to log the error for or null
	 * @since 6.0.0
	 */
	public static void logError(final Session s, final Throwable ee, final String msg, final Level severityType,
			final Document doc) {
		getOpenLogItem().logError(s, ee, msg, severityType, doc);
	}

	/**
	 * Logs an error / throwable at a specific severity for a specific document
	 * 
	 * @param ee
	 *            Throwable holding the error
	 * @param msg
	 *            String message to log
	 * @param severityType
	 *            Level to log at
	 * @param doc
	 *            Document to log the error for or null
	 * @return String error message logged
	 * @since 6.0.0
	 */
	public static String logErrorEx(final Throwable ee, final String msg, final Level severityType, final Document doc) {
		return getOpenLogItem().logErrorEx(ee, msg, severityType, doc);
	}

	/**
	 * Logs an event for a specific Session at a specific severity for a specific document
	 * 
	 * @param s
	 *            Session to log the event for
	 * @param ee
	 *            Throwable - use <code>new Throwable()</code>
	 * @param msg
	 *            String message to log
	 * @param severityType
	 *            Level to log at
	 * @param doc
	 *            Document to log the event for or null
	 * @since 6.0.0
	 */
	public static void logEvent(final Session s, final Throwable ee, final String msg, final Level severityType,
			final Document doc) {
		getOpenLogItem().logEvent(s, ee, msg, severityType, doc);
	}

	/**
	 * Logs an event at a specific severity for a specific document
	 * 
	 * @param ee
	 *            Throwable - use <code>new Throwable()</code>
	 * @param msg
	 *            String message to log
	 * @param severityType
	 *            Level to log at
	 * @param doc
	 *            Document to log the event for or null
	 * @since 6.0.0
	 */
	public static String logEvent(final Throwable ee, final String msg, final Level severityType, final Document doc) {
		return getOpenLogItem().logEvent(ee, msg, severityType, doc);
	}

	/*
	 * This method decides what to do with any Exceptions that we encounter internal to this class, based on the
	 * olDebugLevel variable.
	 */
	static void debugPrint(Throwable ee) {
		if ((ee == null)) {
			return;
		}

		try {
			// debug level of 1 prints the basic error message
			int debugLevel = Integer.parseInt(getXspProperty("xsp.openlog.debugLevel", "2"));
			if (debugLevel >= 1) {
				String debugMsg = ee.toString();
				try {
					if (ee instanceof NotesException) {
						NotesException ne = (NotesException) ee;
						debugMsg = "Notes error " + ne.id + ": " + ne.text;
					}
				} catch (Exception e2) {
				}
				System.err.println("OpenLogItem error: " + debugMsg);
			}

			// debug level of 2 prints the whole stack trace
			if (debugLevel >= 2) {
				System.err.println("OpenLogItem error trace: ");
				ee.printStackTrace();
			}
		} catch (Exception e) {
			// at this point, if we have an error just discard it
		}
	}

	/**
	 * Get a notes.ini variable value or use default value passed. Moved from OpenLogItem in 6.0.0
	 * 
	 * @param propertyName
	 *            String property to retrieve from notes.ini
	 * @param defaultValue
	 *            String default if no notes.ini setting declared
	 * @return String notes.ini variable value or default
	 * @since 6.0.0
	 */
	public static String getIniVar(String propertyName, String defaultValue) {
		try {
			String newVal = ExtLibUtil.getCurrentSession().getEnvironmentString(propertyName, true);
			if (StringUtil.isNotEmpty(newVal)) {
				return newVal;
			} else {
				return defaultValue;
			}
		} catch (NotesException e) {
			debugPrint(e);
			return defaultValue;
		}
	}

	/**
	 * Gets a proeprty value from xsp.properties either in NSF or on server. If it's not found, it falls back to looking
	 * to notes.ini variable. Moved from OpenLogItem in 6.0.0
	 * 
	 * @param propertyName
	 *            String property to retrieve from notes.ini
	 * @param defaultValue
	 *            String default if no notes.ini setting declared
	 * @return String xsp.property valuenotes.ini variable value or default
	 * @since 6.0.0
	 */
	static String getXspProperty(String propertyName, String defaultValue) {
		String retVal = ApplicationEx.getInstance().getApplicationProperty(propertyName,
				getIniVar(propertyName, defaultValue));
		return retVal;
	}

}
