package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Component;
import dagger.MapKey;
import dagger.Provides;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.producers.Produces;
import dagger.producers.ProductionComponent;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.collect.Sets.immutableEnumSet;
import static dagger.internal.codegen.ContributionBinding.Kind.IS_SYNTHETIC_KIND;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MapKeys.unwrapValue;
import static javax.lang.model.element.Modifier.STATIC;

abstract class ContributionBinding extends Binding implements HasContributionType {

  @Override
  Set<DependencyRequest> implicitDependencies() {
    if (!membersInjectionRequest().isPresent()) {
      return dependencies();
    } else {
      return Sets.union(membersInjectionRequest().asSet(), dependencies());
    }
  }

  abstract Optional<DeclaredType> nullableType();

  Optional<TypeElement> contributedBy() {
    return sourceElement().contributedBy();
  }

  boolean isSyntheticBinding() {
    return IS_SYNTHETIC_KIND.apply(bindingKind());
  }

  static final Function<ContributionBinding, Kind> KIND =
      new Function<ContributionBinding, Kind>() {
        @Override
        public Kind apply(ContributionBinding binding) {
          return binding.bindingKind();
        }
      };

  abstract Optional<DependencyRequest> membersInjectionRequest();

  enum Kind {
    SYNTHETIC_MAP,
    SYNTHETIC_MULTIBOUND_SET,
    SYNTHETIC_MULTIBOUND_MAP,
    INJECTION,
    PROVISION,
    COMPONENT,
    COMPONENT_PROVISION,
    SUBCOMPONENT_BUILDER,
    EXECUTOR_DEPENDENCY,
    IMMEDIATE,
    FUTURE_PRODUCTION,
    COMPONENT_PRODUCTION,
    ;

    static final Predicate<Kind> IS_SYNTHETIC_KIND =
        Predicates.in(
            immutableEnumSet(SYNTHETIC_MAP, SYNTHETIC_MULTIBOUND_SET, SYNTHETIC_MULTIBOUND_MAP));

    static final Predicate<Kind> IS_SYNTHETIC_MULTIBINDING_KIND =
        Predicates.in(immutableEnumSet(SYNTHETIC_MULTIBOUND_SET, SYNTHETIC_MULTIBOUND_MAP));

    static Kind forMultibindingRequest(DependencyRequest request) {
      Key key = request.key();
      if (SetType.isSet(key.type())) {
        return SYNTHETIC_MULTIBOUND_SET;
      } else if (MapType.isMap(key.type())) {
        return SYNTHETIC_MULTIBOUND_MAP;
      } else {
        throw new IllegalArgumentException(
            String.format("request is not for a set or map: %s", request));
      }
    }
  }

  protected abstract Kind bindingKind();

  static Predicate<ContributionBinding> isOfKind(Kind kind) {
    return Predicates.compose(Predicates.equalTo(kind), KIND);
  }

  abstract Provides.Type provisionType();

  @Override
  public ContributionType contributionType() {
    return ContributionType.forProvisionType(provisionType());
  }

  enum FactoryCreationStrategy {
    ENUM_INSTANCE,
    CLASS_CONSTRUCTOR,
  }

  FactoryCreationStrategy factoryCreationStrategy() {
    switch (bindingKind()) {
      case PROVISION:
        return implicitDependencies().isEmpty() && bindingElement().getModifiers().contains(STATIC)
            ? FactoryCreationStrategy.ENUM_INSTANCE
            : FactoryCreationStrategy.CLASS_CONSTRUCTOR;

      case INJECTION:
      case SYNTHETIC_MULTIBOUND_SET:
      case SYNTHETIC_MULTIBOUND_MAP:
        return implicitDependencies().isEmpty()
            ? FactoryCreationStrategy.ENUM_INSTANCE
            : FactoryCreationStrategy.CLASS_CONSTRUCTOR;
        
      default:
        return FactoryCreationStrategy.CLASS_CONSTRUCTOR;
    }
  }

  static ImmutableSetMultimap<Object, ContributionBinding> indexMapBindingsByMapKey(
      Set<ContributionBinding> mapBindings) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            mapBindings,
            new Function<ContributionBinding, Object>() {
              @Override
              public Object apply(ContributionBinding mapBinding) {
                AnnotationMirror mapKey = getMapKey(mapBinding.bindingElement()).get();
                Optional<? extends AnnotationValue> unwrappedValue = unwrapValue(mapKey);
                return unwrappedValue.isPresent() ? unwrappedValue.get().getValue() : mapKey;
              }
            }));
  }

  static ImmutableSetMultimap<Wrapper<DeclaredType>, ContributionBinding>
      indexMapBindingsByAnnotationType(Set<ContributionBinding> mapBindings) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            mapBindings,
            new Function<ContributionBinding, Equivalence.Wrapper<DeclaredType>>() {
              @Override
              public Equivalence.Wrapper<DeclaredType> apply(ContributionBinding mapBinding) {
                return MoreTypes.equivalence()
                    .wrap(getMapKey(mapBinding.bindingElement()).get().getAnnotationType());
              }
            }));
  }
}
