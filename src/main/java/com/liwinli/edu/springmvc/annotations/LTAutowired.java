package com.liwinli.edu.springmvc.annotations;

import java.lang.annotation.*;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LTAutowired {
    String value() default "";
}
