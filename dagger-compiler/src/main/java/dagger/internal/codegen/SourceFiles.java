package dagger.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import dagger.internal.DoubleCheckLazy;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.Snippet;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;

class SourceFiles {

  static final Ordering<DependencyRequest> DEPENDENCY_ORDERING = new Ordering<DependencyRequest>() {
    @Override
    public int compare(DependencyRequest left, DependencyRequest right) {
      return ComparisonChain.start()
          .compare(left.requestElement().getKind(), right.requestElement().getKind())
          .compare(left.kind(), right.kind())
          .compare(left.requestElement().getSimpleName().toString(),
              right.requestElement().getSimpleName().toString()).result();
    }
  };

  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByUnresolvedKey(
      Types types, Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      BindingKey resolved = dependency.bindingKey();
      TypeMirror unresolvedType =
          DependencyRequest.Factory.extractKindAndType(dependency.requestElement().asType()).type();
      BindingKey unresolved =
          BindingKey.create(resolved.kind(), resolved.key().withType(types, unresolvedType));
      dependenciesByKeyBuilder.put(unresolved, dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  static ImmutableSetMultimap<BindingKey, DependencyRequest> indexDependenciesByKey(
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        new ImmutableSetMultimap.Builder<BindingKey, DependencyRequest>()
            .orderValuesBy(DEPENDENCY_ORDERING);
    for (DependencyRequest dependency : dependencies) {
      dependenciesByKeyBuilder.put(dependency.bindingKey(), dependency);
    }
    return dependenciesByKeyBuilder.build();
  }

  static ImmutableMap<BindingKey, FrameworkField> generateBindingFieldsForDependencies(
      DependencyRequestMapper dependencyRequestMapper,
      Iterable<? extends DependencyRequest> dependencies) {
    ImmutableSetMultimap<BindingKey, DependencyRequest> dependenciesByKey =
        indexDependenciesByKey(dependencies);
    Map<BindingKey, Collection<DependencyRequest>> dependenciesByKeyMap =
        dependenciesByKey.asMap();
    ImmutableMap.Builder<BindingKey, FrameworkField> bindingFields = ImmutableMap.builder();
    for (Entry<BindingKey, Collection<DependencyRequest>> entry
        : dependenciesByKeyMap.entrySet()) {
      BindingKey bindingKey = entry.getKey();
      Collection<DependencyRequest> requests = entry.getValue();
      Class<?> frameworkClass =
          dependencyRequestMapper.getFrameworkClass(requests.iterator().next());
      ImmutableSet<String> dependencyNames =
          FluentIterable.from(requests).transform(new DependencyVariableNamer()).toSet();

      if (dependencyNames.size() == 1) {
        String name = Iterables.getOnlyElement(dependencyNames);
        bindingFields.put(bindingKey,
            FrameworkField.createWithTypeFromKey(frameworkClass, bindingKey, name));
      } else {
        Iterator<String> namesIterator = dependencyNames.iterator();
        String first = namesIterator.next();
        StringBuilder compositeNameBuilder = new StringBuilder(first);
        while (namesIterator.hasNext()) {
          compositeNameBuilder.append("And").append(
              CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
        }
        bindingFields.put(bindingKey, FrameworkField.createWithTypeFromKey(
            frameworkClass, bindingKey, compositeNameBuilder.toString()));
      }
    }
    return bindingFields.build();
  }

  static Snippet frameworkTypeUsageStatement(Snippet frameworkTypeMemberSelect,
      DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return Snippet.format("%s.create(%s)", ClassName.fromClass(DoubleCheckLazy.class),
            frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return Snippet.format("%s.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
      case MEMBERS_INJECTOR:
        return Snippet.format("%s", frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
    }
  }

  static ClassName factoryNameForProvisionBinding(ProvisionBinding binding) {
    TypeElement enclosingTypeElement = binding.bindingTypeElement();
    ClassName enclosingClassName = ClassName.fromTypeElement(enclosingTypeElement);
    switch (binding.bindingKind()) {
      case INJECTION:
      case PROVISION:
        return enclosingClassName.topLevelClassName().peerNamed(
            enclosingClassName.classFileName() + "_" + factoryPrefix(binding) + "Factory");
      case SYNTHETIC_PROVISON:
        throw new IllegalArgumentException();
      default:
        throw new AssertionError();
    }
  }

  static TypeName parameterizedFactoryNameForProvisionBinding(
      ProvisionBinding binding) {
    ClassName factoryName = factoryNameForProvisionBinding(binding);
    List<TypeName> parameters = ImmutableList.of();
    if (binding.bindingType().equals(BindingType.UNIQUE)) {
      switch(binding.bindingKind()) {
        case INJECTION:
          TypeName bindingName = TypeNames.forTypeMirror(binding.key().type());
          if (bindingName instanceof ParameterizedTypeName) {
            parameters = ((ParameterizedTypeName) bindingName).parameters();
          }
          break;
        case PROVISION:
          if (!binding.bindingTypeElement().getTypeParameters().isEmpty()) {
            parameters = ((ParameterizedTypeName) TypeNames.forTypeMirror(
                binding.bindingTypeElement().asType())).parameters();
          }
          break;
        default:
      }
    }
    return parameters.isEmpty() ? factoryName
        : ParameterizedTypeName.create(factoryName, parameters);
  }

  static ClassName factoryNameForProductionBinding(ProductionBinding binding) {
    TypeElement enclosingTypeElement = binding.bindingTypeElement();
    ClassName enclosingClassName = ClassName.fromTypeElement(enclosingTypeElement);
    switch (binding.bindingKind()) {
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return enclosingClassName.topLevelClassName().peerNamed(
            enclosingClassName.classFileName() + "_" + factoryPrefix(binding) + "Factory");
      default:
        throw new AssertionError();
    }
  }

  static TypeName parameterizedMembersInjectorNameForMembersInjectionBinding(
      MembersInjectionBinding binding) {
    ClassName factoryName = membersInjectorNameForMembersInjectionBinding(binding);
    TypeName bindingName = TypeNames.forTypeMirror(binding.key().type());
    if (bindingName instanceof ParameterizedTypeName) {
      return ParameterizedTypeName.create(factoryName,
          ((ParameterizedTypeName) bindingName).parameters());
    }
    return factoryName;
  }

  static ClassName membersInjectorNameForMembersInjectionBinding(MembersInjectionBinding binding) {
    ClassName injectedClassName = ClassName.fromTypeElement(binding.bindingElement());
    return injectedClassName.topLevelClassName().peerNamed(
        injectedClassName.classFileName() + "_MembersInjector");
  }

  private static String factoryPrefix(ProvisionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";
      case PROVISION:
        return CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL,
            ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());
      default:
        throw new IllegalArgumentException();
    }
  }

  private static String factoryPrefix(ProductionBinding binding) {
    switch (binding.bindingKind()) {
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL,
            ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());
      default:
        throw new IllegalArgumentException();
    }
  }

  private SourceFiles() {}
}
