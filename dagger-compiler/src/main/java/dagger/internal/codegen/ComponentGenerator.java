package dagger.internal.codegen;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import dagger.Component;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class ComponentGenerator extends JavaPoetSourceFileGenerator<BindingGraph> {
  private final Types types;
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final CompilerOptions compilerOptions;

  ComponentGenerator(
      Filer filer,
      Elements elements,
      Types types,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions) {
    super(filer, elements);
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
  }

  @Override
  ClassName nameGeneratedType(BindingGraph input) {
    ClassName componentDefinitionClassName =
        ClassName.get(input.componentDescriptor().componentDefinitionType());
    String componentName =
        "Dagger" + Joiner.on('_').join(componentDefinitionClassName.simpleNames());
    return componentDefinitionClassName.topLevelClassName().peerClass(componentName);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(BindingGraph input) {
    return Optional.of(input.componentDescriptor().componentDefinitionType());
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName componentName, BindingGraph input) {
    return Optional.of(
        new ComponentWriter(types, elements, keyFactory, compilerOptions, componentName, input)
            .write());
  }
}
