package com.demo.common;

import java.io.IOException;
import java.io.Serializable;

public class TargetDetectClass implements Serializable {
    //private static final long serialVersionUID = 11L;
   private static final long serialVersionUID = 2L;

    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
    }
}
