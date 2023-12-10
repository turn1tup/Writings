package com.demo1;


import java.io.*;

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
        System.out.println("输出的文件路径："+System.getProperty("user.dir"));
        Person person = new Person("tom",3);

        serialize(person,"person.ser");

        Object person2 =  deserialize("person.ser");
        System.out.println(person2);
    }
}