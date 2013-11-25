package core

import spray.httpx.unmarshalling.{MalformedContent, Unmarshaller, Deserialized}
import spray.http._
import spray.json._
import spray.client.pipelining._
import java.text.SimpleDateFormat
import akka.actor.{Props, ActorSystem, ActorRef, Actor}
import akka.io.IO
import spray.can.Http
import spray.http.HttpRequest
import scala.Some
import domain.Tweet

trait TweetMarshaller {
  type Tweets = List[Tweet]

  implicit object TweetUnmarshaller extends Unmarshaller[Tweets] {

    val dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss Z yyyy")

    def mkTweet(status: JsValue): Deserialized[Tweet] = {
      val json = status.asJsObject
      (json.fields.get("id_str"), json.fields.get("text"), json.fields.get("created_at"), json.fields.get("user")) match {
        case (Some(JsString(id)), Some(JsString(text)), Some(JsString(createdAt)), Some(JsObject(user))) =>
          user.get("id_str") match {
            case Some(JsString(userId)) => Right(Tweet(id, userId, text, dateFormat.parse(createdAt)))
            case _                      => Left(MalformedContent("Bad tweet JSON"))
          }
        case _                          => Left(MalformedContent("Bad status JSON"))
      }
    }

    def apply(entity: HttpEntity): Deserialized[Tweets] = {
      val json = JsonParser(entity.asString).asJsObject
      json.fields.get("statuses") match {
        case Some(JsArray(statuses)) => Right(statuses.map(t => mkTweet(t).right.get))
        case _                       => Left(MalformedContent("statuses missing"))
      }
    }
  }

}

class TweetScannerActor(tweetWrite: ActorRef, queryUrl: String => String) extends Actor with TweetMarshaller {
  import context.dispatcher
  import akka.pattern.pipe

  private val pipeline = sendReceive ~> unmarshal[Tweets]

  def receive: Receive = {
    case query: String => pipeline(Get(queryUrl(query))) pipeTo tweetWrite
  }
}

class RealTweetScannerActor(io: ActorRef) extends Actor with TweetMarshaller {
  val signer = OAuth.sign(OAuth.Consumer("hVdsWmYtTW4TnaUPVzqkA", "63Ir9LQVFV7CA0Oj69hs0Xvew6o88WWvXMN1qK6C0"),
    OAuth.Token("29976216-JP2lKH1CKzQ7G467dGh0TJ9WeGUjmnWQvZ5GwnBR2", "3ILPoYz0hjrFpMo33JxwqbJhfm52fF0qUBTybgUNqrZwD"))

  def receive: Receive = {
    case query: String =>
      val post = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = Uri("https://stream.twitter.com/1.1/statuses/filter.json"), entity = post)
      val signedRq = signer(rq)
      sendTo(io).withResponsesReceivedBy(self)(signedRq)
    case ChunkedResponseStart(_) =>
    case MessageChunk(entity, _) =>
      println(entity.asString)
    case x =>
      println("??? " + x)
  }
}
