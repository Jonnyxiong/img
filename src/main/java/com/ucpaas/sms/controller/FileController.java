package com.ucpaas.sms.controller;

import com.ucpaas.sms.utils.FileUtils;
import com.ucpaas.sms.utils.SpringUtil;
import com.ucpaas.sms.utils.encrypt.EncryptUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;

@Controller
@RequestMapping("/file")
public class FileController{
	
	private static final Logger logger = LoggerFactory.getLogger(FileController.class);
	
	
	@RequestMapping("/view")
	@ResponseBody
	public void view(HttpServletRequest request,HttpServletResponse response) {
		String path = getPath(request, response);
		if (StringUtils.isNotBlank(path)) {
			FileUtils.view(response, path);
		}
	}
	/**
	 * @Description: 解析加密路径图片
	 * @author: Niu.T 
	 * @date: 2016年9月30日 下午12:16:14
	 */
	@RequestMapping("/scanPic")
	@ResponseBody
	public void viewEncrypt(HttpServletRequest request,HttpServletResponse response) {
		String path = getPath(request, response);
		if (StringUtils.isNotBlank(path)) {
			FileUtils.view(response, EncryptUtils.decodeDes3(path));
		}
	}
	
	/**
	 * 下载加密路径文件
	 * 
	 * @return
	 */
	@RequestMapping("/downloadFile")
	public void downloadFile(HttpServletRequest request,HttpServletResponse response) {
		String path = getPath(request, response);
		if (StringUtils.isNotBlank(path)) {
			FileUtils.download( EncryptUtils.decodeDes3(path),response);
		}
	}
	/**
	 * 下载文件
	 * 
	 * @return
	 */
	@RequestMapping("/file/download")
	public void download(HttpServletRequest request,HttpServletResponse response) {
		String path = getPath(request, response);
		if (StringUtils.isNotBlank(path)) {
			FileUtils.download(response, path);
		}
	}
	
	/**
	 * 获取文件路径
	 * 
	 * @return
	 */
	private String getPath(HttpServletRequest request,HttpServletResponse response) {
		String path = SpringUtil.getParameterTrim(request, "path");// 原始路径
		if (StringUtils.isNotBlank(path)) {
			try {
				path = new String(path.getBytes("iso-8859-1"), "utf-8");
			} catch (UnsupportedEncodingException e) {
				logger.error("文件路径编码转换失败：path=" + path, e);
			}
		} else {
			String encodePath = SpringUtil.getParameterTrim(request, "encode_path");// des3加密路径
			if (StringUtils.isNotBlank(encodePath)) {
				path = EncryptUtils.decodeDes3(encodePath);
			}
		}
		return path;
	}

}
