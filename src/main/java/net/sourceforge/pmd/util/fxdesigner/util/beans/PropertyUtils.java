/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util.fxdesigner.util.beans;

import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * @author Clément Fournier
 */
public final class PropertyUtils {

    private static final Map<Class<?>, Map<String, PropertyDescriptor>> PROPERTY_CACHE = new WeakHashMap<>();


    private PropertyUtils() {

    }

    public static Map<String, PropertyDescriptor> getPropertyDescriptors(Object object) {
        if (object == null) {
            return Collections.emptyMap();
        }

        return PROPERTY_CACHE.computeIfAbsent(object.getClass(), klass -> {
            try {
                return new LinkedHashMap<>(Arrays.stream(Introspector.getBeanInfo(klass).getPropertyDescriptors())
                                                 .collect(Collectors.toMap(FeatureDescriptor::getName, pd -> pd, (p, s) -> s)));
            } catch (IntrospectionException e) {
                return Collections.emptyMap();
            }
        });
    }

    public static void setProperty(Object target, String name, Object value) throws InvocationTargetException, IllegalAccessException {
        PropertyDescriptor descriptor = getPropertyDescriptors(target).get(name);
        if (descriptor == null) {
            System.out.println("No property named '" + name + "' on object of type " + target.getClass());
            return;
        }
        Method setter = descriptor.getWriteMethod();
        setter.invoke(target, value);
    }

    public static Object getProperty(Object target, String name) throws InvocationTargetException, IllegalAccessException {
        PropertyDescriptor descriptor = getPropertyDescriptors(target).get(name);
        if (descriptor == null) {
            System.out.println("No property named '" + name + "' on object of type " + target.getClass());
            return null;
        }
        Method getter = descriptor.getReadMethod();
        return getter.invoke(target);
    }


}
