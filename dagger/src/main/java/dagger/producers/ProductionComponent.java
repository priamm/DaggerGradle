package dagger.producers;

import com.google.common.util.concurrent.ListenableFuture;
import dagger.Module;
import dagger.Provides;
import dagger.internal.Beta;
import java.lang.annotation.Documented;
import java.lang.annotation.Target;
import javax.inject.Inject;
import javax.inject.Qualifier;

import static java.lang.annotation.ElementType.TYPE;

@Documented
@Target(TYPE)
@Beta
public @interface ProductionComponent {

  Class<?>[] modules() default {};

  Class<?>[] dependencies() default {};

  @Target(TYPE)
  @Documented
  @interface Builder {}
}
