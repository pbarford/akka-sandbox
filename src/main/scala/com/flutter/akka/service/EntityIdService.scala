package com.flutter.akka.service

import com.flutter.akka.service.EntityIdService.EntityId

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContextExecutor, Future}

object EntityIdService {
  case class EntityId(value:Int, entityType:String)
}

class EntityIdService(idGenerator: IdGenerator)(implicit ec: ExecutionContextExecutor) {

  private val MAX_BATCH = 8

  def getIds(numberOfIds: Int, idType:String): Future[List[EntityId]] = {
    reserveSequenceIdsBatch(numberOfIds, idType)
  }

  private def reserveSequenceIdsBatch(numberOfIdsToReserve: Int, idType:String): Future[List[EntityId]] = {

    val generateIds : Int => Future[List[EntityId]] = idGenerator.generate(idType)
    import cats.implicits._

    @tailrec
    def doReserve(n: Int, acc: Future[List[EntityId]] = Future(List.empty)): Future[List[EntityId]] = {
      if (n <= 0)
        acc
      else if (n <= MAX_BATCH)
        acc |+| generateIds(n)
      else
        doReserve(n - MAX_BATCH, acc |+| generateIds(MAX_BATCH))
    }

    doReserve(numberOfIdsToReserve)
  }

}
