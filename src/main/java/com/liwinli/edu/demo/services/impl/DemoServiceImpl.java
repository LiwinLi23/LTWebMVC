package com.liwinli.edu.demo.services.impl;

import com.liwinli.edu.demo.services.IDemoService;
import com.liwinli.edu.springmvc.annotations.LTService;

@LTService
public class DemoServiceImpl implements IDemoService {
    public String get(String name) {
        System.out.println("实现IDemoService中的方法" + name);
        return name;
    }
}
