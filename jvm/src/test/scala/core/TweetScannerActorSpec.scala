package core

import akka.actor.ActorSystem
import org.specs2.mutable.SpecificationLike
import akka.testkit.{TestActorRef, TestKit, ImplicitSender}
import domain.Tweet
import spray.http.Uri
import spray.can.Http
import akka.io.IO

class TweetScannerActorSpec extends TestKit(ActorSystem()) with SpecificationLike with ImplicitSender {
  sequential

  val port = 12345
  val tweetStream = TestActorRef(new TweetStreamerActor(IO(Http), Uri(s"http://localhost:$port/"), testActor))

  "Getting all 'typesafe' tweets" >> {

    "should receive the tweets" in {
      val twitterApi = TwitterApi(port)
      tweetStream ! "typesafe"
      Thread.sleep(1000)
      val tweet = expectMsgType[Tweet]
      tweet.text mustEqual "Aggressive Ponytail #freebandnames"
      tweet.user.lang mustEqual "en"
      tweet.user.id mustEqual "137238150"
      twitterApi.stop()
      success
    }
  }
}
