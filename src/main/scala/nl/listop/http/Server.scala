package nl.listop.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.mongodb.ServerAddress
import com.mongodb.async.client

import scala.io.StdIn
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import nl.listop.domain.ListItem
import org.bson.Document
import org.mongodb.scala.connection.ClusterSettings
import org.mongodb.scala.{Completed, MongoClient, MongoClientSettings, MongoCollection, MongoCredential, Observer}
import spray.json.{DefaultJsonProtocol, JsonFormat}

case class Foo(i: Int, foo: Foo)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat: JsonFormat[ListItem] = lazyFormat(jsonFormat(ListItem, "item", "parent"))
  //implicit val fooFormat: JsonFormat[Foo] = lazyFormat(jsonFormat(Foo, "i", "foo"))

}
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{fromRegistries, fromProviders}

object mongo {
  var creds = new java.util.ArrayList[MongoCredential]
  creds.add(MongoCredential.createCredential("jeroen", "test", Array('n','e', 'o','r','e','j')))
    var servers = new java.util.ArrayList[ServerAddress]
  servers.add(new ServerAddress("localhost", 27017))
  val clusterSettings: ClusterSettings = ClusterSettings.builder.hosts(servers).build()
  var mongoClient =  MongoClient(MongoClientSettings.builder().credentialList(creds).clusterSettings(clusterSettings).build())

  def getDatabaseNames(): List[String] = {
    var result = List[String]()
    var database = mongoClient.getDatabase("test")
    val collection: MongoCollection[Document] = database.getCollection("users");
    collection.find().subscribe((user: Document) => println(user.toJson))
    result
  }
}
//
//val codecRegistry = fromRegistries(fromProviders(classOf[ListItem]), DEFAULT_CODEC_REGISTRY )

object Server extends JsonSupport {
  val mongoClient = MongoClient()
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher
    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~
      path("store") {
        post {
//          entity(as[List[Foo]]) { f =>
//            println(f.size)
//            complete("f.i")
//          }
          entity(as[List[ListItem]]) {list =>
            { val result = list.mkString(", ")
              println(s"collection: ${mongo.getDatabaseNames().mkString(", ")}")
              println(s"result $result")
            complete(result) }
          }
        }
      }

    val config = ConfigFactory.load()
    val port = config.getInt("http.port")
    val bindingFuture = Http().bindAndHandle(route
        ,config.getString("http.interface"), port)
    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

