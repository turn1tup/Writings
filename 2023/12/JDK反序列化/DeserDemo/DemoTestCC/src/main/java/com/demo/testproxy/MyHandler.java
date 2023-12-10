package com.demo.testproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MyHandler implements InvocationHandler {
    // 原始对象，被代理的对象
    Object target;

    public MyHandler(Object target) {
        this.target = target;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("sayHello".equals(method.getName())) {
            before();
            Object result = method.invoke(target, args);  // 调用 target 的 method 方法
            after();
            return result;
        } else {
            return method.invoke(target, args);
        }
    }

    private void before() {
        System.out.println("MyHandler : before");
    }

    private void after() {
        System.out.println("MyHandler : after");
    }




}
