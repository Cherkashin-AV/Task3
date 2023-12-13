package ru.vtb.javaCourse.Task3;

import ru.vtb.javaCourse.Task3.Annotation.Cache;
import ru.vtb.javaCourse.Task3.Annotation.Mutator;
import ru.vtb.javaCourse.Task3.Annotation.ReturnFromCacheFlag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/*Стратегия кэширования:
Для получения значения, идентифицирующего текущее состояние объекта, используется перебор всех полей
через Reflection. Операция достаточно длительная, но такой подход позволяет сделать кэширование для
любого класса без дополнительных требований к нему. Для минимизации временных затрат состояние объекта
рассчитывается только при смене состояния, что исключает задержки при поиске в кэше, но незначительно
увеличивает время создания кэша для нового состояния.

В cacheHolder хранятся кэши для разных состояний, кеш для текущего состояния доступен через поле cache.
При изменении состояния ищем уже существующий кэш в cacheHolder (синхронизация по cacheHolder).
Если нашли, то записываем его в поле cache. Если не нашли, то создаем новый и записываем его в cacheHolder.
При работе с кэшем для текущего состояния cache синхронизируемся по полю cache.

Удаление записей с истекшим сроком действия производится по всему cacheHolder (синхронизация по ссылке на cache).
Удаление записей в cacheHolder у которых нет записей в кэше производится с синхронизацией по cacheHolder.

Такой подход позволяет минимизировать возможные замедления при работе с текущим кэшем и при добавлении в
cacheHolder кэша для нового состояния.
 */

public class CacheStateAndTimeout <T> implements StateMethodsCacheable {
    T obj;
    //Ссылка на поле, которое содержит признак возврата из кэша
    private final Field fromCacheField;
    //Кэш для текущего состояния
    private volatile Map<Object, TimeoutResult> cache;
    //Хранилище кэшей для состояний
    private volatile Map<String, Object> cachesHolder = new HashMap<>();

    //Таймер
    Timer timer = new Timer("cleaner", true);

    //Метод для установки поля с аннотацией @ReturnFromCacheFlag
    private void setFromCacheField(Boolean value) {
        if (fromCacheField != null) {
            try {
                fromCacheField.set(obj, value);
            } catch (IllegalAccessException e) {
                System.out.println("Не удалось установить признак возврата кэшированного значения");
            }
        }
    }

    //Конструктор
    public CacheStateAndTimeout(T cachedObject) {
        //Сохраняем исходный объект
        this.obj = cachedObject;
        //Проверяем наличие поле с признаком возврата результата из кэша
        fromCacheField = Arrays.stream(obj.getClass().getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ReturnFromCacheFlag.class)
                        && f.getType() == Boolean.class)
                .findFirst()
                .orElse(null);
        //Инициализируем кэш
        cache = new HashMap<>();
        //и помещяем его в хранилище
        cachesHolder.put(getState(), cache);
        //Запускаем таймер очистки кэша
        timer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        actualizeCache();
                    }
                }, 500, 500);
    }

    //Вложенный класс для хранения результатов выполнения метода и времени жизни кеша
    private static class TimeoutResult {
        Object result;
        Long timeout;
        int duration;

        public TimeoutResult(Object result, int duration) {
            this.result = result;
            this.duration = duration;
            resetTimeout();
        }

        public boolean isALife() {
            return (timeout == null || timeout >= System.currentTimeMillis());
        }

        public void resetTimeout() {
            if (duration > 0) {
                this.timeout = System.currentTimeMillis() + duration;
            }
        }
    }

    //Вычисление состояния объекта
    private String getState() {
        String result = "";
        Class currentClass = obj.getClass();
        while (currentClass != Object.class) {
            result += "[" + Arrays.stream(currentClass.getDeclaredFields())
                    .sorted(Comparator.comparing(Field::getName))
                    .map(p -> {
                        try {
                            p.setAccessible(true);
                            return p.get(obj) == null ? "" : p.get(obj).toString();
                        } catch (IllegalAccessException e) {
                            System.out.println(e.getMessage());
                            return "";
                        }
                    })
                    .collect(Collectors.joining(":")) + "]";
            currentClass = currentClass.getSuperclass();
        }
        return result;
    }

    //Выполнение метода (обработка исключений)
    private Object methodInvoke(Object obj, Method method, Object[] args) {
        try {
            return method.invoke(obj, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    //Функция, обрабатывающая вызов методов объекта, реализация кэширование
    @Override
    public Object getResult(Method method, Object[] args) {
        Method m;
        setFromCacheField(null);
        try {
            m = obj.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return methodInvoke(obj, method, args);
        }
        if (m.getAnnotation(Mutator.class) != null) {
            methodInvoke(obj, method, args);
            changeState();
        }
        if (m.getAnnotation(Cache.class) != null) {
            synchronized (cache) {
                if (cache.containsKey(method) && cache.get(method).isALife()) {
                    setFromCacheField(true);
                    TimeoutResult res = cache.get(method);
                    res.resetTimeout();
                    return res.result;
                } else {
                    Object res = methodInvoke(obj, method, args);
                    int tm = m.getAnnotation(Cache.class).value();
                    cache.put(method, new TimeoutResult(res, tm));
                    setFromCacheField(false);
                    return res;
                }
            }
        }
        return methodInvoke(obj, method, args);
    }

    //Смена кэша при изменении состояния объекта
    private void changeState() {
        String currentState = getState();
        synchronized (cachesHolder) {
            //Сохраняем текущий кэш в хранилище
            if (cachesHolder.containsKey(currentState)) {
                cache = (Map) cachesHolder.get(currentState);
            } else {
                cache = new HashMap<>();
                cachesHolder.put(currentState, cache);
            }
        }
    }

    //Очистка кэша
    private void actualizeCache() {
        HashSet<Object> vals;
        synchronized (cachesHolder) {
            vals = new HashSet<>(cachesHolder.values());
        }
        for (Object val : vals) {
            synchronized (val) {
                HashMap cache = (HashMap) val;
                cache.values().removeIf(p -> !((TimeoutResult) p).isALife());
            }
        }

        synchronized (cachesHolder) {
            cachesHolder.values().removeIf(p -> ((Map) p).isEmpty());
        }
    }
    public Map<String, Object> getCachesHolder() {
        return new HashMap<>(cachesHolder);
    }
}
