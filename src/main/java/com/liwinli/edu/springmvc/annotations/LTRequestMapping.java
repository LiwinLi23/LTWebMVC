package com.liwinli.edu.springmvc.annotations;
import java.lang.annotation.*;

@Documented
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LTRequestMapping {
    String value() default "";
}
