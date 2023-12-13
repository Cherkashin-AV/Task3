package ru.vtb.javaCourse.Task3;

import java.lang.reflect.Proxy;
import java.util.Arrays;

public class Utils {
    public static <T> T cache(T obj){
        ClassLoader classLoader = obj.getClass().getClassLoader();
        Class[] interfaces = obj.getClass().getInterfaces();
        T a = (T) Proxy.newProxyInstance(classLoader, interfaces, new InvocationHandlerImpl(obj));
        return a;
    }
}
