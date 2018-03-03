package com.ucpaas.sms.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 系统配置工具类
 * 
 * @author xiejiaan
 */
@Component
public class ConfigUtils {
	private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

	public static String system_version;
	/**
	 * 运行环境：development（开发）、devtest（开发测试）、test（测试）、production（线上）
	 */
	public static String spring_profiles_active;
	/**
	 * 是否自动登录
	 */
	public static boolean is_auto_login;

	/**
	 * 配置文件路径
	 */
	public static String config_file_path;
	/**
	 * 客户上传图片保存的临时文件夹
	 */
	public static String upload_pic_temp;
	
	/**
	 * 代理商公用路径(代理商保存上传的证件图片)
	 */
	public static String agent_oauth_pic;
	
	/**
	 * 客户公用路径(客户保存上传的证件图片)
	 */
	public static String client_oauth_pic;
	/**
	 * 大文件保存路径
	 */
	public static String file_save_path;
	/**
	 * 允许访问本服务器的站点
	 */
	public static String access_web_context;

	
	/**
	 * 初始化
	 */
	@PostConstruct
	public void init() {
		String path = ConfigUtils.class.getClassLoader().getResource("").getPath() ;
		config_file_path = path + "system.properties";

		initValue();
		logger.info("\n\n-------------------------【smsp-img_v{}，{}环境服务器启动】\n加载配置文件：\n{}\n",system_version, spring_profiles_active,
				config_file_path);
	}

	/**
	 * 初始化配置项的值
	 */
	private void initValue() {
		Field[] fields = ConfigUtils.class.getFields();
		Object fieldValue = null;
		String name = null, value = null, tmp = null;
		Class<?> type = null;
		Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}");
		Matcher matcher = null;
		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(config_file_path));

			for (Field field : fields) {
				name = field.getName();
				value = properties.getProperty(name);
				if (null != value && !"".equals(value.trim())) {
					matcher = pattern.matcher(value);
					while (matcher.find()) {
						tmp = properties.getProperty(matcher.group(1));
						if (null == value || "".equals(tmp.trim())) {
							logger.error("配置{}存在其它配置{}，请检查您的配置文件", name, matcher.group(1));
						}
						value = value.replace(matcher.group(0), tmp);
					}

					type = field.getType();
					if (String.class.equals(type)) {
						fieldValue = value;
					} else if (Integer.class.equals(type)) {
						fieldValue = Integer.valueOf(value);
					} else if (Boolean.class.equals(type)) {
						fieldValue = Boolean.valueOf(value);
					} else {
						fieldValue = value;
					}
					field.set(this, fieldValue);
				}
				logger.info("加载配置：{}={}", name, field.get(this));
			}
		} catch (Throwable e) {
			logger.error("初始化配置项的值失败：" + name + "=" + value, e);
		}
	}

}
