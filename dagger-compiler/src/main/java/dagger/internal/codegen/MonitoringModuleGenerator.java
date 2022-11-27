package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;
import dagger.Module;
import dagger.Provides;
import dagger.producers.monitoring.ProductionComponentMonitor;
import dagger.producers.monitoring.internal.MonitorCache;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.AnnotationSpecs.PROVIDES_SET_VALUES;
import static dagger.internal.codegen.TypeNames.SET_OF_FACTORIES;
import static dagger.internal.codegen.TypeNames.providerOf;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.FINAL;

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
            .addModifiers(PUBLIC, FINAL)

            .addMethod(
                methodBuilder("defaultSetOfFactories")
                    .returns(SET_OF_FACTORIES)
                    .addModifiers(STATIC)
                    .addAnnotation(PROVIDES_SET_VALUES)
                    .addStatement("return $T.of()", ClassName.get(ImmutableSet.class))
                    .build())
            .addField(
                FieldSpec.builder(MonitorCache.class, "monitorCache", PRIVATE, FINAL)
                    .initializer("new $T()", MonitorCache.class)
                    .build())
            .addMethod(
                methodBuilder("monitor")
                    .returns(ProductionComponentMonitor.class)
                    .addAnnotation(Provides.class)
                    .addParameter(providerOf(ClassName.get(componentElement.asType())), "component")
                    .addParameter(providerOf(SET_OF_FACTORIES), "factories")
                    .addStatement("return monitorCache.monitor(component, factories)")
                    .build()));
  }
}
