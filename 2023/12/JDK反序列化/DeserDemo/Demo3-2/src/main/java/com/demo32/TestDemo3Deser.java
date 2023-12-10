package com.demo32;

import com.demo.common.Utils;

public class TestDemo3Deser {
    public static void main(String[] args) throws Exception{
        String file = "demo3.ser";
        Object o = Utils.Deserialize(file);
        System.out.println(o);
    }
}
