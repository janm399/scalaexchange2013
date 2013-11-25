package core

import akka.actor.{Props, ActorSystem}
import scala.annotation.tailrec
import spray.can.Http
import akka.io.IO
import spray.http._
import scala.Some
import spray.http.HttpRequest
import scala.Some

object Main extends App {
  import Commands._
  import akka.actor.ActorDSL._

  def twitterSearchProxy(query: String) = s"http://twitter-search-proxy.herokuapp.com/search/tweets?q=$query"

  implicit lazy val system = ActorSystem()
  lazy val io = IO(Http)
  val scan = system.actorOf(Props(new RealTweetScannerActor(io)))
  implicit val _ = actor(new Act {
    become {
      case x => println(">>> " + x)
    }
  })

  @tailrec
  private def commandLoop(): Unit = {
    Console.readLine() match {
      case QuitCommand                => return

      case ScanCommand(query)         => scan ! query

      case _                          => println("WTF??!!")
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

  val ListCommand  = "list (\\d+)".r
  val CountCommand = "count"
  val QuitCommand  = "quit"
  val ScanCommand  = "scan (.*)".r

}
