package com.paulwithers.tests;

//	Copyright 2013 Paul Withers Licensed under the Apache License, Version 2.0
//	(the "License"); you may not use this file except in compliance with the
//	License. You may obtain a copy of the License at
//
//	http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
//	or agreed to in writing, software distributed under the License is distributed
//	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
//	express or implied. See the License for the specific language governing
//	permissions and limitations under the License

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.View;

import com.ibm.xsp.extlib.util.ExtLibUtil;
import com.paulwithers.openLog.OpenLogItem;

public class OpenLogTest {
	public OpenLogTest() {

	}

	public static String demoFail() {
		String retVal = "";
		try {
			Database currDb = ExtLibUtil.getCurrentDatabase();
			View tmpView = currDb.getView("Fail");
			Document errDoc = tmpView.getFirstDocument();
		} catch (Exception e) {
			OpenLogItem.logError(e);
			OpenLogItem.addFacesMessage(null, e.getMessage());
		}
		return retVal;
	}
}
