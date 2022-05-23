package lang.taxi

import com.winterbe.expekt.should
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.*
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import lang.taxi.expressions.TypeExpression
import lang.taxi.services.FilterCapability
import lang.taxi.services.SimpleQueryCapability
import lang.taxi.types.*

class QueryOperationGrammarSpec : DescribeSpec({
   describe("Grammar for query operations") {
      it("should compile query grammar") {
         val queryOperation = """
            model Person {}
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.compiled()
            .service("PersonService")
            .queryOperation("personQuery")

         queryOperation.grammar.should.equal("vyneQl")
         queryOperation.parameters.should.have.size(1)
         queryOperation.parameters[0].let { parameter ->
            parameter.name.should.equal("body")
            parameter.type.qualifiedName.should.equal("VyneQlQuery")
            parameter.annotations.should.have.size(1)
            parameter.annotations.first().name.should.equal("RequestBody")
         }
         queryOperation.returnType.toQualifiedName().parameterizedName.should.equal("lang.taxi.Array<Person>")
         val capabilities = queryOperation.capabilities
         capabilities.should.have.size(3)
         capabilities.should.contain.elements(SimpleQueryCapability.SUM, SimpleQueryCapability.COUNT)
         val filter = capabilities.filterIsInstance<FilterCapability>().first()
         filter.supportedOperations.should.have.size(3)
         filter.supportedOperations.should.contain.elements(Operator.EQUAL, Operator.IN, Operator.LIKE)
      }

      it("should generate taxi back from a compiled query operation") {
         val queryOperationTaxi = """
            model Person {}
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.compiled()
            .service("PersonService")
            .queryOperation("personQuery")
            .asTaxi()
         val expected =
            """vyneQl query personQuery(@RequestBody body: VyneQlQuery):lang.taxi.Array<Person> with capabilities {
filter(==,in,like),
sum,
count
}"""
         queryOperationTaxi
            .withoutWhitespace()
            .should.equal(expected.withoutWhitespace())
      }

      it("should give a compilation error for an unknown return type") {
         val errors = """
            type VyneQlQuery inherits String
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):BadType[] with capabilities {
                  filter(==,in,like),
                  sum,
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("BadType is not defined")
      }
      it("should give a compilation error for an unknown capability") {
         val errors = """
            type VyneQlQuery inherits String
            model Person {}
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(==,in,like),
                  farting,
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("Unable to parse 'farting' to a query capability.  Expected one of filter, sum, count, avg, min, max")
      }
      it("should give a compilation error for an unknown filter capability") {
         val errors = """
            type VyneQlQuery inherits String
            model Person {}
            service PersonService {
               vyneQl query personQuery(@RequestBody body:VyneQlQuery):Person[] with capabilities {
                  filter(guessing),
                  count
               }
            }
         """.validated()
         errors.should.have.size(1)
         errors.first().detailMessage.should.equal("mismatched input 'guessing' expecting {'in', 'like', '>', '>=', '<', '<=', '==', '!='}")
      }



      it("should allow union types as return type") {
         val type = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}
            model Studio {}

            service MovieDb {
               vyneQl query movieQuery(VyneQlQuery): Movie | Actor | Review | Studio
            }
         """.compiled()
            .service("MovieDb").queryOperation("movieQuery")
            .returnType
         type.qualifiedName.shouldBe("Movie|Actor|Review|Studio")
         val unionType = type.shouldBeInstanceOf<UnionType>()
         unionType.types.shouldHaveSize(4)
         unionType.types.shouldExist { type -> type.qualifiedName == "Movie" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Actor" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Review" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Studio" }
      }

      it("should allow union type collections as return type") {
         val type = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}
            model Studio {}

            service MovieDb {
               vyneQl query movieQuery(VyneQlQuery): (Movie | Actor | Review | Studio)[]
            }
         """.compiled()
            .service("MovieDb").queryOperation("movieQuery")
            .returnType
         val arrayType = type.shouldBeInstanceOf<ArrayType>()
         val arrayTypeMember = arrayType.type
         arrayTypeMember.qualifiedName.shouldBe("Movie|Actor|Review|Studio")
         val unionType = arrayTypeMember.shouldBeInstanceOf<UnionType>()
         unionType.types.shouldHaveSize(4)
         unionType.types.shouldExist { type -> type.qualifiedName == "Movie" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Actor" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Review" }
         unionType.types.shouldExist { type -> type.qualifiedName == "Studio" }
      }

      it("should report errors in union type declarations") {
         val errors = """
            type VyneQlQuery inherits String
            model Movie {}

            service MovieDb {
               vyneQl query movieQuery(VyneQlQuery): Movie | Actor // Actor doesn't exist
            }
         """.validated()
         errors.shouldHaveSize(1)
         errors.shouldContainMessage("Actor is not defined")
      }

      it("should be possible to declare union types as top level types") {
         val type = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}

            type MovieDbType = Movie | Actor | Review

            service MovieDb {
               vyneQl query movieQuery(VyneQlQuery): MovieDbType
            }
         """.compiled()
            .service("MovieDb").queryOperation("movieQuery")
            .returnType as ObjectType
         type.qualifiedName.should.equal("MovieDbType")
         type.isUnionTypeWrapper.shouldBe(true)
         type.wrappedUnionType!!.types.should.have.size(3)
      }

      it("should be possible to declare collections of top-level union types") {
         val arrayType = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}

            type MovieDbType = Movie | Actor | Review

            service MovieDb {
               vyneQl query movieQuery(VyneQlQuery): MovieDbType[]
            }
         """.compiled()
            .service("MovieDb").queryOperation("movieQuery")
            .returnType as ArrayType
         val type = arrayType.type as ObjectType
         type.qualifiedName.should.equal("MovieDbType")
         type.isUnionTypeWrapper.shouldBe(true)
         type.wrappedUnionType!!.types.should.have.size(3)
      }

      it("can declare single joinTo type") {
         val type = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}
            model MovieActor {}

            service Movies {
                vyneQl query movieQuery(body:VyneQlQuery):Movie joinTo( MovieActor[] , Review )
            }

         """.compiled()
            .service("Movies")
            .queryOperation("movieQuery")
            .returnType
         type.shouldNotBeNull()
         val joinType = type.shouldBeInstanceOf<JoinType>()
         joinType.leftType.qualifiedName.should.equal("Movie")
         joinType.rightTypes.map { it.toQualifiedName().parameterizedName }.shouldContainAll(
            "lang.taxi.Array<MovieActor>",
            "Review"
         )

      }

      it("should be possible to declare inline union joinTo types") {
         val type = """
            type VyneQlQuery inherits String
            model Movie {}
            model Actor {}
            model Review {}
            model MovieActor {}

            service Movies {
                vyneQl query movieQuery(body:VyneQlQuery):
                           (Movie joinTo( MovieActor[] , Review )
                           | MovieActor joinTo( Actor[] )
                           | Review[])
                        []
            }

         """.compiled()
            .service("Movies")
            .queryOperation("movieQuery")
            .returnType


         type.shouldNotBeNull()
         val arrayType = type.shouldBeInstanceOf<ArrayType>()
         val unionType = arrayType.type.shouldBeInstanceOf<UnionType>()
         val firstJoinType = unionType.types[0].shouldBeInstanceOf<JoinType>()
         firstJoinType.leftType.qualifiedName.shouldBe("Movie")
         firstJoinType.rightTypes.map { it.toQualifiedName().parameterizedName }
            .shouldContainInOrder(
               "lang.taxi.Array<MovieActor>",
               "Review"
            )
         val secondJoinType = unionType.types[1].shouldBeInstanceOf<JoinType>()
         secondJoinType.leftType.qualifiedName.shouldBe("MovieActor")
         secondJoinType.rightTypes.map { it.toQualifiedName().parameterizedName }
            .shouldContainInOrder(
               "lang.taxi.Array<Actor>"
            )
         val thirdType = unionType.types[2]
         thirdType.toQualifiedName().parameterizedName.shouldBe("lang.taxi.Array<Review>")
      }
   }

   it("is possible to declare union type A | B | C") {
      val type = """
         type A
         type B
         type C
         type All = A | B | C
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      type.types.shouldHaveSize(3)
      type.types.map { it.qualifiedName }
         .shouldContainInOrder("A", "B", "C")
   }

   it("is possible to declare join on rhs of union type") {
      val unionType = """
         type A
         type B
         type C
         type All = A | B joinTo ( C )
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      unionType.types.shouldHaveSize(2)
      unionType.types[0].qualifiedName.shouldBe("A")
      val joinType = unionType.types[1].shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("B")
      joinType.rightTypes.map { it.qualifiedName }.shouldContainExactly("C")
   }
   it("is possible to declare a union on the rhs of a join type") {
      val unionType = """
         type A
         type B
         type C
         type All = A joinTo ( B ) | C
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<UnionType>()
      unionType.types.shouldHaveSize(2)
      val joinType = unionType.types[0].shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("A")
      joinType.rightTypes.map { it.qualifiedName }.shouldContainExactly("B")

      unionType.types[1].qualifiedName.shouldBe("C")
   }

   it("is possible to dcelare a union type inside a join type" ) {
      val joinType = """
         type A
         type B
         type C
         type All = A joinTo ( B | C )
      """.compiled()
         .type("All")
         .typeExpression().type
         .shouldBeInstanceOf<JoinType>()
      joinType.leftType.qualifiedName.shouldBe("A")
      joinType.rightTypes.shouldHaveSize(1)
      val unionType = joinType.rightTypes.single().shouldBeInstanceOf<UnionType>()
      unionType.types.map { it.qualifiedName }
         .shouldContainInOrder("B", "C")
   }



})
fun Type.typeExpression(): TypeExpression {
   return (this as ObjectType).expression as TypeExpression
}
