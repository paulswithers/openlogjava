package com.paulwithers.openLog;

/*

 <!--
 Copyright 2011 Paul Withers, Nathan T. Freeman & Tim Tripcony
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

/*
 * Paul Withers, Intec March 2013
 * Some significant enhancements here since the version Nathan cleaned up for XPages Help Application.
 * Consequently, I've renamed the package completely, so there is no conflict
 *
 * 1. Everything is now static, so no need to create an OpenLog object
 *
 * 2. Everything now uses ExtLibUtil instead of Tim Tripcony's code (sorry, Tim! ;-) )
 *
 * 3. _logDbName and olDebugLevel are set using getXspProperty(String, String). That method gets a value from
 * xsp.properties looking for the first parameter, looking first to current NSF, then the server.
 * If nothing is found, getIniVar is then called, looking to the notes.ini using the same key.
 * If nothing is still found, the second parameter from both methods is used, the default.
 *
 * 4. setThisAgent(boolean) method has been added. By default it gets the current page.
 * Otherwise it gets the previous page. Why? Because if we've been redirected to an error page,
 * we want to know which page the ACTUAL error occurred on.
 *
 * 5. logErrorEx has been fixed. It didn't work before.
 *
 * 6. _eventTime and _startTime recycled after creating logDoc. Nathan, I'll sleep a little less tonight,
 * but it's best practice ;-)
 */

/*
 * Nathan T. Freeman, GBS Jun 20, 2011
 * Developers notes...
 *
 * There's more than I'd like to do here, but I think the entry points are much more sensible now.
 *
 * Because the log methods are static, one simply needs to call..
 *
 * OpenLogItem.logError(session, throwable)
 *
 * or...
 *
 * OpenLogItem.logError(session, throwable, message, level, document)
 *
 * or...
 *
 * OpenLogItem.logEvent(session, message, level, document)
 *
 * All Domino objects have been made recycle-proof. All the nonsense about "init" and "reset"
 * and overloading constructors to do all the work is gone.
 *
 * There really SHOULD be an OpenLogManager that tracks settings like the java.util.Logging package does
 * but that is well beyond the scope of this little update
 *
 * Honestly, knowing that Julian does so much more Java work now, I can completely
 * sympathize with his occasional statement that OpenLog could use a major refactor.
 *
 * I wouldn't call this "major" but certainly "significant."
 *
 * One thing that would be SUPER useful is if the logEvent traced the caller automatically
 * even without a Throwable object passed. The problem is that the most likely use for this
 * entire class is from SSJS, which won't pass a contextual call stack by default.
 *
 * We'd need a LOT more infrastructure for that!
 */

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.application.ApplicationEx;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class OpenLogItem implements Serializable {
	/*
	 * ======================================================= <HEADER> NAME: OpenLogClass script library VERSION:
	 * 20070321a AUTHOR(S): Julian Robichaux ( http://www.nsftools.com ) ORIGINAL SOURCE: The OpenLog database,
	 * available as an open-source project at http://www.OpenNTF.org HISTORY: 20070321a: Added startTime global to mark
	 * when this particular agent run began, so you can group multiple errors/events together more easily (see the
	 * "by Database and Start Time" view) 20060314a: fixed bug where logErrorEx would set the message to the severity
	 * type instead of the value of msg. 20041111a: made SEVERITY_ and TYPE_ constants public. 20040928a: added
	 * callingMethodDepth variable, which should be incremented by one in the Synchronized class so we'll get a proper
	 * reference to the calling method; make $PublicAccess = "1" when we create new log docs, so users with Depositor
	 * access to this database can still create log docs. 20040226a: removed synchronization from all methods in the
	 * main OpenLogItem class and added the new SynchronizedOpenLogItem class, which simply extends OpenLogItem and
	 * calls all the public methods as synchronized methods. Added olDebugLevel and debugOut public members to report on
	 * internal errors. 20040223a: add variables for user name, effective user name, access level, user roles, and
	 * client version; synchronized most of the public methods. 20040222a: this version got a lot less aggressive with
	 * the Notes object recycling, due to potential problems. Also made LogErrorEx and LogEvent return "" if an error
	 * occurred (to be consistent with LogError); added OpenLogItem(Session s) constructor; now get server name from the
	 * Session object, not the AgentContext object (due to differences in what those two things will give you); add
	 * UseDefaultLogDb and UseCustomLogDb methods; added getLogDatabase method, to be consistent with LotusScript
	 * functions; added useServerLogWhenLocal and logToCurrentDatabase variables/options 20040217a: this version made
	 * the agentContext object global and fixed a problem where the agentContext was being recycled in the constuctor
	 * (this is very bad) 20040214b: initial version
	 *
	 * DISCLAIMER: This code is provided "as-is", and should be used at your own risk. The authors make no express or
	 * implied warranty about anything, and they will not be responsible or liable for any damage caused by the use or
	 * misuse of this code or its byproducts. No guarantees are made about anything.
	 *
	 * That being said, you can use, modify, and distribute this code in any way you want, as long as you keep this
	 * header section intact and in a prominent place in the code. </HEADER>
	 * =======================================================
	 *
	 * This class contains generic functions that can be used to log events and errors to the OpenLog database. All you
	 * have to do it copy this script library to any database that should be sending errors to the OpenLog database, and
	 * add it to your Java agents using the Edit Project button (see the "Using This Database" doc in the OpenLog
	 * database for more details).
	 *
	 * At the beginning of your agent, create a global instance of this class like this:
	 *
	 * private OpenLogItem oli = new OpenLogItem();
	 *
	 * and then in all the try/catch blocks that you want to send errors from, add the line:
	 *
	 * oli.logError(e);
	 *
	 * where "e" is the Exception that you caught. That's all you have to do. The LogError method will automatically
	 * create a document in the OpenLog database that contains all sorts of information about the error that occurred,
	 * including the name of the agent and function/sub that it occurred in.
	 *
	 * For additional functionality, you can use the LogErrorEx function to add a custom message, a severity level,
	 * and/or a link to a NotesDocument to the log doc.
	 *
	 * In addition, you can use the LogEvent function to add a notification document to the OpenLog database.
	 *
	 * You'll notice that I trap and discard almost all of the Exceptions that may occur as the methods in this class
	 * are running. This is because the class is normally only used when an error is occurring anyway, so there's not
	 * sense in trying to pass any new errors back up the stack.
	 *
	 * The master copy of this script library resides in the OpenLog database. All copies of this library in other
	 * databases should be set to inherit changes from that database.
	 */

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;
	public static final String TYPE_ERROR = "Error";
	public static final String TYPE_EVENT = "Event";

	private final static String _logFormName = "LogEvent";

	// MODIFY THESE FOR YOUR OWN ENVIRONMENT
	// (don't forget to use double-backslashes if this database
	// is in a Windows subdirectory -- like "logs\\OpenLog.nsf")
	private static String _logDbName = "";

	private static String _thisDatabase;
	private static String _thisServer;
	private static String _thisAgent;
	// why the object? Because the object version is serializable
	private static Boolean _logSuccess = true;
	private static String _accessLevel;
	private static Vector<String> _userRoles;
	private static Vector<String> _clientVersion;
	private static Boolean _displayError;
	private static String _displayErrorGeneric;

	private String _formName;
	private static Level _severity;
	private static String _eventType;
	private static String _message;

	private static Throwable _baseException;
	private static Date _startJavaTime;
	private static Date _eventJavaTime;
	private static String _errDocUnid;

	// These objects cannot be serialized, so they must be considered transient
	// so they'll be null on a restore
	private transient static Session _session;
	private transient static Database _logDb;
	private transient static Database _currentDatabase;
	private transient static DateTime _startTime;
	private transient static DateTime _eventTime;
	private transient static Document _errDoc;

	public static void setBase(Throwable base) {
		_baseException = base;
	}

	public static Throwable getBase() {
		return _baseException;
	}

	public static void setSeverity(Level severity) {
		_severity = severity;
	}

	/**
	 * @param message
	 *            the message to set
	 */
	public static void setMessage(String message) {
		_message = message;
	}

	public static String getThisDatabase() {
		if (_thisDatabase == null) {
			try {
				_thisDatabase = getCurrentDatabase().getFilePath();
			} catch (Exception e) {
				debugPrint(e);
			}
		}
		return _thisDatabase;
	}

	/**
	 * @return the thisServer
	 */
	public static String getThisServer() {
		if (_thisServer == null) {
			try {
				_thisServer = getSession().getServerName();
				if (_thisServer == null) _thisServer = "";
			} catch (Exception e) {
				debugPrint(e);
			}
		}
		return _thisServer;

	}

	/**
	 * @return the thisAgent
	 */
	public static String getThisAgent() {
		if (_thisAgent == null) {
			setThisAgent(true);
		}
		return _thisAgent;
	}

	public static void setThisAgent(boolean currPage) {
		String fromPage = "";
		String[] historyUrls = ExtLibUtil.getXspContext().getHistoryUrls();
		if (currPage) {
			fromPage = historyUrls[0];
		} else {
			if (historyUrls.length > 1) {
				fromPage = historyUrls[1];
			} else {
				fromPage = historyUrls[0];
			}
		}
		_thisAgent = fromPage;
		if (fromPage.indexOf("?") > -1) {
			_thisAgent = _thisAgent.substring(1, _thisAgent.indexOf("?"));
		}
	}

	/**
	 * @return the logDb
	 */
	public static Database getLogDb() {
		if (_logDb == null) {
			try {
				_logDb = getSession().getDatabase(getThisServer(), getLogDbName(), false);
			} catch (Exception e) {
				debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				boolean pointless = _logDb.isOpen();
			} catch (NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_logDb = getSession().getDatabase(getThisServer(), getLogDbName(), false);
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _logDb;
	}

	/**
	 * @return the currentDatabase
	 */
	public static Database getCurrentDatabase() {
		if (_currentDatabase == null) {
			try {
				_currentDatabase = getSession().getCurrentDatabase();
			} catch (Exception e) {
				debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				boolean pointless = _currentDatabase.isOpen();
			} catch (NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_currentDatabase = getSession().getCurrentDatabase();
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _currentDatabase;
	}

	/**
	 * @return the userName
	 */
	public static String getUserName() {
		try {
			return getSession().getUserName();
		} catch (Exception e) {
			debugPrint(e);
			return "";
		}
	}

	/**
	 * @return the effName
	 */
	public static String getEffName() {
		try {
			return getSession().getEffectiveUserName();
		} catch (Exception e) {
			debugPrint(e);
			return "";
		}
	}

	/**
	 * @return the accessLevel
	 */
	public static String getAccessLevel() {
		if (_accessLevel == null) {
			try {
				switch (getCurrentDatabase().getCurrentAccessLevel()) {
				case 0:
					_accessLevel = "0: No Access";
					break;
				case 1:
					_accessLevel = "1: Depositor";
					break;
				case 2:
					_accessLevel = "2: Reader";
					break;
				case 3:
					_accessLevel = "3: Author";
					break;
				case 4:
					_accessLevel = "4: Editor";
					break;
				case 5:
					_accessLevel = "5: Designer";
					break;
				case 6:
					_accessLevel = "6: Manager";
					break;
				}
			} catch (Exception e) {
				debugPrint(e);
			}
		}
		return _accessLevel;
	}

	/**
	 * @return the userRoles
	 */
	@SuppressWarnings("unchecked")
	public static Vector<String> getUserRoles() {
		if (_userRoles == null) {
			try {
				_userRoles = getSession().evaluate("@UserRoles");
			} catch (Exception e) {
				debugPrint(e);
			}
		}
		return _userRoles;
	}

	/**
	 * @return the clientVersion
	 */
	public static Vector<String> getClientVersion() {
		if (_clientVersion == null) {
			_clientVersion = new Vector<String>();
			try {
				String cver = getSession().getNotesVersion();
				if (cver != null) {
					if (cver.indexOf("|") > 0) {
						_clientVersion.addElement(cver.substring(0, cver.indexOf("|")));
						_clientVersion.addElement(cver.substring(cver.indexOf("|") + 1));
					} else {
						_clientVersion.addElement(cver);
					}
				}
			} catch (Exception e) {
				debugPrint(e);
			}
		}
		return _clientVersion;
	}

	/**
	 * @return the startTime
	 */
	public static DateTime getStartTime() {
		if (_startTime == null) {
			try {
				_startTime = getSession().createDateTime("Today");
				_startTime.setNow();
				_startJavaTime = _startTime.toJavaDate();
			} catch (Exception e) {
				debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				boolean junk = _startTime.isDST();
			} catch (NotesException recycleSucks) {
				try {
					_startTime = getSession().createDateTime(_startJavaTime);
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _startTime;
	}

	/**
	 * @return the logDbName
	 */
	public static String getLogDbName() {
		if ("".equals(_logDbName)) {
			_logDbName = getXspProperty("xsp.openlog.filepath", "OpenLog.nsf");
			if ("[CURRENT]".equals(_logDbName.toUpperCase())) {
				setLogDbName(getThisDatabasePath());
			}
		}
		return _logDbName;
	}

	private static String getThisDatabasePath() {
		try {
			return getCurrentDatabase().getFilePath();
		} catch (NotesException e) {
			debugPrint(e);
			return "";
		}
	}

	/**
	 * @return whether errors should be displayed or not
	 */
	public static Boolean getDisplayError() {
		if (null == _displayError) {
			String dummyVar = getXspProperty("xsp.openlog.displayError", "true");
			if ("FALSE".equals(dummyVar.toUpperCase())) {
				setDisplayError(false);
			} else {
				setDisplayError(true);
			}
		}
		return _displayError;
	}

	/**
	 * @param error
	 *            whether or not to display the errors
	 */
	public static void setDisplayError(Boolean error) {
		_displayError = error;
	}

	/**
	 * @return String of a generic error message or an empty string
	 */
	public static String getDisplayErrorGeneric() {
		if (null == _displayErrorGeneric) {
			_displayErrorGeneric = getXspProperty("xsp.openlog.genericErrorMessage", "");
		}
		return _displayErrorGeneric;
	}

	/**
	 * @return the logFormName
	 */
	public String getLogFormName() {
		return _logFormName;
	}

	/**
	 * @return the formName
	 */
	public String getFormName() {
		return _formName;
	}

	/**
	 * @return the errLine
	 */
	public static int getErrLine(Throwable ee) {
		return ee.getStackTrace()[0].getLineNumber();
	}

	/**
	 * @return the severity
	 */
	public static Level getSeverity() {
		return _severity;
	}

	/**
	 * @return the eventTime
	 */
	public static DateTime getEventTime() {
		if (_eventTime == null) {
			try {
				_eventTime = getSession().createDateTime("Today");
				_eventTime.setNow();
				_eventJavaTime = _eventTime.toJavaDate();
			} catch (Exception e) {
				debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				boolean junk = _eventTime.isDST();
			} catch (NotesException recycleSucks) {
				try {
					_eventTime = getSession().createDateTime(_eventJavaTime);
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _eventTime;
	}

	/**
	 * @return the eventType
	 */
	public static String getEventType() {
		return _eventType;
	}

	/**
	 * @return the message
	 */
	public static String getMessage() {
		if (_message.length() > 0) return _message;
		return getBase().getMessage();
	}

	/**
	 * @return the errDoc
	 */
	public static Document getErrDoc() {
		if (_errDoc != null) {
			try {
				@SuppressWarnings("unused")
				boolean junk = _errDoc.isProfile();
			} catch (NotesException recycleSucks) {
				try {
					_errDoc = getCurrentDatabase().getDocumentByUNID(_errDocUnid);
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _errDoc;
	}

	public static void setErrDoc(Document doc) {
		if (doc != null) {
			_errDoc = doc;
			try {
				_errDocUnid = doc.getUniversalID();
			} catch (NotesException ne) {
				debugPrint(ne);
			} catch (Exception ee) { // Added PW
				debugPrint(ee); // Added PW
			}
		}
	}

	// this variable sets the "debug level" of all the methods. Right now
	// the valid debug levels are:
	// 0 -- internal errors are discarded
	// 1 -- Exception messages from internal errors are printed
	// 2 -- stack traces from internal errors are also printed
	public static String olDebugLevel = getXspProperty("xsp.openlog.debugLevel", "2");

	// debugOut is the PrintStream that errors will be printed to, for debug
	// levels
	// greater than 1 (System.err by default)
	public static PrintStream debugOut = System.err;

	// this is a strange little variable we use to determine how far down the
	// stack
	// the calling method should be (see the getBasicLogFields method if you're
	// curious)
	// protected int callingMethodDepth = 2;

	// Added PW 27/04/2011 to initialise variables for XPages Java and allow the
	// user to update logDbName
	public static void setLogDbName(String newLogPath) {
		_logDbName = newLogPath;
	}

	public static void setOlDebugLevel(String newDebugLevel) {
		olDebugLevel = newDebugLevel;
	}

	/*
	 * Use this constructor when you're creating an instance of the class within the confines of an Agent. It will
	 * automatically pick up the agent name, the current database, and the server.
	 */
	public OpenLogItem() {

	}

	private static String getXspProperty(String propertyName, String defaultValue) {
		String retVal = ApplicationEx.getInstance().getApplicationProperty(propertyName,
				getIniVar(propertyName, defaultValue));
		return retVal;
	}

	private static String getIniVar(String propertyName, String defaultValue) {
		try {
			String newVal = getSession().getEnvironmentString(propertyName, true);
			if (StringUtil.isEmpty(newVal)) {
				return newVal;
			} else {
				return defaultValue;
			}
		} catch (NotesException e) {
			debugPrint(e);
			return defaultValue;
		}
	}

	private static Session getSession() {
		if (_session == null) {
			_session = ExtLibUtil.getCurrentSession();
		} else {
			try {
				@SuppressWarnings("unused")
				boolean pointless = _session.isOnServer();
			} catch (NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_session = ExtLibUtil.getCurrentSession();
				} catch (Exception e) {
					debugPrint(e);
				}
			}
		}
		return _session;
	}

	/*
	 * We can't really safely recycle() any of the global Notes objects, because there's no guarantee that nothing else
	 * is using them. Instead, just set everything to null
	 */
	public void recycle() {
		_errDoc = null;
		_logDb = null;
		_session = null;
	}

	/*
	 * This gets called automatically when the object is destroyed.
	 */
	// @Override
	// protected void finalize() throws Throwable {
	// recycle();
	// super.finalize();
	// }
	/*
	 * see what the status of the last logging event was
	 */
	public boolean getLogSuccess() {
		return _logSuccess;
	}

	/*
	 * reset all the global fields to their default values
	 */
	// public void resetFields() {
	// try {
	// _formName = _logFormName;
	// _errNum = 0;
	// _errLine = 0;
	// _errMsg = "";
	// _methodName = "";
	// _eventType = TYPE_ERROR;
	// _message = "";
	// _errDoc = null;
	// } catch (Exception e) {
	// debugPrint(e);
	// }
	// }
	/*
	 * The basic method you can use to log an error. Just pass the Exception that you caught and this method collects
	 * information and saves it to the OpenLog database.
	 */
	public static String logError(Throwable ee) {
		if (ee != null) {
			for (StackTraceElement elem : ee.getStackTrace()) {
				if (elem.getClassName().equals(OpenLogItem.class.getName())) {
					// NTF - we are by definition in a loop
					System.out.println(ee.toString());
					debugPrint(ee);
					_logSuccess = false;
					return "";
				}
			}
		}
		try {
			StackTraceElement[] s = ee.getStackTrace();
			FacesMessage m = new FacesMessage("Error in " + s[0].getClassName() + ", line " + s[0].getLineNumber()
					+ ": " + ee.toString());
			ExtLibUtil.getXspContext().getFacesContext().addMessage(null, m);
			setBase(ee);

			// if (ee.getMessage().length() > 0) {
			if (ee.getMessage() != null) {
				setMessage(ee.getMessage());
			} else {
				setMessage(ee.getClass().getCanonicalName());
			}
			setSeverity(Level.WARNING);
			setEventType(TYPE_ERROR);

			_logSuccess = writeToLog();
			return getMessage();

		} catch (Exception e) {
			System.out.println(e.toString());
			debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	private static void setEventType(String typeError) {
		_eventType = typeError;
	}

	/*
	 * A slightly more flexible way to send an error to the OpenLog database. This allows you to include a message
	 * string (which gets added to the log document just before the stack trace), a severity type (normally one of the
	 * standard severity types within this class: OpenLogItem.SEVERITY_LOW, OpenLogItem.SEVERITY_MEDIUM, or
	 * OpenLogItem.SEVERITY_HIGH), and/or a Document that may have something to do with the error (in which case a
	 * DocLink to that Document will be added to the log document).
	 */
	public static String logErrorEx(Throwable ee, String msg, Level severityType, Document doc) {
		if (ee != null) {
			for (StackTraceElement elem : ee.getStackTrace()) {
				if (elem.getClassName().equals(OpenLogItem.class.getName())) {
					// NTF - we are by definition in a loop
					System.out.println(ee.toString());
					debugPrint(ee);
					_logSuccess = false;
					return "";
				}
			}
		}
		try {
			setBase((ee == null ? new Throwable() : ee));
			setMessage((msg == null ? "" : msg));
			setSeverity(severityType == null ? Level.WARNING : severityType);
			setEventType(TYPE_ERROR);
			setErrDoc(doc);

			_logSuccess = writeToLog();
			return msg;

		} catch (Exception e) {
			debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	/*
	 * This method allows you to log an Event to the OpenLog database. You should include a message describing the
	 * event, a severity type (normally one of the standard severity types within this class: OpenLogItem.SEVERITY_LOW,
	 * OpenLogItem.SEVERITY_MEDIUM, or OpenLogItem.SEVERITY_HIGH), and optionally a Document that may have something to
	 * do with the event (in which case a DocLink to that Document will be added to the log document).
	 */
	public static String logEvent(Throwable ee, String msg, Level severityType, Document doc) {
		try {
			setMessage(msg);
			setSeverity(severityType == null ? Level.INFO : severityType);
			setEventType(TYPE_EVENT);
			setErrDoc(doc);
			if (ee == null) { // Added PW - LogEvent will not pass a throwable
				setBase(new Throwable("")); // Added PW
			} else { // Added PW
				setBase(ee); // Added PW
			} // Added PW
			_logSuccess = writeToLog();
			return msg;

		} catch (Exception e) {
			debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	/*
	 * A helper method that gets some basic information for the global variables that's common to all errors and events
	 * (event time and the name of the calling method).
	 *
	 * The stacklevel parameter probably looks a little mysterious. It's supposed to be the number of levels below the
	 * calling method that we're at right now, so we know how far down the stack trace we need to look to get the name
	 * of the calling method. For example, if another method called the logError method, and the logError method called
	 * this method, then the calling method is going to be 2 levels down the stack, so stacklevel should be = 2. That
	 * may not make sense to anyone but me, but it seems to work...
	 */
	// private boolean getBasicLogFields(int stacklevel) {
	// try {
	// try {
	// Throwable ee = new Throwable("whatever");
	// _stackTrace = getStackTrace(ee, stacklevel + 1);
	// // stackTrace = getStackTrace(ee);
	// } catch (Exception e) {
	// }
	//
	// // if (_eventTime == null)
	// // _eventTime = _session.createDateTime("Today");
	// // _eventTime.setNow();
	//
	// _methodName = getMethodName(_stackTrace, 0);
	//
	// return true;
	// } catch (Exception e) {
	// debugPrint(e);
	// return false;
	// }
	// }
	/*
	 * If an Exception is a NotesException, this method will extract the Notes error number and error message.
	 */
	// private boolean setErrorLogFields(Throwable ee) {
	// try {
	//
	// try {
	// if (ee instanceof NotesException) {
	// NotesException ne = (NotesException) ee;
	// setErrNum(ne.id);
	// setErrMsg(ne.text);
	// } else {
	// setErrMsg(getStackTrace().elementAt(0).toString());
	// }
	// } catch (Exception e) {
	// setErrMsg("");
	// }
	//
	// return true;
	// } catch (Exception e) {
	// debugPrint(e);
	// return false;
	// }
	// }
	/*
	 * Get the stack trace of an Exception as a Vector, without the initial error message, and skipping over a given
	 * number of items (as determined by the skip variable)
	 */
	private static Vector<String> getStackTrace(Throwable ee, int skip) {
		Vector<String> v = new Vector<String>(32);
		try {
			StringWriter sw = new StringWriter();
			ee.printStackTrace(new PrintWriter(sw));
			StringTokenizer st = new StringTokenizer(sw.toString(), "\n");
			int count = 0;
			while (st.hasMoreTokens()) {
				if (skip <= count++)
					v.addElement(st.nextToken().trim());
				else
					st.nextToken();
			}

		} catch (Exception e) {
			debugPrint(e);
		}

		return v;
	}

	private static Vector<String> getStackTrace(Throwable ee) {
		return getStackTrace(ee, 0);
	}

	public static void logError(Session s, Throwable ee) {
		if (s != null) {
			_session = s;
		}
		logError(ee);
	}

	public static void logError(Session s, Throwable ee, String message, Level severity, Document doc) {
		if (s != null) {
			_session = s;
		}
		logErrorEx(ee, message, severity, doc);
	}

	public static void logEvent(Session s, Throwable ee, String message, Level severity, Document doc) {
		if (s != null) {
			_session = s;
		}
		logEvent(ee, message, severity, doc);
	}

	/*
	 * This is the method that does the actual logging to the OpenLog database. You'll notice that I actually have a
	 * Database parameter, which is normally a reference to the OpenLog database that you're using. However, it occurred
	 * to me that you might want to use this class to write to an alternate log database at times, so I left you that
	 * option (although you're stuck with the field names used by the OpenLog database in that case).
	 *
	 * This method creates a document in the log database, populates the fields of that document with the values in our
	 * global variables, and adds some associated information about any Document that needs to be referenced. If you do
	 * decide to send log information to an alternate database, you can just call this method manually after you've
	 * called logError or logEvent, and it will write everything to the database of your choice.
	 */
	public static boolean writeToLog() {
		// exit early if there is no database
		Database db = getLogDb();
		if (db == null) {
			System.out.println("Could not retrieve database at path " + getLogDbName());
			return false;
		}

		boolean retval = false;
		Document logDoc = null;
		RichTextItem rtitem = null;
		Database docDb = null;

		try {
			logDoc = db.createDocument();

			logDoc.appendItemValue("Form", _logFormName);

			Throwable ee = getBase();
			StackTraceElement ste = ee.getStackTrace()[0];
			String errMsg = "";
			if (ee instanceof NotesException) {
				logDoc.replaceItemValue("LogErrorNumber", ((NotesException) ee).id);
				errMsg = ((NotesException) ee).text;
			} else if ("Interpret exception".equals(ee.getMessage())
					&& ee instanceof com.ibm.jscript.JavaScriptException) {
				com.ibm.jscript.InterpretException ie = (com.ibm.jscript.InterpretException) ee;
				errMsg = "Expression Language Interpret Exception " + ie.getExpressionText();
			} else {
				errMsg = ee.getMessage();
			}

			if (null == errMsg) {
				errMsg = getMessage();
			}

			logDoc.replaceItemValue("LogErrorMessage", errMsg);
			logDoc.replaceItemValue("LogStackTrace", getStackTrace(ee));
			logDoc.replaceItemValue("LogErrorLine", ste.getLineNumber());
			logDoc.replaceItemValue("LogSeverity", getSeverity().getName());
			logDoc.replaceItemValue("LogEventTime", getEventTime());
			logDoc.replaceItemValue("LogEventType", getEventType());
			logDoc.replaceItemValue("LogMessage", getMessage());
			logDoc.replaceItemValue("LogFromDatabase", getThisDatabase());
			logDoc.replaceItemValue("LogFromServer", getThisServer());
			logDoc.replaceItemValue("LogFromAgent", getThisAgent());
			logDoc.replaceItemValue("LogFromMethod", ste.getClass() + "." + ste.getMethodName());
			logDoc.replaceItemValue("LogAgentLanguage", "Java");
			logDoc.replaceItemValue("LogUserName", getUserName());
			logDoc.replaceItemValue("LogEffectiveName", getEffName());
			logDoc.replaceItemValue("LogAccessLevel", getAccessLevel());
			logDoc.replaceItemValue("LogUserRoles", getUserRoles());
			logDoc.replaceItemValue("LogClientVersion", getClientVersion());
			logDoc.replaceItemValue("LogAgentStartTime", getStartTime());

			if (getErrDoc() != null) {
				docDb = getErrDoc().getParentDatabase();
				rtitem = logDoc.createRichTextItem("LogDocInfo");
				rtitem.appendText("The document associated with this event is:");
				rtitem.addNewLine(1);
				rtitem.appendText("Server: " + docDb.getServer());
				rtitem.addNewLine(1);
				rtitem.appendText("Database: " + docDb.getFilePath());
				rtitem.addNewLine(1);
				rtitem.appendText("UNID: " + getErrDoc().getUniversalID());
				rtitem.addNewLine(1);
				rtitem.appendText("Note ID: " + getErrDoc().getNoteID());
				rtitem.addNewLine(1);
				rtitem.appendText("DocLink: ");
				rtitem.appendDocLink(_errDoc, getErrDoc().getUniversalID());
			}

			// make sure Depositor-level users can add documents too
			logDoc.appendItemValue("$PublicAccess", "1");

			logDoc.save(true);
			retval = true;
		} catch (Exception e) {
			debugPrint(e);
			retval = false;
		} finally {
			// recycle all the logDoc objects when we're done with them
			try {
				if (rtitem != null) rtitem.recycle();
			} catch (Exception e2) {
				// NTF why the hell does .recycle() throw an Exception?
			}
			rtitem = null;
			try {
				if (logDoc != null) logDoc.recycle();
			} catch (Exception e2) {
				// see above
			}
			logDoc = null;
			try {
				if (_startTime != null) _startTime.recycle();
			} catch (Exception e2) {
				// see above
			}
			_startTime = null;
			try {
				if (_eventTime != null) _eventTime.recycle();
			} catch (Exception e2) {
				// see above
			}
			_eventTime = null;
		}

		return retval;
	}

	/*
	 * This method decides what to do with any Exceptions that we encounter internal to this class, based on the
	 * olDebugLevel variable.
	 */
	private static void debugPrint(Throwable ee) {
		if ((ee == null) || (debugOut == null)) return;

		try {
			// debug level of 1 prints the basic error message#
			int debugLevel = Integer.parseInt(olDebugLevel);
			if (debugLevel >= 1) {
				String debugMsg = ee.toString();
				try {
					if (ee instanceof NotesException) {
						NotesException ne = (NotesException) ee;
						debugMsg = "Notes error " + ne.id + ": " + ne.text;
					}
				} catch (Exception e2) {
				}
				debugOut.println("OpenLogItem error: " + debugMsg);
			}

			// debug level of 2 prints the whole stack trace
			if (debugLevel >= 2) {
				debugOut.print("OpenLogItem error trace: ");
				ee.printStackTrace(debugOut);
			}
		} catch (Exception e) {
			// at this point, if we have an error just discard it
		}
	}

	/**
	 * @param component
	 *            String component ID
	 * @param msg
	 *            String message to be passed back to the browser
	 */
	public static void addFacesMessage(String component, String msg) {
		if (!"".equals(getDisplayErrorGeneric())) {
			if (null == ExtLibUtil.getRequestScope().get("genericOpenLogMessage")) {
				ExtLibUtil.getRequestScope().put("genericOpenLogMessage", "Added");
			} else {
				return;
			}
			msg = _displayErrorGeneric;
		}
		FacesContext.getCurrentInstance().addMessage(component, new FacesMessage(msg));
	}
}
