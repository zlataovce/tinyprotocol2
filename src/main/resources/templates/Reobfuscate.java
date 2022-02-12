import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A data holder annotation holding mappings for the annotated type. This is needed for bidirectional raw-wrapper conversion of the annotated type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Reobfuscate {
    /**
     * Returns a compacted mapping string in the format: mapping=ver,ver+mapping=ver,ver
     *
     * @return the compacted mapping string
     */
    String value();

    /**
     * Returns the minimum server protocol version needed for reobfuscation the annotated type.
     *
     * @return the minimum version
     */
    int min() default -1;

    /**
     * Returns the maximum server protocol version needed for reobfuscation the annotated type.
     *
     * @return the maximum version
     */
    int max() default -1;
}