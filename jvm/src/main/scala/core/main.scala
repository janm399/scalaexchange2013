package core

import akka.actor.{Props, ActorSystem}
import scala.annotation.tailrec
import spray.can.Http
import akka.io.IO

object Main extends App {
  import Commands._
  import akka.actor.ActorDSL._

  val system = ActorSystem()
  val sentiment = system.actorOf(Props(new SentimentAnalysisActor with CSVLoadedSentimentSets with AnsiConsoleSentimentOutput))
  val stream = system.actorOf(Props(new TweetStreamerActor(TweetStreamerActor.twitterUri, sentiment) with OAuthTwitterAuthorization))

  @tailrec
  private def commandLoop(): Unit = {
    Console.readLine() match {
      case QuitCommand         => return
      case TrackCommand(query) => stream ! query
      case _                   => println("WTF??!!")
    }

    commandLoop()
  }

  // start processing the commands
  commandLoop()

  system.shutdown()
}

/**
 * Various regexes for the shell to use
 */
object Commands {

  val QuitCommand   = "quit"
  val TrackCommand = "track (.*)".r

}
