# 读写分离架构设计

## 1. 背景与目标

当前项目使用单节点 MySQL，随着 AI 对话记录、用户情绪数据持续写入，读查询（历史记录、报告生成）与写操作竞争同一连接池，在高并发下会出现慢查询阻塞。

读写分离目标：
- 写操作（INSERT / UPDATE / DELETE）→ 主库
- 读操作（SELECT）→ 从库（一主一从即可）
- 应用层**零感知**，无需修改业务代码

---

## 2. 架构图

```
┌──────────────────────────────────────────────────────┐
│                   Spring Boot App                    │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │          DataSource Router（路由层）             │  │
│  │   写事务 → Master DS   读事务 → Replica DS      │  │
│  └────────────────────────────────────────────────┘  │
└──────────────┬──────────────────────┬────────────────┘
               │ write                │ read
       ┌───────▼──────┐      ┌────────▼──────┐
       │  MySQL Master │─────▶│ MySQL Replica │
       │  (Port 3306) │ binlog│  (Port 3307)  │
       └──────────────┘      └───────────────┘
```

---

## 3. 方案对比

### 方案 A：Spring AbstractRoutingDataSource（原生，轻量）

**原理**：继承 `AbstractRoutingDataSource`，根据当前线程上下文（ThreadLocal）决定使用主库或从库 DataSource。

**实现步骤**：

```java
// 1. 枚举数据源类型
public enum DataSourceType { MASTER, REPLICA }

// 2. ThreadLocal 持有当前路由键
public class DataSourceContextHolder {
    private static final ThreadLocal<DataSourceType> CONTEXT =
            ThreadLocal.withInitial(() -> DataSourceType.MASTER);

    public static void set(DataSourceType type) { CONTEXT.set(type); }
    public static DataSourceType get()          { return CONTEXT.get(); }
    public static void clear()                  { CONTEXT.remove(); }
}

// 3. 路由 DataSource
public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.get();
    }
}

// 4. AOP 拦截 @Transactional，只读事务切从库
@Aspect
public class DataSourceAspect {
    @Around("@annotation(tx)")
    public Object route(ProceedingJoinPoint pjp, Transactional tx) throws Throwable {
        DataSourceContextHolder.set(tx.readOnly() ? DataSourceType.REPLICA : DataSourceType.MASTER);
        try {
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
```

**优点**：
- 无额外依赖，纯 Spring 原生
- 代码量少，易于理解和调试
- 路由逻辑完全自控

**缺点**：
- 需要手动标注 `@Transactional(readOnly = true)`，有遗漏风险
- 不支持多从库负载均衡
- 主从延迟问题需业务层自行处理（强一致读强制走主库）

**适用场景**：从库只有 1 个，团队熟悉 Spring，优先轻量方案。

---

### 方案 B：ShardingSphere-JDBC（推荐，功能完整）

**原理**：作为 JDBC 驱动层代理，在 SQL 解析阶段识别 DML/DQL，自动路由，支持多从库负载均衡。

**Maven 依赖**：
```xml
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc</artifactId>
    <version>5.5.0</version>
</dependency>
```

**配置（application-prod.properties）**：
```properties
spring.datasource.driver-class-name=org.apache.shardingsphere.driver.ShardingSphereDriver
spring.datasource.url=jdbc:shardingsphere:classpath:shardingsphere.yaml
```

**shardingsphere.yaml**：
```yaml
dataSources:
  master:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: com.mysql.cj.jdbc.Driver
    jdbcUrl: jdbc:mysql://mysql-master:3306/innerflow?serverTimezone=UTC
    username: root
    password: ${DB_PASSWORD}
  replica:
    dataSourceClassName: com.zaxxer.hikari.HikariDataSource
    driverClassName: com.mysql.cj.jdbc.Driver
    jdbcUrl: jdbc:mysql://mysql-replica:3307/innerflow?serverTimezone=UTC
    username: root
    password: ${DB_PASSWORD}

rules:
  - !READWRITE_SPLITTING
    dataSources:
      rw_ds:
        writeDataSourceName: master
        readDataSourceNames:
          - replica
        transactionalReadQueryStrategy: PRIMARY  # 事务内读操作走主库，保证一致性
        loadBalancerName: round_robin
    loadBalancers:
      round_robin:
        type: ROUND_ROBIN

props:
  sql-show: false
```

**优点**：
- SQL 级别自动路由，业务代码零修改
- 原生支持多从库轮询 / 随机负载均衡
- 事务内强制走主库（`transactionalReadQueryStrategy: PRIMARY`），解决主从延迟一致性问题
- 后续可扩展分库分表，配置复用

**缺点**：
- 引入新依赖，jar 包较大（~30MB）
- SQL 兼容性（复杂子查询、存储过程）需验证
- 调试时日志解析层增加一层

**适用场景**：需要多从库、未来可能分库分表、团队偏好配置驱动。

---

## 4. 推荐方案

**本项目推荐方案 B（ShardingSphere-JDBC）**，理由：

1. 与现有 JPA / Spring Data 零冲突，无需改动 Repository 层
2. `transactionalReadQueryStrategy: PRIMARY` 自动处理事务内一致性读，不需要手动标注
3. 配置文件驱动，后续扩展只改 YAML

---

## 5. MySQL 主从搭建（docker-compose 扩展片段）

```yaml
mysql-master:
  image: mysql:8.0
  environment:
    MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
    MYSQL_DATABASE: innerflow
  command:
    - --server-id=1
    - --log-bin=mysql-bin
    - --binlog-format=ROW
    - --binlog-do-db=innerflow
  volumes:
    - mysql_master_data:/var/lib/mysql

mysql-replica:
  image: mysql:8.0
  environment:
    MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
  command:
    - --server-id=2
    - --relay-log=relay-bin
    - --read-only=1
  volumes:
    - mysql_replica_data:/var/lib/mysql
  depends_on:
    - mysql-master
```

主从同步初始化命令（在 master 容器内执行）：
```sql
CREATE USER 'repl'@'%' IDENTIFIED BY 'repl_password';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS;  -- 记录 File 和 Position
```

在 replica 容器内执行：
```sql
CHANGE MASTER TO
  MASTER_HOST='mysql-master',
  MASTER_USER='repl',
  MASTER_PASSWORD='repl_password',
  MASTER_LOG_FILE='<File from above>',
  MASTER_LOG_POS=<Position from above>;
START SLAVE;
SHOW SLAVE STATUS\G
```

---

## 6. 主从延迟处理策略

| 场景 | 处理方式 |
|---|---|
| 写后立即读（如注册后查用户信息） | 用 `@Transactional` 包裹，ShardingSphere 自动走主库 |
| 报告生成、历史查询 | 走从库，可容忍秒级延迟 |
| 强一致读（余额、权限校验） | 在 SQL 添加 `/* SHARDINGSPHERE_HINT:writeRouteOnly=true */` 强制走主库 |

---

## 7. 实施顺序建议

1. 搭建 MySQL 主从（docker-compose）
2. 引入 ShardingSphere 依赖
3. 编写 `shardingsphere.yaml`
4. 本地验证 SQL 路由日志（`props.sql-show: true`）
5. 压测对比单库 vs 读写分离 QPS
