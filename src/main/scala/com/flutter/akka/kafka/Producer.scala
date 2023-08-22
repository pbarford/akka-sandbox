package com.flutter.akka.kafka

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import zio.{Task, ZIO}

import java.util.Properties
import scala.concurrent.ExecutionContext

object Producer {

  private val props = new Properties()
  props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
  props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer")
  props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "com.flutter.akka.kafka.KeyPartitioner")
  private val producer = new KafkaProducer[String, Array[Byte]](props)

  def publish(key:String, value:Array[Byte])(implicit ec:ExecutionContext): Task[RecordMetadata] = {
    val record = new ProducerRecord[String, Array[Byte]]("AccountTopic", key, value)
    ZIO.fromFutureJava (producer.send(record))
  }
}


