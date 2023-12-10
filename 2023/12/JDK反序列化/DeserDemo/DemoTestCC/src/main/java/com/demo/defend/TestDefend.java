package com.demo.defend;

import com.demo.common.Utils;
import com.demo.testcc.TestCC1;

public class TestDefend {

    public static void main(String[] args)throws Exception {
        System.setProperty("jdk.serialFilter", "com.test.Foo;!sun.reflect.annotation.AnnotationInvocationHandler;");

        String file = "testdefend.ser";
        Utils.Serialize(TestCC1.getCC1("calc"), file);
        Utils.Deserialize(file);
    }
}
