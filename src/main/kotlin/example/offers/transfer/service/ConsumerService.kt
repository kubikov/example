package example.service

import org.apache.kafka.common.TopicPartition

interface ConsumerService<in T> {

    fun statuses(statusRecords: List<T>, topicPartitions: List<TopicPartition>)
    fun processRecords(pagingStateRecords: List<T>, partition: Int)
}
