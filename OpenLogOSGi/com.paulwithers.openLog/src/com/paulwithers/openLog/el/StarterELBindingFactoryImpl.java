package com.paulwithers.openLog.el;

import javax.faces.application.Application;
import javax.faces.el.MethodBinding;
import javax.faces.el.ValueBinding;

import com.ibm.xsp.binding.BindingFactory;
import com.ibm.xsp.util.ValueBindingUtil;

public class StarterELBindingFactoryImpl implements BindingFactory {
	private final static String _prefix = "starter";

	@SuppressWarnings("unchecked")
	public MethodBinding createMethodBinding(Application arg0, String arg1, Class[] arg2) {
		String str = ValueBindingUtil.parseSimpleExpression(arg1);
		return new StarterMethodBinding(str);
	}

	public ValueBinding createValueBinding(Application arg0, String arg1) {
		String as[] = ValueBindingUtil.splitFormula(getPrefix(), arg1);
		return new StarterValueBinding(as[0]);
	}

	public String getPrefix() {
		return _prefix;
	}

}
