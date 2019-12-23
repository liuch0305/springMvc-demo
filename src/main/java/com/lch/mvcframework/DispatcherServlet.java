package com.lch.mvcframework;

import com.lch.mvcframework.annotation.Autowired;
import com.lch.mvcframework.annotation.Controller;
import com.lch.mvcframework.annotation.RequestBody;
import com.lch.mvcframework.annotation.RequestMapping;
import com.lch.mvcframework.annotation.RequestParam;
import com.lch.mvcframework.annotation.Service;
import com.lch.tomcat.HttpServelt;
import com.lch.tomcat.exception.ServletException;
import com.lch.tomcat.http.HttpServletRequest;
import com.lch.tomcat.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author: liuchenhui
 * @create: 2019-12-11 15:36
 **/
@Slf4j
public class DispatcherServlet extends HttpServelt {

    public static final String SCAN_PACKAGE = "scanPackage";

    //保存application.properties配置文件中的内容
    private Properties contextConfig = new Properties();
    // 存储所有实例化后的bean
    private Map<String, Object> iocMap = new ConcurrentHashMap<>();
    // 扫描到的所有待Controller或者Service的类全称
    private List<String> clazzList = new ArrayList<>();
    // 所有请求
    private List<Handler> handlerMappings = new ArrayList<>();


    @Override
    protected void init() {
        log.info("spring context begin init.....");
        // 获取配置文件
        doconfig(this.getServletConfig().getInitParameter());
        // 扫描配置中的包路径
        doscanner(contextConfig.getProperty(SCAN_PACKAGE));
        // 初始化所有bean
        doInitialize();
        // autowire装配
        doAutowire();
        // 初始化hadlerMapping
        initHandlerMapping();

        log.info("spring context init success!");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            log.error("请求异常", e);
            resp.sendError(500, "Exection,Detail : " + Arrays.toString(e.getStackTrace()));
        }
    }

    // 处理请求
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Handler handler = getHandler(req);
        if (handler == null) {
            resp.sendError(404, "404 Not Found!!!");
            return;
        }

        String method = req.getMethod();
        String httpMethod = handler.getHttpMethod();
        if (method != null && !method.equals(httpMethod)) {
            resp.sendError(500, "only support " + httpMethod);
            return;
        }
        Class<?>[] paramTypes = handler.getParamTypes();
        Map<String, Integer> paramIndexMapping = handler.getParamIndexMapping();
        Object[] paramObjs = new Object[paramTypes.length];
        for (Map.Entry<String, Integer> entry : paramIndexMapping.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            Class<?> paramType = paramTypes[value];
            if (paramType == HttpServletRequest.class) {
                paramObjs[value] = req;
                continue;
            }
            if (paramType == HttpServletResponse.class) {
                paramObjs[value] = resp;
                continue;
            }
            paramObjs[value] = convert(req.getParameter(key), paramType);
        }
        try {
            resp.write(handler.getMethod().invoke(handler.getObject(), paramObjs));
        } catch (Exception e) {
            log.error("服务异常", e);
            resp.sendError(500, "服务异常" + e.getStackTrace());
        }
    }

    private void doconfig(String initparam) {
        String path = this.getClass().getResource("/").getPath();
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(path + "/" + initparam);
            contextConfig.load(new FileInputStream(path + "/" + initparam));
        } catch (IOException e) {
            log.error("read application.properties fail", e);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doscanner(String packageName) {
        String classpath = packageName.replaceAll("\\.", "/");
        String path = this.getClass().getResource("/").getPath() + classpath;
        File listFile = new File(path);
        for (File file : listFile.listFiles()) {
            if (file.isDirectory()) {
                doscanner(packageName + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String clazzName = (packageName + "." + file.getName()).replaceAll(".class", "");
                clazzList.add(clazzName);
            }
        }
    }

    private void doInitialize() {
        if (clazzList.isEmpty()) {
            return;
        }
        for (String clazzName : clazzList) {
            try {
                Class<?> clazz = Class.forName(clazzName);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    iocMap.put(toLowChar(clazz.getSimpleName()), clazz.newInstance());
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Object object = clazz.newInstance();
                    iocMap.put(toLowChar(clazz.getSimpleName()), object);
                    Service service = clazz.getAnnotation(Service.class);
                    String value = service.value();
                    if (!"".equals(value.trim())) {
                        if (iocMap.containsKey(value.trim())) {
                            throw new Exception("The “" + clazz.getName() + "” is exists!!");
                        }
                        iocMap.put(value.trim(), object);
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Initialize bean named " + clazzName + " fail !");
            }
        }
    }

    private void doAutowire() {
        if (iocMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Object beanInstance = entry.getValue();
            Field[] declaredFields = beanInstance.getClass().getDeclaredFields();
            for (Field declaredField : declaredFields) {
                if (declaredField.isAnnotationPresent(Autowired.class)) {
                    Autowired autowired = declaredField.getAnnotation(Autowired.class);
                    String beanName = autowired.value();
                    if ("".equals(beanName.trim())) {
                        beanName = declaredField.getType().getName();
                        String[] split = beanName.split("\\.");
                        beanName = toLowChar(split[split.length - 1]);
                    }
                    declaredField.setAccessible(true);
                    try {
                        declaredField.set(beanInstance, iocMap.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void initHandlerMapping() {
        if (iocMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : iocMap.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(Controller.class)) {
                continue;
            }
            RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
            String basePath = requestMapping.value();
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(RequestMapping.class)) {
                    continue;
                }
                RequestMapping methodAnnotation = method.getAnnotation(RequestMapping.class);
                String path = "/" + basePath + "/" + methodAnnotation.value();
                path = path.replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(path);
                handlerMappings.add(new Handler(methodAnnotation.method().name(), pattern, method, entry.getValue()));
                log.info("Mapped :" + pattern + "," + method);
            }
        }
    }

    private String toLowChar(String clazz) {
        char[] chars = clazz.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) {
        String requestURL = req.getRequestURL();
        for (Handler handler : handlerMappings) {
            Matcher matcher = handler.getPattern().matcher(requestURL);
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }
        return null;
    }

    // 将string装换为对应参数
    private Object convert(String parameter, Class<?> paramType) throws Exception {
        if (paramType == String.class) {
            return parameter;
        } else if (paramType == Integer.class) {
            try {
                return Integer.parseInt(parameter);
            } catch (Exception e) {
                throw new Exception("String can not canvert to Integer");
            }
        } else if (paramType == Double.class) {
            try {
                Integer.parseInt(parameter);
            } catch (Exception e) {
                throw new Exception("String can not canvert to Integer");
            }
        }
        throw new Exception(paramType.getName() + "is not support");
    }

    @Data
    class Handler {

        private String httpMethod;
        private Pattern pattern;
        private Method method;
        private Object object;
        private Class<?>[] paramTypes;
        //形参列表
        //参数的名字作为key,参数的顺序，位置作为值
        private Map<String, Integer> paramIndexMapping;

        public Handler(String httpMethod, Pattern pattern, Method method, Object object) {
            this.httpMethod = httpMethod;
            this.pattern = pattern;
            this.method = method;
            this.object = object;
            this.paramTypes = method.getParameterTypes();
            this.paramIndexMapping = putParamIndexMapping(method);
        }

        private Map<String, Integer> putParamIndexMapping(Method method) {
            Map<String, Integer> map = new HashMap<>();

            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                String name = parameter.getName();
                map.put(name, i);
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                if (requestParam != null) {
                    String value = requestParam.value();
                    map.put(value, i);
                }
                RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
                if (requestBody != null) {
                    // TODO: 2019-12-20 liuchenhui 从body中取参数
                }
            }
            return map;
        }
    }
}
