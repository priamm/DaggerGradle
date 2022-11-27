package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import dagger.Component;
import dagger.internal.codegen.BindingGraph.ResolvedBindings;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.internal.codegen.ContributionBinding.BindingType;
import dagger.internal.codegen.ValidationReport.Builder;
import dagger.internal.codegen.writer.TypeNames;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ErrorMessages.INDENT;
import static dagger.internal.codegen.ErrorMessages.MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE;
import static dagger.internal.codegen.ErrorMessages.NULLABLE_TO_NON_NULLABLE;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.REQUIRES_PROVIDER_OR_PRODUCER_FORMAT;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;

public class BindingGraphValidator implements Validator<BindingGraph> {

  private final Types types;
  private final InjectBindingRegistry injectBindingRegistry;
  private final ValidationType scopeCycleValidationType;
  private final Diagnostic.Kind nullableValidationType;
  private final ProvisionBindingFormatter provisionBindingFormatter;
  private final ProductionBindingFormatter productionBindingFormatter;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final DependencyRequestFormatter dependencyRequestFormatter;
  private final KeyFormatter keyFormatter;

  BindingGraphValidator(
      Types types,
      InjectBindingRegistry injectBindingRegistry,
      ValidationType scopeCycleValidationType,
      Diagnostic.Kind nullableValidationType,
      ProvisionBindingFormatter provisionBindingFormatter,
      ProductionBindingFormatter productionBindingFormatter,
      MethodSignatureFormatter methodSignatureFormatter,
      DependencyRequestFormatter dependencyRequestFormatter,
      KeyFormatter keyFormatter) {
    this.types = types;
    this.injectBindingRegistry = injectBindingRegistry;
    this.scopeCycleValidationType = scopeCycleValidationType;
    this.nullableValidationType = nullableValidationType;
    this.provisionBindingFormatter = provisionBindingFormatter;
    this.productionBindingFormatter = productionBindingFormatter;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.dependencyRequestFormatter = dependencyRequestFormatter;
    this.keyFormatter = keyFormatter;
  }

  @Override
  public ValidationReport<BindingGraph> validate(final BindingGraph subject) {
    final ValidationReport.Builder<BindingGraph> reportBuilder =
        ValidationReport.Builder.about(subject);
    return validate(subject, reportBuilder);
  }

  private ValidationReport<BindingGraph> validate(final BindingGraph subject,
      final ValidationReport.Builder<BindingGraph> reportBuilder) {
    ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings = subject.resolvedBindings();

    validateComponentScope(subject, reportBuilder, resolvedBindings);
    validateDependencyScopes(subject, reportBuilder);

    for (ComponentMethodDescriptor componentMethod :
        subject.componentDescriptor().componentMethods()) {
      Optional<DependencyRequest> entryPoint = componentMethod.dependencyRequest();
      if (entryPoint.isPresent()) {
        traverseRequest(entryPoint.get(), new ArrayDeque<ResolvedRequest>(), subject,
            reportBuilder);
      }
    }

    validateSubcomponents(subject, reportBuilder);

    return reportBuilder.build();
  }

  private void traverseRequest(
      DependencyRequest request,
      Deque<ResolvedRequest> bindingPath,
      BindingGraph graph,
      ValidationReport.Builder<BindingGraph> reportBuilder) {
    BindingKey requestKey = request.bindingKey();
    for (ResolvedRequest pathElement : bindingPath) {
      if (pathElement.request().bindingKey().equals(requestKey)) {
        reportCycle(request, bindingPath, reportBuilder);
        return;
      }
    }

    ResolvedRequest resolvedRequest = ResolvedRequest.create(request, graph);
    bindingPath.push(resolvedRequest);
    validateResolvedBinding(bindingPath, resolvedRequest.binding(), reportBuilder);

    for (Binding binding : resolvedRequest.binding().bindings()) {
      for (DependencyRequest nextRequest : binding.implicitDependencies()) {
        traverseRequest(nextRequest, bindingPath, graph, reportBuilder);
      }
    }
    bindingPath.poll();
  }

  private void validateSubcomponents(BindingGraph graph,
      ValidationReport.Builder<BindingGraph> reportBuilder) {
    for (Entry<ExecutableElement, BindingGraph> subgraphEntry : graph.subgraphs().entrySet()) {
      validate(subgraphEntry.getValue(), reportBuilder);
    }
  }

  private boolean validateResolvedBinding(
      Deque<ResolvedRequest> path,
      ResolvedBindings resolvedBinding,
      Builder<BindingGraph> reportBuilder) {
    if (resolvedBinding.bindings().isEmpty()) {
      reportMissingBinding(path, reportBuilder);
      return false;
    }

    ImmutableSet.Builder<ProvisionBinding> provisionBindingsBuilder =
        ImmutableSet.builder();
    ImmutableSet.Builder<ProductionBinding> productionBindingsBuilder =
        ImmutableSet.builder();
    ImmutableSet.Builder<MembersInjectionBinding> membersInjectionBindingsBuilder =
        ImmutableSet.builder();
    for (Binding binding : resolvedBinding.bindings()) {
      if (binding instanceof ProvisionBinding) {
        provisionBindingsBuilder.add((ProvisionBinding) binding);
      }
      if (binding instanceof ProductionBinding) {
        productionBindingsBuilder.add((ProductionBinding) binding);
      }
      if (binding instanceof MembersInjectionBinding) {
        membersInjectionBindingsBuilder.add((MembersInjectionBinding) binding);
      }
    }
    ImmutableSet<ProvisionBinding> provisionBindings = provisionBindingsBuilder.build();
    ImmutableSet<ProductionBinding> productionBindings = productionBindingsBuilder.build();
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        membersInjectionBindingsBuilder.build();

    switch (resolvedBinding.bindingKey().kind()) {
      case CONTRIBUTION:
        if (!membersInjectionBindings.isEmpty()) {
          throw new IllegalArgumentException(
              "contribution binding keys should never have members injection bindings");
        }
        Set<ContributionBinding> combined = Sets.union(provisionBindings, productionBindings);
        if (!validateNullability(path.peek().request(), combined, reportBuilder)) {
          return false;
        }
        if (!productionBindings.isEmpty() && doesPathRequireProvisionOnly(path)) {
          reportProviderMayNotDependOnProducer(path, reportBuilder);
          return false;
        }
        if ((provisionBindings.size() + productionBindings.size()) <= 1) {
          return true;
        }
        ImmutableListMultimap<BindingType, ContributionBinding> bindingsByType =
            ContributionBinding.bindingTypesFor(
                Iterables.<ContributionBinding>concat(provisionBindings, productionBindings));
        if (bindingsByType.keySet().size() > 1) {
          reportMultipleBindingTypes(path, reportBuilder);
          return false;
        } else if (getOnlyElement(bindingsByType.keySet()).equals(BindingType.UNIQUE)) {
          reportDuplicateBindings(path, reportBuilder);
          return false;
        }
        break;
      case MEMBERS_INJECTION:
        if (!provisionBindings.isEmpty() || !productionBindings.isEmpty()) {
          throw new IllegalArgumentException(
              "members injection binding keys should never have contribution bindings");
        }
        if (membersInjectionBindings.size() > 1) {
          reportDuplicateBindings(path, reportBuilder);
          return false;
        }
        if (membersInjectionBindings.size() == 1) {
          MembersInjectionBinding binding = getOnlyElement(membersInjectionBindings);
          if (!validateMembersInjectionBinding(binding, path, reportBuilder)) {
            return false;
          }
        }
        break;
      default:
        throw new AssertionError();
    }
    return true;
  }

  private boolean validateNullability(DependencyRequest request,
      Set<ContributionBinding> bindings, Builder<BindingGraph> reportBuilder) {
    boolean valid = true;
    String typeName = TypeNames.forTypeMirror(request.key().type()).toString();
    if (!request.isNullable()) {
      for (ContributionBinding binding : bindings) {
        if (binding.nullableType().isPresent()) {
          String methodSignature;
          if (binding instanceof ProvisionBinding) {
            ProvisionBinding provisionBinding = (ProvisionBinding) binding;
            methodSignature = provisionBindingFormatter.format(provisionBinding);
          } else {
            ProductionBinding productionBinding = (ProductionBinding) binding;
            methodSignature = productionBindingFormatter.format(productionBinding);
          }
          reportBuilder.addItem(
              String.format(NULLABLE_TO_NON_NULLABLE, typeName, methodSignature)
              + "\n at: " + dependencyRequestFormatter.format(request),
              nullableValidationType,
              request.requestElement());
          valid = false;
        }
      }
    }
    return valid;
  }

  private boolean validateMembersInjectionBinding(MembersInjectionBinding binding,
      final Deque<ResolvedRequest> path, final Builder<BindingGraph> reportBuilder) {
    return binding.key().type().accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override protected Boolean defaultAction(TypeMirror e, Void p) {
        reportBuilder.addItem("Invalid members injection request.",
            path.peek().request().requestElement());
        return false;
      }

      @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
        for (TypeMirror arg : type.getTypeArguments()) {
          boolean declared;
          switch (arg.getKind()) {
            case ARRAY:
              declared = MoreTypes.asArray(arg).getComponentType().accept(
                  new SimpleTypeVisitor6<Boolean, Void>() {
                    @Override protected Boolean defaultAction(TypeMirror e, Void p) {
                      return false;
                    }

                    @Override public Boolean visitDeclared(DeclaredType t, Void p) {
                      for (TypeMirror arg : t.getTypeArguments()) {
                        if (!arg.accept(this, null)) {
                          return false;
                        }
                      }
                      return true;
                    }

                    @Override public Boolean visitArray(ArrayType t, Void p) {
                      return t.getComponentType().accept(this, null);
                    }

                    @Override public Boolean visitPrimitive(PrimitiveType t, Void p) {
                      return true;
                    }
                  }, null);
              break;
            case DECLARED:
              declared = true;
              break;
            default:
              declared = false;
          }
          if (!declared) {
            ImmutableList<String> printableDependencyPath = FluentIterable.from(path)
                .transform(REQUEST_FROM_RESOLVED_REQUEST)
                .transform(dependencyRequestFormatter)
                .filter(Predicates.not(Predicates.equalTo("")))
                .toList()
                .reverse();
            reportBuilder.addItem(
                String.format(MEMBERS_INJECTION_WITH_UNBOUNDED_TYPE,
                    arg.toString(),
                    type.toString(),
                    Joiner.on('\n').join(printableDependencyPath)),
                    path.peek().request().requestElement());
            return false;
          }
        }

        TypeElement element = MoreElements.asType(type.asElement());
        if (!MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
            && types.isSameType(types.erasure(element.asType()), type)) {
            ImmutableList<String> printableDependencyPath = FluentIterable.from(path)
                .transform(REQUEST_FROM_RESOLVED_REQUEST)
                .transform(dependencyRequestFormatter)
                .filter(Predicates.not(Predicates.equalTo("")))
                .toList()
                .reverse();
          reportBuilder.addItem(
              String.format(ErrorMessages.MEMBERS_INJECTION_WITH_RAW_TYPE,
                  type.toString(),
                  Joiner.on('\n').join(printableDependencyPath)),
              path.peek().request().requestElement());
          return false;
        }

        return true;
      }
    }, null);
  }

  private void validateDependencyScopes(BindingGraph subject,
      Builder<BindingGraph> reportBuilder) {
    ComponentDescriptor descriptor = subject.componentDescriptor();
    Optional<AnnotationMirror> scope = subject.componentDescriptor().scope();
    ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(descriptor.dependencies());
    if (scope.isPresent()) {
      if (scopeCycleValidationType.diagnosticKind().isPresent()
          && isTypeOf(Singleton.class, scope.get().getAnnotationType())) {
        if (!scopedDependencies.isEmpty()) {
          StringBuilder message = new StringBuilder(
              "This @Singleton component cannot depend on scoped components:\n");
          appendIndentedComponentsList(message, scopedDependencies);
          reportBuilder.addItem(message.toString(),
              scopeCycleValidationType.diagnosticKind().get(),
              descriptor.componentDefinitionType(),
              descriptor.componentAnnotation());
        }
      } else if (scopedDependencies.size() > 1) {
        StringBuilder message = new StringBuilder(ErrorMessages.format(scope.get()))
            .append(' ')
            .append(descriptor.componentDefinitionType().getQualifiedName())
            .append(" depends on more than one scoped component:\n");
        appendIndentedComponentsList(message, scopedDependencies);
        reportBuilder.addItem(message.toString(),
            descriptor.componentDefinitionType(),
            descriptor.componentAnnotation());
      } else {
        if (!scopeCycleValidationType.equals(ValidationType.NONE)) {
          validateScopeHierarchy(descriptor.componentDefinitionType(),
              descriptor.componentDefinitionType(),
              reportBuilder,
              new ArrayDeque<Equivalence.Wrapper<AnnotationMirror>>(),
              new ArrayDeque<TypeElement>());
        }
      }
    } else {
      if (!scopedDependencies.isEmpty()) {
        StringBuilder message =
            new StringBuilder(descriptor.componentDefinitionType().getQualifiedName())
                .append(" (unscoped) cannot depend on scoped components:\n");
        appendIndentedComponentsList(message, scopedDependencies);
        reportBuilder.addItem(message.toString(),
            descriptor.componentDefinitionType(),
            descriptor.componentAnnotation());
      }
    }
  }

  private void appendIndentedComponentsList(StringBuilder message, Iterable<TypeElement> types) {
    for (TypeElement scopedComponent : types) {
      message.append(INDENT);
      Optional<AnnotationMirror> scope = getScopeAnnotation(scopedComponent);
      if (scope.isPresent()) {
        message.append(ErrorMessages.format(scope.get())).append(' ');
      }
      message.append(stripCommonTypePrefixes(scopedComponent.getQualifiedName().toString()))
          .append('\n');
    }
  }

  private ImmutableSet<TypeElement> scopedTypesIn(Set<TypeElement> types) {
    return FluentIterable.from(types).filter(new Predicate<TypeElement>() {
      @Override public boolean apply(TypeElement input) {
        return getScopeAnnotation(input).isPresent();
      }
    }).toSet();
  }

  private void validateScopeHierarchy(TypeElement rootComponent,
      TypeElement componentType,
      Builder<BindingGraph> reportBuilder,
      Deque<Equivalence.Wrapper<AnnotationMirror>> scopeStack,
      Deque<TypeElement> scopedDependencyStack) {
    Optional<AnnotationMirror> scope = getScopeAnnotation(componentType);
    if (scope.isPresent()) {
      Equivalence.Wrapper<AnnotationMirror> wrappedScope =
          AnnotationMirrors.equivalence().wrap(scope.get());
      if (scopeStack.contains(wrappedScope)) {
        scopedDependencyStack.push(componentType);
        StringBuilder message = new StringBuilder();
        message.append(rootComponent.getQualifiedName());
        message.append(" depends on scoped components in a non-hierarchical scope ordering:\n");
        appendIndentedComponentsList(message, scopedDependencyStack);
        if (scopeCycleValidationType.diagnosticKind().isPresent()) {
          reportBuilder.addItem(message.toString(),
              scopeCycleValidationType.diagnosticKind().get(),
              rootComponent, getAnnotationMirror(rootComponent, Component.class).get());
        }
        scopedDependencyStack.pop();
      } else {
        Optional<AnnotationMirror> componentAnnotation =
            getAnnotationMirror(componentType, Component.class);
        if (componentAnnotation.isPresent()) {
          ImmutableSet<TypeElement> scopedDependencies = scopedTypesIn(
              MoreTypes.asTypeElements(getComponentDependencies(componentAnnotation.get())));
          if (scopedDependencies.size() == 1) {
            scopeStack.push(wrappedScope);
            scopedDependencyStack.push(componentType);
            validateScopeHierarchy(rootComponent, getOnlyElement(scopedDependencies),
                reportBuilder, scopeStack, scopedDependencyStack);
            scopedDependencyStack.pop();
            scopeStack.pop();
          }
        }
      }
    }
  }

  void validateComponentScope(final BindingGraph subject,
      final ValidationReport.Builder<BindingGraph> reportBuilder,
      ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings) {
    Optional<Equivalence.Wrapper<AnnotationMirror>> componentScope =
        subject.componentDescriptor().wrappedScope();
    ImmutableSet.Builder<String> incompatiblyScopedMethodsBuilder = ImmutableSet.builder();
    for (ResolvedBindings bindings : resolvedBindings.values()) {
      if (bindings.bindingKey().kind().equals(BindingKey.Kind.CONTRIBUTION)) {
        for (ContributionBinding contributionBinding : bindings.ownedContributionBindings()) {
          if (contributionBinding instanceof ProvisionBinding) {
            ProvisionBinding provisionBinding = (ProvisionBinding) contributionBinding;
            if (provisionBinding.scope().isPresent()
                && !componentScope.equals(provisionBinding.wrappedScope())) {
              switch (provisionBinding.bindingKind()) {
                case PROVISION:
                  ExecutableElement provisionMethod =
                      MoreElements.asExecutable(provisionBinding.bindingElement());
                  incompatiblyScopedMethodsBuilder.add(
                      methodSignatureFormatter.format(provisionMethod));
                  break;
                case INJECTION:
                  incompatiblyScopedMethodsBuilder.add(stripCommonTypePrefixes(
                      provisionBinding.scope().get().toString()) + " class "
                          + provisionBinding.bindingTypeElement().getQualifiedName());
                  break;
                default:
                  throw new IllegalStateException();
              }
            }
          }
        }
      }
    }
    ImmutableSet<String> incompatiblyScopedMethods = incompatiblyScopedMethodsBuilder.build();
    if (!incompatiblyScopedMethods.isEmpty()) {
      TypeElement componentType = subject.componentDescriptor().componentDefinitionType();
      StringBuilder message = new StringBuilder(componentType.getQualifiedName());
      if (componentScope.isPresent()) {
        message.append(" scoped with ");
        message.append(stripCommonTypePrefixes(ErrorMessages.format(componentScope.get().get())));
        message.append(" may not reference bindings with different scopes:\n");
      } else {
        message.append(" (unscoped) may not reference scoped bindings:\n");
      }
      for (String method : incompatiblyScopedMethods) {
        message.append(ErrorMessages.INDENT).append(method).append("\n");
      }
      reportBuilder.addItem(message.toString(), componentType,
          subject.componentDescriptor().componentAnnotation());
    }
  }

  @SuppressWarnings("resource")
  private void reportProviderMayNotDependOnProducer(
      Deque<ResolvedRequest> path, ValidationReport.Builder<BindingGraph> reportBuilder) {
    StringBuilder errorMessage = new StringBuilder();
    if (path.size() == 1) {
      new Formatter(errorMessage).format(
          ErrorMessages.PROVIDER_ENTRY_POINT_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
          keyFormatter.format(path.peek().request().key()));
    } else {
      ImmutableSet<ProvisionBinding> dependentProvisions = provisionsDependingOnLatestRequest(path);
      new Formatter(errorMessage).format(ErrorMessages.PROVIDER_MAY_NOT_DEPEND_ON_PRODUCER_FORMAT,
          keyFormatter.format(dependentProvisions.iterator().next().key()));
    }
    reportBuilder.addItem(errorMessage.toString(), path.getLast().request().requestElement());
  }

  private void reportMissingBinding(
      Deque<ResolvedRequest> path, ValidationReport.Builder<BindingGraph> reportBuilder) {
    Key key = path.peek().request().key();
    TypeMirror type = key.type();
    String typeName = TypeNames.forTypeMirror(type).toString();
    boolean requiresContributionMethod = !key.isValidImplicitProvisionKey(types);
    boolean requiresProvision = doesPathRequireProvisionOnly(path);
    StringBuilder errorMessage = new StringBuilder();
    String requiresErrorMessageFormat = requiresContributionMethod
        ? requiresProvision
            ? REQUIRES_PROVIDER_FORMAT
            : REQUIRES_PROVIDER_OR_PRODUCER_FORMAT
        : requiresProvision
            ? REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT
            : REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_OR_PRODUCER_FORMAT;
    errorMessage.append(String.format(requiresErrorMessageFormat, typeName));
    if (key.isValidMembersInjectionKey()
        && !injectBindingRegistry.getOrFindMembersInjectionBinding(key).injectionSites()
            .isEmpty()) {
      errorMessage.append(" ").append(ErrorMessages.MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION);
    }
    ImmutableList<String> printableDependencyPath =
        FluentIterable.from(path)
            .transform(REQUEST_FROM_RESOLVED_REQUEST)
            .transform(dependencyRequestFormatter)
            .filter(Predicates.not(Predicates.equalTo("")))
            .toList()
            .reverse();
    for (String dependency :
        printableDependencyPath.subList(1, printableDependencyPath.size())) {
      errorMessage.append("\n").append(dependency);
    }
    reportBuilder.addItem(errorMessage.toString(), path.getLast().request().requestElement());
  }

  private boolean doesPathRequireProvisionOnly(Deque<ResolvedRequest> path) {
    if (path.size() == 1) {
      switch (path.peek().request().kind()) {
        case INSTANCE:
        case PROVIDER:
        case LAZY:
        case MEMBERS_INJECTOR:
          return true;
        case PRODUCER:
        case PRODUCED:
        case FUTURE:
          return false;
        default:
          throw new AssertionError();
      }
    }
    ImmutableSet<ProvisionBinding> dependentProvisions = provisionsDependingOnLatestRequest(path);
    return !dependentProvisions.isEmpty();
  }

  private ImmutableSet<ProvisionBinding> provisionsDependingOnLatestRequest(
      Deque<ResolvedRequest> path) {
    Iterator<ResolvedRequest> iterator = path.iterator();
    final DependencyRequest request = iterator.next().request();
    ResolvedRequest previousResolvedRequest = iterator.next();
    @SuppressWarnings("unchecked")
    ImmutableSet<ProvisionBinding> bindings = (ImmutableSet<ProvisionBinding>) FluentIterable
        .from(previousResolvedRequest.binding().bindings())
        .filter(new Predicate<Binding>() {
            @Override public boolean apply(Binding binding) {
              return binding instanceof ProvisionBinding
                  && binding.implicitDependencies().contains(request);
            }
        }).toSet();
    return bindings;
  }

  private static final int DUPLICATE_SIZE_LIMIT = 10;

  @SuppressWarnings("resource")
  private void reportDuplicateBindings(
      Deque<ResolvedRequest> path, ValidationReport.Builder<BindingGraph> reportBuilder) {
    ResolvedBindings resolvedBinding = path.peek().binding();
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.DUPLICATE_BINDINGS_FOR_KEY_FORMAT,
        keyFormatter.format(path.peek().request().key()));
    for (Binding binding : Iterables.limit(resolvedBinding.bindings(), DUPLICATE_SIZE_LIMIT)) {
      builder.append('\n').append(INDENT);
      if (binding instanceof ProvisionBinding) {
        builder.append(provisionBindingFormatter.format((ProvisionBinding) binding));
      } else if (binding instanceof ProductionBinding) {
        builder.append(productionBindingFormatter.format((ProductionBinding) binding));
      }
    }
    int numberOfOtherBindings = resolvedBinding.bindings().size() - DUPLICATE_SIZE_LIMIT;
    if (numberOfOtherBindings > 0) {
      builder.append('\n').append(INDENT)
          .append("and ").append(numberOfOtherBindings).append(" other");
    }
    if (numberOfOtherBindings > 1) {
      builder.append('s');
    }
    reportBuilder.addItem(builder.toString(), path.getLast().request().requestElement());
  }

  @SuppressWarnings("resource")
  private void reportMultipleBindingTypes(
      Deque<ResolvedRequest> path, ValidationReport.Builder<BindingGraph> reportBuilder) {
    ResolvedBindings resolvedBinding = path.peek().binding();
    StringBuilder builder = new StringBuilder();
    new Formatter(builder).format(ErrorMessages.MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT,
        keyFormatter.format(path.peek().request().key()));
    ImmutableListMultimap<BindingType, ContributionBinding> bindingsByType =
        ContributionBinding.bindingTypesFor(resolvedBinding.contributionBindings());
    for (BindingType type :
        Ordering.natural().immutableSortedCopy(bindingsByType.keySet())) {
      builder.append(INDENT);
      builder.append(formatBindingType(type));
      builder.append(" bindings:\n");
      for (ContributionBinding binding : bindingsByType.get(type)) {
        builder.append(INDENT).append(INDENT);
        if (binding instanceof ProvisionBinding) {
          builder.append(provisionBindingFormatter.format((ProvisionBinding) binding));
        } else if (binding instanceof ProductionBinding) {
          builder.append(productionBindingFormatter.format((ProductionBinding) binding));
        }
        builder.append('\n');
      }
    }
    reportBuilder.addItem(builder.toString(), path.getLast().request().requestElement());
  }

  private String formatBindingType(BindingType type) {
    switch(type) {
      case MAP:
        return "Map";
      case SET:
        return "Set";
      case UNIQUE:
        return "Unique";
      default:
        throw new IllegalStateException("Unknown binding type: " + type);
    }
  }

  private void reportCycle(DependencyRequest request, Deque<ResolvedRequest> path,
      final ValidationReport.Builder<BindingGraph> reportBuilder) {
    ImmutableList<DependencyRequest> pathElements = ImmutableList.<DependencyRequest>builder()
        .add(request)
        .addAll(Iterables.transform(path, REQUEST_FROM_RESOLVED_REQUEST))
        .build();
    ImmutableList<String> printableDependencyPath = FluentIterable.from(pathElements)
        .transform(dependencyRequestFormatter)
        .filter(Predicates.not(Predicates.equalTo("")))
        .toList()
        .reverse();
    DependencyRequest rootRequest = path.getLast().request();
    TypeElement componentType =
        MoreElements.asType(rootRequest.requestElement().getEnclosingElement());
    reportBuilder.addItem(
        String.format(ErrorMessages.CONTAINS_DEPENDENCY_CYCLE_FORMAT,
            componentType.getQualifiedName(),
            rootRequest.requestElement().getSimpleName(),
            Joiner.on("\n")
                .join(printableDependencyPath.subList(1, printableDependencyPath.size()))),
        rootRequest.requestElement());
  }

  @AutoValue
  abstract static class ResolvedRequest {
    abstract DependencyRequest request();
    abstract ResolvedBindings binding();

    static ResolvedRequest create(DependencyRequest request, BindingGraph graph) {
      BindingKey bindingKey = request.bindingKey();
      ResolvedBindings resolvedBindings = graph.resolvedBindings().get(bindingKey);
      return new AutoValue_BindingGraphValidator_ResolvedRequest(request,
          resolvedBindings == null
              ? ResolvedBindings.create(bindingKey,
                  ImmutableSet.<Binding>of(), ImmutableSet.<Binding>of())
              : resolvedBindings);
    }
  }

  private static final Function<ResolvedRequest, DependencyRequest> REQUEST_FROM_RESOLVED_REQUEST =
      new Function<ResolvedRequest, DependencyRequest>() {
        @Override public DependencyRequest apply(ResolvedRequest resolvedRequest) {
          return resolvedRequest.request();
        }
      };

  abstract static class Traverser {
    abstract boolean visitResolvedRequest(Deque<ResolvedRequest> path);
  }
}
