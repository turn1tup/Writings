package com.demo.testproxy;

public class Business implements SomeInterface {

    public void work() {
        System.out.println("business object : work");

    }

    @Override
    public void sayHello() {
        System.out.println("business object : sayHello");
    }

    @Override
    public void sayGoodbye() {
        System.out.println("business object : sayGoodbye");
    }
}
