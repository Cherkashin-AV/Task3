package ru.vtb.javaCourse.Task3;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import ru.vtb.javaCourse.Task3.Annotation.Cache;
import ru.vtb.javaCourse.Task3.Annotation.Mutator;
import ru.vtb.javaCourse.Task3.Annotation.ReturnFromCacheFlag;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;


public class CacheStateAndTimeoutTest {

    interface Counterable{
        void setCounter(int counter);
        int getCounter();
    }

    //Создаем дочерний класс от тестируемого (Fraction), чтобы через поле с аннотацией @ReturnFromCashFlag
    //отслеживать каким образом получен результат (через вызов метода или через кэш)
    private static class Fraction4Test extends Fraction implements Fractionable, Counterable {
        @ReturnFromCacheFlag
        protected Boolean returnFromCash;

        public Boolean getReturnFromCash(){
            return returnFromCash;
        }

        public Fraction4Test(int num, int denum) {
            super(num, denum);
        }

        private int counter;

        @Cache
        public int getCounter() {
            return ++counter;
        }

        @Mutator
        public void setCounter(int counter) {
            this.counter = counter;
        }
    }

@Test
@DisplayName("Функциональное тестирование кэша с таймаутом")
    public void testCashe() throws InterruptedException {
        Fraction4Test fr = new Fraction4Test(2, 3);
        Fractionable num = Utils.cache(fr);
        num.doubleValue();// sout сработал
        Assertions.assertFalse(fr.getReturnFromCash());
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());

        num.setNum(5);
        num.doubleValue();// sout сработал
        Assertions.assertFalse(fr.getReturnFromCash());
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());

        num.setNum(2);
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());

        num.setNum(5);
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());

        Thread.sleep(1500);
        num.doubleValue();// sout сработал
        Assertions.assertFalse(fr.getReturnFromCash());
        num.doubleValue();// sout молчит
        Assertions.assertTrue(fr.getReturnFromCash());
    }
    @Test
    @DisplayName("Тестирование кэша без таймаута")
    public void testCasheWithoutTimeout() throws InterruptedException {
        Fraction4Test fr = new Fraction4Test(2, 3);
        Counterable num = Utils.cache(fr);
        num.setCounter(0);
        int result = num.getCounter();
        Assertions.assertEquals(1, result);
        Assertions.assertFalse(fr.getReturnFromCash());
        Thread.sleep(3000);
        result = num.getCounter();
        Assertions.assertEquals(1, result);
        Assertions.assertTrue(fr.getReturnFromCash());
    }

    private Throwable exeption;

    @Test
    @RepeatedTest(10)
    @DisplayName("Тестирование параллельной работы кэша и его очистка")
    public void testMutatorWithTimer() throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InterruptedException {
        Fraction4Test fr = new Fraction4Test(2, 3);
        Fractionable num = Utils.cache(fr);

        //Отключаем таймер, будем запускать сами в нужное нам время
        InvocationHandlerImpl invocationHandler = (InvocationHandlerImpl) Proxy.getInvocationHandler(num);
        Field fieldCache = invocationHandler.getClass().getDeclaredField("cache");
        fieldCache.setAccessible(true);
        CacheStateAndTimeout c = (CacheStateAndTimeout) fieldCache.get(invocationHandler);
        c.timer.cancel();
        //Готовим свой поток
        Method actualizeCache = c.getClass().getDeclaredMethod("actualizeCache", null);
        Runnable runnable = new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                actualizeCache.setAccessible(true);
                actualizeCache.invoke(c);
            }
        };
        Thread thread = new Thread(runnable);
        exeption = null;
        thread.setUncaughtExceptionHandler((t, e) -> this.exeption = e);

        //Запускаем изменение состояния и добавление записей в кэш, замеряем скорость
        int i=0;
        long[] sumTime = new long[3];
        long startTime;
        while (true){
            //Смена состояния
            startTime = System.currentTimeMillis();
            num.setDenum(i);
            sumTime[0] = System.currentTimeMillis()-startTime + sumTime[0];
            //Вызов метода
            startTime = System.currentTimeMillis();
            double d = num.doubleValue();
            sumTime[1] = System.currentTimeMillis()-startTime + sumTime[1];
            //Получение результата из кэша
            startTime = System.currentTimeMillis();
            d = num.doubleValue();
            sumTime[2] = System.currentTimeMillis()-startTime + sumTime[2];

            //После добавления 50 значений в кэш запускаем очистку
            if (i==50){
                thread.start();
            }
            if (thread.getState() != Thread.State.NEW && !thread.isAlive()){
                break;
            }
            i++;
        }
        Assertions.assertNull(exeption, exeption == null?null:exeption.getMessage());
        Map cacheHolder =  c.getCachesHolder();
        Assertions.assertTrue(cacheHolder.size() >0, "Нет записей в cashHoldere, они должны остаться до истечения timeout");
        //Запускаем повторную очистку
        if (cacheHolder.size() !=0){
            Thread.sleep(1000);
            Thread thread1 = new Thread(runnable);
            try {
                thread1.start();
                thread1.join();
            } catch (InterruptedException e) {
                Assertions.assertTrue(false, e.getMessage());
            }
        }
        cacheHolder =  c.getCachesHolder();
        Assertions.assertTrue(cacheHolder.size() == 0, "Записей в cashHoldere: "+cacheHolder.size());

        System.out.println("Среднее время выполнения:");
        System.out.println("    - смена состояния: " + sumTime[0]/i);
        System.out.println("    - вызов функции  : " + sumTime[1]/i);
        System.out.println("    - поиск в кэше   : " + sumTime[2]/i);
    }
}