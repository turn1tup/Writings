package com.test;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;


@Controller
@RequestMapping("TestCC1")
public class TestCC1 {
    @RequestMapping("")
    public String test(HttpServletRequest request) throws Exception{
        Object o = Utils.Deserialize(request.getInputStream());
        return String.valueOf(o);
    }

}
