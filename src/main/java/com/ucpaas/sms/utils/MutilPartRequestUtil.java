package com.ucpaas.sms.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.http.HttpServletRequest;


public class MutilPartRequestUtil {

    private static Logger logger = LoggerFactory.getLogger(MutilPartRequestUtil.class);

    public static HttpServletRequest resolver(HttpServletRequest request){
        //创建一个通用的多部分解析器.
        CommonsMultipartResolver commonsMultipartResolver = new CommonsMultipartResolver(request.getSession().getServletContext());
        //设置编码
        //判断 request 是否有文件上传,即多部分请求...
        if (commonsMultipartResolver.isMultipart(request)) {
            commonsMultipartResolver.setDefaultEncoding("utf-8");
            //转换成多部分request
            request = commonsMultipartResolver.resolveMultipart(request);
        }
        return request;
    }

}
