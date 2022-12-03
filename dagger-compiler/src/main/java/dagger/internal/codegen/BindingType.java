package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import dagger.MembersInjector;
import dagger.producers.Producer;
import javax.inject.Provider;

enum BindingType {

  PROVISION(Provider.class),

  MEMBERS_INJECTION(MembersInjector.class),

  PRODUCTION(Producer.class),
  ;

  interface HasBindingType {
    BindingType bindingType();
  }

  private final Class<?> frameworkClass;

  private BindingType(Class<?> frameworkClass) {
    this.frameworkClass = frameworkClass;
  }

  Class<?> frameworkClass() {
    return frameworkClass;
  }

  static Predicate<HasBindingType> isOfType(BindingType type) {
    return Predicates.compose(Predicates.equalTo(type), BINDING_TYPE);
  }

  static Function<HasBindingType, BindingType> BINDING_TYPE =
      new Function<HasBindingType, BindingType>() {
        @Override
        public BindingType apply(HasBindingType hasBindingType) {
          return hasBindingType.bindingType();
        }
      };
}
