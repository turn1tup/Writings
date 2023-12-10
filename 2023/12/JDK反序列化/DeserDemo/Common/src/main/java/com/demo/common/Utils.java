package com.demo.common;

import javassist.ClassPool;
import javassist.CtClass;

import java.io.*;

public class Utils {
    public static void Serialize(Object obj, String file) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
        oos.writeObject(obj);
    }

    public static Object Deserialize(String file) throws IOException, ClassNotFoundException{
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
        return ois.readObject();
    }
    public static Object Deserialize(InputStream inputStream) throws IOException, ClassNotFoundException{
        ObjectInputStream ois = new ObjectInputStream(inputStream);
        return ois.readObject();
    }
    public static Class<?> MakeClass(String clazzName) throws Exception{
        ClassPool classPool = ClassPool.getDefault();
        CtClass ctClass = classPool.makeClass(clazzName);
        Class<?> clazz = ctClass.toClass();
        ctClass.defrost();
        return clazz;
    }
}
