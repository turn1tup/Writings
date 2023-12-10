package com.test.freemarker;

import freemarker.core.TemplateClassResolver;
import freemarker.template.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.file.PathUtils;
@Controller
public class TestController {


    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("message", "happy birthday");
        return "welcome";
    }
    @RequestMapping("/user")
    public String user(User user,Model model) {
        model.addAttribute("user", user);
        return "user";
    }

    @ResponseBody
    @RequestMapping("/test1")
    public String test1(String templateContent) throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);

        // api, has_api  These built-ins exists since FreeMarker 2.3.22
        cfg.setAPIBuiltinEnabled(true);
        cfg.setDefaultEncoding("UTF-8");
        //cfg.setObjectWrapper(new DefaultObjectWrapper(Configuration.VERSION_2_3_32));
        Template template = new Template(null, templateContent, cfg);

        Map<String, Object> templateData = new HashMap<>();

        StringWriter out = new StringWriter();
        template.process(templateData, out);
        out.flush();
        return out.getBuffer().toString();
    }

    // age=100&name=whoami&templateContent= <#assign uri=user?api.class.getResource("/").toURI()> ${uri}
    @ResponseBody
    @RequestMapping("/test2")
    public String test2(String name,int age,String templateContent) throws IOException, TemplateException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_23);

        // api, has_api  These built-ins exists since FreeMarker 2.3.22
        cfg.setAPIBuiltinEnabled(true);
        cfg.setDefaultEncoding("UTF-8");
        // 禁止危险的 build-in 语法
        //cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);

        Template template = new Template(null, templateContent, cfg);

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("user", new User(age,name));

        StringWriter out = new StringWriter();
        template.process(templateData, out);
        out.flush();
        return out.getBuffer().toString();
    }
}
