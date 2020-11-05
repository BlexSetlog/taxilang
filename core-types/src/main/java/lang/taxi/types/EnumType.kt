package lang.taxi.types

import arrow.core.Either
import com.google.common.cache.CacheBuilder
import lang.taxi.Equality

object Enums {
   fun enumValue(enum: QualifiedName, enumValueName: String): EnumValueQualifiedName {
      return "${enum.parameterizedName}.$enumValueName"
   }

   fun splitEnumValueQualifiedName(name: EnumValueQualifiedName): Pair<QualifiedName, String> {
      val parts = name.split(".")
      val enumName = parts.dropLast(1).joinToString(".")
      val valueName = parts.last()
      return QualifiedName.from(enumName) to valueName
   }

}
typealias EnumValueQualifiedName = String

data class EnumValueExtension(val name: String, override val annotations: List<Annotation>, val synonyms: List<EnumValueQualifiedName>, override val typeDoc: String? = null, override val compilationUnit: CompilationUnit) : Annotatable, Documented, TypeDefinition
data class EnumValue(val name: String, val value: Any = name, val qualifiedName: EnumValueQualifiedName, override val annotations: List<Annotation>, val synonyms: List<EnumValueQualifiedName>, override val typeDoc: String? = null, val isDefault: Boolean = false) : Annotatable, Documented {
   fun getSynonymsForType(typeSynonym: EnumType):List<Pair<QualifiedName,String>> {
      return this.synonyms.mapNotNull {
         val (typeName, enumValueName) = EnumValue.qualifiedNameFrom(it)
         if (typeName.fullyQualifiedName == typeSynonym.qualifiedName) {
            typeName to enumValueName
         } else {
            null
         }
      }
   }

   companion object {
      private val qualifiedNameCache = CacheBuilder.newBuilder().build<String, Pair<QualifiedName, String>>()
      fun qualifiedNameFrom(name: String): Pair<QualifiedName, String> {
         return qualifiedNameCache.get(name) {
            val parts = name.split(".")
            QualifiedName.from(parts.dropLast(1).joinToString(".")) to parts.last()
         }
      }
   }
}

data class EnumDefinition(val values: List<EnumValue>,
                          override val annotations: List<Annotation> = emptyList(),
                          override val compilationUnit: CompilationUnit,
                          val inheritsFrom: Set<Type> = emptySet(),
                          val basePrimitive: PrimitiveType,
                          val isLenient: Boolean = false,
                          val typeSynonyms: Set<EnumType> = emptySet(),
                          override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = Equality(this, EnumDefinition::values.toSet(), EnumDefinition::annotations.toSet(), EnumDefinition::typeDoc, EnumDefinition::basePrimitive, EnumDefinition::inheritsFrom.toSet())
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}


data class EnumExtension(val values: List<EnumValueExtension>,
                         override val annotations: List<Annotation> = emptyList(),
                         override val compilationUnit: CompilationUnit,
                         override val typeDoc: String? = null) : Annotatable, TypeDefinition, Documented {
   private val equality = Equality(this, EnumExtension::values.toSet(), EnumExtension::annotations.toSet(), EnumExtension::typeDoc)
   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()
}

data class EnumType(override val qualifiedName: String,
                    override var definition: EnumDefinition?,
                    override val extensions: MutableList<EnumExtension> = mutableListOf()) : UserType<EnumDefinition, EnumExtension>, Annotatable, Documented {
   companion object {
      fun undefined(name: String): EnumType {
         return EnumType(name, definition = null)
      }
   }

   val isLenient: Boolean
      get() {
         return this.definition?.isLenient ?: false
      }

   val typeSynonyms: Set<EnumType>
      get() {
         return this.definition?.typeSynonyms ?: emptySet()
      }

   private val wrapper = LazyLoadingWrapper(this)
   override val allInheritedTypes: Set<Type>
      get() {
         return if (isDefined) wrapper.allInheritedTypes else emptySet()
      }
   val baseEnum: EnumType?
      get() {
         return if (isDefined) wrapper.baseEnum else null
      }
   override val inheritsFromPrimitive: Boolean
      get() {
         return if (isDefined) wrapper.inheritsFromPrimitive else false
      }
   override val definitionHash: String?
      get() {
         return if (isDefined) wrapper.definitionHash else null
      }

   // Not sure it makes sense to support formats on enums.  Let's revisit if there's a usecase.
   override val format: List<String>? = null
   override val formattedInstanceOfType: Type? = null
   override val calculation: Formula?
      get() = null

   override val basePrimitive: PrimitiveType?
      get() = definition?.basePrimitive

   override val inheritsFrom: Set<Type>
      get() = definition?.inheritsFrom ?: emptySet()

   val inheritsFromNames: List<String>
      get() {
         return inheritsFrom.map { it.qualifiedName }
      }

   override val typeDoc: String?
      get() {
         val documented = (listOfNotNull(this.definition) + extensions) as List<Documented>
         return documented.typeDoc()
      }

   override val referencedTypes: List<Type> = emptyList()

   override fun addExtension(extension: EnumExtension): Either<ErrorMessage, EnumExtension> {
      if (!this.isDefined) {
         error("It is invalid to add an extension before the enum is defined")
      }
      val definedValueNames = this.definition!!.values.map { it.name }
      // Validate that the extension doesn't modify members
      val illegalValueDefinitions = extension.values.filter { value -> !definedValueNames.contains(value.name) }
      return if (illegalValueDefinitions.isNotEmpty()) {
         val illegalValueNames = illegalValueDefinitions.joinToString(", ") { it.name }
         Either.left("Cannot modify the members in an enum.  An extension attempted to add a new members $illegalValueNames")
      } else {
         this.extensions.add(extension)
         Either.right(extension)
      }
   }

   val defaultValue: EnumValue?
      get() {
         return values.firstOrNull { it.isDefault }
      }

   val hasDefault: Boolean
      get() {
         return defaultValue != null
      }

   private val enumValueCache = CacheBuilder
      .newBuilder()
      .build<String, List<EnumValue>>()
   val values: List<EnumValue>
      get() {
         return when {
            this.baseEnum != this -> this.baseEnum?.values ?: emptyList()
            this.definition == null -> emptyList()
            else -> {
               // through profiling perfomance whne using the compiler in the LSP tooling,
               // we've found that calls to Enum.values() happens a lot, and is expensive.
               // Therefore, we're caching this.
               // Note the cache must invaliate when things that affect the list of values change.
               // THat's either the definition, or adding an extension.
               val cacheKey = "${this.definition!!.hashCode()}-${this.extensions.hashCode()}"
               this.enumValueCache.get(cacheKey) {
                  this.definition!!.values.map { value ->
                     val valueExtensions: List<EnumValueExtension> = valueExtensions(value.name)
                     val collatedAnnotations = value.annotations + valueExtensions.annotations()
                     val docSources = (listOf(value) + valueExtensions) as List<Documented>
                     val synonyms = value.synonyms + valueExtensions.flatMap { it.synonyms }
                     value.copy(annotations = collatedAnnotations, typeDoc = docSources.typeDoc(), synonyms = synonyms)
                  }
               }
            }
         }
      }

   fun has(valueOrName: Any?): Boolean {
      return (valueOrName is String && this.hasName(valueOrName)) || this.hasValue(valueOrName) || this.hasDefault
   }

   fun hasName(name: String?): Boolean {
      return this.values.any { lenientEqual(it.name, name) } || this.hasDefault
   }

   fun hasValue(value: Any?): Boolean {
      return this.values.any { lenientEqual(it.value, value) } || this.hasDefault
   }

   private fun lenientEqual(first: Any, second: Any?): Boolean {
      return if (!this.isLenient) {
         first == second
      } else {
         when {
            (first is String && second is String) -> first.toLowerCase() == second.toLowerCase()
            else -> first == second
         }
      }
   }

   fun ofValue(value: Any?) = this.values.firstOrNull { lenientEqual(it.value, value) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contain a member with a value of $value")

   fun ofName(name: String?) = this.values.firstOrNull { lenientEqual(it.name, name) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contains a member named $name")

   fun of(valueOrName: Any?) = this.values.firstOrNull { lenientEqual(it.value, valueOrName) || lenientEqual(it.name, valueOrName) }
      ?: defaultValue
      ?: error("Enum ${this.qualifiedName} does not contain either a name nor a value of $valueOrName")

   private fun valueExtensions(valueName: String): List<EnumValueExtension> {
      return this.extensions.flatMap { it.values.filter { value -> value.name == valueName } }
   }

   override val annotations: List<Annotation>
      get() {
         val collatedAnnotations = this.extensions.flatMap { it.annotations }.toMutableList()
         definition?.annotations?.forEach { collatedAnnotations.add(it) }
         return collatedAnnotations.toList()
      }

   @Deprecated("Use ofName(), as this method name is confusing")
   fun value(valueName: String): EnumValue {
      return ofName(valueName)
   }

   /**
    * Returns true if the provided value would resolve
    * only through a default value
    */
   fun resolvesToDefault(valueOrName: Any): Boolean {
      return if (!this.hasDefault) {
         false
      } else {
         this.values.none { lenientEqual(it.name, valueOrName) }
            && this.values.none { lenientEqual(it.value, valueOrName) }
      }
   }
}
