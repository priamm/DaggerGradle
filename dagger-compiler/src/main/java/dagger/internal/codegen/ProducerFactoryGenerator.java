package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.producers.Producer;
import dagger.producers.Produces;
import dagger.producers.Produces.Type;
import dagger.producers.monitoring.ProducerMonitor;
import java.util.List;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.squareup.javapoet.ClassName.OBJECT;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.CodeBlocks.toCodeBlocks;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.TypeNames.ASYNC_FUNCTION;
import static dagger.internal.codegen.TypeNames.FUTURES;
import static dagger.internal.codegen.TypeNames.IMMUTABLE_SET;
import static dagger.internal.codegen.TypeNames.PRODUCERS;
import static dagger.internal.codegen.TypeNames.PRODUCER_TOKEN;
import static dagger.internal.codegen.TypeNames.VOID_CLASS;
import static dagger.internal.codegen.TypeNames.abstractProducerOf;
import static dagger.internal.codegen.TypeNames.listOf;
import static dagger.internal.codegen.TypeNames.listenableFutureOf;
import static dagger.internal.codegen.TypeNames.producedOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class ProducerFactoryGenerator extends JavaPoetSourceFileGenerator<ProductionBinding> {
  private final CompilerOptions compilerOptions;

  ProducerFactoryGenerator(Filer filer, Elements elements, CompilerOptions compilerOptions) {
    super(filer, elements);
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(ProductionBinding binding) {
    return generatedClassNameForBinding(binding);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(ProductionBinding binding) {
    return Optional.of(binding.bindingElement());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, ProductionBinding binding) {
    TypeMirror keyType =
        binding.productionType().equals(Type.MAP)
            ? MapType.from(binding.key().type()).unwrappedValueType(Producer.class)
            : binding.key().type();
    TypeName providedTypeName = TypeName.get(keyType);
    TypeName futureTypeName = listenableFutureOf(providedTypeName);

    TypeSpec.Builder factoryBuilder =
        classBuilder(generatedTypeName.simpleName())
            .addModifiers(PUBLIC, FINAL)
            .superclass(abstractProducerOf(providedTypeName));

    ImmutableMap<BindingKey, FrameworkField> fields =
        SourceFiles.generateBindingFieldsForDependencies(binding);

    MethodSpec.Builder constructorBuilder =
        constructorBuilder()
            .addModifiers(PUBLIC)
            .addStatement(
                "super($L, $L)",
                fields.get(binding.monitorRequest().get().bindingKey()).name(),
                producerTokenConstruction(generatedTypeName, binding));

    if (!binding.bindingElement().getModifiers().contains(STATIC)) {
      TypeName moduleType = TypeName.get(binding.bindingTypeElement().asType());
      addFieldAndConstructorParameter(factoryBuilder, constructorBuilder, "module", moduleType);
    }

    for (FrameworkField bindingField : fields.values()) {
      TypeName fieldType = bindingField.frameworkType();
      addFieldAndConstructorParameter(
          factoryBuilder, constructorBuilder, bindingField.name(), fieldType);
    }

    MethodSpec.Builder computeMethodBuilder =
        methodBuilder("compute")
            .returns(futureTypeName)
            .addAnnotation(Override.class)
            .addModifiers(PROTECTED)
            .addParameter(ProducerMonitor.class, "monitor", FINAL);

    ImmutableList<DependencyRequest> asyncDependencies = asyncDependencies(binding);
    for (DependencyRequest dependency : asyncDependencies) {
      TypeName futureType = listenableFutureOf(asyncDependencyType(dependency));
      CodeBlock futureAccess =
          CodeBlocks.format("$L.get()", fields.get(dependency.bindingKey()).name());
      computeMethodBuilder.addStatement(
          "$T $L = $L",
          futureType,
          dependencyFutureName(dependency),
          dependency.kind().equals(DependencyRequest.Kind.PRODUCED)
              ? CodeBlocks.format("$T.createFutureProduced($L)", PRODUCERS, futureAccess)
              : futureAccess);
    }
    FutureTransform futureTransform = FutureTransform.create(fields, binding, asyncDependencies);
    CodeBlock transformCodeBlock =
        CodeBlocks.format(
            Joiner.on('\n')
                .join(
                    "new $1T<$2T, $3T>() {",
                    "  $4L",
                    "  @Override public $5T apply($2T $6L) $7L {",
                    "    $8L",
                    "  }",
                    "}"),
            ASYNC_FUNCTION,
            futureTransform.applyArgType(),
            providedTypeName,
            futureTransform.hasUncheckedCast()
                ? CodeBlocks.format("$L // safe by specification", SUPPRESS_WARNINGS_UNCHECKED)
                : "",
            futureTypeName,
            futureTransform.applyArgName(),
            getThrowsClause(binding.thrownTypes()),
            getInvocationCodeBlock(
                generatedTypeName,
                binding,
                providedTypeName,
                futureTransform.parameterCodeBlocks()));
    computeMethodBuilder.addStatement(
        "return $T.transformAsync($L, $L, executorProvider.get())",
        FUTURES,
        futureTransform.futureCodeBlock(),
        transformCodeBlock);

    factoryBuilder.addMethod(constructorBuilder.build());
    factoryBuilder.addMethod(computeMethodBuilder.build());

    return Optional.of(factoryBuilder);
  }

  private static void addFieldAndConstructorParameter(
      TypeSpec.Builder typeBuilder,
      MethodSpec.Builder constructorBuilder,
      String variableName,
      TypeName variableType) {
    typeBuilder.addField(variableType, variableName, PRIVATE, FINAL);
    constructorBuilder
        .addParameter(variableType, variableName)
        .addStatement("assert $L != null", variableName)
        .addStatement("this.$1L = $1L", variableName);
  }

  private static ImmutableList<DependencyRequest> asyncDependencies(Binding binding) {
    final ImmutableMap<DependencyRequest, FrameworkDependency> frameworkDependencies =
        FrameworkDependency.indexByDependencyRequest(
            FrameworkDependency.frameworkDependenciesForBinding(binding));
    return FluentIterable.from(binding.implicitDependencies())
        .filter(
            new Predicate<DependencyRequest>() {
              @Override
              public boolean apply(DependencyRequest dependency) {
                return isAsyncDependency(dependency)
                    && frameworkDependencies
                        .get(dependency)
                        .frameworkClass()
                        .equals(Producer.class);
              }
            })
        .toList();
  }

  private CodeBlock producerTokenConstruction(
      ClassName generatedTypeName, ProductionBinding binding) {
    CodeBlock producerTokenArgs =
        compilerOptions.writeProducerNameInToken()
            ? CodeBlocks.format(
                "$S",
                String.format(
                    "%s#%s",
                    ClassName.get(binding.bindingTypeElement()),
                    binding.bindingElement().getSimpleName()))
            : CodeBlocks.format("$T.class", generatedTypeName);
    return CodeBlocks.format("$T.create($L)", PRODUCER_TOKEN, producerTokenArgs);
  }

  private static String dependencyFutureName(DependencyRequest dependency) {
    return dependency.requestElement().getSimpleName() + "Future";
  }

  abstract static class FutureTransform {
    protected final ImmutableMap<BindingKey, FrameworkField> fields;
    protected final ProductionBinding binding;

    FutureTransform(ImmutableMap<BindingKey, FrameworkField> fields, ProductionBinding binding) {
      this.fields = fields;
      this.binding = binding;
    }

    abstract CodeBlock futureCodeBlock();

    abstract TypeName applyArgType();

    abstract String applyArgName();

    abstract ImmutableList<CodeBlock> parameterCodeBlocks();

    boolean hasUncheckedCast() {
      return false;
    }

    static FutureTransform create(
        ImmutableMap<BindingKey, FrameworkField> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      if (asyncDependencies.isEmpty()) {
        return new NoArgFutureTransform(fields, binding);
      } else if (asyncDependencies.size() == 1) {
        return new SingleArgFutureTransform(
            fields, binding, Iterables.getOnlyElement(asyncDependencies));
      } else {
        return new MultiArgFutureTransform(fields, binding, asyncDependencies);
      }
    }
  }

  static final class NoArgFutureTransform extends FutureTransform {
    NoArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields, ProductionBinding binding) {
      super(fields, binding);
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlocks.format("$T.<$T>immediateFuture(null)", FUTURES, VOID_CLASS);
    }

    @Override
    TypeName applyArgType() {
      return VOID_CLASS;
    }

    @Override
    String applyArgName() {
      return "ignoredVoidArg";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        parameterCodeBlocks.add(
            frameworkTypeUsageStatement(
                CodeBlocks.format("$L", fields.get(dependency.bindingKey()).name()),
                dependency.kind()));
      }
      return parameterCodeBlocks.build();
    }
  }

  static final class SingleArgFutureTransform extends FutureTransform {
    private final DependencyRequest asyncDependency;

    SingleArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields,
        ProductionBinding binding,
        DependencyRequest asyncDependency) {
      super(fields, binding);
      this.asyncDependency = asyncDependency;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlocks.format("$L", dependencyFutureName(asyncDependency));
    }

    @Override
    TypeName applyArgType() {
      return asyncDependencyType(asyncDependency);
    }

    @Override
    String applyArgName() {
      return asyncDependency.requestElement().getSimpleName().toString();
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      ImmutableList.Builder<CodeBlock> parameterCodeBlocks = ImmutableList.builder();
      for (DependencyRequest dependency : binding.dependencies()) {
        if (dependency == asyncDependency) {
          parameterCodeBlocks.add(CodeBlocks.format("$L", applyArgName()));
        } else {
          parameterCodeBlocks.add(
              frameworkTypeUsageStatement(
                  CodeBlocks.format("$L", fields.get(dependency.bindingKey()).name()),
                  dependency.kind()));
        }
      }
      return parameterCodeBlocks.build();
    }
  }

  static final class MultiArgFutureTransform extends FutureTransform {
    private final ImmutableList<DependencyRequest> asyncDependencies;

    MultiArgFutureTransform(
        ImmutableMap<BindingKey, FrameworkField> fields,
        ProductionBinding binding,
        ImmutableList<DependencyRequest> asyncDependencies) {
      super(fields, binding);
      this.asyncDependencies = asyncDependencies;
    }

    @Override
    CodeBlock futureCodeBlock() {
      return CodeBlocks.format(
          "$T.<$T>allAsList($L)",
          FUTURES,
          OBJECT,
          makeParametersCodeBlock(
              FluentIterable.from(asyncDependencies)
                  .transform(
                      new Function<DependencyRequest, CodeBlock>() {
                        @Override
                        public CodeBlock apply(DependencyRequest dependency) {
                          return CodeBlocks.format("$L", dependencyFutureName(dependency));
                        }
                      })));
    }

    @Override
    TypeName applyArgType() {
      return listOf(OBJECT);
    }

    @Override
    String applyArgName() {
      return "args";
    }

    @Override
    ImmutableList<CodeBlock> parameterCodeBlocks() {
      return getParameterCodeBlocks(binding, fields, applyArgName());
    }

    @Override
    boolean hasUncheckedCast() {
      return true;
    }
  }

  private static boolean isAsyncDependency(DependencyRequest dependency) {
    switch (dependency.kind()) {
      case INSTANCE:
      case PRODUCED:
        return true;
      default:
        return false;
    }
  }

  private static TypeName asyncDependencyType(DependencyRequest dependency) {
    TypeName keyName = TypeName.get(dependency.key().type());
    switch (dependency.kind()) {
      case INSTANCE:
        return keyName;
      case PRODUCED:
        return producedOf(keyName);
      default:
        throw new AssertionError();
    }
  }

  private static ImmutableList<CodeBlock> getParameterCodeBlocks(
      ProductionBinding binding,
      ImmutableMap<BindingKey, FrameworkField> fields,
      String listArgName) {
    int argIndex = 0;
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    for (DependencyRequest dependency : binding.dependencies()) {
      if (isAsyncDependency(dependency)) {
        codeBlocks.add(
            CodeBlocks.format(
                "($T) $L.get($L)", asyncDependencyType(dependency), listArgName, argIndex));
        argIndex++;
      } else {
        codeBlocks.add(
            frameworkTypeUsageStatement(
                CodeBlocks.format("$L", fields.get(dependency.bindingKey()).name()),
                dependency.kind()));
      }
    }
    return codeBlocks.build();
  }

  private CodeBlock getInvocationCodeBlock(
      ClassName generatedTypeName,
      ProductionBinding binding,
      TypeName providedTypeName,
      ImmutableList<CodeBlock> parameterCodeBlocks) {
    CodeBlock moduleCodeBlock =
        CodeBlocks.format(
            "$L.$L($L)",
            binding.bindingElement().getModifiers().contains(STATIC)
                ? CodeBlocks.format("$T", ClassName.get(binding.bindingTypeElement()))
                : CodeBlocks.format("$T.this.module", generatedTypeName),
            binding.bindingElement().getSimpleName(),
            makeParametersCodeBlock(parameterCodeBlocks));

    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    codeBlocks.add(CodeBlocks.format("monitor.methodStarting();"));

    final CodeBlock valueCodeBlock;
    if (binding.productionType().equals(Produces.Type.SET)) {
      if (binding.bindingKind().equals(ContributionBinding.Kind.FUTURE_PRODUCTION)) {
        valueCodeBlock =
            CodeBlocks.format("$T.createFutureSingletonSet($L)", PRODUCERS, moduleCodeBlock);
      } else {
        valueCodeBlock = CodeBlocks.format("$T.of($L)", IMMUTABLE_SET, moduleCodeBlock);
      }
    } else {
      valueCodeBlock = moduleCodeBlock;
    }
    CodeBlock returnCodeBlock =
        binding.bindingKind().equals(ContributionBinding.Kind.FUTURE_PRODUCTION)
            ? valueCodeBlock
            : CodeBlocks.format(
                "$T.<$T>immediateFuture($L)", FUTURES, providedTypeName, valueCodeBlock);
    return CodeBlocks.format(
        Joiner.on('\n')
            .join(
                "monitor.methodStarting();",
                "try {",
                "  return $L;",
                "} finally {",
                "  monitor.methodFinished();",
                "}"),
        returnCodeBlock);
  }

  private CodeBlock getThrowsClause(List<? extends TypeMirror> thrownTypes) {
    if (thrownTypes.isEmpty()) {
      return CodeBlocks.format("");
    }
    return CodeBlocks.format("throws $L", makeParametersCodeBlock(toCodeBlocks(thrownTypes)));
  }
}
