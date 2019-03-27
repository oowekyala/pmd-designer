package net.sourceforge.pmd.util.fxdesigner.util;

import java.util.Objects;

/**
 * @author Clément Fournier
 */
public class Tuple3<A, B, C> {

    public final A _1;
    public final B _2;
    public final C _3;

    public Tuple3(A a, B b, C c) {
        this._1 = a;
        this._2 = b;
        this._3 = c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tuple3<?, ?, ?> tuple3 = (Tuple3<?, ?, ?>) o;
        return Objects.equals(_1, tuple3._1) &&
            Objects.equals(_2, tuple3._2) &&
            Objects.equals(_3, tuple3._3);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1, _2, _3);
    }

    @Override
    public String toString() {
        return "Tuple3{" +
            "a=" + _1 +
            ", b=" + _2 +
            ", c=" + _3 +
            '}';
    }
}
