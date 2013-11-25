package core

import akka.actor.{Props, ActorSystem}
import scala.annotation.tailrec
import spray.can.Http
import akka.io.IO
import domain.{User, Tweet}
import java.util.Date

object Main extends App {
  import Commands._
  import akka.actor.ActorDSL._

  implicit lazy val system = ActorSystem()
  lazy val io = IO(Http)
  val sentiment = system.actorOf(Props(new SentimentAnalysisActor with CSVLoadedSentimentSets with AnsiConsoleSentimentOutput))
  val scan = system.actorOf(Props(new TweetStreamerActor(io, TweetStreamerActor.twitterUri, sentiment) with OAuthTwitterAuthorization))

  sentiment ! Tweet("123", User("23", "en", 400), "All is jolly good", new Date())
  sentiment ! Tweet("123", User("23", "en", 100), "All is jolly good", new Date())

  @tailrec
  private def commandLoop(): Unit = {
    Console.readLine() match {
      case QuitCommand          => return
      case StreamCommand(query) => scan ! query
      case _                    => println("WTF??!!")
    }

    commandLoop()
  }

  // start processing the commands
  commandLoop()

  system.shutdown()
}

/**
 * Various regexes for the ``Shell`` to use
 */
object Commands {

  val QuitCommand   = "quit"
  val StreamCommand = "stream (.*)".r

}
