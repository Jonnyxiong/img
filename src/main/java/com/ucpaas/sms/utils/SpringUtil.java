package com.ucpaas.sms.utils;


import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class SpringUtil {

	private static Logger logger = LoggerFactory.getLogger(SpringUtil.class);
	
	@SuppressWarnings("unchecked")
	public static Map<String, String> getFormData(HttpServletRequest request) {
		Map<String, String> formData = new HashMap<String, String>();
		String value;
		Set<Entry<String, String[]>> entrySet = request.getParameterMap().entrySet();
		for (Map.Entry<String, String[]> map : entrySet) {
			value = StringUtils.join(map.getValue(), ",");
			if (StringUtils.isNotBlank(value)) {
				formData.put(map.getKey(), value.trim());
			}
		}
		logger.debug("\n\nformData-------------------------{}\n",formData);
		return formData;
	}	
	
	public static String getParameterTrim(HttpServletRequest request,String name) {
		return StringUtils.trim(request.getParameter(name));
	}
	
}
