package com.lch.mvcframework.annotation;

import com.lch.mvcframework.enums.MethodEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: liuchenhui
 * @create: 2019-12-11 17:58
 **/
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {

    /**
     * url
     */
    String value() default "";

    /**
     * 请求方法
     */
    MethodEnum method() default MethodEnum.GET;
}
