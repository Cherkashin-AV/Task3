package ru.vtb.javaCourse.Task3;

import java.lang.reflect.Method;

public interface StateMethodsCacheable {
    Object getResult(Method method, Object[] args);
}
