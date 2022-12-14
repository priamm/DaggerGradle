package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import dagger.MembersInjector;
import dagger.producers.Producer;
import javax.inject.Provider;

import static com.google.common.collect.Iterables.getOnlyElement;

abstract class DependencyRequestMapper {
  abstract Class<?> getFrameworkClass(DependencyRequest request);

  Class<?> getFrameworkClass(Iterable<DependencyRequest> requests) {
    ImmutableSet<Class<?>> classes = FluentIterable.from(requests)
        .transform(new Function<DependencyRequest, Class<?>>() {
          @Override public Class<?> apply(DependencyRequest request) {
            return getFrameworkClass(request);
          }
        })
        .toSet();
    if (classes.size() == 1) {
      return getOnlyElement(classes);
    } else if (classes.equals(ImmutableSet.of(Producer.class, Provider.class))) {
      return Provider.class;
    } else {
      throw new IllegalStateException("Bad set of framework classes: " + classes);
    }
  }

  private static final class MapperForProvider extends DependencyRequestMapper {
    @Override public Class<?> getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
          return Provider.class;
        case MEMBERS_INJECTOR:
          return MembersInjector.class;
        case PRODUCED:
        case PRODUCER:
          throw new IllegalArgumentException();
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PROVIDER = new MapperForProvider();

  private static final class MapperForProducer extends DependencyRequestMapper {
    @Override public Class<?> getFrameworkClass(DependencyRequest request) {
      switch (request.kind()) {
        case INSTANCE:
        case PRODUCED:
        case PRODUCER:
          return Producer.class;
        case PROVIDER:
        case LAZY:
          return Provider.class;
        case MEMBERS_INJECTOR:
          return MembersInjector.class;
        default:
          throw new AssertionError();
      }
    }
  }

  static final DependencyRequestMapper FOR_PRODUCER = new MapperForProducer();

  static DependencyRequestMapper forBindingType(BindingType bindingType) {
    return bindingType.equals(BindingType.PRODUCTION) ? FOR_PRODUCER : FOR_PROVIDER;
  }
}
