package com.flutter.akka.kafka

import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster

import java.util

class KeyPartitioner extends Partitioner {
  override def partition(topic: String, key: Any, keyBytes: Array[Byte], value: Any, valueBytes: Array[Byte], cluster: Cluster): Int = {
    val partitions = cluster.partitionCountForTopic(topic)
    println(s"partitions=$partitions --> partition = ${Math.abs(key.hashCode()) % partitions}")
    Math.abs(key.hashCode()) % partitions
  }

  override def close(): Unit = {}
  override def configure(configs: util.Map[String, _]): Unit = {}

}

