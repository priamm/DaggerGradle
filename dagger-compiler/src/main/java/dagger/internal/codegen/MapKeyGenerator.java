package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoAnnotation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.MapKey;
import dagger.internal.codegen.MapKeyGenerator.MapKeyCreatorSpecification;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.MapKeys.getMapKeyCreatorClassName;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.methodsIn;

final class MapKeyGenerator extends JavaPoetSourceFileGenerator<MapKeyCreatorSpecification> {

  @AutoValue
  abstract static class MapKeyCreatorSpecification {

    abstract TypeElement mapKeyElement();

    abstract TypeElement annotationElement();

    static MapKeyCreatorSpecification wrappedMapKey(TypeElement mapKeyElement) {
      return new AutoValue_MapKeyGenerator_MapKeyCreatorSpecification(mapKeyElement, mapKeyElement);
    }

    static MapKeyCreatorSpecification unwrappedMapKeyWithAnnotationValue(
        TypeElement mapKeyElement, TypeElement annotationElement) {
      return new AutoValue_MapKeyGenerator_MapKeyCreatorSpecification(
          mapKeyElement, annotationElement);
    }
  }

  MapKeyGenerator(Filer filer, Elements elements) {
    super(filer, elements);
  }

  @Override
  ClassName nameGeneratedType(MapKeyCreatorSpecification mapKeyCreatorType) {
    return getMapKeyCreatorClassName(mapKeyCreatorType.mapKeyElement());
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(
      MapKeyCreatorSpecification mapKeyCreatorType) {
    return Optional.of(mapKeyCreatorType.mapKeyElement());
  }

  @Override
  Optional<TypeSpec.Builder> write(
      ClassName generatedTypeName, MapKeyCreatorSpecification mapKeyCreatorType) {
    TypeSpec.Builder mapKeyCreatorBuilder =
        classBuilder(generatedTypeName.simpleName()).addModifiers(PUBLIC, FINAL);

    mapKeyCreatorBuilder.addMethod(constructorBuilder().addModifiers(PRIVATE).build());

    for (TypeElement annotationElement :
        nestedAnnotationElements(mapKeyCreatorType.annotationElement())) {
      mapKeyCreatorBuilder.addMethod(buildCreateMethod(generatedTypeName, annotationElement));
    }

    return Optional.of(mapKeyCreatorBuilder);
  }

  private MethodSpec buildCreateMethod(
      ClassName mapKeyGeneratedTypeName, TypeElement annotationElement) {
    String createMethodName = "create" + annotationElement.getSimpleName();
    MethodSpec.Builder createMethod =
        methodBuilder(createMethodName)
            .addAnnotation(AutoAnnotation.class)
            .addModifiers(PUBLIC, STATIC)
            .returns(TypeName.get(annotationElement.asType()));

    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (ExecutableElement annotationMember : methodsIn(annotationElement.getEnclosedElements())) {
      String parameterName = annotationMember.getSimpleName().toString();
      TypeName parameterType = TypeName.get(annotationMember.getReturnType());
      createMethod.addParameter(parameterType, parameterName);
      parameters.add(CodeBlocks.format("$L", parameterName));
    }

    ClassName autoAnnotationClass = mapKeyGeneratedTypeName.peerClass(
        "AutoAnnotation_" + mapKeyGeneratedTypeName.simpleName() + "_" + createMethodName);
    createMethod.addStatement(
        "return new $T($L)", autoAnnotationClass, makeParametersCodeBlock(parameters.build()));
    return createMethod.build();
  }

  private static Set<TypeElement> nestedAnnotationElements(TypeElement annotationElement) {
    return nestedAnnotationElements(annotationElement, new LinkedHashSet<TypeElement>());
  }

  @CanIgnoreReturnValue
  private static Set<TypeElement> nestedAnnotationElements(
      TypeElement annotationElement, Set<TypeElement> annotationElements) {
    if (annotationElements.add(annotationElement)) {
      for (ExecutableElement method : methodsIn(annotationElement.getEnclosedElements())) {
        TRAVERSE_NESTED_ANNOTATIONS.visit(method.getReturnType(), annotationElements);
      }
    }
    return annotationElements;
  }

  private static final SimpleTypeVisitor6<Void, Set<TypeElement>> TRAVERSE_NESTED_ANNOTATIONS =
      new SimpleTypeVisitor6<Void, Set<TypeElement>>() {
        @Override
        public Void visitDeclared(DeclaredType t, Set<TypeElement> p) {
          TypeElement typeElement = MoreTypes.asTypeElement(t);
          if (typeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
            nestedAnnotationElements(typeElement, p);
          }
          return null;
        }
      };
}
