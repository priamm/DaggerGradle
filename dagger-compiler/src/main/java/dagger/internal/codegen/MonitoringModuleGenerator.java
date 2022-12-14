package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Multibindings;
import dagger.Provides;
import dagger.producers.ProductionScope;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.Monitors;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.TypeNames.SET_OF_FACTORIES;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class MonitoringModuleGenerator extends JavaPoetSourceFileGenerator<TypeElement> {

  MonitoringModuleGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(TypeElement componentElement) {
    return SourceFiles.generatedMonitoringModuleName(componentElement);
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
            .addType(
                TypeSpec.interfaceBuilder("DefaultSetOfFactories")
                    .addAnnotation(Multibindings.class)
                    .addMethod(
                        MethodSpec.methodBuilder("setOfFactories")
                            .addModifiers(PUBLIC, ABSTRACT)
                            .returns(SET_OF_FACTORIES)
                            .build())
                    .build())
            .addMethod(
                methodBuilder("monitor")
                    .returns(ProductionComponentMonitor.class)
                    .addModifiers(STATIC)
                    .addAnnotation(Provides.class)
                    .addAnnotation(ProductionScope.class)
                    .addParameter(providerOf(ClassName.get(componentElement.asType())), "component")
                    .addParameter(providerOf(SET_OF_FACTORIES), "factories")
                    .addStatement(
                        "return $T.createMonitorForComponent(component, factories)", Monitors.class)
                    .build()));
  }
}
