package net.sourceforge.pmd.util.fxdesigner.util.beans.converters;

import static net.sourceforge.pmd.util.fxdesigner.util.beans.converters.Serializer.stringConversion;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * @author Clément Fournier
 */
public class SerializationBean {


    private final Map<Class<?>, Serializer<?>> converters = new WeakHashMap<>();

    private SerializationBean() {
        registerStandard();
    }

    private void registerStandard() {

        register(stringConversion(Function.identity(), Function.identity()), String.class);
        register(stringConversion(Integer::valueOf, i -> Integer.toString(i)), Integer.class, Integer.TYPE);
        register(stringConversion(Double::valueOf, d -> Double.toString(d)), Double.class, Double.TYPE);
        register(stringConversion(Boolean::valueOf, b -> Boolean.toString(b)), Boolean.class, Boolean.TYPE);
        register(stringConversion(Long::valueOf, b -> Long.toString(b)), Long.class, Long.TYPE);
        register(stringConversion(Float::valueOf, b -> Float.toString(b)), Float.class, Float.TYPE);
        register(stringConversion(Short::valueOf, b -> Short.toString(b)), Short.class, Short.TYPE);
        register(stringConversion(Byte::valueOf, b -> Byte.toString(b)), Byte.class, Byte.TYPE);
        register(stringConversion(s -> s.charAt(0), b -> Character.toString(b)), Character.class, Character.TYPE);


        register(getSerializer(Long.TYPE).map(Date::new, Date::getTime), Date.class);
        register(getSerializer(Long.TYPE).map(java.sql.Date::new, java.sql.Date::getTime), java.sql.Date.class);
        register(getSerializer(Long.TYPE).map(java.sql.Time::new, java.sql.Time::getTime), java.sql.Time.class);
        register(stringConversion(File::new, File::getPath), File.class);
        register(stringConversion(Paths::get, Path::toString), Path.class);
        register(stringConversion(className -> {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }, Class::getName), Class.class);

    }


    @SafeVarargs
    public final <T> void register(Serializer<T> serializer, Class<T>... type) {
        for (Class<T> tClass : type) {
            converters.put(tClass, serializer);
        }
    }

    public final <T> Serializer<T> getSerializer(Class<T> type) {
        @SuppressWarnings("unchecked")
        Serializer<T> t = (Serializer<T>) converters.get(type);
        return t;
    }


}
