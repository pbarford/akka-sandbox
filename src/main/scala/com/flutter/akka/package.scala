package com.flutter

import com.flutter.akka.service.EntityIdService
import com.flutter.akka.service.EntityIdService.EntityId

import scala.concurrent.{ExecutionContext, Future}

package object akka {

  sealed trait Entity {
    val id: Int
    val entityType: String
  }

  case class Market(id: Int, name: String, entityType: String = "market") extends Entity
  case class Selection(id: Int, name: String, entityType: String = "selection") extends Entity

  def mapEntities(idService: EntityIdService, entities: List[Entity], entityType: String)(implicit ec: ExecutionContext): Future[Map[Entity, EntityId]] = {
    resolveDisjunction(entities, idService.getIds(entities.size, entityType))
  }

  def zipEntitiesWithIds(entities: List[Entity], ids: Future[List[EntityId]])(implicit ec: ExecutionContext): Future[Map[Entity, EntityId]] = {
    resolveDisjunction(entities, ids)
  }

  def resolveDisjunction(externalId: Iterable[Entity], rampId: Future[Iterable[EntityId]])(implicit ec: ExecutionContext): Future[Map[Entity, EntityId]] = {
    rampId.map(externalId.zip(_).to(Map))
  }
}
