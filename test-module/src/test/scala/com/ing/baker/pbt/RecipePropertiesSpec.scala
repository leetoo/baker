package com.ing.baker.pbt

import java.io.{File, PrintWriter}

import com.ing.baker.compiler.RecipeCompiler
import com.ing.baker.il.CompiledRecipe
import com.ing.baker.recipe.common
import com.ing.baker.recipe.common.{FiresOneOfEvents, InteractionOutput, ProvidesIngredient, ProvidesNothing}
import com.ing.baker.recipe.scaladsl.{Event, Ingredient, Interaction, InteractionDescriptor, Recipe}
import org.scalacheck.{Gen, Test}
import org.scalacheck.Prop.forAll
import org.scalatest.FunSuite
import org.scalatest.prop.Checkers

class RecipePropertiesSpec extends FunSuite with Checkers {

  import RecipePropertiesSpec._

  test("baker can compile any small or huge recipe") {
    val prop = forAll(recipeGen) { recipe =>

      logRecipeStats(recipe)

      val compiledRecipe = RecipeCompiler.compileRecipe(recipe)

      logCompiledRecipeStats(compiledRecipe)

      if (compiledRecipe.validationErrors.nonEmpty) {
        println(s"Validation errors: ${compiledRecipe.validationErrors}")
        dumpVisualRecipe(recipeVisualizationOutputPath, compiledRecipe)
      }

      compiledRecipe.validationErrors.isEmpty
    }

    check(prop, Test.Parameters.defaultVerbose)
  }

}

object RecipePropertiesSpec {

  // where to output the .dot files of the generated recipe visualizations
  val recipeVisualizationOutputPath: String = System.getProperty("java.io.tmpdir")

  val ingredientGen: Gen[Ingredient[_]] = for (name <- Gen.uuid) yield Ingredient[String](s"ingredient-$name")

  val eventGen: Gen[Event] = for {
    name <- Gen.uuid
    //    ingredients <- Gen.listOf(ingredientGen)
    nrOfIngredients <- Gen.choose(0, 10)
    ingredients <- Gen.listOfN(nrOfIngredients, ingredientGen)
  } yield Event(s"event-$name", ingredients)

  val interactionOutputGen: Gen[InteractionOutput] = for {
  //    events <- Gen.listOf(eventGen)
    nrOfEvents <- Gen.choose(0, 10)
    events <- Gen.listOfN(nrOfEvents, eventGen)
    ingredient <- ingredientGen
    output <- Gen.oneOf(ProvidesNothing(), FiresOneOfEvents(events), ProvidesIngredient(ingredient))
  } yield output

  def interactionGen(ingredients: Seq[common.Ingredient]): Gen[Interaction] = for {
    name <- Gen.uuid
    output <- interactionOutputGen
  } yield Interaction(s"interaction-$name", ingredients, output)

  def interactionDescriptorGen(ingredients: Seq[common.Ingredient]): Gen[InteractionDescriptor] = for {
    interaction <- interactionGen(ingredients)
  } yield InteractionDescriptor(interaction)

  val recipeGen: Gen[Recipe] = for {
    name <- Gen.uuid
    //    sensoryEvents <- Gen.listOf(eventGen)
    nrOfSensoryEvents <- Gen.choose(0, 10)
    sensoryEvents <- Gen.listOfN(nrOfSensoryEvents, eventGen)
    allProvidedIngredients = sensoryEvents.flatMap(_.providedIngredients)
    inputIngredients <- Gen.someOf(allProvidedIngredients) if inputIngredients.nonEmpty
    interactionDescriptors <- Gen.listOf(interactionDescriptorGen(inputIngredients))
  } yield Recipe(s"recipe-$name")
    .withSensoryEvents(sensoryEvents: _*)
    .withInteractions(interactionDescriptors: _*)

  def dumpVisualRecipe(dumpDir: String, compiledRecipe: CompiledRecipe): Unit = {
    val fileName =
      if (dumpDir endsWith "/") s"$dumpDir${compiledRecipe.name}.dot"
      else s"$dumpDir/${compiledRecipe.name}.dot"

    val outFile = new File(fileName)
    val writer = new PrintWriter(outFile)

    try {
      println(s"Generating the visual recipe ...")
      val dotRepresentation = compiledRecipe.getRecipeVisualization
      writer.write(dotRepresentation)
      println(s"Recipe visualization in bytes: ${dotRepresentation.length}. Dump location: $fileName")
    } finally {
      writer.close()
    }
  }

  def logRecipeStats(recipe: Recipe): Unit = println(s"Generated recipe ::: " +
    s"name: ${recipe.name} " +
    s"nrOfSensoryEvents: ${recipe.sensoryEvents.size} " +
    s"nrOfInteractions: ${recipe.interactions.size} " +
    s"")

  def logCompiledRecipeStats(compiledRecipe: CompiledRecipe): Unit = println(s"Compiled recipe ::: " +
    s"name: ${compiledRecipe.name} " +
    s"nrOfAllIngredients: ${compiledRecipe.ingredients.size} " +
    s"nrOfSensoryEvents: ${compiledRecipe.sensoryEvents.size} " +
    s"nrOfInteractionEvents: ${compiledRecipe.interactionEvents.size} " +
    s"nrOfInteractions: ${compiledRecipe.interactionTransitions.size} " +
    s"")

}
