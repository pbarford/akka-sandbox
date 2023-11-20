package com.flutter.akka.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.flutter.akka.service.EntityIdService.EntityId
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.apache.http.{HttpEntity, HttpHost}

import scala.concurrent.{ExecutionContext, Future}

trait IdGenerator {
  def generate(idType:String)(numberOfIds: Int)(implicit ec:ExecutionContext): Future[List[EntityId]]
}

class RandomGenerator extends IdGenerator {
  private val rand = new scala.util.Random
  override def generate(idType: String)(numberOfIds: Int)(implicit ec: ExecutionContext): Future[List[EntityId]] = {
    Future {
      println(s"RandomGenerator::Thread[${Thread.currentThread().getId}]::generate[$idType][$numberOfIds]")
      Array.range(0, numberOfIds).map(_ => EntityId(rand.nextInt(), idType)).toList
    }
  }
}

class AkkaHttpGenerator(implicit system: ActorSystem) extends IdGenerator {

  val client = Http(system)
  private def httpRequest(numberOfIds: Int): HttpRequest = {
    Get(s"https://www.random.org/integers/?num=$numberOfIds&min=1&max=30000&col=1&base=10&format=plain&rnd=new")
  }

  override def generate(idType: String)(numberOfIds: Int)(implicit ec: ExecutionContext): Future[List[EntityId]] = {
    println(s"AkkaHttpGenerator::Thread[${Thread.currentThread().getId}]::generate[$idType][$numberOfIds]")
    client.singleRequest(httpRequest(numberOfIds)).flatMap {
      response =>
        if (response.status == StatusCodes.OK) {
          Unmarshal(response.entity).to[String].map(s => s.split('\n').map(s => EntityId(s.toInt, idType)).toList)
        } else {
          Future(List.empty[EntityId])
        }
    }
  }
}

class ApacheHttpGenerator extends IdGenerator {
  private val connManager = new PoolingHttpClientConnectionManager()
  connManager.setMaxTotal(5)
  connManager.setDefaultMaxPerRoute(4)
  private val host: HttpHost = new HttpHost("www.random.org", 443)
  connManager.setMaxPerRoute(new HttpRoute(host), 50)

  override def generate(idType: String)(numberOfIds: Int)(implicit ec:ExecutionContext): Future[List[EntityId]] = {

    Future {
      println(s"ApacheHttpGenerator::Thread[${Thread.currentThread().getId}]::generate[$idType][$numberOfIds]")
      val client = HttpClients.custom().setConnectionManager(connManager).build()
      val get = new HttpGet(s"https://www.random.org/integers/?num=$numberOfIds&min=1&max=30000&col=1&base=10&format=plain&rnd=new")
      var entity: HttpEntity = null
      try {
        entity = client.execute(get).getEntity()
        if (entity != null) {
          val result = EntityUtils.toString(entity)
          result.split('\n').map(s => EntityId(s.toInt, idType)).toList
        } else {
          List.empty[EntityId]
        }
      }
      catch {
        case _: Exception => List.empty[EntityId]
      } finally {
        EntityUtils.consume(entity)
      }
    }
  }
}
