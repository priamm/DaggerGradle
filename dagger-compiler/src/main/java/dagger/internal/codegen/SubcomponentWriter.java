package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.Preconditions;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Sets.difference;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

final class SubcomponentWriter extends AbstractComponentWriter {

  private AbstractComponentWriter parent;
  private ExecutableElement subcomponentFactoryMethod;

  public SubcomponentWriter(
      AbstractComponentWriter parent,
      ExecutableElement subcomponentFactoryMethod,
      BindingGraph subgraph) {
    super(
        parent.types,
        parent.elements,
        parent.keyFactory,
        parent.compilerOptions,
        subcomponentName(parent, subgraph),
        subgraph,
        parent.subcomponentNames);
    this.parent = parent;
    this.subcomponentFactoryMethod = subcomponentFactoryMethod;
  }

  private static ClassName subcomponentName(AbstractComponentWriter parent, BindingGraph subgraph) {
    return parent.name.nestedClass(
        parent.subcomponentNames.get(subgraph.componentDescriptor()) + "Impl");
  }

  @Override
  protected InitializationState getInitializationState(BindingKey bindingKey) {
    InitializationState initializationState = super.getInitializationState(bindingKey);
    return initializationState.equals(UNINITIALIZED)
        ? parent.getInitializationState(bindingKey)
        : initializationState;
  }

  @Override
  protected Optional<CodeBlock> getOrCreateComponentContributionFieldExpression(
      TypeElement contributionType) {
    return super.getOrCreateComponentContributionFieldExpression(contributionType)
        .or(parent.getOrCreateComponentContributionFieldExpression(contributionType));
  }

  @Override
  protected MemberSelect getMemberSelect(BindingKey key) {
    MemberSelect memberSelect = super.getMemberSelect(key);
    return memberSelect == null ? parent.getMemberSelect(key) : memberSelect;
  }

  private ExecutableType resolvedSubcomponentFactoryMethod() {
    return MoreTypes.asExecutable(
        types.asMemberOf(
            MoreTypes.asDeclared(parent.componentDefinitionType().asType()),
            subcomponentFactoryMethod));
  }

  @Override
  protected TypeSpec.Builder createComponentClass() {
    TypeSpec.Builder subcomponent = classBuilder(name.simpleName()).addModifiers(PRIVATE, FINAL);

    addSupertype(
        subcomponent,
        MoreTypes.asTypeElement(
            graph.componentDescriptor().builderSpec().isPresent()
                ? graph.componentDescriptor().builderSpec().get().componentType()
                : resolvedSubcomponentFactoryMethod().getReturnType()));
    return subcomponent;
  }

  @Override
  protected void addBuilder() {
    if (graph.componentDescriptor().builderSpec().isPresent()) {
      super.addBuilder();
    }
  }

  @Override
  protected ClassName builderName() {
    return name.peerClass(subcomponentNames.get(graph.componentDescriptor()) + "Builder");
  }

  @Override
  protected TypeSpec.Builder createBuilder(String builderSimpleName) {
    verify(graph.componentDescriptor().builderSpec().isPresent());
    return classBuilder(builderSimpleName);
  }

  @Override
  protected void addBuilderClass(TypeSpec builder) {
    parent.component.addType(builder);
  }

  @Override
  protected void addFactoryMethods() {
    MethodSpec.Builder componentMethod =
        methodBuilder(subcomponentFactoryMethod.getSimpleName().toString())
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class);
    if (graph.componentDescriptor().builderSpec().isPresent()) {
      BuilderSpec spec = graph.componentDescriptor().builderSpec().get();
      componentMethod
          .returns(ClassName.get(spec.builderDefinitionType()))
          .addStatement("return new $T()", builderName.get());
    } else {
      ExecutableType resolvedMethod = resolvedSubcomponentFactoryMethod();
      componentMethod.returns(ClassName.get(resolvedMethod.getReturnType()));
      writeSubcomponentWithoutBuilder(componentMethod, resolvedMethod);
    }
    parent.component.addMethod(componentMethod.build());
  }

  private void writeSubcomponentWithoutBuilder(
      MethodSpec.Builder componentMethod, ExecutableType resolvedMethod) {
    ImmutableList.Builder<CodeBlock> subcomponentConstructorParameters = ImmutableList.builder();
    List<? extends VariableElement> params = subcomponentFactoryMethod.getParameters();
    List<? extends TypeMirror> paramTypes = resolvedMethod.getParameterTypes();
    for (int i = 0; i < params.size(); i++) {
      VariableElement moduleVariable = params.get(i);
      TypeElement moduleTypeElement = MoreTypes.asTypeElement(paramTypes.get(i));
      TypeName moduleType = TypeName.get(paramTypes.get(i));
      componentMethod.addParameter(moduleType, moduleVariable.getSimpleName().toString());
      if (!componentContributionFields.containsKey(moduleTypeElement)) {
        String preferredModuleName =
            CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleTypeElement.getSimpleName().toString());
        FieldSpec contributionField =
            componentField(ClassName.get(moduleTypeElement), preferredModuleName)
                .addModifiers(PRIVATE, FINAL)
                .build();
        component.addField(contributionField);

        String actualModuleName = contributionField.name;
        constructor
            .addParameter(moduleType, actualModuleName)
            .addStatement(
                "this.$1L = $2T.checkNotNull($1L)",
                actualModuleName,
                Preconditions.class);

        MemberSelect moduleSelect = localField(name, actualModuleName);
        componentContributionFields.put(moduleTypeElement, moduleSelect);
        subcomponentConstructorParameters.add(
            CodeBlocks.format("$L", moduleVariable.getSimpleName()));
      }
    }

    Set<TypeElement> uninitializedModules =
        difference(graph.componentRequirements(), componentContributionFields.keySet());

    for (TypeElement moduleType : uninitializedModules) {
      String preferredModuleName =
          CaseFormat.UPPER_CAMEL.to(LOWER_CAMEL, moduleType.getSimpleName().toString());
      FieldSpec contributionField =
          componentField(ClassName.get(moduleType), preferredModuleName)
              .addModifiers(PRIVATE, FINAL)
              .build();
      component.addField(contributionField);
      String actualModuleName = contributionField.name;
      constructor.addStatement(
          "this.$L = new $T()", actualModuleName, ClassName.get(moduleType));
      MemberSelect moduleSelect = localField(name, actualModuleName);
      componentContributionFields.put(moduleType, moduleSelect);
    }

    componentMethod.addStatement("return new $T($L)",
        name, makeParametersCodeBlock(subcomponentConstructorParameters.build()));
  }
}
