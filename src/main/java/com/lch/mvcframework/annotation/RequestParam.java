package com.lch.mvcframework.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 参数匹配注解
 *
 * @author: liuchenhui
 * @create: 2019-12-11 18:02
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface RequestParam {
    /**
     * 参数对应
     */
    String value();
}
