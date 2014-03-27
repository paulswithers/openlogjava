/*
 * © Copyright GBS Inc 2011
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


import com.ibm.xsp.library.XspContributor;
import com.paulwithers.openLog.el.StarterELBindingFactoryImpl;
import com.paulwithers.openLog.implicit.ImplicitObjectFactory;

public class StarterContributor extends XspContributor {
	public static final String STARTER_IMPLICITOBJECTS_FACTORY = "com.paulwithers.openLog.implicit.IMPLICIT_OBJECT_FACTORY";
	public static final String STARTER_ELBINDING_FACTORY = "com.paulwithers.openLog.implicit.ELBINDING_FACTORY";

	public StarterContributor() {

	}

	@Override
	public Object[][] getFactories() {
		return new Object[][] { { STARTER_IMPLICITOBJECTS_FACTORY, ImplicitObjectFactory.class },
				{ STARTER_ELBINDING_FACTORY, StarterELBindingFactoryImpl.class } };
	}
}
