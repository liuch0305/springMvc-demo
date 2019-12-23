package com.lch.demo.service;

import com.lch.mvcframework.annotation.Service;

/**
 * @author: liuchenhui
 * @create: 2019-12-20 18:52
 **/
@Service
public class IndexService {

    public Integer add(Integer a, Integer b) {
        return a + b;
    }
}
