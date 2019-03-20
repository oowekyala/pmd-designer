package net.sourceforge.pmd.util.fxdesigner.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Clément Fournier
 */
public final class DataHolder {

    private final Map<DataKey<?>, Object> data;

    private DataHolder(Map<DataKey<?>, Object> data) {
        this.data = data;
    }

    public DataHolder() {
        this(Collections.emptyMap());
    }


    @SuppressWarnings("unchecked")
    public <T> T getData(DataKey<T> key) {
        return (T) data.get(key);
    }

    public <T> DataHolder withData(DataKey<T> key, T value) {
        Map<DataKey<?>, Object> newMap = new HashMap<>(data);
        newMap.put(key, value);
        return new DataHolder(newMap);
    }

    public boolean hasData(DataKey<?> key) {
        return data.containsKey(key);
    }

    @Override
    public String toString() {
        return data.toString();
    }

    /**
     * Uses instance equality.
     *
     * @param <T> Type of data
     */
    public static final class DataKey<T> {

        private final String name;


        public DataKey(String name) {this.name = name;}

        @Override
        public String toString() {
            return name;
        }
    }
}
