package dagger;

import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Documented
@Target(TYPE)
@Beta
public @interface Multibindings {}
