package annotations;

/*>>>
import checkers.nullness.quals.Nullable;
import checkers.javari.quals.ReadOnly;
*/

import annotations.el.AnnotationDef;
import annotations.field.AnnotationFieldType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.lang.reflect.*;


/**
 * A very simple annotation representation constructed with a map of field names
 * to values. See the rules for values on {@link SceneAnnotation#getFieldValue};
 * furthermore, subannotations must be {@link SceneAnnotation}s.
 * {@link SceneAnnotation}s are immutable.
 *
 * <p>
 * {@link SceneAnnotation}s can be constructed directly or through
 * {@link AnnotationFactory#saf}. Either way works, but if you construct
 * one directly, you must provide a matching {@link annotations.el.AnnotationDef} yourself.
 */
public final /*@ReadOnly*/ class SceneAnnotation implements  Comparable<SceneAnnotation>{

    /**
     * The annotation definition.
     */
    @NotNull
    public final AnnotationDef def;

    /**
     * An unmodifiable copy of the passed map of field values.
     */
    public final /*@ReadOnly*/ Map<String, /*@ReadOnly*/ Object> fieldValues;

    /** Check the representation, throw assertion failure if it is violated. */
    public void checkRep() {
        assert fieldValues != null;
        assert fieldValues.keySet() != null;
        assert def != null;
        assert def.fieldTypes != null;
        assert def.fieldTypes.keySet() != null;
        if (! fieldValues.keySet().equals(def.fieldTypes.keySet())) {
            for (String s : fieldValues.keySet()) {
                assert def.fieldTypes.containsKey(s)
                    : String.format("Annotation contains field %s but AnnotationDef does not%n  annotation: %s%n  def: %s%n", s, this, this.def);
            }
            // TODO: Faulty assertions, fails when default value is used
//            for (String s : def.fieldTypes.keySet()) {
//                assert fieldValues.containsKey(s)
//                    : String.format("AnnotationDef contains field %s but Annotation does not", s);
//            }
//            assert false : "This can't happen.";
        }

        for (String fieldname : fieldValues.keySet()) {
            AnnotationFieldType aft = def.fieldTypes.get(fieldname);
            Object value = fieldValues.get(fieldname);
            String valueString;
            String classString = value.getClass().toString();
            if (value instanceof Object[]) {
                Object[] arr = (Object[]) value;
                valueString = Arrays.toString(arr);
                classString += " {";
                for (Object elt : arr) {
                    classString += " " + elt.getClass();
                }
                classString += "}";
            } else if (value instanceof Collection) {
                Collection<?> coll = (Collection<?>) value;
                valueString = Arrays.toString(coll.toArray());
                classString += " {";
                for (Object elt : coll) {
                    classString += " " + elt.getClass();
                }
                classString += " }";
            } else {
                valueString = value.toString();
                // No need to modify valueString.
            }
            assert aft.isValidValue(value)
                : String.format("Bad field value%n  %s (%s)%nfor field%n  %s (%s)%nin annotation%n  %s",
                                valueString, classString, aft, aft.getClass(), def);
        }
    }

    // TODO make sure the field values are valid?
    /**
     * Constructs a {@link SceneAnnotation} with the given definition and
     * field values.  Make sure that the field values obey the rules given on
     * {@link SceneAnnotation#getFieldValue} and that subannotations are also
     * {@link SceneAnnotation}s; this constructor does not validate the
     * values.
     */
    public SceneAnnotation(AnnotationDef def,
            /*@ReadOnly*/ Map<String, ? extends /*@ReadOnly*/ Object> fields) {
        this.def = def;
        this.fieldValues = Collections.unmodifiableMap(
                new LinkedHashMap<String, /*@ReadOnly*/ Object>(fields));
        checkRep();
    }

    /** Use adefs to look up (or insert into it) missing AnnotationDefs. */
    public SceneAnnotation(java.lang.annotation.Annotation ja, Map<String, AnnotationDef> adefs) {
        Class<? extends java.lang.annotation.Annotation> jaType = ja.annotationType();
        String name = jaType.getName();
        if (adefs.containsKey(name)) {
            def = adefs.get(name);
        } else {
            def = AnnotationDef.fromClass(jaType, adefs);
            adefs.put(name, def);
        }
        fieldValues = new LinkedHashMap<String,Object>();
        try {
            for (String fieldname : def.fieldTypes.keySet()) {
                AnnotationFieldType aft = def.fieldTypes.get(fieldname);
                Method m = jaType.getDeclaredMethod(fieldname);
                Object val = m.invoke(ja);
                if (! aft.isValidValue(val)) {
                    if (val instanceof Class[]) {
                        Class<?>[] vala = (Class[]) val;
                        List<Class<?>> vall = new ArrayList<Class<?>>(vala.length);
                        for (Class<?> elt : vala) {
                            vall.add(elt);
                        }
                        val = vall;
                    } else if (val instanceof Object[]) {
                        Object[] vala = (Object[]) val;
                        List<Object> vall = new ArrayList<Object>(vala.length);
                        for (Object elt : vala) {
                            vall.add(elt.toString());
                        }
                        val = vall;
                    } else {
                        val = val.toString();
                    }
                }
                assert aft.isValidValue(val)
                    : String.format("invalid value \"%s\" for field \"%s\" of class \"%s\" and expected type \"%s\"; ja=%s", val, val.getClass(), fieldname, aft, ja);
                fieldValues.put(fieldname, val);
            }
        } catch (NoSuchMethodException e) {
            throw new Error(String.format("no such method (annotation field) in %s%n  from: %s %s", jaType, ja, adefs), e);
        } catch (InvocationTargetException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
        checkRep();
    }

    /**
     * Returns the value of the field whose name is given.
     *
     * <p>
     * Everywhere in the annotation scene library, field values are to be
     * represented as follows:
     *
     * <ul>
     * <li>Primitive value: wrapper object, such as {@link Integer}.
     * <li>{@link String}: {@link String}.
     * <li>Class token: name of the type as a {@link String}, using the source
     * code notation <code>int[]</code> for arrays.
     * <li>Enumeration constant: name of the constant as a {@link String}.
     * <li>Subannotation: <code>Annotation</code> object.
     * <li>Array: {@link java.util.List} of elements in the formats defined here.  If
     * the element type is unknown (see
     * {@link AnnotationBuilder#addEmptyArrayField}), the array must have zero
     * elements.
     * </ul>
     */
    public /*@ReadOnly*/ Object getFieldValue(String fieldName) {
        return fieldValues.get(fieldName);
    }

    /**
     * Returns the definition of the annotation type to which this annotation
     * belongs.
     */
    public final AnnotationDef def() {
        return def;
    }

    /**
     * This {@link SceneAnnotation} equals <code>o</code> if and only if
     * <code>o</code> is a nonnull {@link SceneAnnotation} and <code>this</code> and
     * <code>o</code> have recursively equal definitions and field values,
     * even if they were created by different {@link AnnotationFactory}s.
     */
    @Override
    public final boolean equals(/*>>> @ReadOnly Annotation this,*/ /*@ReadOnly*/ Object o) {
        return o instanceof SceneAnnotation && equals((SceneAnnotation) o);
    }

    /**
     * Returns whether this annotation equals <code>o</code>; a slightly faster
     * variant of {@link #equals(Object)} for when the argument is statically
     * known to be another nonnull {@link SceneAnnotation}. Subclasses may wish to
     * override this with a hard-coded "&amp;&amp;" of field comparisons to improve
     * performance.
     */
    public boolean equals(/*>>> @ReadOnly Annotation this,*/ SceneAnnotation o) {
        return def.equals(o.def())
            && fieldValues.equals(o.fieldValues);
    }

    /**
     * Returns the hash code of this annotation as defined on
     * {@link SceneAnnotation#hashCode}.  Subclasses may wish to override
     * this with a hard-coded XOR/addition of fields to improve performance.
     */
    @Override
    public int hashCode(/*>>> @ReadOnly Annotation this*/) {
        return def.hashCode() + fieldValues.hashCode();
    }

    /**
     * Returns a string representation of this for
     * debugging purposes.  For now, this method relies on
     * {@link java.util.AbstractMap#toString} and the {@link Object#toString toString}
     * methods of the field values, so the representation is only a first
     * approximation to how the annotation would appear in source code.
     */
    @Override
    public String toString(/*>>> @ReadOnly Annotation this*/) {
        StringBuilder sb = new StringBuilder("@");
        sb.append(def.name);
        if (!fieldValues.isEmpty()) {
            sb.append('(');
            sb.append(fieldValues.toString());
            sb.append(')');
        }
        return sb.toString();
    }

    @Override
    public int compareTo(SceneAnnotation o) {
        return toString().compareTo(o.toString());
    }
}