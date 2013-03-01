package com.paulwithers.openLog;

import javax.faces.FacesException;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;

import com.ibm.xsp.exception.EvaluationExceptionEx;
import com.ibm.xsp.extlib.util.ExtLibUtil;

public class OpenLogPhaseListener implements PhaseListener {
	private static final long serialVersionUID = 1L;
	private static final int RESTORE_VIEW = 1;
	private static final int APPLY_REQUEST_VALUES = 2;
	private static final int PROCESS_VALIDATIONS = 3;
	private static final int UPDATE_MODEL_VALUES = 4;
	private static final int INVOKE_APPLICATION = 5;
	private static final int RENDER_RESPONSE = 6;

	public void beforePhase(PhaseEvent event) {
		if (RENDER_RESPONSE == event.getPhaseId().getOrdinal()) {
			if (null != ExtLibUtil.getRequestScope().get("error")) {
				EvaluationExceptionEx ee = null;
				Object error = ExtLibUtil.getRequestScope().get("error");
				if ("com.ibm.xsp.exception.EvaluationExceptionEx".equals(error.getClass().getName())) {
					ee = (EvaluationExceptionEx) error;
				} else if ("javax.faces.FacesException".equals(error.getClass().getName())) {
					FacesException fe = (FacesException) error;
					ee = (EvaluationExceptionEx) fe.getCause();
				}
				OpenLogItem.logErrorEx(ee, "Error on " + ee.getErrorComponentId() + " " + ee.getErrorPropertyId()
						+ " property/value: " + ee.getErrorText(), null, null);
			}
		}
	}

	public void afterPhase(PhaseEvent event) {
	}

	public PhaseId getPhaseId() {
		return PhaseId.ANY_PHASE;
	}

}
