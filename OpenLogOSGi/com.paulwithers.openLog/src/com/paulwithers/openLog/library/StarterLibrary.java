/*
 * � Copyright GBS Inc 2011
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package com.paulwithers.openLog.library;

import com.ibm.xsp.library.AbstractXspLibrary;
import com.paulwithers.openLog.Activator;
import com.paulwithers.openLog.OpenLogUtil;

public class StarterLibrary extends AbstractXspLibrary {
	private final static String LIBRARY_ID = StarterLibrary.class.getName();
	// change this string to establish a namespace for your resources:
	public final static String LIBRARY_RESOURCE_NAMESPACE = "Paulwithers";
	public final static String LIBRARY_BEAN_PREFIX = "Paulwithers";
	private final static boolean _debug = Activator._debug;

	static {
		if (_debug) {
			OpenLogUtil.print(StarterLibrary.class.getName() + " loaded");
		}
	}

	public StarterLibrary() {
		if (_debug) {
			OpenLogUtil.print(StarterLibrary.class.getName() + " created");
		}
	}

	public String getLibraryId() {
		return LIBRARY_ID;
	}

	@Override
	public String getPluginId() {
		return Activator.PLUGIN_ID;
	}

	@Override
	public String[] getDependencies() {
		return new String[] { "com.ibm.xsp.core.library", "com.ibm.xsp.extsn.library", "com.ibm.xsp.domino.library",
				"com.ibm.xsp.designer.library", "com.ibm.xsp.extlib.library" };
	}

	@Override
	public String[] getXspConfigFiles() {
		String[] files = new String[] { "META-INF/starter.xsp-config" };

		return files;
	}

	@Override
	public String[] getFacesConfigFiles() {
		String[] files = new String[] { "META-INF/starter-faces-config.xml" };
		return files;
	}

	@Override
	public boolean isGlobalScope() {
		return false;
	}
}
