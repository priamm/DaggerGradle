package dagger.internal.codegen;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.util.Iterator;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.FrameworkDependency.frameworkDependenciesForBinding;
import static dagger.internal.codegen.TypeNames.DOUBLE_CHECK_LAZY;

class SourceFiles {

  private static final Joiner CLASS_FILE_NAME_JOINER = Joiner.on('_');
  private static final Joiner CANONICAL_NAME_JOINER = Joiner.on('$');

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

  static ImmutableMap<BindingKey, FrameworkField> generateBindingFieldsForDependencies(
      Binding binding) {
    checkArgument(!binding.unresolved().isPresent(), "binding must be unresolved: %s", binding);

    ImmutableMap.Builder<BindingKey, FrameworkField> bindingFields = ImmutableMap.builder();
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
      bindingFields.put(
          frameworkDependency.bindingKey(),
          FrameworkField.createWithTypeFromKey(
              frameworkDependency.frameworkClass(),
              frameworkDependency.bindingKey().key(),
              fieldNameForDependency(frameworkDependency)));
    }
    return bindingFields.build();
  }

  private static String fieldNameForDependency(FrameworkDependency frameworkDependency) {
    ImmutableSet<String> dependencyNames =
        FluentIterable.from(frameworkDependency.dependencyRequests())
            .transform(new DependencyVariableNamer())
            .toSet();

    if (dependencyNames.size() == 1) {
      return Iterables.getOnlyElement(dependencyNames);
    } else {
      Iterator<String> namesIterator = dependencyNames.iterator();
      String first = namesIterator.next();
      StringBuilder compositeNameBuilder = new StringBuilder(first);
      while (namesIterator.hasNext()) {
        compositeNameBuilder
            .append("And")
            .append(CaseFormat.LOWER_CAMEL.to(UPPER_CAMEL, namesIterator.next()));
      }
      return compositeNameBuilder.toString();
    }
  }

  static CodeBlock frameworkTypeUsageStatement(
      CodeBlock frameworkTypeMemberSelect, DependencyRequest.Kind dependencyKind) {
    switch (dependencyKind) {
      case LAZY:
        return CodeBlocks.format(
            "$T.create($L)", DOUBLE_CHECK_LAZY, frameworkTypeMemberSelect);
      case INSTANCE:
      case FUTURE:
        return CodeBlocks.format("$L.get()", frameworkTypeMemberSelect);
      case PROVIDER:
      case PRODUCER:
      case MEMBERS_INJECTOR:
        return CodeBlocks.format("$L", frameworkTypeMemberSelect);
      default:
        throw new AssertionError();
    }
  }

  static ClassName generatedClassNameForBinding(Binding binding) {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contribution = (ContributionBinding) binding;
        checkArgument(!contribution.isSyntheticBinding());
        ClassName enclosingClassName = ClassName.get(contribution.bindingTypeElement());
        switch (contribution.bindingKind()) {
          case INJECTION:
          case PROVISION:
          case IMMEDIATE:
          case FUTURE_PRODUCTION:
            return enclosingClassName
                .topLevelClassName()
                .peerClass(
                    canonicalName(enclosingClassName)
                        + "_"
                        + factoryPrefix(contribution)
                        + "Factory");

          default:
            throw new AssertionError();
        }

      case MEMBERS_INJECTION:
        return membersInjectorNameForType(binding.bindingTypeElement());

      default:
        throw new AssertionError();
    }
  }

  static TypeName parameterizedGeneratedTypeNameForBinding(
      Binding binding) {
    ClassName className = generatedClassNameForBinding(binding);
    ImmutableList<TypeName> typeParameters = bindingTypeParameters(binding);
    if (typeParameters.isEmpty()) {
      return className;
    } else {
      return ParameterizedTypeName.get(
          className,
          FluentIterable.from(typeParameters).toArray(TypeName.class));
    }
  }

  private static Optional<TypeMirror> typeMirrorForBindingTypeParameters(Binding binding)
      throws AssertionError {
    switch (binding.bindingType()) {
      case PROVISION:
      case PRODUCTION:
        ContributionBinding contributionBinding = (ContributionBinding) binding;
        switch (contributionBinding.bindingKind()) {
          case INJECTION:
            return Optional.of(contributionBinding.key().type());

          case PROVISION:
            return Optional.of(contributionBinding.bindingTypeElement().asType());

          case IMMEDIATE:
          case FUTURE_PRODUCTION:
            throw new UnsupportedOperationException();
            
          default:
            return Optional.absent();
        }

      case MEMBERS_INJECTION:
        return Optional.of(binding.key().type());

      default:
        throw new AssertionError();
    }
  }

  static ImmutableList<TypeName> bindingTypeParameters(
      Binding binding) {
    Optional<TypeMirror> typeMirror = typeMirrorForBindingTypeParameters(binding);
    if (!typeMirror.isPresent()) {
      return ImmutableList.of();
    }
    TypeName bindingTypeName = TypeName.get(typeMirror.get());
    return bindingTypeName instanceof ParameterizedTypeName
        ? ImmutableList.copyOf(((ParameterizedTypeName) bindingTypeName).typeArguments)
        : ImmutableList.<TypeName>of();
  }

  static ClassName membersInjectorNameForType(TypeElement typeElement) {
    return siblingClassName(typeElement,  "_MembersInjector");
  }

  @Deprecated
  static String canonicalName(ClassName className) {
    return CANONICAL_NAME_JOINER.join(className.simpleNames());
  }

  static String classFileName(ClassName className) {
    return CLASS_FILE_NAME_JOINER.join(className.simpleNames());
  }

  static ClassName generatedMonitoringModuleName(
      TypeElement componentElement) {
    return siblingClassName(componentElement, "_MonitoringModule");
  }

  static ClassName generatedProductionExecutorModuleName(TypeElement componentElement) {
    return siblingClassName(componentElement, "_ProductionExecutorModule");
  }

  private static ClassName siblingClassName(TypeElement typeElement, String suffix) {
    ClassName className = ClassName.get(typeElement);
    return className.topLevelClassName().peerClass(canonicalName(className) + suffix);
  }

  private static String factoryPrefix(ContributionBinding binding) {
    switch (binding.bindingKind()) {
      case INJECTION:
        return "";

      case PROVISION:
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return CaseFormat.LOWER_CAMEL.to(
            UPPER_CAMEL, ((ExecutableElement) binding.bindingElement()).getSimpleName().toString());

      default:
        throw new IllegalArgumentException();
    }
  }

  static ImmutableList<TypeVariableName> bindingTypeElementTypeVariableNames(Binding binding) {
    ImmutableList.Builder<TypeVariableName> builder = ImmutableList.builder();
    for (TypeParameterElement typeParameter : binding.bindingTypeElement().getTypeParameters()) {
      builder.add(TypeVariableName.get(typeParameter));
    }
    return builder.build();
  }

  private SourceFiles() {}
}
