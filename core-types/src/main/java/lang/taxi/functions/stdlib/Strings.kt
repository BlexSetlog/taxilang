package lang.taxi.functions.stdlib

import lang.taxi.functions.stdlib.StdLib.stdLibName
import lang.taxi.types.QualifiedName

/**
 * This class provides the API of the stdlib of functions.
 * We don't ship implementations - that's up to a parsing library (such as Vyne)
 * to provide.
 */
object Strings {
   val functions: List<FunctionApi> = listOf(
      Left,
      Right,
      Mid,
      Concat,
      Uppercase,
      Lowercase,
      Trim,
      Length,
      Find,
      Replace
//      Coalesce
   )
}

object Concat : FunctionApi {
   override val taxi: String = "declare function concat(String...):String"
   override val name: QualifiedName = stdLibName("concat")

}

object Trim : FunctionApi {
   override val taxi: String = "declare function trim(String):String"
   override val name: QualifiedName = stdLibName("trim")
}

object Left : FunctionApi {
   override val taxi: String = "declare function left(String,Int):String"
   override val name: QualifiedName = stdLibName("left")
}

object Right : FunctionApi {
   override val taxi: String = "declare function right(String,Int):String"
   override val name: QualifiedName = stdLibName("right")
}

object Mid : FunctionApi {
   override val taxi: String = "declare function mid(String,Int,Int):String"
   override val name: QualifiedName = stdLibName("mid")
}

object Uppercase : FunctionApi {
   override val taxi: String = "declare function upperCase(String):String"
   override val name: QualifiedName = stdLibName("upperCase")
}


object Lowercase : FunctionApi {
   override val taxi: String = "declare function lowerCase(String):String"
   override val name: QualifiedName = stdLibName("lowerCase")
}

object Length: FunctionApi {
   override val taxi: String
      get() = "declare function length(String):Int"
   override val name: QualifiedName
      get() = stdLibName("length")
}

object Find: FunctionApi {
   override val taxi: String
      get() = "declare function indexOf(String, String):Int"
   override val name: QualifiedName
      get() = stdLibName("indexOf")

}

object Replace : FunctionApi {
   override val taxi: String = "declare function replace(String, String,String):String"
   override val name: QualifiedName = stdLibName("replace")

}

