package com.ucpaas.sms.controller;

import com.alibaba.fastjson.JSON;
import com.ucpaas.sms.utils.ConfigUtils;
import com.ucpaas.sms.utils.encrypt.EncryptUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Controller
@RequestMapping("/chunks")
public class WebUploaderController {

    private static final Logger logger = LoggerFactory.getLogger(WebUploaderController.class);
    private ConcurrentMap<String, String> fileCache = new ConcurrentHashMap();
    private static final long _5MB = 1024 * 1024 * 5;
    private static final String sysPath;

    static {
        // 容错判断路径是否以 "/" "\"结尾
        if (ConfigUtils.file_save_path.endsWith("/")) {
            sysPath = ConfigUtils.file_save_path.substring(0, ConfigUtils.file_save_path.lastIndexOf("/"));
        } else if (ConfigUtils.file_save_path.endsWith("\\")) {
            sysPath = ConfigUtils.file_save_path.substring(0, ConfigUtils.file_save_path.lastIndexOf("\\"));
        } else {
            sysPath = ConfigUtils.file_save_path;
        }
    }
    /**
     * 分块文件合并接口
     */
    @PostMapping("/merge")
    @ResponseBody
    public String merge(HttpServletRequest request, HttpServletResponse response) {
        String fileName = request.getParameter("fileName");
        String fileMd5 = request.getParameter("fileMd5");
        String flag = request.getParameter("flag");
        Map result = new HashMap();
        if(StringUtils.isBlank(fileMd5)){
            result.put("msg", "fileMd5不能为空");
            result.put("progress", "0");
        }else if(StringUtils.isBlank(fileName)){
            result.put("msg", "fileName不能为空");
            result.put("progress", "0");
        }
        if(!result.isEmpty()){
            return JSON.toJSONString(result);
        }
        String resStr = null;
        File tempFileDir;// 分块临时文件目录
        try {
            String tempSavePath = getChunksTempDir(flag, fileMd5, fileName).toString();
            //读取目录里的所有文件
            logger.debug("分块临时文件目录路径 --> {}", tempSavePath);
            tempFileDir = new File(tempSavePath);
            File[] fileArray = tempFileDir.listFiles(this.new AcceptFile());
            List<File> fileList = Arrays.asList(fileArray);
            Collections.sort(fileList, this.new CompareChunks());
            //生成合并后的文件
            String newFilePath = generateNewFilePathName(DateTime.now(), fileMd5, flag, fileName);
            File outputFile = new File(newFilePath);

            try {
                outputFile.createNewFile();//创建文件
            } catch (IOException e) {
                logger.error("创建文件异常 --> {}", e);
            }
            //输出流
            FileChannel outChnnel = new FileOutputStream(outputFile).getChannel();
            //合并
            FileChannel inChannel;
            int chunksSize = 0;
            for (File chunkFile : fileList) {
                inChannel = new FileInputStream(chunkFile).getChannel();
                try {
                    inChannel.transferTo(0, inChannel.size(), outChnnel);
                    inChannel.close();
                } catch (IOException e) {
                    logger.error("FileChannel 合并分块文件异常 ---> {}", e);
                }
                long length = chunkFile.length();
                chunksSize += length;
                logger.debug("分块文件名 => {}, 大小 ==> {}",chunkFile.getName(),length);
                chunkFile.delete();//删除分片

            }
            try {
                logger.debug("合并后的分块文件名 => {}, 合并文件大小 ==> {} , 每块大小合计 ==> {}",outputFile.getName(),outputFile.length(),chunksSize);

                outChnnel.close();
            } catch (IOException e) {
                logger.error("FileChannel 关闭资源异常 ---> {}", e);
            }

            if (tempFileDir != null && tempFileDir.isDirectory() && tempFileDir.exists()) { //清除临时文件夹
                tempFileDir.delete();
            }

            Map<String, String> resultMap = new HashMap<>();
            //将文件的最后上传时间和生成的文件名返回
            logger.debug("合并后保存的文件路径 ---> {}", newFilePath);
            resultMap.put("lastUploadTime", fileCache.get(getLastUploadTimeKey(flag, fileMd5, fileName)));
            resultMap.put("pathFileName", EncryptUtils.encodeDes3(newFilePath));

            /****************清除缓存中的相关信息**********************/
            removeCacheAfterDel(flag, fileMd5, fileName);

            resStr = JSON.toJSONString(resultMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.debug("resStr ---> {}", resStr);
        return resStr;
    }

    @PostMapping("/check")
    @ResponseBody
    public String check(HttpServletRequest request, HttpServletResponse response) {
        DateTime now = DateTime.now();
        String fileName = request.getParameter("fileName");
        String fileMd5 = request.getParameter("fileMd5");
        String flag = request.getParameter("flag");

        /*************************检查当前分块是否上传成功**********************************/
        String chunk = request.getParameter("chunk");
        String chunkSize = request.getParameter("chunkSize");
        String progress = request.getParameter("progress");//文件上传的实时进度

        if (StringUtils.isBlank(progress)) {
            progress = "0";
        }
        updateCacheAfterCheck(flag, fileMd5, fileName, progress, now);
        // 分片文件临时路径
        String tempSavePath = getChunksTempDir(flag, fileMd5, fileName).append(File.separatorChar).append(chunk).toString();
        logger.debug("检查上传文件临时文件夹下是否有分片文件 --> {}", tempSavePath);
        File checkFile = new File(tempSavePath);

        //检查文件是否存在，且大小是否一致
        if (checkFile.exists() && checkFile.length() == Integer.parseInt(chunkSize)) {//上传过
            logger.debug("上传过 ---> {\"isExist\":1}");
            return "{\"isExist\":1}";
        } else {//没有上传过
            logger.debug("没有上传过 ---> {\"isExist\":0}");
            return "{\"isExist\":0}";
        }
    }

    /**
     * 分块文件保存接口
     * 保存上传分片
     *
     * @param request
     * @param response 必须
     */
    @PostMapping("/file_save")
    @ResponseBody
    public String fileSave(HttpServletRequest request, HttpServletResponse response) {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload sfu = new ServletFileUpload(factory);
        sfu.setHeaderEncoding("utf-8");
        String fileMd5 = null;
        String chunk = null;
        String flag = null;
        String fileName;
        String resStr = null;
        try {
            List<FileItem> items = sfu.parseRequest(request);
            for (FileItem item : items) {
                //上传文件的真实名称
                fileName = item.getName();
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    try {
                        if (fieldName.equals("fileMd5")) {
                            fileMd5 = item.getString("utf-8");
                        }
                        if (fieldName.equals("flag")) {
                            flag = item.getString("utf-8");
                        }
                        if (fieldName.equals("chunk")) {
                            chunk = item.getString("utf-8");
                        }
                    } catch (UnsupportedEncodingException e) {
                        logger.error("保存文件时, 文件编码异常 -- > {}", e);
                    }
                } else {
                    if(item.getSize() > _5MB){
                        resStr = "{\"isSaved\":0,\"msg\":\"文件大小超出限制\"}";
                    }
                    StringBuilder tempSavePath = getChunksTempDir(flag, fileMd5, fileName);
                    logger.debug("保存的分块文件夹 --> {}", tempSavePath);
                    File file = new File(tempSavePath.toString());
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    String chunksTempPath = tempSavePath.append(File.separatorChar).append(chunk).toString();
                    logger.debug("保存的分块文件路径 ---->  {}", chunksTempPath);
                    File chunkFile = new File(chunksTempPath);
                    try {
                        FileUtils.copyInputStreamToFile(item.getInputStream(), chunkFile);
                        logger.debug("分块文件名 => {}, 大小 ==> {}",chunkFile.getName(),chunkFile.length());
                        resStr = "{\"isSaved\":1}";
                    } catch (IOException e) {
                        logger.error("拷贝分块文件异常 -- > {}", e);
                        resStr = "{\"isSaved\":0,\"msg\":\"文件保存失败\"}";
                    }
                }
            }
        } catch (FileUploadException e) {
            logger.error("文件上传中断 -- > {}", e);
            resStr = "{\"isSaved\":0,\"msg\":\"文件大小超出限制\"}";
        }

        return resStr;
    }

    /**
     * 文件进度查看接口
     * 当有文件添加进队列时 通过文件名查看该文件是否上传过 上传进度是多少
     *
     * @param fileName
     * @return
     */
    @PostMapping("/progress")
    @ResponseBody
    public String getProgress(String fileMd5, String flag, String fileName, String fileSize) {
        Map result = new HashMap();
        if(StringUtils.isBlank(fileMd5)){
            result.put("msg", "fileMd5不能为空");
            result.put("progress", "0");
        }else if(StringUtils.isBlank(fileName)){
            result.put("msg", "fileName不能为空");
            result.put("progress", "0");
        }else if(StringUtils.isBlank(fileSize)){
            result.put("msg", "fileSize不能为空");
            result.put("progress", "0");
        }
        if(!result.isEmpty()){
            return JSON.toJSONString(result);
        }

        String progress = null;
        if (null != fileName && !"".equals(fileName)) {
            String progressKey = getProgressKey(flag, fileMd5, fileName);
            progress = fileCache.get(progressKey);
        }

        if (StringUtils.isBlank(progress)) {
            DateTime now = DateTime.now();
            long size = Long.parseLong(fileSize);
            String path;
            for (int i = 0; i < 2; i++) { //查看当前月和上个月的是否有相同文件
                path = generateNewFilePathName(now.minusMonths(i), fileMd5, flag, fileName);
                File checkFile = new File(path);
                if (checkFile.exists() && checkFile.isFile()) {
                    long length = checkFile.length();
                    logger.debug("查看文件上传进度前, 先检查文件是否存在,文件存在, 大小 == {}, 待上传的文件大小 == {}",length,size);
                    if(length == size){
                        progress = "100";
                        result.put("pathFileName", EncryptUtils.encodeDes3(path));
                        break;
                    }
                }
                logger.debug("查看文件上传进度前, 先检查文件是否存在, 文件不存在 ---> {}",path);
            }
            if (StringUtils.isBlank(progress)) {
                progress = "0";
            }
        }

        result.put("progress", progress);
        String resultStr = JSON.toJSONString(result);
        logger.debug("查看进度 -------> {}", resultStr);
        return resultStr;
    }

    /**
     * 临时文件删除接口
     * 文件没有合并前的删除接口
     *
     * @param fileName
     * @return
     */
    @PostMapping("/del_chunks")
    @ResponseBody
    public String delChunks(String flag, String fileMd5, String fileName) {
        Map result = new HashMap();
        String tempSavePath = getChunksTempDir(flag, fileMd5, fileName).toString();
        File tempFileDir = new File(tempSavePath);
        boolean delete = false;
        if (tempFileDir != null && tempFileDir.exists() && tempFileDir.isDirectory() ) { //清除临时文件夹
            try {
                File[] fileArray = tempFileDir.listFiles();
                for (File file : fileArray) {
                    file.delete();
                }
                delete = tempFileDir.delete();
            } catch (Exception e) {
                logger.error("删除临时文件异常 ---> {}", e);
                return "{\"isDeleted\":0}";
            }
        }
        if (delete) {
            removeCacheAfterDel(flag, fileMd5, fileName);
        }
        return "{\"isDeleted\":1}";
    }

    /**
     * 文件删除接口
     * @param filePath
     * @return
     */
    /*@PostMapping("/del_file")
    @ResponseBody
    public String delFile(String filePath) {
        Map result = new HashMap();
        boolean delete = false;
        try {
            filePath = EncryptUtils.decodeDes3(filePath);
            File file = new File(filePath);
            delete = false;
            if (file != null && file.exists() && file.isFile()) { //清除文件
                delete = file.delete();
            }else {
                result.put("msg", "文件不存在");
            }
        } catch (Exception e) {
            logger.debug("删除文件失败 --> {}",e);
            result.put("msg", "文件不存在");
        }
        result.put("isDeleted", delete);
        return JSON.toJSONString(result);
    }*/

    /**
     * 在check文件后, 更新缓存
     *
     * @param fileMd5
     * @param fileName
     * @param progress
     * @param now
     */
    private void updateCacheAfterCheck(String flag, String fileMd5, String fileName, String progress, DateTime now) {
        String progressKey = getProgressKey(flag, fileMd5, fileName);
        String lastUploadTimeKey = getLastUploadTimeKey(flag, fileMd5, fileName);
        String fileNameKey = getFileNameKey(flag, fileMd5, fileName);
        fileCache.put(progressKey, progress); // 更新进度
        //将最后上传时间以字符串形式存入缓存
        fileCache.put(lastUploadTimeKey, now.toString("yyyy-MM-dd HH:mm:ss"));
        if (StringUtils.isBlank((fileCache.get(fileNameKey)))) {
            StringBuilder tempFile = new StringBuilder(now.toString("yyyyMM"))
                    .append(File.separatorChar)
                    .append(System.currentTimeMillis())
                    .append(fileMd5);
            //文件上传时生成的存储分块的临时文件夹的名称由: 上级文件夹 + 文件 MD5 + 时间戳组成
            fileCache.put(fileNameKey, tempFile.toString());
        }
    }

    /**
     * @param fileMd5
     * @param fileName
     */
    private void removeCacheAfterDel(String flag, String fileMd5, String fileName) {
        /****************清除缓存中的相关信息**********************/
        String lastUploadTimeKey = getLastUploadTimeKey(flag, fileMd5, fileName);
        String progressKey = getProgressKey(flag, fileMd5, fileName);
        String fileNameKey = getFileNameKey(flag, fileMd5, fileName);

        fileCache.remove(progressKey);
        fileCache.remove(lastUploadTimeKey);
        fileCache.remove(fileNameKey);
    }

    private String getProgressKey(String flag, String fileMd5, String fileName) {

        return new StringBuilder(Objects.toString(flag,"")).append("progress_").append(fileMd5).append(fileName).toString();
    }
    private String getLastUploadTimeKey(String flag, String fileMd5, String fileName) {
        return new StringBuilder(Objects.toString(flag,"")).append("lastUploadTime_").append(fileMd5).append(fileName).toString();
    }

    private String getFileNameKey(String flag, String fileMd5, String fileName) {
        return new StringBuilder(Objects.toString(flag,"")).append("fileName_").append(fileMd5).append(fileName).toString();
    }

    /**
     * 生成新文件路径 + 文件名
     *
     * @param fileMd5
     * @param flag
     * @param fileName
     * @return
     */
    private String generateNewFilePathName(DateTime dateTime, String fileMd5, String flag, String fileName) {
        StringBuilder newFilePath = new StringBuilder(sysPath).append(File.separatorChar)
                .append(dateTime.toString("yyyyMM")).append(File.separatorChar);
        if (StringUtils.isNotBlank(flag)) {
            newFilePath.append(flag).append("$$$");
        }
        newFilePath.append(fileMd5).append("$$$").append(fileName);
        logger.debug("生成的新文件的全路径 + 文件名 ---> {}", newFilePath);
        return newFilePath.toString();
    }

    /**
     * 获取保存分块文件的临时目录
     *
     * @param fileMd5
     * @param fileName
     * @return
     */
    private StringBuilder getChunksTempDir(String flag, String fileMd5, String fileName) {
        String fileNameKey = getFileNameKey(flag, fileMd5, fileName);
        StringBuilder tempSavePath = new StringBuilder(sysPath)
                .append(File.separatorChar)
                .append(fileCache.get(fileNameKey));
        logger.debug("保存分块文件的临时文件夹 ---> {}", tempSavePath);
        return tempSavePath;
    }

    /**
     * 临时文件夹的分块文件比较
     */
    private class CompareChunks implements Comparator<File> {
        @Override
        public int compare(File o1, File o2) {
            if (Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName())) {
                return -1;
            }
            return 1;
        }
    }

    /**
     * 文件过滤器, 只要文件 排除目录
     */
    private class AcceptFile implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() ? false : true;
        }
    }

}



