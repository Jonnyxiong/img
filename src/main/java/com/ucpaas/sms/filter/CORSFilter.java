package com.ucpaas.sms.filter;

import com.ucpaas.sms.utils.ConfigUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CORSFilter extends OncePerRequestFilter implements Filter {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		
		String webContext = request.getHeader("Referer") ;
		logger.debug("跨域访问服务器的站点 --------------> {} ,ip ---------> {}",webContext,request.getRemoteAddr());
//		if(webContext != null && checkAccessSite(webContext)){
			// CORS 的域名白名单，不支持正则，允许所有可以用 *
			response.addHeader("Access-Control-Allow-Origin", "*");
			// 对于非简单请求，浏览器会自动发送一个 OPTIONS 请求，利用 Header 来告知浏览器可以使用的请求方式及 Header 的类型
			if (request.getHeader("Access-Control-Request-Method") != null && "OPTIONS".equals(request.getMethod())) {
				response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
				response.addHeader("Access-Control-Allow-Headers", "Content-Type");
				response.addHeader("Access-Control-Max-Age", "1");
			}
			filterChain.doFilter(request, response);
//		}
    }
	/**
	 * 检查是否是允许访问的站点
	 * @param webContext
	 * @return boolean
	 */
	private boolean checkAccessSite (String webContext){
		String accessWebContext = ConfigUtils.access_web_context;
		String[] sites = accessWebContext.split(",");
		logger.debug("允许访问的站点 ------------> {}",accessWebContext);
		for (String site : sites) {
			if(webContext.contains(site)) return true;
		}
		logger.debug("禁止访问: 无效的访问的站点 ---------------> {}",webContext);
		return false;
	}
}