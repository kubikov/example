package example.config

data class AppConfig(
    var kafka: Kafka,
    val postgres: PostgresConfig,
    val paging: Paging
)

data class Paging(
    val partitionLastIndex: Int,
    val pageSize: Int
)

data class Kafka(
    val server: List<String>,
    val consumer: Consumer,
    val producer: Producer
)

data class Consumer(
    var pagingStateTopic: String,
    var statusTopic: String,
    var dataTopic: String,
    var dlqTopic: String,
    var clientId: String,
    var groupId: String
)

data class Producer(
    var clientId: String
)

data class PostgresConfig(
    val url: String,
    val user: String,
    val password: String
)
