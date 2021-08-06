package lang.taxi.generators.openApi

import lang.taxi.types.QualifiedName

object Utils {

     private val illegalIdentifierCharacters = "[^a-zA-Z0-9\$_]+".toRegex()
     fun String.replaceIllegalCharacters(): String = replace(illegalIdentifierCharacters, "_")
     fun String.removeIllegalCharacters(): String = replace(illegalIdentifierCharacters, "")

     fun qualifyTypeNameIfRaw(typeName: String, defaultNamespace: String): QualifiedName {
        val qualifiedName = QualifiedName.from(typeName)
        return if (qualifiedName.namespace.isEmpty()) {
            QualifiedName(defaultNamespace, typeName)
        } else {
            qualifiedName
        }
    }

}
