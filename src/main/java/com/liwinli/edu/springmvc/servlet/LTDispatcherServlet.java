package com.liwinli.edu.springmvc.servlet;

import com.liwinli.edu.springmvc.annotations.LTAutowired;
import com.liwinli.edu.springmvc.annotations.LTController;
import com.liwinli.edu.springmvc.annotations.LTRequestMapping;
import com.liwinli.edu.springmvc.annotations.LTService;
import com.liwinli.edu.springmvc.pojo.Handler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class LTDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();
    //缓存扫描到的类的全类名
    private List<String> classNames = new ArrayList<String>();

    //IOC容器
    private Map<String,Object> iocMap = new HashMap<String,Object>();

    //handleMapping ，存储url和method直接的映射关系
//    private Map<String,Object> handleMapping = new HashMap<String,Object>();
    private List<Handler> handlerMapping = new ArrayList<>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //根据uri获取到能够处理当前请求的Handler（从handlerMapping中（list））
        Handler handler = getHandler(req);
        if (handler==null){
            resp.getWriter().write("404 not found");
            return;
        }
        //参数绑定
        //该方法所有参数得类型数组
        Class<?>[] parameterTypes = handler.getMethod().getParameterTypes();
        //根据上述数组长度创建一个新的数组（参数数组，传入反射调用的）
        Object[] paramValues = new Object[parameterTypes.length];
        //以下就是为了向参数数组中设值，而且还得保证参数得顺序和方法中形参顺序一致。
        Map<String,String[]> parameterMap = req.getParameterMap();
        //遍历request中所有的参数 ，（填充除了request、response之外的参数）
        for (Map.Entry<String,String[]> entry: parameterMap.entrySet()){
            //name=1&name=2 name[1,2]
            String value = StringUtils.join(entry.getValue(), ",");// 如同 1,2
            //如果参数和方法中的参数匹配上了，填充数据
            if (!handler.getParamIndexMapping().containsKey(entry.getKey())){continue;}
            //方法形参确实有该参数，找到它得索引位置，对应得把参数值放入paramValues
            Integer index = handler.getParamIndexMapping().get(entry.getKey());
            //把前台传递过来的参数值，填充到对应得位置去
            paramValues[index] = value;
        }
        Integer requestIndex = handler.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName());
        paramValues[requestIndex] = req;
        Integer responseIndex = handler.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName());
        paramValues[responseIndex] = resp;
        //最终调用handler得method属性
        try {
            Object invoke = handler.getMethod().invoke(handler.getController(), paramValues);
//简单操作，把方法返回的数据以字符串的形式写出
            resp.getWriter().write(invoke.toString());
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("1. 加载Web配置文件, 为扫描作准备");
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);

        System.out.println("2. 根据注解扫描对应的类");
        doScan("");

        //3. 初始化Bean对象（实现IOC容器，基于注解）
        doInstance();
        //4. 实现依赖注入
        doAutoWired();
        //5. 构造一个handleMapping处理器映射器，将配置好的url和method建立映射关系
        initHandleMapping();
        System.out.println("MVC 初始化完成");
        //6. 等待请求进入处理请求
    }

    //TODO 5，构造一个映射器,将url和method进行关联
    private void initHandleMapping() {
        if (iocMap.isEmpty()){return;}
        for (Map.Entry<String,Object> entry: iocMap.entrySet()){
            //获取ioc中当前遍历对象的class类型
            Class<?> aClass = entry.getValue().getClass();
            //排除非controller层的类
            if (!aClass.isAnnotationPresent(LTController.class)){
                continue;
            }
            String baseUrl = "";
            if (aClass.isAnnotationPresent(LTRequestMapping.class)){
                //Controller层 类上 注解@DxhRequestMapping中的value值
                baseUrl = aClass.getAnnotation(LTRequestMapping.class).value();
            }
            //获取方法
            Method[] methods = aClass.getMethods();
            for (Method method : methods) {
                //排除没有@DxhRequestMapping注解的方法
                if (!method.isAnnotationPresent(LTRequestMapping.class)){continue;}
                //Controller层 类中方法上 注解@DxhRequestMapping中的value值
                String methodUrl = method.getAnnotation(LTRequestMapping.class).value();
                String url = baseUrl+methodUrl;
                //把method所有信息以及url封装为Handler
                Handler handler = new Handler(entry.getValue(),method, Pattern.compile(url));
                //处理计算方法的参数位置信息
                Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    //不做太多的参数类型判断，只做：HttpServletRequest request, HttpServletResponse response和基本类型参数
                    if (parameter.getType()==HttpServletRequest.class||parameter.getType()==HttpServletResponse.class){
                        //如果时request和response对象，那么参数名称存 HttpServletRequest 和 HttpServletResponse
                        handler.getParamIndexMapping().put(parameter.getType().getSimpleName(),i);
                    }else{
                        handler.getParamIndexMapping().put(parameter.getName(),i);
                    }
                }
                handlerMapping.add(handler);
            }
        }
    }

    private void doAutoWired() {
        log.info("步骤4: implement DI(dependency injection)");
        if (iocMap.isEmpty()) { return; }

        for (Map.Entry<String,Object> entry: iocMap.entrySet()) {
            String classFullName = entry.getKey();
            log.info("当前正在查看类名为:{} 的对象中是否有需要DI的声明属性", classFullName);
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field declaredField : declaredFields) {
                if (!declaredField.isAnnotationPresent(LTAutowired.class)) { continue; }

                log.info("{}中属性{}声明了需要DL", classFullName, declaredField.getName());
                LTAutowired annotation = declaredField.getAnnotation(LTAutowired.class);
                String beanName = annotation.value(); //需要注入的bean的Id
                log.info("Need inject bean name: {}", StringUtils.isNotBlank(beanName) ? beanName : "");
                if ("".equals(beanName.trim())) {
                    beanName = declaredField.getType().getName();
                    log.info("Use typeName: {} as beanName", beanName);
                }

                declaredField.setAccessible(true);
                try {
                    log.info("Field:{}在对象中设置为beanName:{}对象", declaredField.getName(), beanName);
                    declaredField.set(entry.getValue(), iocMap.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //TODO 3,IOC容器
    //基于classNames缓存的类的全限定类名，以及反射技术，完成对象创建和管理
    private void doInstance() {
        if (classNames.size() == 0) { return; }

        try {
            for (int i = 0; i < classNames.size(); ++i) {
                String className = classNames.get(i);
                Class<?> aClass = Class.forName(className);
                if (aClass.isAnnotationPresent(LTController.class)) {
                    log.warn("Controller name: {}", className);
                    String simpleName = aClass.getSimpleName();
                    String lowerFirstSimpleName = lowerFirst(simpleName);
                    Object bean = aClass.newInstance();
                    iocMap.put(lowerFirstSimpleName, bean);
                } else if (aClass.isAnnotationPresent(LTService.class)) {
                    LTService annotation = aClass.getAnnotation(LTService.class);
                    String beanName = annotation.value();
                    log.info("注解的value是: {}", StringUtils.isNotBlank(beanName) ? beanName : "");
                    if (!"".equals(beanName.trim())){
                        iocMap.put(beanName,aClass.newInstance());
                    }else {
                        String lowerFirstSimpleName = lowerFirst(aClass.getSimpleName());
                        log.info("服务的名字: {}", lowerFirstSimpleName);
                        iocMap.put(lowerFirstSimpleName,aClass.newInstance());
                    }

                    Class<?>[] interfaces = aClass.getInterfaces();
                    for (Class<?> curInterface : interfaces) {
                        log.info("curInterface name: {}", curInterface.getName());
                        iocMap.put(curInterface.getName(),aClass.newInstance());
                    }
                } else { continue; }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    //TODO 2,扫描类
    //scanPackage :    package--->磁盘的文件夹（File）
    private void doScan(String scanPackage) {
        System.out.println("+ doScan: " + scanPackage);
        String clasPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        System.out.println("Class path: " + clasPath);
        //2.拼接,得到scanPackage在磁盘上的路径
        String  scanPackagePath = clasPath + scanPackage.replaceAll("\\.","/");
        File pack = new File(scanPackagePath);
        File[] files = pack.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if (StringUtils.isBlank(scanPackage)) {
                    doScan(file.getName());
                } else {
                    doScan(scanPackage + "." + file.getName());
                }
            }else if(file.getName().endsWith(".class")){
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }
    //TODO 1，加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        //根据指定路径加载成流：
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 把字符串的首字母小写
     * @param name
     * @return
     */
    private String lowerFirst(String name) {
        char[] charArray = name.toCharArray();
        charArray[0] += 32;
        return String.valueOf(charArray);
    }

    private Handler getHandler(HttpServletRequest req) {
        if (handlerMapping.isEmpty()){return null;}
        String url = req.getRequestURI();
        //遍历 handlerMapping
        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.getPattern().matcher(url);
            if (!matcher.matches()){continue;}
            return handler;
        }
        return null;
    }
}
