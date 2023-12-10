package com.test.thymeleaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;

@Controller
public class TestController {


    Logger log = LoggerFactory.getLogger(TestController.class);
    // 正常页面
    @RequestMapping("/index")
    public String index(Model model) {
        model.addAttribute("message", "happy birthday");
        return "welcome";
    }
    @RequestMapping("/forward")
    public String forward() {
        return "forward:index";
    }
    // 漏洞
    @RequestMapping("/setViewName")
    public ModelAndView test1(@RequestParam String test) {
        ModelAndView mv=new ModelAndView();
        mv.setViewName(test);
        return mv;
    }

    // 请求为json数据格式，thymeleaf的相关修复将失效
    @RequestMapping("/test2")
    public ModelAndView test2(@RequestBody String test) {
        ModelAndView mv=new ModelAndView();
        mv.setViewName(test);
        return mv; //fragment is tainted
    }

    //
    // section=__${''.getClass().forName('java.lang.Runtime').getRuntime().exec('calc')}__
    @RequestMapping("/fragment1")
    public String fragment1(@RequestParam String section) {
        return "welcome :: " + section;
    }

    // section=__${''.getClass().forName('java.lang.Runtime').getRuntime().exec('calc')}__
    @RequestMapping("/fragment2")
    public String fragment2(@RequestParam String section,HttpServletResponse response) {
        return "welcome :: " + section;
    }

    // safe
    @RequestMapping("/fragment3")
    @ResponseBody
    public String fragment3(@RequestParam String section) {
        return "welcome :: " + section;
    }
    /**
     POC
     /void1/%3a%3a%5f%5f%24%7b%54%20%20%28%6a%61%76%61%2e%6c%61%6e%67%2e%52%75%6e%74%69%6d%65%29%2e%67%65%74%52%75%6e%74%69%6d%65%28%29%2e%65%78%65%63%28%22%63%61%6c%63%22%29%7d%5f%5f%2e
     */
    @RequestMapping("/void1/{document}")
    public String void1() {
        return null;
    }
    // safe
    @RequestMapping("/void2/{document}")
    @ResponseBody
    public void void2(@PathVariable String document) {
        log.info("Retrieving " + document);
    }
    // safe
    @RequestMapping("/void3/{document}")
    public void void3(@PathVariable String document,HttpServletResponse rsp) {
        log.info("Retrieving " + document);
    }

    //
    @RequestMapping("/path")
    public String path(@RequestParam String lang) {
        return "user/" + lang + "/welcome"; //template path is tainted
    }
    


    @RequestMapping("/safe/fragment")
    @ResponseBody
    public String safeFragment(@RequestParam String section) {
        return "welcome :: " + section; //FP, as @ResponseBody annotation tells Spring to process the return values as body, instead of view name
    }

    @RequestMapping("/safe/redirect")
    public String redirect(@RequestParam String url) {
        return "redirect:" + url; //FP as redirects are not resolved as expressions
    }

    @RequestMapping("/safe/doc/{document}")
    public void getDocument(@PathVariable String document, HttpServletResponse response) {
        log.info("Retrieving " + document); //FP
    }
}
