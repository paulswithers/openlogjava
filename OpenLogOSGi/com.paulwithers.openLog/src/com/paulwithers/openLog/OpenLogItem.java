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

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.logging.Level;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import com.ibm.commons.util.StringUtil;
import com.ibm.xsp.extlib.util.ExtLibUtil;

import lotus.domino.Database;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.RichTextItem;
import lotus.domino.Session;

public class OpenLogItem implements Serializable {
	/*
	 * ======================================================= <HEADER> NAME:
	 * OpenLogClass script library VERSION: 20070321a AUTHOR(S): Julian
	 * Robichaux ( http://www.nsftools.com ) ORIGINAL SOURCE: The OpenLog
	 * database, available as an open-source project at http://www.OpenNTF.org
	 * HISTORY: 20070321a: Added startTime global to mark when this particular
	 * agent run began, so you can group multiple errors/events together more
	 * easily (see the "by Database and Start Time" view) 20060314a: fixed bug
	 * where logErrorEx would set the message to the severity type instead of
	 * the value of msg. 20041111a: made SEVERITY_ and TYPE_ constants public.
	 * 20040928a: added callingMethodDepth variable, which should be incremented
	 * by one in the Synchronized class so we'll get a proper reference to the
	 * calling method; make $PublicAccess = "1" when we create new log docs, so
	 * users with Depositor access to this database can still create log docs.
	 * 20040226a: removed synchronization from all methods in the main
	 * OpenLogItem class and added the new SynchronizedOpenLogItem class, which
	 * simply extends OpenLogItem and calls all the public methods as
	 * synchronized methods. Added olDebugLevel and debugOut public members to
	 * report on internal errors. 20040223a: add variables for user name,
	 * effective user name, access level, user roles, and client version;
	 * synchronized most of the public methods. 20040222a: this version got a
	 * lot less aggressive with the Notes object recycling, due to potential
	 * problems. Also made LogErrorEx and LogEvent return "" if an error
	 * occurred (to be consistent with LogError); added OpenLogItem(Session s)
	 * constructor; now get server name from the Session object, not the
	 * AgentContext object (due to differences in what those two things will
	 * give you); add UseDefaultLogDb and UseCustomLogDb methods; added
	 * getLogDatabase method, to be consistent with LotusScript functions; added
	 * useServerLogWhenLocal and logToCurrentDatabase variables/options
	 * 20040217a: this version made the agentContext object global and fixed a
	 * problem where the agentContext was being recycled in the constuctor (this
	 * is very bad) 20040214b: initial version
	 *
	 * DISCLAIMER: This code is provided "as-is", and should be used at your own
	 * risk. The authors make no express or implied warranty about anything, and
	 * they will not be responsible or liable for any damage caused by the use
	 * or misuse of this code or its byproducts. No guarantees are made about
	 * anything.
	 *
	 * That being said, you can use, modify, and distribute this code in any way
	 * you want, as long as you keep this header section intact and in a
	 * prominent place in the code. </HEADER>
	 * =======================================================
	 *
	 * This class contains generic functions that can be used to log events and
	 * errors to the OpenLog database. All you have to do it copy this script
	 * library to any database that should be sending errors to the OpenLog
	 * database, and add it to your Java agents using the Edit Project button
	 * (see the "Using This Database" doc in the OpenLog database for more
	 * details).
	 *
	 * At the beginning of your agent, create a global instance of this class
	 * like this:
	 *
	 * private OpenLogItem oli = new OpenLogItem();
	 *
	 * and then in all the try/catch blocks that you want to send errors from,
	 * add the line:
	 *
	 * oli.logError(e);
	 *
	 * where "e" is the Exception that you caught. That's all you have to do.
	 * The LogError method will automatically create a document in the OpenLog
	 * database that contains all sorts of information about the error that
	 * occurred, including the name of the agent and function/sub that it
	 * occurred in.
	 *
	 * For additional functionality, you can use the LogErrorEx function to add
	 * a custom message, a severity level, and/or a link to a NotesDocument to
	 * the log doc.
	 *
	 * In addition, you can use the LogEvent function to add a notification
	 * document to the OpenLog database.
	 *
	 * You'll notice that I trap and discard almost all of the Exceptions that
	 * may occur as the methods in this class are running. This is because the
	 * class is normally only used when an error is occurring anyway, so there's
	 * not sense in trying to pass any new errors back up the stack.
	 *
	 * The master copy of this script library resides in the OpenLog database.
	 * All copies of this library in other databases should be set to inherit
	 * changes from that database.
	 */

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	private final static String _logFormName = "LogEvent";

	private String _logDbName = "";

	private String _thisDatabase;
	private String _thisServer;
	private String _thisAgent;
	// why the object? Because the object version is serializable
	private Boolean _logSuccess = true;
	private String _accessLevel;
	private Vector<String> _userRoles;
	private Vector<String> _clientVersion;

	private String _formName;
	private Level _severity;
	private String _eventType;
	private String _message;

	private Throwable _baseException;
	private Date _startJavaTime;
	private Date _eventJavaTime;
	private String _errDocUnid;

	// These objects cannot be serialized, so they must be considered transient
	// so they'll be null on a restore
	private transient Session _session;
	private transient Session _sessionAsSigner;
	private transient Database _logDb;
	private transient Boolean _suppressEventStack;
	private transient String _logEmail;
	private transient String _logExpireDate;
	private transient Database _currentDatabase;
	private transient DateTime _startTime;
	private transient DateTime _eventTime;
	private transient Document _errDoc;
	private transient Boolean suppressControlIdsForEvents;
	private transient Boolean _displayError;
	private transient String _displayErrorGeneric;
	private transient String _currentDbPath;

	/**
	 * Enum to define log type
	 *
	 * @since 6.0.0
	 */
	public static enum LogType {
		TYPE_ERROR("Error"), TYPE_EVENT("Event");

		private final String value_;

		private LogType(final String value) {
			value_ = value;
		}

		public String getValue() {
			return value_;
		}
	}

	/**
	 * Sets the Throwable that is the error to be logged
	 *
	 * @param base
	 */
	public void setBase(Throwable base) {
		_baseException = base;
	}

	/**
	 * Gets the Throwable that is the error to be logged
	 *
	 * @return Throwable current error object
	 */
	public Throwable getBase() {
		return _baseException;
	}

	/**
	 * Sets the severity level to be logged
	 *
	 * @param severity
	 *            Level severity
	 */
	public void setSeverity(Level severity) {
		_severity = severity;
	}

	/**
	 * @param message
	 *            the message to set
	 */
	public void setMessage(String message) {
		_message = message;
	}

	/**
	 * Gets the database the error is being logged for
	 *
	 * @return String database filepath
	 */
	public String getThisDatabase() {
		if (_thisDatabase == null) {
			try {
				_thisDatabase = getCurrentDatabase().getFilePath();
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		}
		return _thisDatabase;
	}

	/**
	 * @return the thisServer
	 */
	public String getThisServer() {
		if (_thisServer == null) {
			try {
				_thisServer = getSession().getServerName();
				if (_thisServer == null) {
					_thisServer = "";
				}
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		}
		return _thisServer;

	}

	/**
	 * @return the thisAgent
	 */
	public String getThisAgent() {
		if (_thisAgent == null) {
			setThisAgent(true);
		}
		return _thisAgent;
	}

	/**
	 * Complex method to get the "page" to log the error against.
	 * xsp.openlog.includeQueryString allows the querystring to be logged as
	 * well. If parameter is true, it logs for the current XPage being
	 * processed. Otherwise, we're now in the application's Error page, so we
	 * need to get the previous page (if there is one) and log for that.
	 *
	 * @param currPage
	 *            boolean whether to log current URL or previous
	 */
	public void setThisAgent(boolean currPage) {
		String fromPage = "";
		final String includeQueryString = OpenLogUtil.getXspProperty("xsp.openlog.includeQueryString", "false");
		final String[] historyUrls = ExtLibUtil.getXspContext().getHistoryUrls();
		if (StringUtil.isEmpty(historyUrls)) {
			fromPage = ExtLibUtil.getXspContext().getUrl().toSiteRelativeString(ExtLibUtil.getXspContext());
		} else {
			if (currPage) {
				fromPage = historyUrls[0];
			} else {
				if (historyUrls.length > 1) {
					fromPage = historyUrls[1];
				} else {
					fromPage = historyUrls[0];
				}
			}
		}
		if (fromPage.indexOf("/") > -1) {
			fromPage = fromPage.substring(1, fromPage.length());
		}
		if (!"true".equalsIgnoreCase(includeQueryString)) {
			if (fromPage.indexOf("?") > -1) {
				fromPage = fromPage.substring(1, fromPage.indexOf("?"));
			}
		}
		_thisAgent = fromPage;
	}

	/**
	 * @return the logDb
	 */
	public Database getLogDb() {
		if (_logDb == null) {
			try {
				_logDb = getSession().getDatabase(getThisServer(), getLogDbName(), false);
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean pointless = _logDb.isOpen();
			} catch (final NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_logDb = getSession().getDatabase(getThisServer(), getLogDbName(), false);
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _logDb;
	}

	/**
	 * @return the currentDatabase
	 */
	public Database getCurrentDatabase() {
		if (_currentDatabase == null) {
			try {
				_currentDatabase = getSession().getCurrentDatabase();
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean pointless = _currentDatabase.isOpen();
			} catch (final NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_currentDatabase = getSession().getCurrentDatabase();
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _currentDatabase;
	}

	public void setCurrentDatabase(final Database db) {
		_currentDatabase = db;
	}

	public String getCurrentDatabasePath() {
		try {
			final Database db = getCurrentDatabase();
			if (null == db) {
				return "";
			} else {
				return db.getFilePath();
			}
		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
			return "";
		}
	}

	/**
	 * @return the userName
	 */
	public String getUserName() {
		try {
			return getSession().getUserName();
		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
			return "";
		}
	}

	/**
	 * @return the effName
	 */
	public String getEffName() {
		try {
			return getSession().getEffectiveUserName();
		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
			return "";
		}
	}

	/**
	 * @return the accessLevel
	 */
	public String getAccessLevel() {
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
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		}
		return _accessLevel;
	}

	/**
	 * @return the userRoles
	 */
	@SuppressWarnings("unchecked")
	public Vector<String> getUserRoles() {
		if (_userRoles == null) {
			try {
				_userRoles = getSession().evaluate("@UserRoles");
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		}
		return _userRoles;
	}

	/**
	 * @return the server / client (XPiNC, not Run On Server) Version
	 */
	public Vector<String> getClientVersion() {
		if (_clientVersion == null) {
			_clientVersion = new Vector<String>();
			try {
				final String cver = getSession().getNotesVersion();
				if (cver != null) {
					if (cver.indexOf("|") > 0) {
						_clientVersion.addElement(cver.substring(0, cver.indexOf("|")));
						_clientVersion.addElement(cver.substring(cver.indexOf("|") + 1));
					} else {
						_clientVersion.addElement(cver);
					}
				}
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		}
		return _clientVersion;
	}

	/**
	 * @return the startTime
	 */
	public DateTime getStartTime() {
		if (_startTime == null) {
			try {
				_startTime = getSession().createDateTime("Today");
				_startTime.setNow();
				_startJavaTime = _startTime.toJavaDate();
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean junk = _startTime.isDST();
			} catch (final NotesException recycleSucks) {
				try {
					_startTime = getSession().createDateTime(_startJavaTime);
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _startTime;
	}

	/**
	 *
	 */
	public void reinitialiseSettings() {
		_logEmail = null;
		_logDbName = null;
		_displayError = null;
		_displayErrorGeneric = null;
		olDebugLevel = getDefaultDebugLevel();
		_currentDatabase = null;
		_currentDbPath = null;
		_accessLevel = null;
		_eventTime = null;
	}

	/**
	 * @return the expire date
	 */
	public String getLogExpireDate() {
		if (StringUtil.isEmpty(_logExpireDate)) {
			_logExpireDate = OpenLogUtil.getXspProperty("xsp.openlog.expireDate", "");
		}
		return _logExpireDate;
	}

	/**
	 * @return the log email address
	 */
	public String getLogEmail() {
		if (StringUtil.isEmpty(_logEmail)) {
			_logEmail = OpenLogUtil.getXspProperty("xsp.openlog.email", "");
		}
		return _logEmail;
	}

	/**
	 * @return the logDbName
	 */
	public String getLogDbName() {
		if ("".equals(_logDbName)) {
			_logDbName = OpenLogUtil.getXspProperty("xsp.openlog.filepath", "OpenLog.nsf");
			if ("[CURRENT]".equalsIgnoreCase(_logDbName)) {
				setLogDbName(getThisDatabasePath());
			}
		}
		return _logDbName;
	}

	/**
	 * Gets xsp.property of whether to suppress stack trace. Should be
	 * xsp.openlog.suppressEventStack=true to suppress. Anything else will
	 * return false
	 *
	 * @return whether or not stack should be suppressed for events
	 * @since 4.0.0
	 */
	public Boolean getSuppressEventStack() {
		final String dummyVar = OpenLogUtil.getXspProperty("xsp.openlog.suppressEventStack", "false");
		if (StringUtil.isEmpty(dummyVar)) {
			setSuppressEventStack(true);
		} else if ("false".equalsIgnoreCase(dummyVar)) {
			setSuppressEventStack(false);
		} else {
			setSuppressEventStack(true);
		}
		return _suppressEventStack;
	}

	/**
	 * @param suppressEventStack
	 *            Boolean whether or not to suppress stack trace for Events
	 * @since 4.0.0
	 */
	public void setSuppressEventStack(final Boolean suppressEventStack) {
		_suppressEventStack = suppressEventStack;
	}

	/**
	 * @return the database path
	 */
	private String getThisDatabasePath() {
		try {
			return getCurrentDatabase().getFilePath();
		} catch (final NotesException e) {
			OpenLogUtil.debugPrint(e);
			return "";
		}
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
	public int getErrLine(Throwable ee) {
		return ee.getStackTrace()[0].getLineNumber();
	}

	/**
	 * @return the severity
	 */
	public Level getSeverity() {
		return _severity;
	}

	/**
	 * @return the eventTime
	 */
	public DateTime getEventTime() {
		if (_eventTime == null) {
			try {
				_eventTime = getSession().createDateTime("Today");
				_eventTime.setNow();
				_eventJavaTime = _eventTime.toJavaDate();
			} catch (final Exception e) {
				OpenLogUtil.debugPrint(e);
			}
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean junk = _eventTime.isDST();
			} catch (final NotesException recycleSucks) {
				try {
					_eventTime = getSession().createDateTime(_eventJavaTime);
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _eventTime;
	}

	/**
	 * @return the eventType
	 */
	public String getEventType() {
		return _eventType;
	}

	/**
	 * @return the message
	 */
	public String getMessage() {
		if (_message.length() > 0) {
			return _message;
		}
		return getBase().getMessage();
	}

	/**
	 * @return the errDoc
	 */
	public Document getErrDoc() {
		if (_errDoc != null) {
			try {
				@SuppressWarnings("unused")
				final boolean junk = _errDoc.isProfile();
			} catch (final NotesException recycleSucks) {
				try {
					_errDoc = getCurrentDatabase().getDocumentByUNID(_errDocUnid);
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _errDoc;
	}

	/**
	 * @param doc
	 *            the document
	 */
	public void setErrDoc(Document doc) {
		if (doc != null) {
			_errDoc = doc;
			try {
				_errDocUnid = doc.getUniversalID();
			} catch (final NotesException ne) {
				OpenLogUtil.debugPrint(ne);
			} catch (final Exception ee) { // Added PW
				OpenLogUtil.debugPrint(ee); // Added PW
			}
		}
	}

	// this variable sets the "debug level" of all the methods. Right now
	// the valid debug levels are:
	// 0 -- internal errors are discarded
	// 1 -- Exception messages from internal errors are printed
	// 2 -- stack traces from internal errors are also printed
	public transient String olDebugLevel = OpenLogUtil.getXspProperty("xsp.openlog.debugLevel", "2");

	// this is a strange little variable we use to determine how far down the
	// stack
	// the calling method should be (see the getBasicLogFields method if you're
	// curious)
	// protected int callingMethodDepth = 2;

	// Added PW 27/04/2011 to initialise variables for XPages Java and allow the
	// user to update logDbName
	public void setLogDbName(String newLogPath) {
		_logDbName = newLogPath;
	}

	public void setOlDebugLevel(String newDebugLevel) {
		olDebugLevel = newDebugLevel;
	}

	/**
	 * Allows suppressing control IDs when logging messages from openLogBean
	 * with SSJS
	 *
	 * @return the includeControlIdsForEvents
	 * @since 6.0.0
	 */
	public Boolean isSuppressControlIdsForEvents() {
		if (null == suppressControlIdsForEvents) {
			setSuppressControlIdsForEvents();
		}
		return suppressControlIdsForEvents;
	}

	/**
	 * Allows suppressing control IDs when logging messages from openLogBean
	 * with SSJS
	 *
	 * @param includeControlIdsForEvents
	 *            the includeControlIdsForEvents to set
	 * @since 6.0.0
	 */
	public void setSuppressControlIdsForEvents() {
		suppressControlIdsForEvents = false;
		final String retVal = OpenLogUtil.getXspProperty("xsp.openlog.suppressEventControl", "");
		if (!"".equals(retVal)) {
			suppressControlIdsForEvents = true;
		}
	}

	/**
	 * @return whether errors should be displayed or not
	 * @since 2.0.0
	 */
	public Boolean getDisplayError() {
		if (null == _displayError) {
			final String dummyVar = OpenLogUtil.getXspProperty("xsp.openlog.displayError", "true");
			if ("false".equalsIgnoreCase(dummyVar)) {
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
	 * @since 2.0.0
	 */
	public void setDisplayError(Boolean error) {
		_displayError = error;
	}

	/**
	 * @return String of a generic error message or an empty string
	 * @since 2.0.0
	 */
	public String getDisplayErrorGeneric() {
		if (null == _displayErrorGeneric) {
			_displayErrorGeneric = OpenLogUtil.getXspProperty("xsp.openlog.genericErrorMessage", "");
		}
		return _displayErrorGeneric;
	}

	/*
	 * Use this constructor when you're creating an instance of the class within
	 * the confines of an Agent. It will automatically pick up the agent name,
	 * the current database, and the server.
	 */
	public OpenLogItem() {

	}

	/**
	 * @return the session
	 */
	private Session getSession() {
		if (_session == null) {
			_session = ExtLibUtil.getCurrentSession();
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean pointless = _session.isOnServer();
			} catch (final NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_session = ExtLibUtil.getCurrentSession();
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _session;
	}

	/**
	 * @return the signer session
	 */
	private Session getSessionAsSigner() {
		if (_sessionAsSigner == null) {
			_sessionAsSigner = ExtLibUtil.getCurrentSessionAsSigner();
		} else {
			try {
				@SuppressWarnings("unused")
				final boolean pointless = _sessionAsSigner.isOnServer();
			} catch (final NotesException recycleSucks) {
				// our database object was recycled so we'll need to get it
				// again
				try {
					_sessionAsSigner = ExtLibUtil.getCurrentSessionAsSigner();
				} catch (final Exception e) {
					OpenLogUtil.debugPrint(e);
				}
			}
		}
		return _sessionAsSigner;
	}

	/*
	 * We can't really safely recycle() any of the global Notes objects, because
	 * there's no guarantee that nothing else is using them. Instead, just set
	 * everything to null
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
	 * The basic method you can use to log an error. Just pass the Exception
	 * that you caught and this method collects information and saves it to the
	 * OpenLog database.
	 */
	public String logError(Throwable ee) {
		if (ee != null) {
			for (final StackTraceElement elem : ee.getStackTrace()) {
				if (elem.getClassName().equals(OpenLogItem.class.getName())) {
					// NTF - we are by definition in a loop
					System.out.println(ee.toString());
					OpenLogUtil.debugPrint(ee);
					_logSuccess = false;
					return "";
				}
			}
		}
		try {
			final StackTraceElement[] s = ee.getStackTrace();
			final String m = "Error in " + s[0].getClassName() + ", line " + s[0].getLineNumber() + ": " + ee.toString();
			addFacesMessage("", m);
			setBase(ee);

			// if (ee.getMessage().length() > 0) {
			if (ee.getMessage() != null) {
				setMessage(ee.getMessage());
			} else {
				setMessage(ee.getClass().getCanonicalName());
			}
			setSeverity(Level.WARNING);
			setEventType(LogType.TYPE_ERROR);

			_logSuccess = writeToLog();
			return getMessage();

		} catch (final Exception e) {
			System.out.println(e.toString());
			OpenLogUtil.debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	/**
	 * @param typeError
	 *            LogType of Error or Event
	 */
	private void setEventType(LogType typeError) {
		_eventType = typeError.getValue();
	}

	/*
	 * A slightly more flexible way to send an error to the OpenLog database.
	 * This allows you to include a message string (which gets added to the log
	 * document just before the stack trace), a severity type (normally one of
	 * the standard severity types within this class: OpenLogItem.SEVERITY_LOW,
	 * OpenLogItem.SEVERITY_MEDIUM, or OpenLogItem.SEVERITY_HIGH), and/or a
	 * Document that may have something to do with the error (in which case a
	 * DocLink to that Document will be added to the log document).
	 */
	public String logErrorEx(Throwable ee, String msg, Level severityType, Document doc) {
		if (ee != null) {
			for (final StackTraceElement elem : ee.getStackTrace()) {
				if (elem.getClassName().equals(OpenLogItem.class.getName())) {
					// NTF - we are by definition in a loop
					System.out.println(ee.toString());
					OpenLogUtil.debugPrint(ee);
					_logSuccess = false;
					return "";
				}
			}
		}
		try {
			setBase((ee == null ? new Throwable() : ee));
			setMessage((msg == null ? "" : msg));
			setSeverity(severityType == null ? Level.WARNING : severityType);
			setEventType(LogType.TYPE_ERROR);
			setErrDoc(doc);

			_logSuccess = writeToLog();
			return msg;

		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	/*
	 * This method allows you to log an Event to the OpenLog database. You
	 * should include a message describing the event, a severity type (normally
	 * one of the standard severity types within this class:
	 * OpenLogItem.SEVERITY_LOW, OpenLogItem.SEVERITY_MEDIUM, or
	 * OpenLogItem.SEVERITY_HIGH), and optionally a Document that may have
	 * something to do with the event (in which case a DocLink to that Document
	 * will be added to the log document).
	 */
	public String logEvent(Throwable ee, String msg, Level severityType, Document doc) {
		try {
			setMessage(msg);
			setSeverity(severityType == null ? Level.INFO : severityType);
			setEventType(LogType.TYPE_EVENT);
			setErrDoc(doc);
			if (ee == null) { // Added PW - LogEvent will not pass a throwable
				setBase(new Throwable("")); // Added PW
			} else { // Added PW
				setBase(ee); // Added PW
			} // Added PW
			_logSuccess = writeToLog();
			return msg;

		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
			_logSuccess = false;
			return "";
		}
	}

	/*
	 * Get the stack trace of an Exception as a Vector, without the initial
	 * error message, and skipping over a given number of items (as determined
	 * by the skip variable)
	 */
	private Vector<String> getStackTrace(Throwable ee, int skip) {
		final Vector<String> v = new Vector<String>(32);
		try {
			final StringWriter sw = new StringWriter();
			ee.printStackTrace(new PrintWriter(sw));
			final StringTokenizer st = new StringTokenizer(sw.toString(), "\n");
			int count = 0;
			while (st.hasMoreTokens()) {
				if (skip <= count++) {
					v.addElement(st.nextToken().trim());
				} else {
					st.nextToken();
				}
			}

		} catch (final Exception e) {
			OpenLogUtil.debugPrint(e);
		}

		return v;
	}

	/**
	 * @param ee
	 *            the Throwable
	 * @return Vector of stack trace elements
	 */
	private Vector<String> getStackTrace(Throwable ee) {
		return getStackTrace(ee, 0);
	}

	private String getDefaultDebugLevel() {
		try {
			final String defaultLevel_ = OpenLogUtil.getXspProperty("xsp.openlog.debugLevel", "2");
			return defaultLevel_;
		} catch (final Throwable t) {
			OpenLogUtil.debugPrint(t);
			return "2";
		}
	}

	/**
	 * @param s
	 *            Session the session to log for
	 * @param ee
	 *            Throwable the error to log
	 */
	public void logError(Session s, Throwable ee) {
		if (s != null) {
			_session = s;
		}
		logError(ee);
	}

	/**
	 * @param s
	 *            Session the session to log for
	 * @param ee
	 *            Throwable the error to log
	 * @param message
	 *            String the alternative message to log
	 * @param severity
	 *            Level to log as
	 * @param doc
	 *            Document to provide link for
	 */
	public void logError(Session s, Throwable ee, String message, Level severity, Document doc) {
		if (s != null) {
			_session = s;
		}
		logErrorEx(ee, message, severity, doc);
	}

	/**
	 * @param s
	 *            Session the session to log for
	 * @param ee
	 *            Throwable the event to log
	 * @param message
	 *            String the alternative message to log
	 * @param severity
	 *            Level to log as
	 * @param doc
	 *            Document to provide link for
	 */
	public void logEvent(Session s, Throwable ee, String message, Level severity, Document doc) {
		if (s != null) {
			_session = s;
		}
		logEvent(ee, message, severity, doc);
	}

	/*
	 * This is the method that does the actual logging to the OpenLog database.
	 * You'll notice that I actually have a Database parameter, which is
	 * normally a reference to the OpenLog database that you're using. However,
	 * it occurred to me that you might want to use this class to write to an
	 * alternate log database at times, so I left you that option (although
	 * you're stuck with the field names used by the OpenLog database in that
	 * case).
	 *
	 * This method creates a document in the log database, populates the fields
	 * of that document with the values in our global variables, and adds some
	 * associated information about any Document that needs to be referenced. If
	 * you do decide to send log information to an alternate database, you can
	 * just call this method manually after you've called logError or logEvent,
	 * and it will write everything to the database of your choice.
	 */
	public boolean writeToLog() {
		// exit early if there is no database
		Database db = null;
		boolean retval = false;
		Document logDoc = null;
		RichTextItem rtitem = null;
		Database docDb = null;

		try {
			if (!StringUtil.equals(getCurrentDatabasePath(), ExtLibUtil.getCurrentDatabase().getFilePath())) {
				reinitialiseSettings();
			}

			if (StringUtil.isEmpty(getLogEmail())) {
				db = getLogDb();
				if (db == null) {
					db = createLogDbFromTemplate();
				}
			} else {
				db = getSessionAsSigner().getDatabase(getThisServer(), "mail.box", false);
			}
			if (db == null) {
				System.out.println("Could not retrieve database at path " + getLogDbName());
				return false;
			}

			logDoc = db.createDocument();
			rtitem = logDoc.createRichTextItem("LogDocInfo");

			logDoc.appendItemValue("Form", _logFormName);

			final Throwable ee = getBase();
			String errMsg = "";
			if (null != ee) {
				final StackTraceElement ste = ee.getStackTrace()[0];
				if (ee instanceof NotesException) {
					logDoc.replaceItemValue("LogErrorNumber", ((NotesException) ee).id);
					errMsg = ((NotesException) ee).text;
				} else if ("Interpret exception".equals(ee.getMessage()) && ee instanceof com.ibm.jscript.JavaScriptException) {
					final com.ibm.jscript.InterpretException ie = (com.ibm.jscript.InterpretException) ee;
					errMsg = "Expression Language Interpret Exception " + ie.getExpressionText();
				} else {
					errMsg = ee.getMessage();
				}

				if (LogType.TYPE_EVENT.getValue().equals(getEventType())) {
					if (!getSuppressEventStack()) {
						logDoc.replaceItemValue("LogStackTrace", getStackTrace(ee));
					}
				} else {
					logDoc.replaceItemValue("LogStackTrace", getStackTrace(ee));
				}
				logDoc.replaceItemValue("LogErrorLine", ste.getLineNumber());
				logDoc.replaceItemValue("LogFromMethod", ste.getClass() + "." + ste.getMethodName());
			}

			if ("".equals(errMsg)) {
				errMsg = getMessage();
			} else {
				errMsg += " - " + getMessage();
			}

			logDoc.replaceItemValue("LogErrorMessage", errMsg);
			logDoc.replaceItemValue("LogEventTime", getEventTime());
			logDoc.replaceItemValue("LogEventType", getEventType());
			// If greater than 32k, put in logDocInfo
			if (getMessage().length() > 32000) {
				rtitem.appendText(getMessage());
				rtitem.addNewLine();
				logDoc.replaceItemValue("LogMessage", getMessage().substring(0, 100) + "...");
			} else {
				logDoc.replaceItemValue("LogMessage", getMessage());
			}
			logDoc.replaceItemValue("LogSeverity", getSeverity().getName());
			logDoc.replaceItemValue("LogFromDatabase", getCurrentDatabasePath());
			logDoc.replaceItemValue("LogFromServer", getThisServer());
			logDoc.replaceItemValue("LogFromAgent", getThisAgent());
			logDoc.replaceItemValue("LogAgentLanguage", "Java");
			logDoc.replaceItemValue("LogUserName", getUserName());
			logDoc.replaceItemValue("LogEffectiveName", getEffName());
			logDoc.replaceItemValue("LogAccessLevel", getAccessLevel());
			logDoc.replaceItemValue("LogUserRoles", getUserRoles());
			logDoc.replaceItemValue("LogClientVersion", getClientVersion());
			logDoc.replaceItemValue("LogAgentStartTime", getStartTime());

			if (getErrDoc() != null) {
				docDb = getErrDoc().getParentDatabase();
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

			if (StringUtil.isNotEmpty(getLogEmail())) {
				logDoc.replaceItemValue("Recipients", getLogEmail());
				logDoc.replaceItemValue("SendTo", getLogEmail());
				logDoc.replaceItemValue("From", getUserName());
				logDoc.replaceItemValue("Principal", getUserName());
			}

			// Set expiry date, if defined
			if (!StringUtil.isEmpty(getLogExpireDate())) {
				try {
					final Integer expiryPeriod = new Integer(getLogExpireDate());
					_startTime.adjustDay(expiryPeriod);
					logDoc.replaceItemValue("ExpireDate", _startTime);
				} catch (final Throwable t) {
					logDoc.replaceItemValue("ArchiveFlag",
							"WARNING: Xsp Properties in the application has a non-numeric value for xsp.openlog.expireDate, so cannot be set to auto-expire");
				}
			}
			logDoc.save(true);
			retval = true;
		} catch (final Throwable t) {
			OpenLogUtil.debugPrint(t);
			retval = false;
		} finally {
			// recycle all the logDoc objects when we're done with them
			try {
				if (rtitem != null) {
					rtitem.recycle();
				}
			} catch (final Exception e2) {
				// NTF why the hell does .recycle() throw an Exception?
			}
			rtitem = null;
			try {
				if (logDoc != null) {
					logDoc.recycle();
				}
			} catch (final Exception e2) {
				// see above
			}
			logDoc = null;
			try {
				if (_startTime != null) {
					_startTime.recycle();
				}
			} catch (final Exception e2) {
				// see above
			}
			_startTime = null;
			try {
				if (_eventTime != null) {
					_eventTime.recycle();
				}
			} catch (final Exception e2) {
				// see above
			}
			_eventTime = null;
		}

		return retval;
	}

	/**
	 * Checks whether there is an org.openlog.templateFilepath xsp/notes.ini
	 * variable. If so, creates a copy of that database to use as the logDb
	 *
	 * @return Database log database to log to
	 * @throws NotesException
	 */
	private Database createLogDbFromTemplate() throws NotesException {
		Database returnDb = null;
		// If a templateFilePath is defined, create a copy of the template to
		// the logDbFilePath
		final String templateFilePath = OpenLogUtil.getXspProperty("xsp.openlog.templateFilepath", "");
		if (!"".equals(templateFilePath)) {
			final Session sessFullAccess = ExtLibUtil.getCurrentSessionAsSignerWithFullAccess();
			final Database templateDb = sessFullAccess.getDatabase(sessFullAccess.getServerName(), templateFilePath, false);
			if (null != templateDb) {
				returnDb = templateDb.createCopy(sessFullAccess.getServerName(), getLogDbName());
			}
		}
		return returnDb;
	}

	/**
	 * @param component
	 *            String component ID
	 * @param msg
	 *            String message to be passed back to the browser
	 */
	public void addFacesMessage(String component, String msg) {
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
