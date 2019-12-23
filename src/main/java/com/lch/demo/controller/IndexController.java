package com.lch.demo.controller;

import com.lch.demo.service.IndexService;
import com.lch.mvcframework.annotation.Autowired;
import com.lch.mvcframework.annotation.Controller;
import com.lch.mvcframework.annotation.RequestMapping;

/**
 * @author: liuchenhui
 * @create: 2019-12-19 15:16
 **/
@Controller
@RequestMapping("/demo")
public class IndexController {

    @Autowired
    private IndexService indexService;

    @RequestMapping("/add")
    public Integer add(Integer a, Integer b) {
        return indexService.add(a, b);
    }
}
