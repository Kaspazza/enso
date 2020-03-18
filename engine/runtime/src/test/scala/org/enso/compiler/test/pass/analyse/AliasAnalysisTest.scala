package org.enso.compiler.test.pass.analyse

import org.enso.compiler.core.IR
import org.enso.compiler.core.IR.Module.Scope.Definition.{Atom, Method}
import org.enso.compiler.pass.IRPass
import org.enso.compiler.pass.analyse.AliasAnalysis
import org.enso.compiler.pass.analyse.AliasAnalysis.{Graph, Info}
import org.enso.compiler.pass.analyse.AliasAnalysis.Graph.{Link, Occurrence}
import org.enso.compiler.pass.desugar.{LiftSpecialOperators, OperatorToFunction}
import org.enso.compiler.test.CompilerTest

class AliasAnalysisTest extends CompilerTest {

  // === Utilities ============================================================

  /** Adds an extension method to preprocess the source as IR.
    *
    * @param source the source code to preprocess
    */
  implicit class Preprocess(source: String) {
    val precursorPasses: List[IRPass] = List(
      LiftSpecialOperators,
      OperatorToFunction
    )

    /** Translates the source code into appropriate IR for testing this pass.
      *
      * @return IR appropriate for testing the alias analysis pass as a module
      */
    def preprocessModule: IR.Module = {
      source.toIrModule.runPasses(precursorPasses).asInstanceOf[IR.Module]
    }

    /** Translates the source code into appropriate IR for testing this pass
      *
      * @return IR appropriate for testing the alias analysis pass as an
      *         expression
      */
    def preprocessExpression: Option[IR.Expression] = {
      source.toIrExpression.map(
        _.runPasses(precursorPasses).asInstanceOf[IR.Expression]
      )
    }
  }

  /** Adds an extension method to run alias analysis on an [[IR.Module]].
    *
    * @param ir the module to run alias analysis on
    */
  implicit class AnalyseModule(ir: IR.Module) {

    /** Runs alias analysis on a module.
      *
      * @return [[ir]], with attached aliasing information
      */
    def analyse: IR.Module = {
      AliasAnalysis.runModule(ir)
    }
  }

  /** Adds an extension method to run alias analusis on an [[IR.Expression]].
    *
    * @param ir the expression to run alias analysis on
    */
  implicit class AnalyseExpression(ir: IR.Expression) {

    /** Runs alias analysis on an expression.
      *
      * @return [[ir]], with attached aliasing information
      */
    def analyse: IR.Expression = {
      AliasAnalysis.runExpression(ir)
    }
  }

  // === The Tests ============================================================

  "The analysis scope" should {
    val graph = new Graph()

    val flatScope = new Graph.Scope()

    val complexScope        = new Graph.Scope()
    val child1              = complexScope.addChild()
    val child2              = complexScope.addChild()
    val childOfChild        = child1.addChild()
    val childOfChildOfChild = childOfChild.addChild()

    val aDefId = graph.nextId()
    val aDef   = Occurrence.Def(aDefId, "a")

    val bDefId = graph.nextId()
    val bDef   = Occurrence.Def(bDefId, "b")

    val aUseId = graph.nextId()
    val aUse   = Occurrence.Use(aUseId, "a")

    val bUseId = graph.nextId()
    val bUse   = Occurrence.Use(bUseId, "b")

    val cUseId = graph.nextId()
    val cUse   = Occurrence.Use(cUseId, "c")

    // Add occurrences to the scopes
    complexScope.add(aDef)
    child2.add(cUse)
    childOfChild.add(bDef)
    childOfChild.add(bUse)
    childOfChildOfChild.add(aUse)

    "have a number of scopes of 1 without children" in {
      flatScope.scopeCount shouldEqual 1
    }

    "have a nesting level of 1 without children" in {
      flatScope.maxNesting shouldEqual 1
    }

    "have the correct number of scopes with children" in {
      complexScope.scopeCount shouldEqual 5
    }

    "have the correct nesting depth with children" in {
      complexScope.maxNesting shouldEqual 4
    }

    "allow correctly getting the n-th parent" in {
      childOfChildOfChild.nThParent(2) shouldEqual Some(child1)
    }

    "return `None` for nonexistent parents" in {
      childOfChildOfChild.nThParent(10) shouldEqual None
    }

    "find the occurrence for an ID in the current scope if it exists" in {
      complexScope.getOccurrence(aDefId) shouldEqual Some(aDef)
    }

    "find the occurrence for an name in the current scope if it exists" in {
      complexScope.getOccurrences[Occurrence](aDef.symbol) shouldEqual Set(aDef)
    }

    "find no occurrences if they do not exist" in {
      complexScope.getOccurrence(cUseId) shouldEqual None
    }

    "correctly resolve usage links where they exist" in {
      childOfChild.resolveUsage(bUse) shouldEqual Some(Link(bUseId, 0, bDefId))
      childOfChildOfChild.resolveUsage(aUse) shouldEqual Some(
        Link(aUseId, 3, aDefId)
      )
    }

    "correctly find the scope where a given ID occurs" in {
      complexScope.scopeFor(aUseId) shouldEqual Some(childOfChildOfChild)
    }

    "correctly find the scopes in which a given symbol occurs" in {
      complexScope.scopesForSymbol[Occurrence.Def]("a").length shouldEqual 1
      complexScope.scopesForSymbol[Occurrence.Use]("a").length shouldEqual 1

      complexScope.scopesForSymbol[Occurrence]("a").length shouldEqual 2

      complexScope.scopesForSymbol[Occurrence]("a") shouldEqual List(
        complexScope,
        childOfChildOfChild
      )
    }

    "return the correct set of symbols" in {
      complexScope.symbols shouldEqual Set("a", "b", "c")
    }

    "check correctly for specified occurrences of a symbol" in {
      complexScope.hasSymbolOccurrenceAs[Occurrence.Def]("a") shouldEqual true
      complexScope.hasSymbolOccurrenceAs[Occurrence]("b") shouldEqual false
    }

    "be able to convert from a symbol to identifiers that use it" in {
      complexScope.symbolToIds[Occurrence]("a") shouldEqual List(aDefId, aUseId)
    }

    "be able to convert from an identifier to the associated symbol" in {
      complexScope.idToSymbol(aDefId) shouldEqual Some("a")
    }

    "be able to check if a provided scope is a child of the current scope" in {
      pending
      child1.isChildOf(complexScope) shouldEqual true
      child2.isChildOf(complexScope) shouldEqual true
      childOfChild.isChildOf(complexScope) shouldEqual true

      complexScope.isChildOf(child1) shouldEqual false
    }
  }

  "The Aliasing graph" should {
    val graph = new Graph()

    val rootScope  = graph.rootScope
    val childScope = rootScope.addChild()

    val aDefId = graph.nextId()
    val aDef   = Occurrence.Def(aDefId, "a")

    val bDefId = graph.nextId()
    val bDef   = Occurrence.Def(bDefId, "b")

    val aUse1Id = graph.nextId()
    val aUse1   = Occurrence.Use(aUse1Id, "a")

    val aUse2Id = graph.nextId()
    val aUse2   = Occurrence.Use(aUse2Id, "a")

    val cUseId = graph.nextId()
    val cUse   = Occurrence.Use(cUseId, "c")

    rootScope.add(aDef)
    rootScope.add(aUse1)
    rootScope.add(bDef)

    childScope.add(aUse2)
    childScope.add(cUse)

    val use1Link = graph.resolveUsage(aUse1)
    val use2Link = graph.resolveUsage(aUse2)
    val cUseLink = graph.resolveUsage(cUse)

    "generate monotonically increasing identifiers" in {
      val ids       = List.fill(100)(graph.nextId())
      var currentId = ids.head - 1

      ids.forall(id => {
        currentId += 1
        currentId == id
      }) shouldEqual true
    }

    "correctly resolve usages where possible" in {
      use1Link shouldBe defined
      use2Link shouldBe defined
      cUseLink shouldBe empty

      use1Link.foreach { link =>
        {
          link.source shouldEqual aUse1Id
          link.target shouldEqual aDefId
          link.scopeCount shouldEqual 0
        }
      }

      use2Link.foreach { link =>
        {
          link.source shouldEqual aUse2Id
          link.target shouldEqual aDefId
          link.scopeCount shouldEqual 1
        }
      }
    }

    "gather all links for a given ID correctly" in {
      val linksForA = graph.linksFor(aDefId)
      val linksForC = graph.linksFor(cUseId)

      linksForA.size shouldEqual 2
      linksForC shouldBe empty

      linksForA should contain(use1Link.get)
      linksForA should contain(use2Link.get)
    }

    "find the scope where a given ID is defined" in {
      val scopeForA       = graph.scopeFor(aDefId)
      val scopeForUndefId = graph.scopeFor(100)

      scopeForA shouldBe defined
      scopeForUndefId shouldBe empty

      scopeForA shouldEqual Some(rootScope)
    }

    "find all scopes where a given symbol occurs" in {
      val aDefs = graph.scopesFor[Occurrence.Def]("a")
      val aUses = graph.scopesFor[Occurrence.Use]("a")
      val aOccs = graph.scopesFor[Occurrence]("a")
      val dOccs = graph.scopesFor[Occurrence]("d")

      aDefs.length shouldEqual 1
      aUses.length shouldEqual 2
      aOccs.length shouldEqual 2
      dOccs.length shouldEqual 0

      aDefs shouldEqual List(rootScope)
      aUses shouldEqual List(rootScope, childScope)
      aOccs shouldEqual List(rootScope, childScope)
    }

    "correctly determine the number of scopes in the graph" in {
      graph.numScopes shouldEqual 2
    }

    "correctly determine the maximum level of nesting in the graph" in {
      graph.nesting shouldEqual 2
    }

    "correctly determines whether an occurrence shadows other bindings" in {
      graph.shadows(aDefId) shouldEqual true
      graph.shadows("a") shouldEqual true
      graph.shadows(aUse1Id) shouldEqual false
      graph.shadows("c") shouldEqual false
    }

    "correctly determine all symbols that occur in the graph" in {
      graph.symbols shouldEqual Set("a", "b", "c")
    }

    "correctly return all links for a given symbol" in {
      graph.linksFor[Occurrence]("a") shouldEqual Set(
        use1Link.get,
        use2Link.get
      )
    }
  }

  "Alias analysis for atom definitions" should {
    val goodAtom =
      """
        |type MyAtom a b (c=a)
        |""".stripMargin.preprocessModule.analyse.bindings.head
        .asInstanceOf[Atom]
    val goodMeta  = goodAtom.getMetadata[AliasAnalysis.Info.Scope.Root]
    val goodGraph = goodMeta.get.graph

    val badAtom =
      """
        |type MyAtom a=b b
        |""".stripMargin.preprocessModule.analyse.bindings.head
        .asInstanceOf[Atom]
    val badMeta  = badAtom.getMetadata[AliasAnalysis.Info.Scope.Root]
    val badGraph = badMeta.get.graph

    "assign Info.Scope.Root metadata to the atom" in {
      goodMeta shouldBe defined
      goodMeta.get shouldBe a[Info.Scope.Root]
    }

    "create definition occurrences in the atom's scope" in {
      goodGraph.rootScope.hasSymbolOccurrenceAs[Occurrence.Def]("a")
      goodGraph.rootScope.hasSymbolOccurrenceAs[Occurrence.Def]("b")
      goodGraph.rootScope.hasSymbolOccurrenceAs[Occurrence.Def]("c")
    }

    "create defaults in the same scope as the argument" in {
      goodGraph.nesting shouldEqual 1
      goodGraph.rootScope.hasSymbolOccurrenceAs[Occurrence.Use]("a")
    }

    "create usage links where valid" in {
      val aDefId = goodAtom
        .arguments(0)
        .getMetadata[Info.Occurrence]
        .get
        .id
      val aUseId = goodAtom
        .arguments(2)
        .defaultValue
        .get
        .getMetadata[Info.Occurrence]
        .get
        .id

      goodGraph.links should contain(Link(aUseId, 0, aDefId))
    }

    "enforce the ordering scope constraint on function arguments" in {
      badGraph.links shouldBe empty
    }
  }

  "Alias analysis on function methods" should {
    val methodWithLambda =
      """
        |Bar.foo = a b c ->
        |   d = a b -> a b
        |   g =
        |     1 + 1
        |   d c (a + b)
        |""".stripMargin.preprocessModule.analyse.bindings.head
        .asInstanceOf[Method]
    val methodWithLambdaGraph =
      methodWithLambda.getMetadata[Info.Scope.Root].get.graph

    "assign Info.Scope.Root metadata to the method" in {
      val meta = methodWithLambda.getMetadata[AliasAnalysis.Metadata]

      meta shouldBe defined
      meta.get shouldBe a[Info.Scope.Root]
    }

    "assign Info.Scope.Child to all child scopes" in {
      pending
      methodWithLambda.body
        .asInstanceOf[IR.Function.Lambda]
        .getMetadata[Info.Scope.Child]
        .get shouldBe an[Info.Scope.Child]

      methodWithLambda.body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]
        .getMetadata[Info.Scope.Child]
        .get shouldBe an[Info.Scope.Child]
    }

    "not allocate additional scopes unnecessarily" in {
      pending
      methodWithLambdaGraph.nesting shouldEqual 2
      methodWithLambdaGraph.numScopes shouldEqual 3

      val blockChildLambdaScope =
        methodWithLambda.body
          .asInstanceOf[IR.Function.Lambda]
          .getMetadata[Info.Scope.Child]
          .get
          .scope
      val blockChildBlockScope =
        methodWithLambda.body
          .asInstanceOf[IR.Function.Lambda]
          .body
          .asInstanceOf[IR.Expression.Block]
          .getMetadata[Info.Scope.Child]
          .get
          .scope

      blockChildBlockScope shouldEqual methodWithLambdaGraph.rootScope
      blockChildLambdaScope shouldEqual methodWithLambdaGraph.rootScope
    }

    "allocate new scopes where necessary" in {
      pending
    }

    "assign Info.Occurrence to definitions and usages of symbols" in {
      pending
    }

    "create the correct usage links for resolvable entities" in {
      pending
    }

    "not resolve links for unknwon symbols" in {
      pending
    }
  }

  "Alias analysis on block methods" should {
    val methodWithBlock =
      """
        |Bar.foo =
        |  a = 2 + 2
        |  b = a * a
        |
        |  IO.println b
        |""".stripMargin.preprocessModule.analyse.bindings.head
        .asInstanceOf[Method]
    val methodWithBlockGraph =
      methodWithBlock.getMetadata[Info.Scope.Root].get.graph

    "assign Info.Scope.Root metadata to the method" in {
      val meta1 = methodWithBlock.getMetadata[AliasAnalysis.Metadata]

      meta1 shouldBe defined
      meta1.get shouldBe a[Info.Scope.Root]
    }

    "assign Info.Scope.Child to all child scopes" in {
      methodWithBlock.body
        .asInstanceOf[IR.Function.Lambda]
        .getMetadata[Info.Scope.Child]
        .get shouldBe an[Info.Scope.Child]

      methodWithBlock.body
        .asInstanceOf[IR.Function.Lambda]
        .body
        .asInstanceOf[IR.Expression.Block]
        .getMetadata[Info.Scope.Child]
        .get shouldBe an[Info.Scope.Child]
    }

    "not allocate additional scopes unnecessarily" in {
      pending
      methodWithBlockGraph.nesting shouldEqual 1
      methodWithBlockGraph.numScopes shouldEqual 1

      val blockChildLambdaScope =
        methodWithBlock.body
          .asInstanceOf[IR.Function.Lambda]
          .getMetadata[Info.Scope.Child]
          .get
          .scope
      val blockChildBlockScope =
        methodWithBlock.body
          .asInstanceOf[IR.Function.Lambda]
          .body
          .asInstanceOf[IR.Expression.Block]
          .getMetadata[Info.Scope.Child]
          .get
          .scope

      blockChildBlockScope shouldEqual methodWithBlockGraph.rootScope
      blockChildLambdaScope shouldEqual methodWithBlockGraph.rootScope
    }

    "assign Info.Occurrence to definitions and usages of symbols" in {
      pending
    }

    "create the correct usage links for resolvable entities" in {
      pending
    }

    "not resolve links for unknwon symbols" in {
      pending
    }
  }

  "Alias analysis on case expressions" should {}

  "Redefinitions" should {
    "be caught for argument lists" in {
      val atom =
        """
          |type MyAtom a b a
          |""".stripMargin.preprocessModule.analyse.bindings.head
          .asInstanceOf[Atom]

      atom.arguments(2) shouldBe an[IR.Error.Redefined.Argument]
      atLeast(1, atom.arguments) shouldBe an[IR.Error.Redefined.Argument]
    }

    "be caught for bindings" in {
      val method =
        """
          |main =
          |    a = 1 + 1
          |    b = a * 10
          |    a = b + 1
          |
          |    IO.println a
          |""".stripMargin.preprocessModule.analyse.bindings.head
          .asInstanceOf[Method]
      val block =
        method.body
          .asInstanceOf[IR.Function.Lambda]
          .body
          .asInstanceOf[IR.Expression.Block]

      block.expressions(2) shouldBe an[IR.Error.Redefined.Binding]
      atLeast(1, block.expressions) shouldBe an[IR.Error.Redefined.Binding]
    }
  }
}
