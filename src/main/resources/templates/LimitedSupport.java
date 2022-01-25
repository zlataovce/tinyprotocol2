public @interface LimitedSupport {
    int min() default -1;
    int max() default -1;
}