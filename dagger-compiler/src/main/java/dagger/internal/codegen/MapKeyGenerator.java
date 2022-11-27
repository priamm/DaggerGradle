package dagger.internal.codegen;

import com.google.auto.value.AutoAnnotation;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import dagger.MapKey;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.MethodWriter;
import dagger.internal.codegen.writer.TypeWriter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Generated;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

final class MapKeyGenerator extends SourceFileGenerator<Element> {
  MapKeyGenerator(Filer filer) {
    super(filer);
  }

  @Override
  ClassName nameGeneratedType(Element e) {
    ClassName enclosingClassName = ClassName.fromTypeElement((TypeElement)e);
    return enclosingClassName.topLevelClassName().peerNamed(
        enclosingClassName.classFileName() + "Creator");
  }

  @Override
  Iterable<? extends Element> getOriginatingElements(Element e) {
    return ImmutableSet.of(e);
  }

  @Override
  Optional<? extends Element> getElementForErrorReporting(Element e) {
    return Optional.of(e);
  }

  @Override
  ImmutableSet<JavaWriter> write(ClassName generatedTypeName, Element e) {
    JavaWriter writer = JavaWriter.inPackage(generatedTypeName.packageName());
    TypeWriter mapKeyWriter = writer.addClass(generatedTypeName.simpleName());
    mapKeyWriter.annotate(Generated.class).setValue(ComponentProcessor.class.getName());
    mapKeyWriter.addModifiers(PUBLIC);

    MethodWriter getMethodWriter = mapKeyWriter.addMethod(e.asType(), "create");
    List<? extends Element> enclosingElements = e.getEnclosedElements();
    List<String> paraList = new ArrayList<String>();

    getMethodWriter.annotate(AutoAnnotation.class);
    getMethodWriter.addModifiers(PUBLIC, STATIC);

    for (Element element : enclosingElements) {
      if (element instanceof ExecutableElement) {
        ExecutableElement executableElement = (ExecutableElement) element;
        Name parameterName = executableElement.getSimpleName();
        getMethodWriter.addParameter(
            (TypeElement) ((DeclaredType) (executableElement.getReturnType())).asElement(),
            parameterName.toString());
        paraList.add(parameterName.toString());
      } else {
        throw new IllegalStateException();
      }
    }

    getMethodWriter.body().addSnippet(
        "return new AutoAnnotation_" + generatedTypeName.simpleName() + "_create(%s);",
        Joiner.on(", ").join(paraList));

    return ImmutableSet.of(writer);
  }
}
