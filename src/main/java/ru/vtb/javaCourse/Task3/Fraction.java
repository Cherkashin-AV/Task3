package ru.vtb.javaCourse.Task3;

import lombok.SneakyThrows;
import ru.vtb.javaCourse.Task3.Annotation.Cache;
import ru.vtb.javaCourse.Task3.Annotation.Mutator;

public class Fraction implements Fractionable {
    private int num;
    private int denum;

    public Fraction() {
    }
    public Fraction(int num, int denum) {
        this.num = num;
        this.denum = denum;
    }
    @Mutator
    public void setNum(int num) {
        this.num = num;
    }

    @Mutator
    public void setDenum(int denum) {
        this.denum = denum;
    }

    @SneakyThrows
    @Override
    @Cache(1000)
    public double doubleValue() {
        Thread.sleep(4);
        return (double) num / denum;
    }
}
