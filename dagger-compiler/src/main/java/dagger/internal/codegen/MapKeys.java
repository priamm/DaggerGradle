package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import dagger.MapKey;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.AnnotationMirrors.getAnnotatedAnnotations;
import static com.google.auto.common.AnnotationMirrors.getAnnotationValuesWithDefaults;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.collect.Iterables.transform;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.SourceFiles.canonicalName;
import static javax.lang.model.util.ElementFilter.methodsIn;

final class MapKeys {

  static Optional<? extends AnnotationMirror> getMapKey(Element bindingElement) {
    ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(bindingElement);
    return mapKeys.isEmpty()
        ? Optional.<AnnotationMirror>absent()
        : Optional.of(getOnlyElement(mapKeys));
  }

  static ImmutableSet<? extends AnnotationMirror> getMapKeys(Element bindingElement) {
    return getAnnotatedAnnotations(bindingElement, MapKey.class);
  }

  static Optional<? extends AnnotationValue> unwrapValue(AnnotationMirror mapKey) {
    MapKey mapKeyAnnotation = mapKey.getAnnotationType().asElement().getAnnotation(MapKey.class);
    checkArgument(
        mapKeyAnnotation != null, "%s is not annotated with @MapKey", mapKey.getAnnotationType());
    return mapKeyAnnotation.unwrapValue()
        ? Optional.of(getOnlyElement(mapKey.getElementValues().values()))
        : Optional.<AnnotationValue>absent();
  }

  public static DeclaredType getUnwrappedMapKeyType(
      final DeclaredType mapKeyAnnotationType, final Types types) {
    checkArgument(
        MoreTypes.asTypeElement(mapKeyAnnotationType).getKind() == ElementKind.ANNOTATION_TYPE,
        "%s is not an annotation type",
        mapKeyAnnotationType);

    final ExecutableElement onlyElement =
        getOnlyElement(methodsIn(mapKeyAnnotationType.asElement().getEnclosedElements()));

    SimpleTypeVisitor6<DeclaredType, Void> keyTypeElementVisitor =
        new SimpleTypeVisitor6<DeclaredType, Void>() {

          @Override
          public DeclaredType visitArray(ArrayType t, Void p) {
            throw new IllegalArgumentException(
                mapKeyAnnotationType + "." + onlyElement.getSimpleName() + " cannot be an array");
          }

          @Override
          public DeclaredType visitPrimitive(PrimitiveType t, Void p) {
            return MoreTypes.asDeclared(types.boxedClass(t).asType());
          }

          @Override
          public DeclaredType visitDeclared(DeclaredType t, Void p) {
            return t;
          }
        };
    return keyTypeElementVisitor.visit(onlyElement.getReturnType());
  }

  public static ClassName getMapKeyCreatorClassName(TypeElement mapKeyType) {
    ClassName mapKeyTypeName = ClassName.get(mapKeyType);
    return mapKeyTypeName.topLevelClassName().peerClass(canonicalName(mapKeyTypeName) + "Creator");
  }

  static CodeBlock getMapKeyExpression(Element bindingElement) {
    AnnotationMirror mapKey = getMapKey(bindingElement).get();
    ClassName mapKeyCreator =
        getMapKeyCreatorClassName(MoreTypes.asTypeElement(mapKey.getAnnotationType()));
    Optional<? extends AnnotationValue> unwrappedValue = unwrapValue(mapKey);
    if (unwrappedValue.isPresent()) {
      return new MapKeyExpressionExceptArrays(mapKeyCreator)
          .visit(unwrappedValue.get(), unwrappedValue.get());
    } else {
      return annotationExpression(mapKey, new MapKeyExpression(mapKeyCreator));
    }
  }

  private static class MapKeyExpression
      extends SimpleAnnotationValueVisitor6<CodeBlock, AnnotationValue> {

    final ClassName mapKeyCreator;

    MapKeyExpression(ClassName mapKeyCreator) {
      this.mapKeyCreator = mapKeyCreator;
    }

    @Override
    public CodeBlock visitEnumConstant(VariableElement c, AnnotationValue p) {
      return CodeBlocks.format(
          "$T.$L", TypeName.get(c.getEnclosingElement().asType()), c.getSimpleName());
    }

    @Override
    public CodeBlock visitAnnotation(AnnotationMirror a, AnnotationValue p) {
      return annotationExpression(a, this);
    }

    @Override
    public CodeBlock visitType(TypeMirror t, AnnotationValue p) {
      return CodeBlocks.format("$T.class", TypeName.get(t));
    }

    @Override
    public CodeBlock visitString(String s, AnnotationValue p) {
      return CodeBlocks.format("$S", s);
    }

    @Override
    public CodeBlock visitByte(byte b, AnnotationValue p) {
      return CodeBlocks.format("(byte) $L", b);
    }

    @Override
    public CodeBlock visitChar(char c, AnnotationValue p) {
      return CodeBlocks.format("$L", p);
    }

    @Override
    public CodeBlock visitDouble(double d, AnnotationValue p) {
      return CodeBlocks.format("$LD", d);
    }

    @Override
    public CodeBlock visitFloat(float f, AnnotationValue p) {
      return CodeBlocks.format("$LF", f);
    }

    @Override
    public CodeBlock visitInt(int i, AnnotationValue p) {
      return CodeBlocks.format("(int) $L", i);
    }

    @Override
    public CodeBlock visitLong(long i, AnnotationValue p) {
      return CodeBlocks.format("$LL", i);
    }

    @Override
    public CodeBlock visitShort(short s, AnnotationValue p) {
      return CodeBlocks.format("(short) $L", s);
    }

    @Override
    protected CodeBlock defaultAction(Object o, AnnotationValue p) {
      return CodeBlocks.format("$L", o);
    }

    @Override
    public CodeBlock visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
      for (int i = 0; i < values.size(); i++) {
        codeBlocks.add(this.visit(values.get(i), p));
      }
      return CodeBlocks.format("{$L}", makeParametersCodeBlock(codeBlocks.build()));
    }
  }

  private static class MapKeyExpressionExceptArrays extends MapKeyExpression {

    MapKeyExpressionExceptArrays(ClassName mapKeyCreator) {
      super(mapKeyCreator);
    }

    @Override
    public CodeBlock visitArray(List<? extends AnnotationValue> values, AnnotationValue p) {
      throw new IllegalArgumentException("Cannot unwrap arrays");
    }
  }

  private static CodeBlock annotationExpression(
      AnnotationMirror mapKeyAnnotation, final MapKeyExpression mapKeyExpression) {
    return CodeBlocks.format(
        "$T.create$L($L)",
        mapKeyExpression.mapKeyCreator,
        mapKeyAnnotation.getAnnotationType().asElement().getSimpleName(),
        makeParametersCodeBlock(
            transform(
                getAnnotationValuesWithDefaults(mapKeyAnnotation).entrySet(),
                new Function<Map.Entry<ExecutableElement, AnnotationValue>, CodeBlock>() {
                  @Override
                  public CodeBlock apply(Map.Entry<ExecutableElement, AnnotationValue> entry) {
                    return ARRAY_LITERAL_PREFIX.visit(
                        entry.getKey().getReturnType(),
                        mapKeyExpression.visit(entry.getValue(), entry.getValue()));
                  }
                })));
  }

  private static final SimpleTypeVisitor6<CodeBlock, CodeBlock> ARRAY_LITERAL_PREFIX =
      new SimpleTypeVisitor6<CodeBlock, CodeBlock>() {

        @Override
        public CodeBlock visitArray(ArrayType t, CodeBlock p) {
          return CodeBlocks.format("new $T[] $L", RAW_TYPE_NAME.visit(t.getComponentType()), p);
        }

        @Override
        protected CodeBlock defaultAction(TypeMirror e, CodeBlock p) {
          return p;
        }
      };

  private static final SimpleTypeVisitor6<TypeName, Void> RAW_TYPE_NAME =
      new SimpleTypeVisitor6<TypeName, Void>() {
        @Override
        public TypeName visitDeclared(DeclaredType t, Void p) {
          return ClassName.get(MoreTypes.asTypeElement(t));
        }

        @Override
        protected TypeName defaultAction(TypeMirror e, Void p) {
          return TypeName.get(e);
        }
      };

  private MapKeys() {}
}
