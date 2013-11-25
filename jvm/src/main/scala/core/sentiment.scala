package core

import akka.actor.Actor
import domain.Tweet
import scala.io.Source
import scala.collection.parallel.ParSet

trait SentimentSets {
  def positiveWords: ParSet[String]
  def negativeWords: ParSet[String]
}

trait SentimentOutput {
  type Category = String

  def outputCount(values: List[Iterable[(Category, Int)]]): Unit
}

trait AnsiConsoleSentimentOutput extends SentimentOutput {

  case class AnsiString(ansiText: String, realLength: Int) {
    def +(that: AnsiString) = AnsiString(ansiText + that.ansiText, realLength + that.realLength)
    def +(that: String)     = AnsiString(ansiText + that, realLength + that.length)
    override def toString = ansiText
  }
  object AnsiString {
    def apply(text: String): AnsiString = AnsiString(text, text.length)
    def zero = apply("")
  }

  object AnsiControls {
    val Reset        = "\u001B[0m"
    val EraseDisplay = "\033[2J\033[;H"
    val EraseLine    = "\033[2K\033[;H"
    val Bold         = "\u001B[1m"
    def goto(x: Int, y: Int): String = "\u001B[%d;%df" format (y, x)
  }

  object AnsiColors {
    val Red    = "\u001B[31m"
    val Yellow = "\u001B[33m"
    val Cyan   = "\u001B[36m"
    val White  = "\u001B[37m"
  
    val Green  = "\u001B[32m"
    val Purple = "\u001B[35m"
    val Blue   = "\u001B[34m"

    val allColors = List(White, Purple, Blue, Red, Yellow, Cyan, Green)
  }

  val categoryPadding = 30
  val consoleWidth = 80

  println(AnsiControls.EraseDisplay)

  def outputCount(allValues: List[Iterable[(Category, Int)]]): Unit = {
    print(AnsiControls.EraseDisplay)
    allValues.foreach { values =>
      values.zipWithIndex.foreach {
        case ((k, v), i) =>
          val color = AnsiColors.allColors(i % AnsiColors.allColors.size)
          print(color)
          print(AnsiControls.Bold)
          print(k)
          print(" " * (categoryPadding - k.length))
          print(AnsiControls.Reset)
          println(v)
      }
      println("-" * consoleWidth)
    }
  }
}

trait CSVLoadedSentimentSets extends SentimentSets {
  lazy val positiveWords = loadWords("/positive_words.csv")
  lazy val negativeWords = loadWords("/negative_words.csv")

  private def loadWords(fileName: String): ParSet[String] = {
    Source.
      fromInputStream(getClass.getResourceAsStream(fileName)).
      getLines().
      map(line => line.split(",")(1)).
      toSet.
      par
  }
}

class SentimentAnalysisActor extends Actor {
  this: SentimentSets with SentimentOutput =>
  
  private val counts = collection.mutable.Map[Category, Int]()
  private val languages = collection.mutable.Map[Category, Int]()

  private def update(data: collection.mutable.Map[Category, Int])(category: Category, delta: Int): Unit = data.put(category, data.getOrElse(category, 0) + delta)
  val updateCounts = update(counts)_
  val updateLanguages = update(languages)_

  def receive: Receive = {
    case tweet: Tweet =>
      val positive: Int = if (positiveWords.exists(word => tweet.text.toLowerCase contains word)) 1 else 0
      val negative: Int = if (negativeWords.exists(word => tweet.text.toLowerCase contains word)) 1 else 0

      updateCounts("positive", positive)
      updateCounts("negative", negative)
      if (tweet.user.followersCount > 200) {
        updateCounts("positive.gurus", positive)
        updateCounts("negative.gurus", negative)
      }
      updateCounts("all", 1)
      updateLanguages(tweet.user.lang, 1)
    
      outputCount(List(counts, languages))
  }
}
