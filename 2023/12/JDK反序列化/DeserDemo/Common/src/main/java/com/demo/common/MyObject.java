package com.demo.common;

import java.io.*;

public class MyObject implements Serializable {

    public MyObject() {

    }

    // 此方法返回代理对象
    private Object writeReplace() throws ObjectStreamException {
        return new TargetDetectClass();
    }
//
//    // 此代理对象包含自定义的序列化和反序列化逻辑
//    private static class MyObjectProxy implements Serializable {
//        private int data;
//        //private static final long serialVersionUID = 111L;
//       private static final long serialVersionUID = 222L;
//        public MyObjectProxy(int data) {
//            this.data = data;
//        }
//
//        private void writeObject(ObjectOutputStream out) throws IOException {
//            // 自定义序列化逻辑
//            out.writeInt(data);
//        }
//
//        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
//            // 自定义反序列化逻辑
//            data = in.readInt();
//        }
//
//        // 此方法返回原始对象
//        private Object readResolve() throws ObjectStreamException {
//            return new MyObject(data);
//        }
//    }
}
