![EOSC Beyond Logo][eosc-logo]

# Federation Search

## Description
The **Federation Search** is a Java-based service that aggregates EOSC Resources from multiple 
**[Resource Catalogue](https://github.com/madgeek-arc/resource-catalogue)** instances into a single API endpoint.

Results from different nodes are merged and ranked using the **Reciprocal Rank Fusion (RRF)** algorithm, ensuring a fair and robust ordering of results across the entire federation.

## Prerequisites
- Java 25
- Maven 3.x

## Installation
```
git clone https://github.com/madgeek-arc/federation-search.git
```

## Build
```
mvn clean package
```
Add `-DskipTests` to skip tests and speed up the build.

## Configuration

Edit `src/main/resources/application.properties`. The service supports two modes of endpoint configuration, controlled by the `node.endpoints.manual-config` property.

### Mode 1: Registry-based (default)

```properties
node.endpoints.manual-config=false
node.registry.url=https://<registry-host>/federation-backend/tenants/<tenant>/nodes
node.registry.key=<api-key>
```

The service fetches the list of node endpoints dynamically from the URL specified in `node.registry.url`. Use `node.registry.key` if the registry requires authentication.

### Mode 2: Manual configuration

```properties
node.endpoints.manual-config=true
```

The service uses the static list of endpoints defined in [node-endpoints.json](src/main/resources/node-endpoints.json). Edit this file to add or remove endpoints:

```json
{
  "endpoints": [
    "https://<node-host>/api"
  ]
}
```

The service appends the resource-type path automatically (e.g. `public/service/search`).

### Result Scoring Configuration

The aggregator collects results from all endpoints and scores them using Reciprocal Rank Fusion.
The ranking behavior can be tuned via the following property:

```properties
# Reciprocal Rank Fusion (RRF) smoothing constant.
# Low values (10-20) favor top-ranked results; high values (60+) favor consensus.
# Default is 20 (optimized for fetching 10 results per node).
scoring.rrf.k=20
```

## Run

### Option 1: Java
1. Configure the service as described above.
2. Start the service:
   <!-- x-release-please-start-version -->
   ```
   java -jar target/search-aggregator-1.2.1-SNAPSHOT
   ```
   To supply an external configuration file at runtime:
   <!-- x-release-please-end -->
   <!-- x-release-please-start-version -->
   ```
   java -jar target/search-aggregator-1.2.1-SNAPSHOT \
     --spring.config.location=file:/path/to/application.properties
   ```
   <!-- x-release-please-end -->

### Option 2: Docker
1. Copy the example env file and fill in your values:
   ```
   cp .env.example .env
   ```
2. Build the jar:
   ```
   mvn clean install -DskipTests
   ```
3. Build and start the container:
   ```
   docker compose up --build
   ```

The service will be available at `http://localhost:8090/api`.

## API
The service exposes endpoints that query all configured nodes, merges the data, and applies RRF ranking.

**Endpoints**:
```
GET http://localhost:8080/api/federation/adapters
GET http://localhost:8080/api/federation/catalogues
GET http://localhost:8080/api/federation/configurationTemplateInstances
GET http://localhost:8080/api/federation/datasources
GET http://localhost:8080/api/federation/deployableApplications
GET http://localhost:8080/api/federation/interoperabilityRecords
GET http://localhost:8080/api/federation/organisations
GET http://localhost:8080/api/federation/resourceInteroperabilityRecords
GET http://localhost:8080/api/federation/services
GET http://localhost:8080/api/federation/trainingResources
```

### Query Parameters

| Parameter  | Type    | Default | Description                      |
|------------|---------|---------|----------------------------------|
| `keyword`  | string  | —       | Full-text search term            |
| `from`     | integer | `0`     | Offset into the result set       |
| `quantity` | integer | `10`    | Number of results to return      |
| `sort`     | string  | —       | Field to sort by                 |
| `order`    | string  | `asc`   | Sort direction (`asc` / `desc`)  |

Resource-specific facet parameters (e.g. `categories`, `trl`) are listed in [docs/examples.md](docs/examples.md).

**Example request**:
```bash
curl "http://localhost:8080/api/federation/services?keyword=storage&quantity=5"
```

### Response Structure

Each result in the response contains ranking metadata:
*   `score`: The definitive RRF score used for the final ranking.
*   `originalScore`: The raw relevance score provided by the originating node (preserved for debugging and tie-breaking).
*   `result`: The actual resource data.
*   `highlights`: Snippets showing where keywords matched.

**Example response** (truncated):
```json
{
  "total": 42,
  "from": 0,
  "to": 10,
  "results": [
    {
      "score": 0.182,
      "originalScore": 8.74,
      "result": { "id": "12341234", "name": "Storage Service" },
      "highlights": [
        { "field": "name", "snippet": "<em>Storage</em> Service" },
        { "field": "description", "snippet": "...object <em>storage</em> service..." }
      ]
    }
  ],
  "facets": [],
  "metadata": {}
}
```

For a full list of facet parameters and more request examples, see [docs/examples.md](docs/examples.md).


[eosc-logo]: https://eosc.eu/wp-content/uploads/2024/02/EOSC-Beyond-logo.png
