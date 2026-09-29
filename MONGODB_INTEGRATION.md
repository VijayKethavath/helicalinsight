# MongoDB Driver Integration — Helical Insight

## Summary of Changes

MongoDB database driver/connectivity support has been added to the Helical Insight application
following the **exact same pattern** used for other NoSQL data source types (`db.noSql`, etc.)
already present in the codebase.

---

## Files Added / Modified

### 1. New File — `MongoLoader.java`
**Path:** [`server/core/src/main/java/com/helicalinsight/datasource/nosql/MongoLoader.java`](server/core/src/main/java/com/helicalinsight/datasource/nosql/MongoLoader.java)

- Extends `NoSQLLoader` (the existing abstract base in the `nosql` package)
- Registered as a `@Component("mongo")` Spring bean — this is the **key hook**: `NoSqlUtils.getNoSqlImplementation("mongo")` looks up the Spring bean by name, so the `subType` field in connection config must be `"mongo"`
- Implements both `loadToMiddleWare(JsonObject)` and `testConnection(JsonObject)`
- `testConnection` builds a `MongoClient` and calls `listDatabaseNames().first()` — a real network round-trip that validates connectivity with minimal overhead
- Supports two connection modes:
  - **URI-based**: `jdbcUrl` or `url` field → `mongodb://host:port/db` (takes precedence)
  - **Individual fields**: `hostName`/`host`, `port`, `databaseName`/`database`/`dbName`, `userName`/`username`, `password`
- Closes the `MongoClient` in a `finally` block to avoid leaks during connection tests

### 2. Modified File — `globalConnections.xml`
**Path:** [`server/hi-repository/System/Admin/globalConnections.xml`](server/hi-repository/System/Admin/globalConnections.xml)

- Added a **commented-out sample** `<noSqlDataSource>` entry (id=2, name=SampleMongoDB)
- Instructions embedded in comments explaining how to activate it

### 3. Modified File — `driverDefaultQuery.properties`
**Path:** [`server/hi-repository/System/Admin/driverDefaultQuery.properties`](server/hi-repository/System/Admin/driverDefaultQuery.properties)

- Added entry `mongo=SELECT 1` so the validation query registry recognises the `mongo` subType
- Existing `mongodb.jdbc.MongoDriver` and `cdata.jdbc.mongodb.MongoDBDriver` entries were already present (JDBC bridge drivers)

### 4. Modified File — `pom.xml` (parent)
**Path:** [`server/pom.xml`](server/pom.xml)

- Added `<version>${mongo.java.driver}</version>` (= `3.12.10`) to the `org.mongodb:mongo-java-driver` shared dependency entry that was missing it
- The `mongo.java.driver` property and the `<dependencyManagement>` entry for the driver were already present

---

## How It Integrates With the Existing Architecture

```
User adds connection (subType="mongo") via UI / globalConnections.xml
        │
        ▼
NoSqlDataSourceProperties.writeNoSqlDataSource()  (or DB variant)
        │  calls NoSqlUtils.getNoSqlImplementation("mongo")
        ▼
Spring ApplicationContext → bean named "mongo" → MongoLoader
        │
        ├─ loadToMiddleWare(formData) → delegates to testConnection()
        └─ testConnection(formData)  → builds MongoClient, pings server, closes
```

The `DataSourceUtils.testNosqlDS()` method follows the same path for on-demand connection testing from the UI.

---

## Steps to Configure a MongoDB Connection

### Option A — Via the Helical Insight UI (Recommended)

1. Log in as an Admin.
2. Go to **Admin → Data Sources → Add Data Source**.
3. Select **NoSQL** as the data source type.
4. Fill in the form:

| Field | Value |
|---|---|
| Name | `MyMongoDB` (no spaces) |
| Data Source Provider | `noSql` |
| Sub Type | `mongo` |
| JDBC URL *(optional)* | `mongodb://localhost:27017/mydb` |
| Host Name | `localhost` *(if no URL)* |
| Port | `27017` *(if no URL)* |
| Database Name | `mydb` |
| Username | *(your MongoDB username, if auth enabled)* |
| Password | *(your MongoDB password, if auth enabled)* |

5. Click **Test Connection** — should return "The connection test is successful."
6. Save.

### Option B — Via `globalConnections.xml` (File-Based)

Edit [`globalConnections.xml`](server/hi-repository/System/Admin/globalConnections.xml) and uncomment the sample block (lines 49–68), then replace the placeholder values:

```xml
<noSqlDataSource id="2" name="SampleMongoDB" type="noSqlDataSource" baseType="global.jdbc">
    <visible>true</visible>
    <dataSourceProvider>noSql</dataSourceProvider>
    <subType>mongo</subType>
    <!-- Use a URI: -->
    <url>mongodb://localhost:27017/sampledb</url>
    <!-- OR individual fields: -->
    <databaseName>sampledb</databaseName>
    <username>mongouser</username>
    <password>mongopassword</password>
    <security>
        <createdBy>1</createdBy>
        <organization></organization>
    </security>
    <share mandatory="true">
        <roles mandatory="true">
            <role id="2">2</role>
        </roles>
    </share>
</noSqlDataSource>
```

Then restart the application server.

---

## Connection URI Formats Supported

| Scenario | URI Example |
|---|---|
| No auth, default port | `mongodb://localhost/mydb` |
| With auth | `mongodb://user:password@localhost:27017/mydb` |
| With auth source | `mongodb://user:password@host:27017/mydb?authSource=admin` |
| Replica set | `mongodb://h1:27017,h2:27017/mydb?replicaSet=rs0` |
| TLS/SSL | `mongodb://host:27017/mydb?ssl=true` |

> [!NOTE]
> The URI takes precedence over individual host/port/database fields when both are provided.

---

## Maven Dependency

The `mongo-java-driver:3.12.10` legacy all-in-one JAR is used (already declared in the parent POM).
No additional JAR downloads are needed — the dependency is resolved transitively from the parent.

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>3.12.10</version>
</dependency>
```

> [!TIP]
> The legacy `mongo-java-driver` is intentional — it matches the version already in the parent POM
> and is compatible with the `com.mongodb.MongoClient` / `MongoClientURI` API used throughout the codebase.

---

## Existing Functionality — Not Broken

- All existing JDBC drivers remain untouched.
- The `MongoConnectionFactory` (used for `mongodb.jdbc.MongoDriver` JDBC bridge flows) is unchanged.
- The `NoSQLLoader` abstract class is unchanged.
- The `NoSqlUtils`, `NoSqlDataSourceProperties`, `NoSqlDataSourcePropertiesDB`, and `DataSourceUtils` classes required **zero changes** — `MongoLoader` wires in via Spring's bean-name convention that those classes already use.
