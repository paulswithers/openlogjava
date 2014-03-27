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
package com.paulwithers.openLog.resources;


import com.ibm.xsp.resource.DojoModulePathResource;
import com.paulwithers.openLog.Activator;
import com.paulwithers.openLog.library.StarterLibrary;

public class ModulePath extends DojoModulePathResource {
	public final static String NAMESPACE = StarterLibrary.LIBRARY_RESOURCE_NAMESPACE;

	public ModulePath() {
		super(NAMESPACE, "/.ibmxspres/." + NAMESPACE + "/" + Activator.getVersion());
	}

}
