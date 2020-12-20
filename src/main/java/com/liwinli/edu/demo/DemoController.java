package com.liwinli.edu.demo;

import com.liwinli.edu.demo.services.IDemoService;
import com.liwinli.edu.springmvc.annotations.LTAutowired;
import com.liwinli.edu.springmvc.annotations.LTController;
import com.liwinli.edu.springmvc.annotations.LTRequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@LTController
@LTRequestMapping("/demo")
public class DemoController {
    @LTAutowired
    private IDemoService iDemoService;

    @LTRequestMapping("/")
    public String get(HttpServletRequest request, HttpServletResponse response, String name){
        return iDemoService.get(name);
    }
}
