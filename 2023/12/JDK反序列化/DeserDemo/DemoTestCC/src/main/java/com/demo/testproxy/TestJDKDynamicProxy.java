package com.demo.testproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class TestJDKDynamicProxy {
    public static void main(String[] args)  {
        // 输出动态生成的代理类 ，以 .class 文件形式输出在项目 DeserDemo\com\sun\proxy 路径下
        System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        System.setProperty("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");

        // 原始对象，被代理 的对象
        Business business = new Business();

        InvocationHandler handler = new MyHandler(business);

        SomeInterface businessProxy = (SomeInterface) Proxy.newProxyInstance(TestJDKDynamicProxy.class.getClassLoader(),
                //new Class[]{SomeInterface.class},
                business.getClass().getInterfaces(),
                handler);

        businessProxy.sayHello();
        businessProxy.sayGoodbye();
    }
}
