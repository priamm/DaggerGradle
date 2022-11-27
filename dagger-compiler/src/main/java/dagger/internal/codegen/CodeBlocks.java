package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterSpec;
import java.util.Iterator;
import javax.lang.model.type.TypeMirror;

final class CodeBlocks {

  static CodeBlock format(String format, Object... args) {
    return CodeBlock.builder().add(format, args).build();
  }

  static CodeBlock makeParametersCodeBlock(Iterable<CodeBlock> codeBlocks) {
    return join(codeBlocks, ", ");
  }

  static CodeBlock concat(Iterable<CodeBlock> codeBlocks) {
    return join(codeBlocks, "\n");
  }

  static CodeBlock join(Iterable<CodeBlock> codeBlocks, String delimiter) {
    CodeBlock.Builder builder = CodeBlock.builder();
    Iterator<CodeBlock> iterator = codeBlocks.iterator();
    while (iterator.hasNext()) {
      builder.add(iterator.next());
      if (iterator.hasNext()) {
        builder.add(delimiter);
      }
    }
    return builder.build();
  }

  static FluentIterable<CodeBlock> toCodeBlocks(Iterable<? extends TypeMirror> typeMirrors) {
    return FluentIterable.from(typeMirrors).transform(TYPE_MIRROR_TO_CODE_BLOCK);
  }

  static CodeBlock stringLiteral(String toWrap) {
    return format("$S", toWrap);
  }

  private static final Function<TypeMirror, CodeBlock> TYPE_MIRROR_TO_CODE_BLOCK =
      new Function<TypeMirror, CodeBlock>() {
        @Override
        public CodeBlock apply(TypeMirror typeMirror) {
          return CodeBlocks.format("$T", typeMirror);
        }
      };

  static Function<ParameterSpec, CodeBlock> PARAMETER_NAME =
      new Function<ParameterSpec, CodeBlock>() {
          @Override
          public CodeBlock apply(ParameterSpec input) {
            return CodeBlocks.format("$N", input);
          }
      };

  private CodeBlocks() {}
}
