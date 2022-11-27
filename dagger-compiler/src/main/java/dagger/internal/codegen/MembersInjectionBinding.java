package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

@AutoValue
abstract class MembersInjectionBinding extends Binding {
  @Override abstract TypeElement bindingElement();

  abstract ImmutableSortedSet<InjectionSite> injectionSites();

  abstract Optional<DependencyRequest> parentInjectorRequest();

  enum Strategy {
    NO_OP,
    DELEGATE,
    INJECT_MEMBERS,
  }

  Strategy injectionStrategy() {
    if (injectionSites().isEmpty()) {
      return parentInjectorRequest().isPresent()
          ? Strategy.DELEGATE
          : Strategy.NO_OP;
    } else {
      return Strategy.INJECT_MEMBERS;
    }
  }

  MembersInjectionBinding withoutParentInjectorRequest() {
    return new AutoValue_MembersInjectionBinding(
          key(),
          dependencies(),
          implicitDependencies(),
          bindingPackage(),
          hasNonDefaultTypeParameters(),
          bindingElement(),
          injectionSites(),
          Optional.<DependencyRequest>absent());
  }

  private static final Ordering<InjectionSite> INJECTION_ORDERING =
      new Ordering<InjectionSite>() {
        @Override
        public int compare(InjectionSite left, InjectionSite right) {
          checkArgument(left.element().getEnclosingElement()
              .equals(right.element().getEnclosingElement()));
          return ComparisonChain.start()
              .compare(left.element().getKind(), right.element().getKind())
              .compare(targetIndexInEnclosing(left), targetIndexInEnclosing(right))
              .result();
        }

        private int targetIndexInEnclosing(InjectionSite injectionSite)  {
          return injectionSite.element().getEnclosingElement().getEnclosedElements()
              .indexOf(injectionSite.element());
        }
      };

  @AutoValue
  abstract static class InjectionSite {
    enum Kind {
      FIELD,
      METHOD,
    }

    abstract Kind kind();

    abstract Element element();

    abstract ImmutableSet<DependencyRequest> dependencies();
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = checkNotNull(elements);
      this.types = checkNotNull(types);
      this.keyFactory = checkNotNull(keyFactory);
      this.dependencyRequestFactory = checkNotNull(dependencyRequestFactory);
    }

    private InjectionSite injectionSiteForInjectMethod(ExecutableElement methodElement,
        DeclaredType containingType) {
      checkNotNull(methodElement);
      checkArgument(methodElement.getKind().equals(ElementKind.METHOD));
      checkArgument(isAnnotationPresent(methodElement, Inject.class));
      ExecutableType resolved =
          MoreTypes.asExecutable(types.asMemberOf(containingType, methodElement));
      return new AutoValue_MembersInjectionBinding_InjectionSite(InjectionSite.Kind.METHOD,
          methodElement,
          dependencyRequestFactory.forRequiredResolvedVariables(
              containingType,
              methodElement.getParameters(),
              resolved.getParameterTypes()));
    }

    private InjectionSite injectionSiteForInjectField(VariableElement fieldElement,
        DeclaredType containingType) {
      checkNotNull(fieldElement);
      checkArgument(fieldElement.getKind().equals(ElementKind.FIELD));
      checkArgument(isAnnotationPresent(fieldElement, Inject.class));
      TypeMirror resolved = types.asMemberOf(containingType, fieldElement);
      return new AutoValue_MembersInjectionBinding_InjectionSite(InjectionSite.Kind.FIELD,
          fieldElement,
          ImmutableSet.of(dependencyRequestFactory.forRequiredResolvedVariable(
              containingType, fieldElement, resolved)));
    }

    MembersInjectionBinding unresolve(MembersInjectionBinding binding) {
      checkState(binding.hasNonDefaultTypeParameters());
      DeclaredType unresolved = MoreTypes.asDeclared(binding.bindingElement().asType());
      return forInjectedType(unresolved, Optional.<TypeMirror>absent());
    }

    MembersInjectionBinding forInjectedType(DeclaredType type, Optional<TypeMirror> resolvedType) {
      if (!type.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
        DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
        checkState(types.isSameType(types.erasure(resolved), types.erasure(type)),
            "erased expected type: %s, erased actual type: %s",
            types.erasure(resolved), types.erasure(type));
        type = resolved;
      }

      TypeElement typeElement = MoreElements.asType(type.asElement());
      final DeclaredType resolved = type;
      ImmutableSortedSet.Builder<InjectionSite> injectionSitesBuilder =
          ImmutableSortedSet.orderedBy(INJECTION_ORDERING);
      for (Element enclosedElement : typeElement.getEnclosedElements()) {
        injectionSitesBuilder.addAll(enclosedElement.accept(
            new ElementKindVisitor6<Optional<InjectionSite>, Void>(
                Optional.<InjectionSite>absent()) {
                  @Override
                  public Optional<InjectionSite> visitExecutableAsMethod(ExecutableElement e,
                      Void p) {
                    return isAnnotationPresent(e, Inject.class)
                        ? Optional.of(injectionSiteForInjectMethod(e, resolved))
                        : Optional.<InjectionSite>absent();
                  }

                  @Override
                  public Optional<InjectionSite> visitVariableAsField(VariableElement e, Void p) {
                    return isAnnotationPresent(e, Inject.class)
                        ? Optional.of(injectionSiteForInjectField(e, resolved))
                        : Optional.<InjectionSite>absent();
                  }
                }, null).asSet());
      }
      ImmutableSortedSet<InjectionSite> injectionSites = injectionSitesBuilder.build();

      ImmutableSet<DependencyRequest> dependencies = FluentIterable.from(injectionSites)
          .transformAndConcat(new Function<InjectionSite, Set<DependencyRequest>>() {
            @Override public Set<DependencyRequest> apply(InjectionSite input) {
              return input.dependencies();
            }
          })
          .toSet();

      Optional<DependencyRequest> parentInjectorRequest =
          MoreTypes.nonObjectSuperclass(types, elements, type)
              .transform(new Function<DeclaredType, DependencyRequest>() {
                @Override public DependencyRequest apply(DeclaredType input) {
                  return dependencyRequestFactory.forMembersInjectedType(input);
                }
              });

      Key key = keyFactory.forMembersInjectedType(type);
      return new AutoValue_MembersInjectionBinding(
          key,
          dependencies,
          new ImmutableSet.Builder<DependencyRequest>()
              .addAll(parentInjectorRequest.asSet())
              .addAll(dependencies)
              .build(),
          findBindingPackage(key),
          hasNonDefaultTypeParameters(typeElement, key.type(), types),
          typeElement,
          injectionSites,
          parentInjectorRequest);
    }
  }
}
