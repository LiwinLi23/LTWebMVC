package com.liwinli.edu.springmvc.annotations;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LTController {
    String value() default "";
}
