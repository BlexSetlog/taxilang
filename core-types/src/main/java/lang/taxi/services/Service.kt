package lang.taxi.services

import lang.taxi.Equality
import lang.taxi.types.*
import lang.taxi.types.Annotation

data class Parameter(override val annotations: List<Annotation>, val type: Type, val name: String?, override val constraints: List<Constraint>) : Annotatable, ConstraintTarget {
   override val description: String = "param $name"
}

data class Operation(val name: String, val scope: String? = null, override val annotations: List<Annotation>, val parameters: List<Parameter>, val returnType: Type, override val compilationUnits: List<CompilationUnit>, val contract: OperationContract? = null) : Annotatable, Compiled {
   private val equality = Equality(this, Operation::name, Operation::annotations, Operation::parameters, Operation::returnType, Operation::contract)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

data class Service(override val qualifiedName: String, val operations: List<Operation>, override val annotations: List<Annotation>, override val compilationUnits: List<CompilationUnit>) : Annotatable, Named, Compiled {
   private val equality = Equality(this, Service::qualifiedName, Service::operations.toSet(), Service::annotations)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

   fun operation(name: String): Operation {
      return this.operations.first { it.name == name }
   }

   fun containsOperation(name: String) = operations.any { it.name == name }
}


/**
 * Indicates that an attribute of a parameter (which is an Object type)
 * must have a constant value
 * eg:
 * Given Money(amount:Decimal, currency:String),
 * could express that Money.currency must have a value of 'GBP'
 */
data class AttributeConstantValueConstraint(
   // TODO : It may be that we're not always adding constraints
   // to Object types (types with fields).  When I hit a scenario like that,
   // relax this constraint to make it optional, and update accordingly.
   val fieldName: String,
   val expectedValue: Any,
   override val compilationUnits: List<CompilationUnit>
) : Constraint {
   override fun asTaxi(): String = "$fieldName = \"$expectedValue\""

   private val equality = Equality(this, AttributeConstantValueConstraint::fieldName, AttributeConstantValueConstraint::expectedValue)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

/**
 * Indicates that an attribute will be returned updated to a value
 * provided by a parameter (ie., an input on a function)
 */
data class AttributeValueFromParameterConstraint(
   val fieldName: String,
   val attributePath: AttributePath,
   override val compilationUnits: List<CompilationUnit>
) : Constraint {
   override fun asTaxi(): String = "$fieldName = ${attributePath.path}"

   private val equality = Equality(this, AttributeValueFromParameterConstraint::fieldName, AttributeValueFromParameterConstraint::attributePath)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()

}

data class ReturnValueDerivedFromParameterConstraint(
   val attributePath: AttributePath,
   override val compilationUnits: List<CompilationUnit>
) : Constraint {
   override fun asTaxi(): String = "from $path"

   val path = attributePath.path

   private val equality = Equality(this, ReturnValueDerivedFromParameterConstraint::attributePath)

   override fun equals(other: Any?) = equality.isEqualTo(other)
   override fun hashCode(): Int = equality.hash()


}

interface ConstraintTarget {
   val constraints: List<Constraint>

   val description: String
}

interface Constraint : Compiled {
   fun asTaxi(): String
}

typealias FieldName = String
typealias ParamName = String

data class OperationContract(val returnType: Type,
                             val returnTypeConstraints: List<Constraint>
) : ConstraintTarget {
   override val description: String = "Operation returning ${returnType.qualifiedName}"
   override val constraints: List<Constraint> = returnTypeConstraints
}
