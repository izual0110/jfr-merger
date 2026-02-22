# MCP Server

Spring Boot MCP server for heap dump analysis.

## Requirements

- Java 25
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

## Why no springdoc-openapi?

This module now uses the official Spring AI MCP starter (`spring-ai-starter-mcp-server-webmvc`) and exposes tools via MCP protocol. OpenAPI UI is not required for MCP tool discovery.

## MCP tool

Tool name: `topClasses`

Arguments:
- `heapDumpPath` - absolute path to `.hprof` file on server filesystem
- `limit` - number of classes in response (1..500)

Result: list of classes sorted by `objects` (instance count).

## HTTP endpoint (kept for direct upload testing)

`POST /api/v1/heapdump/top-classes`

Multipart params:
- `file` - `.hprof` heap dump file
- `limit` - number of classes in response (default `20`, max `500`)
