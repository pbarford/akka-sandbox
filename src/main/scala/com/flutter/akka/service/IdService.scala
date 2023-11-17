package com.flutter.akka.service

import com.flutter.akka.service.IdService.Id
import org.apache.http.{HttpEntity, HttpHost}
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContextExecutor, Future}

object IdService {
  case class Id(value:Int, entityType:String)
}

class IdService(idGenerator: IdGenerator)(implicit ec: ExecutionContextExecutor) {

  private val MAX_BATCH = 8

  def getIds(numberOfIds: Int, idType:String): Future[List[Id]] = {
    reserveSequenceIdsBatch(numberOfIds, idType)
  }

  private def reserveSequenceIdsBatch(numberOfIdsToReserve: Int, idType:String): Future[List[Id]] = {

    val generateIds : Int => Future[List[Id]] = idGenerator.generate(idType)
    import cats.implicits._

    @tailrec
    def doReserve(n: Int, acc: Future[List[Id]] = Future(List.empty)): Future[List[Id]] = {
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
