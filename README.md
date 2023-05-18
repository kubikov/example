# offers-transfer-service

## Setup

* JDK 17
* Gradle(7.5.1)

### Файлы настроек:

- **./src/main/resources/application.conf** - содержит настройки, подгружаемые при запуске сервиса. Может содержать обращение к переменным kubernetes при их наличии)

### Для сборки проекта через Gradle в терминале в корне проекта выполните

```
.\gradlew build -x generateJooq --build-cache
```

После для запуска выполните:

```
java -jar .\build\libs\offers-transfer-{version}.jar
```

### Перед запуском проекта необходимо 

- создать топик в кафке paging c количеством партиций равным кол-ву запускаемых реплик сервиса
- указать в настройках номер последней партиции и размер батча

```
    paging {
        partition_last_index = ${?PARTITION_LAST_INDEX}
        partition_last_index = 2
        page_size = ${?PAGE_SIZE}
        page_size = 10000
    }
```
- при запуске сервиса в kubernetes указать кол-во реплик равное кол-во партиций

### Ручки
 - GET /start возобновление процесса после перелива всех данных, в случае появления новых
 - POST /kafka/paging инициализация загрузки с самого начала, когда все топики чистые
 - GET /metrics-micrometer метрики для Prometheus
 - POST /postgres/insert_all/0 заливка тестовых данных в БД, где 0 id с которого начинается вставка

### При измение схемы в Postgresql необходимо подготовить актуальный предварительно генерируемый код для взаимодействия с БД:
- Указать в задаче jooq хост и пароли к схеме
- Выполнить генерацию кода:
  ```
  .\gradlew generateJooq
  ```
### Ручки управления

POST http://localhost:8080/postgres/insert_all/0
Content-Type: application/json

### Используемые топики кафки 
- paging (с партициями)
- status
- data
- data_dlq

### Используемые БД
- Postgresql