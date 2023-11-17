package com.flutter.akka

import akka.actor.ActorSystem
import com.flutter.akka.service.{AkkaHttpGenerator, ApacheHttpGenerator, IdService}
import com.flutter.akka.service.IdService.Id

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import scala.language.postfixOps
import scala.concurrent.duration._

object TestIdService extends App {

  sealed trait EntityId {
    val id: Int
    val entityType: String
  }
  case class Market(id: Int, name: String, entityType: String = "market") extends EntityId
  case class Selection(id: Int, name: String, entityType: String = "selection") extends EntityId

  val markets: List[EntityId] = (1 to 12).map(id => Market(id, s"market-$id")).toList
  val selections: List[EntityId] = (1 to 30).map(id => Selection(id, s"selection-$id")).toList

  import cats.implicits._
  val entities: List[EntityId] = markets |+| selections

  private def runAkkaHttp(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("TestIdRequests")
    implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
    val akkaHttpGenerator = new AkkaHttpGenerator()
    val srv = new IdService(akkaHttpGenerator)
    val ids: Future[List[Id]] = srv.getIds(markets.size, "market") |+| srv.getIds(selections.size, "selection")

    val mapped = zipEntitiesWithIds(entities, ids)
    Await.ready(mapped, 5 second).value.get match {
      case Success(res) => println(s"$res")
        println(res.groupBy(_._2.entityType))
      case Failure(err) => println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runAkkaHttpWithFor(): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem("TestIdRequests")
    implicit val ec: ExecutionContextExecutor = actorSystem.dispatcher
    val akkaHttpGenerator = new AkkaHttpGenerator()
    val srv = new IdService(akkaHttpGenerator)
    val ids = for {
      mm <- mapEntities(srv, markets, "market")
      ms <- mapEntities(srv, selections, "selection")
    } yield (mm, ms)
    Await.ready(ids, 5 second).value.get match {
      case Success(res) =>
        println(s"${res._1}")
        println(s"${res._2}")
      case Failure(err) => println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runApacheHttpInParallel(): Unit = {
    val apacheHttpGenerator = new ApacheHttpGenerator()
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val srv = new IdService(apacheHttpGenerator)
    val ids: Future[List[Id]] = srv.getIds(markets.size, "market") |+| srv.getIds(selections.size, "selection")

    val mapped = zipEntitiesWithIds(entities, ids)
    Await.ready(mapped, 5 second).value.get match {
      case Success(res) => println(s"$res")
        println(res.groupBy(_._2.entityType))
      case Failure(err) => println(s"ids :: err=${err.getMessage}")
    }
  }

  private def runApacheHttpWithFor(): Unit = {
    val apacheHttpGenerator = new ApacheHttpGenerator()
    implicit val ec: ExecutionContextExecutor = ExecutionContext.global
    val srv = new IdService(apacheHttpGenerator)
    val ids = for {
      mm <- mapEntities(srv, markets, "market")
      ms <- mapEntities(srv, selections, "selection")
    } yield (mm, ms)
    Await.ready(ids, 5 second).value.get match {
      case Success(res) => println(s"${res._1}")
        println(s"${res._2}")
      case Failure(err) => println(s"ids :: err=${err.getMessage}")
    }
  }

  private def mapEntities(idService: IdService, entities: List[EntityId], entityType: String)(implicit ec:ExecutionContext): Future[Map[EntityId, Id]] = {
    resolveDisjunction(entities, idService.getIds(entities.size, entityType))
  }

  private def zipEntitiesWithIds(entities: List[EntityId], ids: Future[List[Id]])(implicit ec:ExecutionContext): Future[Map[EntityId, Id]] = {
    resolveDisjunction(entities, ids)
  }

  private def resolveDisjunction(externalId: Iterable[EntityId], rampId: Future[Iterable[Id]])(implicit ec:ExecutionContext): Future[Map[EntityId, Id]] = {
    rampId.map(externalId.zip(_).to(Map))
  }

  //runAkkaHttp()
  //runAkkaHttpWithFor()
  //runApacheHttpWithFor()
  runApacheHttpInParallel()

}
