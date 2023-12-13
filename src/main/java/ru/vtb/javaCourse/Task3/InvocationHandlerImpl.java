package ru.vtb.javaCourse.Task3;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class InvocationHandlerImpl <T> implements InvocationHandler {
    private final T a;
    private final StateMethodsCacheable cache;
    public InvocationHandlerImpl(T a)  {
        this.a = a;
        cache = new CacheStateAndTimeout<>(a);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return cache.getResult(method, args);
    }
}
