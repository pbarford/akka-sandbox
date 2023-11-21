package com.flutter.akka

import akka.actor.ActorSystem
import com.flutter.akka.service.EntityIdService.EntityId
import com.flutter.akka.service.{AkkaHttpGenerator, ApacheHttpGenerator, EntityIdService}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object TestIdService extends App {

  val markets: List[Entity] = (1 to 12).map(id => Market(id, s"market-$id")).toList
  val selections: List[Entity] = (1 to 30).map(id => Selection(id, s"selection-$id")).toList

  import cats.implicits._


  private def runAkkaHttp(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("TestIdRequests")
    implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
    val akkaHttpGenerator = new AkkaHttpGenerator()
    val srv = new EntityIdService(akkaHttpGenerator)
    val ids: Future[List[EntityId]] = srv.getIds(markets.size, "market") |+| srv.getIds(selections.size, "selection")
    val entities: List[Entity] = markets |+| selections

    val mapped = zipEntitiesWithIds(entities, ids)
    Await.ready(mapped, 5 second).value.get match {
      case Success(res) =>
        println(s"$res")
        println(res.groupBy(_._2))
      case Failure(err) =>
        println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runAkkaHttpWithFor(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("TestIdRequests")
    implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
    val akkaHttpGenerator = new AkkaHttpGenerator()
    val srv = new EntityIdService(akkaHttpGenerator)
    val ids = for {
      mm <- mapEntities(srv, markets, "market")
      ms <- mapEntities(srv, selections, "selection")
    } yield (mm, ms)
    Await.ready(ids, 5 second).value.get match {
      case Success(res) =>
        println(s"${res._1}")
        println(s"${res._2}")
      case Failure(err) =>
        println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runApacheHttpInParallel(): Unit = {
    val apacheHttpGenerator = new ApacheHttpGenerator()
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val srv = new EntityIdService(apacheHttpGenerator)
    val ids: Future[List[EntityId]] = srv.getIds(markets.size, "market") |+| srv.getIds(selections.size, "selection")
    val entities: List[Entity] = markets |+| selections
    val mapped = zipEntitiesWithIds(entities, ids)
    Await.ready(mapped, 5 second).value.get match {
      case Success(res) =>
        println(s"$res")
        println(res.groupBy(_._2.entityType))
      case Failure(err) =>
        println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runApacheHttpWithFor(): Unit = {
    val apacheHttpGenerator = new ApacheHttpGenerator()
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val srv = new EntityIdService(apacheHttpGenerator)
    val ids = for {
      mm <- mapEntities(srv, markets, "market")
      ms <- mapEntities(srv, selections, "selection")
    } yield (mm, ms)
    Await.ready(ids, 5 second).value.get match {
      case Success(res) =>
        println(s"${res._1}")
        println(s"${res._2}")
      case Failure(err) =>
        println(s"ids :: err=${err.getMessage}")
    }
  }

  //runAkkaHttp()
  //runAkkaHttpWithFor()
  //runApacheHttpWithFor()
  runApacheHttpInParallel()

}
