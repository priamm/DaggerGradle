package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Provides;
import dagger.producers.Production;
import dagger.producers.ProductionScope;
import dagger.producers.internal.ProductionImplementation;

import java.util.concurrent.Executor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.FINAL;

final class ProductionExecutorModuleGenerator extends JavaPoetSourceFileGenerator<TypeElement> {

  ProductionExecutorModuleGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(TypeElement componentElement) {
    return SourceFiles.generatedProductionExecutorModuleName(componentElement);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(TypeElement componentElement) {
    return Optional.of(componentElement);
  }

  @Override
  Optional<TypeSpec.Builder> write(ClassName generatedTypeName, TypeElement componentElement) {
    return Optional.of(
        classBuilder(generatedTypeName.simpleName())
            .addAnnotation(
                AnnotationSpec.builder(Module.class)
                    .build())
            .addModifiers(FINAL)
            .addMethod(
                methodBuilder("executor")
                    .returns(Executor.class)
                    .addModifiers(STATIC)
                    .addAnnotation(Provides.class)
                    .addAnnotation(ProductionScope.class)
                    .addAnnotation(ProductionImplementation.class)
                    .addParameter(
                        ParameterSpec.builder(Executor.class, "executor")
                            .addAnnotation(Production.class)
                            .build())
                    .addStatement("return executor")
                    .build()));
  }
}
