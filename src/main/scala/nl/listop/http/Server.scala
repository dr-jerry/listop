package nl.listop.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.io.StdIn
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import spray.json.DefaultJsonProtocol

object Server extends SprayJsonSupport with DefaultJsonProtocol {
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
          entity(as[List[String]]) {list =>
            complete(list.mkString(", "))
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

