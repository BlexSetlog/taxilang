package lang.taxi

import com.winterbe.expekt.should
import lang.taxi.functions.FunctionAccessor
import lang.taxi.types.CalculatedModelAttributeFieldSetExpression
import lang.taxi.types.ComparisonExpression
import lang.taxi.types.ComparisonOperator
import lang.taxi.types.ConditionalAccessor
import lang.taxi.types.ConstantEntity
import lang.taxi.types.ElseMatchExpression
import lang.taxi.types.EmptyReferenceSelector
import lang.taxi.types.FormulaOperator
import lang.taxi.types.InlineAssignmentExpression
import lang.taxi.types.LiteralAssignment
import lang.taxi.types.ModelAttributeFieldReferenceEntity
import lang.taxi.types.ModelAttributeReferenceSelector
import lang.taxi.types.ModelAttributeTypeReferenceAssignment
import lang.taxi.types.ObjectType
import lang.taxi.types.ScalarAccessorValueAssignment
import lang.taxi.types.WhenCaseBlock
import lang.taxi.types.WhenFieldSetCondition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ViewAggregationSpec : Spek({
   describe("view syntax containing aggregations") {
      it("A view with one find statement and aggregate field definition") {
         val src = """
            import vyne.aggregations.sumOver
            namespace taxi.test
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits Decimal
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type OrderSize inherits String
         type CumulativeQty inherits Decimal
         type TradeNumber inherits Int
         type ExecutedQuantity inherits Decimal
         type MarketId inherits String
         type RemainingQuantity inherits Decimal
         type DisplayedQuantity inherits Decimal

         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
            tradeNo: TradeNumber
            executedQty: ExecutedQuantity
            marketId: MarketId

         }

         model OrderEvent { }



          [[
           Sample View
          ]]
         view OrderView inherits OrderEvent with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
              cumulativeQty: CumulativeQty by when {
                OrderSent::TradeNumber != null ->  sumOver(OrderSent::ExecutedQuantity, OrderSent::SentOrderId, OrderSent::MarketId)
                else -> OrderSent::ExecutedQuantity
               }

              leavesQuantity: RemainingQuantity by when {
                 OrderSent::RequestedQuantity = OrderSent::ExecutedQuantity -> 0
                 else -> (OrderSent::RequestedQuantity - OrderView::CumulativeQty)
               }

              displayQuantity: DisplayedQuantity by when {
                 OrderSent::RequestedQuantity = OrderSent::ExecutedQuantity -> 0
                 else -> OrderView::RemainingQuantity
               }
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val orderSentType = taxiDocument.model("taxi.test.OrderSent")
         fun testType(typeName: String) = taxiDocument.type("taxi.test.$typeName")
         orderSentType.should.not.be.`null`
         val orderView = taxiDocument.view("taxi.test.OrderView")
         orderView.should.not.be.`null`
         orderView?.typeDoc.should.equal("Sample View")
         orderView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = orderView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("taxi.test.OrderSent")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("orderId").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderDateTime").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderType").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("subSecurityType").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("requestedQuantity").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         bodyType.field("orderEntry").memberSource?.fullyQualifiedName.should.equal("taxi.test.OrderSent")
         val cumulativeQtyField = bodyType.field("cumulativeQty")
         cumulativeQtyField.memberSource.should.be.`null`
         val conditionalAccessor = cumulativeQtyField.accessor as ConditionalAccessor
         conditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.NQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("TradeNumber")),
                        right = ConstantEntity(value = null)),
                     assignments = listOf(
                        InlineAssignmentExpression(
                           assignment = ScalarAccessorValueAssignment(
                              accessor = FunctionAccessor(
                                 taxiDocument.function("vyne.aggregations.sumOver"),
                                 listOf(
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("ExecutedQuantity")),
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("SentOrderId")),
                                    ModelAttributeReferenceSelector(memberSource = orderSentType.toQualifiedName(), memberType = testType("MarketId"))
                                 )))))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           assignment = ModelAttributeTypeReferenceAssignment(
                              source = orderSentType.toQualifiedName(),
                              type = testType("ExecutedQuantity")))))
               ))
         )

         val leavesQtyField = bodyType.field("leavesQuantity")
         leavesQtyField.memberSource.should.be.`null`
         val leavesQtyFieldConditionalAccessor = leavesQtyField.accessor as ConditionalAccessor
         leavesQtyFieldConditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(
               selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.EQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("RequestedQuantity")),
                        right = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("ExecutedQuantity"))
                     ),
                     assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment(0)))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           ScalarAccessorValueAssignment(
                              accessor = ConditionalAccessor(
                                 expression = CalculatedModelAttributeFieldSetExpression(
                                    operand1 = ModelAttributeReferenceSelector(
                                       memberSource = orderSentType.toQualifiedName(),
                                       memberType = testType("RequestedQuantity")),
                                    operand2 = ModelAttributeReferenceSelector(
                                       memberSource = orderView.toQualifiedName(),
                                       memberType = testType("CumulativeQty")),
                                    operator = FormulaOperator.Subtract
                                 )
                              )
                           )
                        )
                     )
                  )
               )
            )
         )

         val displayQuantity = bodyType.field("displayQuantity")
         val displayQuantityFieldConditionalAccessor = displayQuantity.accessor as ConditionalAccessor
         displayQuantityFieldConditionalAccessor.expression.should.equal(
            WhenFieldSetCondition(
               selectorExpression = EmptyReferenceSelector,
               cases = listOf(
                  WhenCaseBlock(
                     matchExpression = ComparisonExpression(
                        operator = ComparisonOperator.EQ,
                        left = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("RequestedQuantity")),
                        right = ModelAttributeFieldReferenceEntity(
                           source = orderSentType.toQualifiedName(),
                           fieldType = testType("ExecutedQuantity"))
                     ),
                     assignments = listOf(InlineAssignmentExpression(assignment = LiteralAssignment(0)))),
                  WhenCaseBlock(
                     matchExpression = ElseMatchExpression,
                     assignments = listOf(
                        InlineAssignmentExpression(
                           ModelAttributeTypeReferenceAssignment(
                              source = orderView.toQualifiedName(),
                              type = testType("RemainingQuantity")
                           )
                        )
                     )
                  )
               )
            )
         )
      }

      it("A view one join statement and aggregate field definition") {
         val src = """
         type GroupId inherits String
         type GroupName inherits String
         type ProductName inherits String
         type Price inherits Decimal

         model ProductGroup {
            @Id
            id: GroupId
            name: GroupName
         }

         model Product {
            @Id
            groupId: GroupId
            name: ProductName
            price: Price
         }

          [[
           Sample View
          ]]
         view ProductView with query {
            find { Product[] (joinTo ProductGroup[]) } as {
               productName: Product::ProductName
               price: Product::Price
               groupName: ProductGroup::GroupName
               cumulativeGroupPrice: Price by vyne.aggregations.sumOver(Product::Price, ProductGroup::GroupName)
            }
         }
           """.trimIndent()
         val taxiDocument = Compiler(src).compile()
         val productView = taxiDocument.view("ProductView")
         productView.should.not.be.`null`
         productView?.typeDoc.should.equal("Sample View")
         productView?.viewBodyDefinitions?.size.should.equal(1)
         val findOrderSent = productView!!.viewBodyDefinitions!!.first()
         findOrderSent.bodyType.qualifiedName.should.equal("Product")
         val bodyType = (findOrderSent.viewBodyType!! as ObjectType)
         bodyType.anonymous.should.be.`true`
         bodyType.field("productName").memberSource?.fullyQualifiedName.should.equal("Product")
         bodyType.field("price").memberSource?.fullyQualifiedName.should.equal("Product")
         bodyType.field("groupName").memberSource?.fullyQualifiedName.should.equal("ProductGroup")
         val cumulativeGroupPriceField = bodyType.field("cumulativeGroupPrice")
         cumulativeGroupPriceField.memberSource.should.be.`null`
         val cumulativeGroupPriceAccessor = cumulativeGroupPriceField.accessor as FunctionAccessor
         cumulativeGroupPriceAccessor.function.qualifiedName.should.equal("vyne.aggregations.sumOver")
         val firstArgument = cumulativeGroupPriceAccessor.inputs.first() as ModelAttributeReferenceSelector
         firstArgument.memberSource.fullyQualifiedName.should.equal("Product")
         val secondArgument = cumulativeGroupPriceAccessor.inputs[1] as ModelAttributeReferenceSelector
         secondArgument.memberSource.fullyQualifiedName.should.equal("ProductGroup")
      }

      it("only model attribute based function accessors are allowed within views") {
         val src = """

         declare function avgOver(Decimal,String):Decimal

         model ProductGroup {
            @Id
            id: GroupId as String
            name: GroupName as String
         }

         model Product {
            @Id
            groupId: GroupId as String
            name: ProductName as String
            price: Price as String
         }

          [[
           Sample View
          ]]
         view ProductView with query {
            find { Product[] (joinTo ProductGroup[]) } as {
               productName: Product::ProductName
               price: Product::Price
               groupName: ProductGroup::GroupName
               avgGroupPrice: Price by avgOver(this.price, this.groupName)
            }
         }
           """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.last().detailMessage.should.equal("Only Model Attribute References are  allowed within Views")
      }

      it("should detect references to types that are not part of the view definition") {
         val src = """
         import vyne.aggregations.sumOver
         type SentOrderId inherits String
         type FillOrderId inherits String
         type OrderEventDateTime inherits Instant
         type OrderType inherits String
         type SecurityDescription inherits String
         type RequestedQuantity inherits String
         type OrderStatus inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type AggregatedCumulativeQty inherits Decimal
         type TradeNo inherits String
         type CumulativeQuantity inherits Decimal
         type RemainingQuantity inherits Decimal
         type DisplayedQuantity inherits Decimal


         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            @Between
		      orderDateTime: OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss") by column("Time Submitted")
            orderType: OrderType by default("Market")
            subSecurityType: SecurityDescription? by column("Instrument Desc")
            requestedQuantity: RequestedQuantity? by column("Size")
            remainingQuantity: RemainingQuantity? by column("Size")
            displayedQuantity: DisplayedQuantity? by column("Size")
            entryType: OrderStatus by default("New")
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           orderType: OrderType by default("Market")
           subSecurityType: SecurityDescription? by column("Instrument Desc")
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
           entryType: OrderStatus by default("Filled")
           tradeNo: TradeNo by column("TradeNo")
         }

         model OrderEvent { }

          [[
           Sample View
          ]]
         view OrderView inherits OrderEvent with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              orderDateTime: OrderSent::OrderEventDateTime( @format = "MM/dd/yy HH:mm:ss")
              orderType: OrderSent::OrderType
              subSecurityType: OrderSent::SecurityDescription
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderSent::OrderStatus
              leavesQuantity: OrderSent::RemainingQuantity
              displayQuantity: OrderSent::DisplayedQuantity
              tradeNo: TradeNo
              executedQuantity: DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId
              orderDateTime: OrderEventDateTime
              orderType: OrderFill::OrderType
              subSecurityType: SecurityDescription by coalesce(OrderFill::SecurityDescription, OrderSent::SecurityDescription)
              requestedQuantity: OrderSent::RequestedQuantity
              orderEntry: OrderStatus by when {
                 OrderSent::RequestedQuantity = OrderFill::DecimalFieldOrderFilled -> OrderFill::OrderStatus
                 else -> "PartiallyFilled"
              }
              leavesQuantity: RemainingQuantity by when {
                    OrderSent::RequestedQuantity = OrderFilled::DecimalFieldOrderFilled -> 0
                    else -> (OrderSent::RequestedQuantity - OrderView::CumulativeQuantity)
              }
              displayQuantity: DisplayedQuantity by when {
                  OrderSent::RequestedQuantity = OrderFilled::DecimalFieldOrderFilled -> 0
                  else -> OrderView::RemainingQuantity
              }
              tradeNo: OrderFill::TradeNo
              executedQuantity: OrderFill::DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity by when {
                  OrderFill::TradeNo = null -> OrderFill::DecimalFieldOrderFilled
                  else -> sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::FillOrderId, OrderFill::TradeNo)
                }
            }
         }
   """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.first().detailMessage.should.equal("A Reference to OrderFilled is invalid in this view definition context")
         errors.last().detailMessage.should.equal("OrderFilled::DecimalFieldOrderFilled is invalid as OrderFilled does not have a field with type DecimalFieldOrderFilled")
      }

      it("should detect view field references on the left hand side of when statements") {
         val src = """
         import vyne.aggregations.sumOver
         import vyne.aggregations.sumOver
         type SentOrderId inherits String
         type FillOrderId inherits String
         type MarketTradeId inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type CumulativeQuantity inherits Decimal
         type BuyCumulativeSumOrdered inherits Decimal
         type SellCumulativeSumOrdered inherits Decimal
         type BrokerOrderBuy inherits String
         type BrokerOrderSell inherits String
         type OrderBankDirection inherits String
         type SellCumulativeQuantity inherits Decimal
         type BuyCumulativeQuantity inherits Decimal
         type OrderCumulativeSum inherits Decimal
         type OrderStatus inherits String
         type RequestedQuantity inherits Decimal



         model OrderSent {
            @Id
            sentOrderId : SentOrderId
            direction: OrderBankDirection
            requestedQty: RequestedQuantity
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
           brokerOrderBy: BrokerOrderBuy
           brokerOrderSell: BrokerOrderSell
           marketTradeId: MarketTradeId
           status: OrderStatus
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              executedQuantity: DecimalFieldOrderFilled
              orderCumulativeSum: OrderCumulativeSum
              buyCumulativeSumOrdered: BuyCumulativeSumOrdered
              sellCumulativeSumOrdered: SellCumulativeSumOrdered
              sellCumulativeQuantity: SellCumulativeQuantity
              buyCumulativeQuantity: BuyCumulativeQuantity
              cumulativeQty: CumulativeQuantity
              status: OrderStatus
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId

              executedQuantity: OrderFill::DecimalFieldOrderFilled
              orderCumulativeSum: OrderCumulativeSum by sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill:: FillOrderId, OrderFill::MarketTradeId)
              buyCumulativeSumOrdered: BuyCumulativeSumOrdered by sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::BrokerOrderBuy, OrderFill::MarketTradeId)
              sellCumulativeSumOrdered: SellCumulativeSumOrdered by sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::BrokerOrderSell, OrderFill::MarketTradeId)
              sellCumulativeQuantity: SellCumulativeQuantity by when {
                       OrderFill::BrokerOrderSell != null -> sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::BrokerOrderSell, OrderFill::MarketTradeId)
                       else -> (OrderView::OrderCumulativeSum - OrderView::BuyCumulativeSumOrdered)
              }
              buyCumulativeQuantity: BuyCumulativeQuantity by when {
                  OrderFill::BrokerOrderBuy != null -> sumOver(OrderFilled::DecimalFieldOrderFilled, OrderFilled::BrokerOrderBuy, OrderFilled::MarketTradeId)
                  else -> (OrderView::OrderCumulativeSum - OrderView::SellCumulativeSumOrdered)
               }
              cumulativeQuantity: CumulativeQuantity by when {
                OrderSent::OrderBankDirection = "BankBuys" -> (OrderView::BuyCumulativeQuantity - OrderView::SellCumulativeQuantity)
                else -> (OrderView::SellCumulativeQuantity - OrderView::BuyCumulativeQuantity)
                }
              status: OrderStatus by when {
                OrderSent::RequestedQuantity = OrderView::CumulativeQuantity -> OrderFill::OrderStatus
                else -> "PartiallyFilled"
              }
            }
         }
   """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
        errors.should.be.empty
      }

      it("should allow view field references on the left hand side of when statements if the referenced field has a function accessor only") {
         val src = """
         import vyne.aggregations.sumOver
         type SentOrderId inherits String
         type FillOrderId inherits String
         type DecimalFieldOrderFilled inherits Decimal
         type CumulativeQuantity inherits Decimal
         type LeavesQty inherits Decimal



         model OrderSent {
            @Id
            sentOrderId : SentOrderId
         }

         model OrderFill {
           @Id
           fillOrderId: FillOrderId
           executedQuantity: DecimalFieldOrderFilled? by column("Quantity")
         }

          [[
           Sample View
          ]]
         view OrderView with query {
            find { OrderSent[] } as {
              orderId: OrderSent::SentOrderId
              executedQuantity: DecimalFieldOrderFilled
              cumulativeQty: CumulativeQuantity
              leavesQty: LeavesQty
            },
            find { OrderSent[] (joinTo OrderFill[]) } as {
              orderId: OrderFill::FillOrderId
              executedQuantity: DecimalFieldOrderFilled by when {
                 OrderView::CumulativeQuantity = null -> 0
                 else -> OrderView::CumulativeQuantity
              }
              cumulativeQty: CumulativeQuantity by sumOver(OrderFill::DecimalFieldOrderFilled, OrderFill::FillOrderId)
              LeavesQty:LeavesQty by when {
                 OrderSent::SentOrderId = OrderFill::FillOrderId -> null
                 else -> null
              }
            }
         }
   """.trimIndent()
         val (errors, _) = Compiler(src).compileWithMessages()
         errors.should.be.empty
      }
   }
})
