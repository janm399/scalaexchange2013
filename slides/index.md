#The main code
We begin with simple ``Main`` class. It is the main command loop.

#The actors
We then add the two actors we will be using:

```scala
object TweetStreamerActor {
  val twitterUri = Uri("https://stream.twitter.com/1.1/statuses/filter.json")
}

class TweetStreamerActor(uri: Uri, processor: ActorRef) extends Actor {
  def receive: Receive = ???
}
```

and

```scala
class SentimentAnalysisActor extends Actor {
  def receive: Receive = ???
}
```

#We use them in the main code
We instantiate the actors.

```scala
object Main extends App {
  import Commands._
  import akka.actor.ActorDSL._

  val system = ActorSystem()
  val sentiment = system.actorOf(Props(new SentimentAnalysisActor))
  val stream = system.actorOf(
    Props(new TweetStreamerActor(TweetStreamerActor.twitterUri, sentiment) 
      with OAuthTwitterAuthorization))

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
```

#The twitter stream actor
Next up, let's write the core of the ``TweetStreamerActor``: We construct the Akka IO, and then make the ``HttpRequest`` that we send to Twitter.

```scala
class TweetStreamerActor(uri: Uri, processor: ActorRef) extends Actor {
  val io = IO(Http)(context.system)
  
  def receive: Receive = {
    case query: String =>
      val post = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`), s"track=$query")
      val rq = HttpRequest(HttpMethods.POST, uri = uri, entity = post)
      sendTo(io).withResponsesReceivedBy(self)(rq)
    case ChunkedResponseStart(_) =>
    case MessageChunk(entity, _) =>
    case _ =>
  }
}
```

#Marshalling
Next up, we'll need to unmarshal the responses:

```scala
class TweetStreamerActor(uri: Uri, processor: ActorRef) extends Actor with TweetMarshaller {
  def receive: Receive = {
    case MessageChunk(entity, _) =>
      TweetUnmarshaller(entity) match {
        case Right(tweet) => processor ! tweet
        case _            =>
      }
  }
}
```

And then, the unmarshaller:

```scala
trait TweetMarshaller {

  implicit object TweetUnmarshaller extends Unmarshaller[Tweet] {

    def mkUser(user: JsObject): Deserialized[User] = {
      (user.fields("id_str"), user.fields("lang"), user.fields("followers_count")) match {
        case (JsString(id), JsString(lang), JsNumber(followers)) => Right(User(id, lang, followers.toInt))
        case (JsString(id), _, _)                                => Right(User(id, "", 0))
        case _                                                   => Left(MalformedContent("bad user"))
      }
    }

    def mkPlace(place: JsValue): Option[Place] = place match {
      case JsObject(fields) =>
        (fields.get("country"), fields.get("name")) match {
          case (Some(JsString(country)), Some(JsString(name))) => Some(Place(country, name))
          case _                                               => None
        }
      case _ => None
    }

    def apply(entity: HttpEntity): Deserialized[Tweet] = {
      Try {
        val json = JsonParser(entity.asString).asJsObject

        (json.fields.get("id_str"), json.fields.get("text"), json.fields.get("place"), json.fields.get("user")) match {
          case (Some(JsString(id)), Some(JsString(text)), place, Some(user: JsObject)) =>
            mkUser(user) match {
              case Right(user) => Right(Tweet(id, user, text, place.flatMap(mkPlace)))
              case Left(msg)   => Left(msg)
            }
          case _ => Left(MalformedContent("bad tweet"))
        }
      }
    }.getOrElse(Left(MalformedContent("bad json")))
  }

}
```

And then, we add the tests to verify that we've done our job properly:

```scala
class TweetStreamerActorSpec extends TestKit(ActorSystem()) with SpecificationLike with ImplicitSender {
  sequential

  val port = 12345
  val tweetStream = TestActorRef(new TweetStreamerActor(Uri(s"http://localhost:$port/"), testActor))

  "Getting all 'typesafe' tweets" >> {

    "should receive the tweets" in {
      val twitterApi = TwitterApi(port)
      tweetStream ! "typesafe"
      Thread.sleep(1000)
      val tweet = expectMsgType[Tweet]
      tweet.text mustEqual "Aggressive Ponytail #freebandnames"
      tweet.user.lang mustEqual "en"
      tweet.user.id mustEqual "137238150"
      tweet.place mustEqual None
      twitterApi.stop()
      success
    }
  }

}
```

#OAuth
Let's wire in authentication

```scala
class TweetStreamerActor(uri: Uri, processor: ActorRef) extends Actor with TweetMarshaller {
  this: TwitterAuthorization =>
  ...
  val rq = HttpRequest(HttpMethods.POST, uri = uri, entity = post) ~> authorize
```

With the trait

```scala
trait TwitterAuthorization {
  def authorize: HttpRequest => HttpRequest
}
```

And then have the OAuth implementation:

```scala
object OAuth {
  case class Consumer(key: String, secret: String)
  case class Token(value: String, secret: String)

  def oAuthAuthorizer(consumer: Consumer, token: Token): HttpRequest => HttpRequest = {
    identity
  }
}
```

And then have the OAuthTwitterAuthorization trait:

```scala
trait OAuthTwitterAuthorization extends TwitterAuthorization {
  import OAuth._
  val home = System.getProperty("user.home")
  val lines = Source.fromFile(s"$home/.twitter/scalaexchange2013").getLines().toList

  val consumer = Consumer(lines(0), lines(1))
  val token = Token(lines(2), lines(3))

  val authorize: (HttpRequest) => HttpRequest = oAuthAuthorizer(consumer, token)
}
```

And then apply the required trait in tests and main.

#Real OAuth
Then we implement the real OAuth implementation in ``OAuth``.

...

#C:\ C:\DOS C:\DOS\run
Let's see how it all fits together.

```scala
class SentimentAnalysisActor extends Actor {
  def receive: Receive = {
    case t: Tweet => println(t)
  }
}
```

#Big data
We need some sources of the data:

```scala
trait SentimentSets {
  def positiveWords: Set[String]
  def negativeWords: Set[String]
}
```

And a way to display the output

```scala
trait SentimentOutput {
  type Category = String

  def outputCount(values: List[Iterable[(Category, Int)]]): Unit
}
```

Some trivial output:

```scala
trait SimpleSentimentOutput extends SentimentOutput {
  def outputCount(values: List[Iterable[(Category, Int)]]): Unit = println(values)
}
```

Sentiment sets loaded from files:

```scala
trait CSVLoadedSentimentSets extends SentimentSets {
  lazy val positiveWords = loadWords("/positive_words.csv")
  lazy val negativeWords = loadWords("/negative_words.csv")

  private def loadWords(fileName: String): Set[String] = {
    Source.
      fromInputStream(getClass.getResourceAsStream(fileName)).
      getLines().
      map(line => line.split(",")(1)).
      toSet
  }
}
```

And require them in the ``SentimentAnalysisActor``.

```scala
class SentimentAnalysisActor extends Actor {
  this: SentimentSets with SentimentOutput =>
  private val counts = collection.mutable.Map[Category, Int]()
  private val languages = collection.mutable.Map[Category, Int]()
  private val places = collection.mutable.Map[Category, Int]()

  private def update(data: collection.mutable.Map[Category, Int])(category: Category, delta: Int): Unit = data.put(category, data.getOrElse(category, 0) + delta)
  val updateCounts = update(counts)_
  val updateLanguages = update(languages)_
  val updatePlaces = update(places)_

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
      updatePlaces(tweet.place.toString, 1)

      outputCount(List(counts, places, languages))
  }
}
```

#Monitoring
Show & tell. Datadog.