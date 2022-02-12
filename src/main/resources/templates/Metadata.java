import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A data holder holding additional information about the annotated type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Metadata {
    /**
     * Returns an external (non-JDK) class name of the annotated type's type.
     *
     * @return the external type class
     */
    String externalType() default "";
}