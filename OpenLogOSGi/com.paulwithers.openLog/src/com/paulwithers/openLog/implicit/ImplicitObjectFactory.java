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
package com.paulwithers.openLog.implicit;

import java.util.Map;

import javax.faces.context.FacesContext;

import com.ibm.xsp.context.FacesContextEx;
import com.ibm.xsp.util.TypedUtil;
import com.paulwithers.openLog.Activator;
import com.paulwithers.openLog.OpenLogUtil;

public class ImplicitObjectFactory implements com.ibm.xsp.el.ImplicitObjectFactory {
	private final String[][] implicitObjectList = { { "server", ImplicitObject.class.getName() } };
	private final static boolean _debug = Activator._debug;

	public ImplicitObjectFactory() {
		if (_debug)
			OpenLogUtil.print(getClass().getName() + " created");
	}

	@SuppressWarnings("unchecked")
	public void createImplicitObjects(FacesContextEx paramFacesContextEx) {
		Map localMap = TypedUtil.getRequestMap(paramFacesContextEx.getExternalContext());
		localMap.put("server", new ImplicitObject());
	}

	public Object getDynamicImplicitObject(FacesContextEx paramFacesContextEx, String paramString) {
		// TODO Auto-generated method stub
		return null;
	}

	public void destroyImplicitObjects(FacesContext paramFacesContext) {
		// TODO Auto-generated method stub

	}

	public String[][] getImplicitObjectList() {
		return this.implicitObjectList;
	}

}
