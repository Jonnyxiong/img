/**    
 * @Title: UploadController.java  
 * @Package: com.ucpaas.sms.controller  
 * @Description: TODO
 * @author: Niu.T    
 * @date: 2016年10月19日 上午11:29:46  
 * @version: V1.0    
 */
package com.ucpaas.sms.controller;

import com.ucpaas.sms.utils.ConfigUtils;
import com.ucpaas.sms.utils.MutilPartRequestUtil;
import com.ucpaas.sms.utils.encrypt.EncryptUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**  
 * @ClassName: UploadController  
 * @Description: 图片上传控制
 * @author: Niu.T 
 * @date: 2016年10月19日 上午11:29:46  
 */
@Controller
@RequestMapping("/upload")
public class UploadController {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	/**
	 * @Description: 图片上传到临时文件夹
	 * @author: Niu.T 
	 * @date: 2016年10月23日 下午12:10:24  
	 * @return: Map<String,Object>
	 */
	@RequestMapping(value = "/uploadCerImg", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> saveCer( HttpServletRequest request){

        String fileSize = request.getParameter("fileSize");
        String fileFormats = request.getParameter("fileFormats");
		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) MutilPartRequestUtil.resolver(request);
		CommonsMultipartFile photoFile = (CommonsMultipartFile) multipartRequest.getFile("photoFile");
		Map<String,Object> data = new HashMap<String,Object>();
		// 判断文件是否为空
		if (photoFile != null && !photoFile.isEmpty()) {
			data = checkPhotoFile(photoFile,fileFormats,fileSize);
			// 如果图片的不符合规则,则返回
			if(data.get("result") != null && !"success".equals(data.get("result"))) return data;
			// 获取保存文件的临时路径
			String tempPath = ConfigUtils.upload_pic_temp ;
			// 容错判断路径是否以 "/"结尾
			if(!tempPath.endsWith("/")) tempPath += "/";
			// 生成文件的前缀名,唯一
			StringBuffer prefix = new StringBuffer(UUID.randomUUID().toString());
			StringBuffer path = new StringBuffer(tempPath).append(EncryptUtils.encodeMd5(prefix.toString())).append("$$").append(prefix).append(data.get("suffix"));
			logger.debug("保存图片的路径为----------------->{}", path);
			File saveFile = new File(path.toString());
			// 判断上传文件的所在的临时文件夹是否存在
			if (!saveFile.getParentFile().exists()) {
				boolean mkdir = saveFile.getParentFile().mkdirs();
				logger.debug("临时文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
			}
			data = toSaveFile(photoFile, saveFile); 
			return data;
		}
		data.put("result", "none");
		data.put("msg","没有上传文件");
		return data;
	}

	/**
	 * @Description: 图片转存 
	 * @author: Niu.T 
	 * @date: 2016年10月23日 下午15:06:54  
	 * @param imgPath 	图片的带md5加密字符串的路径,参数名必须是 "imgPath"
	 * @param datePath 	时间路径 : yyyy/MM/dd/,参数名必须是"datePath"
	 * @return: Map<String,Object>
	 */
	@RequestMapping(value = "/saveCer", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> saveCerPic( HttpServletRequest request){
		Map<String,Object> data = new HashMap<String,Object>();
		//页面提交的图片路径 : md5+.jpg/.png/...
		String path = request.getParameter("imgPath");
		//时间路径 : yyyy/MM/dd/
		String datePath = request.getParameter("datePath");
		File tempFile = new File(path);
		String picPath = ConfigUtils.client_oauth_pic;
		// 容错判断,路径中是否带有"/"
		if(!picPath.endsWith("/")) picPath += "/";
		String fileName = "";
		try {
			fileName = path.substring(path.lastIndexOf("$$")+2);
		} catch (Exception e1) {//防止页面提交空数据
			data.put("msg", "图片保存失败了...");
			data.put("result", "fail");
			logger.debug("",e1.getMessage());
			return data;
		}
		File cerPic = new File(picPath + datePath,fileName);
		logger.debug("图片上传到配置文件路径下的文件夹--->{},保存路径--->{},文件名--->{}",picPath,datePath,fileName);
		// 判断保存文件路径的文件夹是否存在
		if (!cerPic.getParentFile().exists()) {
			boolean mkdir = cerPic.getParentFile().mkdirs();
			logger.debug("临时文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
		}
		try {
			FileUtils.copyFile(tempFile, cerPic);
			FileUtils.deleteQuietly(tempFile);
			data.put("msg", "信息上传成功");
			data.put("result", "success");
			logger.debug("图片转存成功 -------------->{}",data);
		} catch (IOException e) {
			data.put("msg", "图片保存失败,请稍后重试");
			data.put("result", "fail");
			logger.debug("从临时文件夹转存到图片文件下失败 -------------->{}",e.getMessage());
		}
		return data;
	}
	
	/**
	 * @Description: 向指定路径上传图片
	 * @author: Niu.T 
	 * @date: 2016年11月11日    下午2:51:19
	 * @param @required "photoFile",上传图片的文件选择标签名!
	 * @param @required "path",即需要保存的路径,带文件名!
	 * @param "useSysPath",即是否使用系统路径,默认为使用,0为不使用(在系统默认路径下新建path路径)
	 * @return Map<String,Object>
	 */
	@RequestMapping(value = "/uploadToPath", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> uploadToPath( HttpServletRequest request){
		String path = request.getParameter("path");
        String fileSize = request.getParameter("fileSize");
		String fileFormats = request.getParameter("fileFormats");
		String useSysPath = request.getParameter("useSysPath");
		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) MutilPartRequestUtil.resolver(request);
		CommonsMultipartFile photoFile = (CommonsMultipartFile) multipartRequest.getFile("photoFile");
		Map<String,Object> data = new HashMap<String,Object>();
		if (photoFile != null && !photoFile.isEmpty()) {	// 判断文件是否为空
			data = checkPhotoFile(photoFile,fileFormats,fileSize);
			// 如果图片的不符合规则,则返回
			if(data.get("result") != null && !"success".equals(data.get("result"))) return data;	
			String sysPath = ConfigUtils.client_oauth_pic;	// 获取保存文件的临时路径
			// 容错判断路径是否以 "/"结尾
			if(sysPath.endsWith("/")) sysPath = sysPath.substring(0, sysPath.lastIndexOf("/"));
			// 判断是否使用系统路径
			if(useSysPath != null && "0".equals(useSysPath)) sysPath = "";
			// 容错判断路径是否以 "/"开头
			if(!path.startsWith("/")) path = "/"+ path;
			logger.debug("保存图片的路径为----------------->{}", sysPath + path);
			File saveFile = new File(sysPath + path);
			// 判断上传文件的所在的文件夹是否存在
			if (!saveFile.getParentFile().exists()) {
				boolean mkdir = saveFile.getParentFile().mkdirs();
				logger.debug("文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
			}
			data = toSaveFile(photoFile, saveFile); 
			return data;
		}
		data.put("result", "none");
		data.put("msg","没有上传文件");
		return data;
	}
	
	/**
	 * @Description: 上传图片,自动生成图片名称并保存到系统指定的路径下,返回图片的全路径信息
	 * @author: Niu.T 
	 * @date: 2016年11月11日    下午4:36:40
	 * @param @required "photoFile",上传图片的文件选择标签名!
	 * @return Map<String,Object>
	 */
	@RequestMapping(value = "/uploadAuto", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> uploadAuto( HttpServletRequest request){
        String fileFormats = request.getParameter("fileFormats");
        String fileSize = request.getParameter("fileSize");
        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) MutilPartRequestUtil.resolver(request);
		CommonsMultipartFile photoFile = (CommonsMultipartFile) multipartRequest.getFile("photoFile");
		Map<String,Object> data = new HashMap<String,Object>();
		// 判断文件是否为空
		if (photoFile != null && !photoFile.isEmpty()) {
			
			data = checkPhotoFile(photoFile,fileFormats,fileSize);
			// 如果图片的不符合规则,则返回
			if(data.get("result") != null && !"success".equals(data.get("result"))) return data;
			String fileName = UUID.randomUUID().toString() + data.get("suffix");
			// 获取保存文件的临时路径
			String sysPath = ConfigUtils.client_oauth_pic;
			// 容错判断路径是否以 "/"结尾
			if(sysPath.endsWith("/")) sysPath = sysPath.substring(0, sysPath.lastIndexOf("/"));
			// 在 路径中添加日期路径
			String path = new SimpleDateFormat("/yyyy/MM/dd/").format(new Date());
			logger.debug("保存图片的路径为----------------->{}", sysPath + path);
			File saveFile = new File(sysPath + path + fileName);
			// 判断上传文件的所在的文件夹是否存在
			if (!saveFile.getParentFile().exists()) {
				boolean mkdir = saveFile.getParentFile().mkdirs();
				logger.debug("文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
			}
			data = toSaveFile(photoFile, saveFile); 
			return data;
		}
		data.put("result", "none");
		data.put("msg","没有上传文件");
		return data;
	}

	/**
	 * @Description: 上传图片,自动生成图片名称并保存到系统指定的路径下,返回图片的全路径信息
	 * @author: Niu.T
	 * @date: 2016年11月11日    下午4:36:40
	 * @param @required "photoFile",上传图片的文件选择标签名!
	 * @return Map<String,Object>
	 */
	@RequestMapping(value = "/uploadAutoNew", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> uploadAutoNew( HttpServletRequest request){
		String fileFormats = request.getParameter("fileFormats");
		String fileSize = request.getParameter("fileSize");
		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) MutilPartRequestUtil.resolver(request);
		CommonsMultipartFile photoFile = (CommonsMultipartFile) multipartRequest.getFile("photoFile");
		Map<String,Object> data = new HashMap<String,Object>();
		// 判断文件是否为空
		if (photoFile != null && !photoFile.isEmpty()) {

			data = checkPhotoFile(photoFile,fileFormats,fileSize);
			// 如果图片的不符合规则,则返回
			if(data.get("result") != null && !"success".equals(data.get("result"))) return data;
			String fileName = UUID.randomUUID().toString() + data.get("suffix");
			// 获取保存文件的临时路径
			String sysPath = ConfigUtils.client_oauth_pic;
			// 容错判断路径是否以 "/"结尾
			if(sysPath.endsWith("/")) sysPath = sysPath.substring(0, sysPath.lastIndexOf("/"));
			// 在 路径中添加日期路径
			String path = new SimpleDateFormat("/yyyy/MM/dd/").format(new Date());
			logger.debug("保存图片的路径为----------------->{}", sysPath + path);
			File saveFile = new File(sysPath + path + fileName);
			// 判断上传文件的所在的文件夹是否存在
			if (!saveFile.getParentFile().exists()) {
				boolean mkdir = saveFile.getParentFile().mkdirs();
				logger.debug("文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
			}
			data = toSaveFile(photoFile, saveFile, request);
			return data;
		}
		data.put("result", "none");
		data.put("msg","没有上传文件");
		return data;
	}

	/**
	 * @Description: 上传文件,支持上传多个文件(文档和图片)返回图片的全路径信息
	 * @author: Niu.T 
	 * @date: 2016年11月29日    上午10:18:40
	 * @param @required "photoFile",上传图片的文件选择标签名!
	 * @return Map<String,Object>
	 */
	@RequestMapping(value = "/multiUpload", method = RequestMethod.POST)
	@ResponseBody
	public Map<String,Object> multiUpload( HttpServletRequest request){
        String fileFormats = request.getParameter("fileFormats");
        String fileSize = request.getParameter("fileSize");

        Map<String,Object> data = new HashMap<String,Object>();
		CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver(request.getSession().getServletContext());  
        //判断 request 是否有文件上传,即多部分请求  
        if(multipartResolver.isMultipart(request)){  
            //转换成多部分request    
            MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest)MutilPartRequestUtil.resolver(request);
            //取得request中的所有文件名  
            Iterator<String> iter = multiRequest.getFileNames(); 
            int count = 0,failcount = 0;
            Map<String,Object> checkData = new HashMap<String,Object>();
            while(iter.hasNext()){  
            	++ count;
                //记录上传过程起始时的时间，用来计算上传时间  
                //取得上传文件  
                MultipartFile file = multiRequest.getFile(iter.next());  
                if(file != null){  
                	checkData = checkFile(file,fileFormats,fileSize);
        			// 如果图片的不符合规则,则返回
        			if(checkData.get("result") != null && !"success".equals(checkData.get("result"))) return data;
        			String fileName = UUID.randomUUID().toString() + checkData.get("suffix");
        			// 获取保存文件的临时路径
        			String sysPath = ConfigUtils.client_oauth_pic;
        			// 容错判断路径是否以 "/"结尾
        			if(sysPath.endsWith("/")) sysPath = sysPath.substring(0, sysPath.lastIndexOf("/"));
        			// 在 路径中添加日期路径
        			String path = new SimpleDateFormat("/yyyy/MM/dd/").format(new Date());
        			logger.debug("保存图片的路径为----------------->{}", sysPath + path + fileName);
        			File saveFile = new File(sysPath + path + fileName);
        			// 判断上传文件的所在的文件夹是否存在
        			if (!saveFile.getParentFile().exists()) {
        				boolean mkdir = saveFile.getParentFile().mkdirs();
        				logger.debug("文件的所在文件夹不存在, 创建 成功 ?----------------->{}", mkdir);
        			}
        			try {
        				file.transferTo(saveFile);		// 保存临时图片
        				data.put("path"+ count,saveFile.getAbsolutePath());				// 封装临时文件所在的全路径信息
        				logger.debug("上传文件成功 ------------->{}",data);
        			} catch (IllegalStateException | IOException e) {
        				logger.debug("上传文件失败 ------------->{}",e.getMessage());
        				++failcount;
        			}
                }  
            } 
            if(count == 0){
            	data.put("result","fail");
            	data.put("msg","上传失败");
            }else{
            	data.put("result","success");
            	data.put("msg","上传成功");
            	data.put("fileNum",count - failcount);
            	
            }
            return data;
        }  
		data.put("result", "fail");
		data.put("msg","缺少上传文件");
		return data;
	}

	
	/**
	 * @Description: 判断图片类型和大小
	 * @author: Niu.T 
	 * @date: 2016年11月29日    上午12:03:49
	 * @return Map<String, Object>
	 */
	private  Map<String, Object>  checkFile(MultipartFile file,String fileFormats,String fileSize) {
		Map<String, Object> data = new HashMap<String,Object>();
        if(file.getOriginalFilename().trim() ==""){
			data.put("msg", "对不起，缺少上传的文件");
			data.put("result", "fail");
			logger.debug("上传文件的错误-------------> 没有选中上传的文件" );
			return data;
		}
		String suffix = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
        if(StringUtils.isNotEmpty(fileFormats) && !checkFormats(fileFormats.toLowerCase(),suffix)){
            data.put("msg", "对不起，上传的文件必须是"+fileFormats+"格式！");
            data.put("result", "fail");
            logger.debug("文件格式不是{},当前格式------------->{}",fileFormats,suffix );
            return data;
        }
        long size = file.getSize();
        if (fileSize != null && isNumeric(fileSize) && Integer.valueOf(fileSize) <size ) {			// 判断文件大小
            data.put("msg", "对不起，上传的文件大小不能超过"+ Integer.valueOf(fileSize)/1024+ "MB！");
            data.put("result", "fail");
            logger.debug("文件大小不能超过5M, 当前大小 ------------->{}",size);
            return data;
        }else if (size > 1024 * 10240) {			// 判断文件大小
			data.put("msg", "对不起，上传的文件大小必须小于10MB！");
			data.put("result", "fail");
			logger.debug("文件大小不能超过10M, 当前大小 ------------->{}",size);
			return data;
		}
		data.put("result", "success");
		data.put("suffix", suffix);	// 返回图片的后缀
		return data;
	}
	/**
	 * @Description: 判断图片类型和大小
	 * @author: Niu.T 
	 * @date: 2016年11月11日    下午3:18:49
	 * @return Map<String, Object>
	 */
	private  Map<String, Object>  checkPhotoFile(MultipartFile photoFile,String fileFormats,String fileSize) {
		Map<String, Object> data = new HashMap<String,Object>();
		String suffix = photoFile.getOriginalFilename().substring(photoFile.getOriginalFilename().lastIndexOf("."));

		if(StringUtils.isNotEmpty(fileFormats) && !checkFormats(fileFormats.toLowerCase(),suffix)){
            data.put("msg", "对不起，上传的图片格式必须是"+fileFormats+"格式！");
            data.put("result", "fail");
            logger.debug("图片格式不是{}！当前格式------------->{}",fileFormats,suffix );
            return data;
        }else if (!suffix.toLowerCase().equals(".gif") && !suffix.toLowerCase().equals(".jpg")
				&& !suffix.toLowerCase().equals(".png")&& !suffix.toLowerCase().equals(".jpeg")) {								// 判断文件类型
			data.put("msg", "对不起，上传的图片格式必须是jpg,gif,png格式！");
			data.put("result", "fail");
			logger.debug("图片格式不是png、gif、jpg、jpeg！当前格式------------->{}",suffix );
			return data;
		}

        long size = photoFile.getSize();

        if (fileSize != null && isNumeric(fileSize) && Integer.valueOf(fileSize) < size ) {			// 判断文件大小
			data.put("msg", "对不起，上传的图片大小不能超过"+ Integer.valueOf(fileSize)/1024+ "MB！");
			data.put("result", "fail");
			logger.debug("图片大小不能超过{}M, 当前大小 ------------->{}",Integer.valueOf(fileSize),size);
			return data;
		}else if(size > 1024 * 5120){
            data.put("msg", "对不起，上传的图片大小不能超过5MB！");
            data.put("result", "fail");
            logger.debug("图片大小不能超过5M, 当前大小 ------------->{}",size);
            return data;
        }
		data.put("result", "success");
		data.put("suffix", suffix);	// 返回图片的后缀
		return data;
	}

	/**
	 * @Description: 根据saveFile中信息,保存photoFile文件
	 * @author: Niu.T
	 * @date: 2016年11月11日    下午4:49:19
	 */
	private Map<String, Object> toSaveFile(MultipartFile file,File saveFile, HttpServletRequest request) {
		Map<String,Object> data = new HashMap<String,Object>();
		try {
			String ctx = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
					+ request.getContextPath();

			file.transferTo(saveFile);		// 保存临时图片
			//data.put("path",saveFile.getAbsolutePath());				// 封装临时文件所在的全路径信息
			data.put("path", ctx+"/file/scanPic.html?path="+EncryptUtils.encodeDes3(saveFile.getAbsolutePath()));
			data.put("result","success");
			data.put("msg","上传成功");
			logger.debug("上传文件成功 ------------->{}",data);
		} catch (IllegalStateException | IOException e) {
			logger.debug("上传文件失败 ------------->{}",e.getMessage());
			data.put("result","fail");
			data.put("msg","上传失败");
		}
		return data;
	}

	/**
	 * @Description: 根据saveFile中信息,保存photoFile文件
	 * @author: Niu.T 
	 * @date: 2016年11月11日    下午4:49:19
	 */
	private Map<String, Object> toSaveFile(MultipartFile file,File saveFile) {
		Map<String,Object> data = new HashMap<String,Object>();
		try {
			file.transferTo(saveFile);		// 保存临时图片
			data.put("path",saveFile.getAbsolutePath());				// 封装临时文件所在的全路径信息
			data.put("result","success");
			data.put("msg","上传成功");
			logger.debug("上传文件成功 ------------->{}",data);
		} catch (IllegalStateException | IOException e) {
			logger.debug("上传文件失败 ------------->{}",e.getMessage());
			data.put("result","fail");
			data.put("msg","上传失败");
		}
		return data;
	}

    private boolean checkFormats(String fileFormats,String suffix){
        String[] split = fileFormats.split(",");
        for (String s:split) {
            if(!s.startsWith("."))
                s = "." + s;
            if(suffix.toLowerCase().equals(s))
                return true;
        }
        return false;
    }

    /**
     * 判断是否是数字字符串
     * @param str
     * @return
     */
    private boolean isNumeric(String str){
        Pattern pattern = Pattern.compile("[0-9]*");
        Matcher isNum = pattern.matcher(str);
        if( !isNum.matches() ){
            return false;
        }
        return true;
    }
}
