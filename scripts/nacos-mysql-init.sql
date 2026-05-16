-- Nacos v2.3.x MySQL Schema
-- 此脚本仅在 MySQL 数据目录为空时自动执行
-- 已有数据的环境请手动执行：mysql -u root -p < scripts/nacos-mysql-init.sql

CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `nacos_config`;

CREATE TABLE IF NOT EXISTS `config_info` (
  `id`                 bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id`            varchar(255) NOT NULL COMMENT 'data_id',
  `group_id`           varchar(128) DEFAULT NULL,
  `content`            longtext     NOT NULL COMMENT 'content',
  `md5`                varchar(32)  DEFAULT NULL COMMENT 'md5',
  `gmt_create`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user`           text         COMMENT 'source user',
  `src_ip`             varchar(50)  DEFAULT NULL COMMENT 'source ip',
  `app_name`           varchar(128) DEFAULT NULL,
  `tenant_id`          varchar(128) DEFAULT '' COMMENT '租户字段',
  `c_desc`             varchar(256) DEFAULT NULL,
  `c_use`              varchar(64)  DEFAULT NULL,
  `effect`             varchar(64)  DEFAULT NULL,
  `type`               varchar(64)  DEFAULT NULL,
  `c_schema`           text         DEFAULT NULL,
  `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`, `group_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info';

CREATE TABLE IF NOT EXISTS `config_info_aggr` (
  `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id`      varchar(255) NOT NULL COMMENT 'data_id',
  `group_id`     varchar(128) NOT NULL COMMENT 'group_id',
  `datum_id`     varchar(255) NOT NULL COMMENT 'datum_id',
  `content`      longtext     NOT NULL COMMENT '内容',
  `gmt_modified` datetime     NOT NULL COMMENT '修改时间',
  `app_name`     varchar(128) DEFAULT NULL,
  `tenant_id`    varchar(128) DEFAULT '' COMMENT '租户字段',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfoaggr_datagrouptenantdatum` (`data_id`, `group_id`, `tenant_id`, `datum_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='增量发布内容表';

CREATE TABLE IF NOT EXISTS `config_info_beta` (
  `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id`            varchar(255)  NOT NULL COMMENT 'data_id',
  `group_id`           varchar(128)  NOT NULL COMMENT 'group_id',
  `app_name`           varchar(128)  DEFAULT NULL,
  `content`            longtext      NOT NULL COMMENT 'content',
  `beta_ips`           varchar(1024) DEFAULT NULL COMMENT 'betaIps',
  `md5`                varchar(32)   DEFAULT NULL COMMENT 'md5',
  `gmt_create`         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user`           text          COMMENT 'source user',
  `src_ip`             varchar(50)   DEFAULT NULL COMMENT 'source ip',
  `tenant_id`          varchar(128)  DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfobeta_datagrouptenant` (`data_id`, `group_id`, `tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info_beta';

CREATE TABLE IF NOT EXISTS `config_info_tag` (
  `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id`      varchar(255) NOT NULL COMMENT 'data_id',
  `group_id`     varchar(128) NOT NULL COMMENT 'group_id',
  `tenant_id`    varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `tag_id`       varchar(128) NOT NULL COMMENT 'tag_id',
  `app_name`     varchar(128) DEFAULT NULL,
  `content`      longtext     NOT NULL COMMENT 'content',
  `md5`          varchar(32)  DEFAULT NULL COMMENT 'md5',
  `gmt_create`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user`     text         COMMENT 'source user',
  `src_ip`       varchar(50)  DEFAULT NULL COMMENT 'source ip',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfotag_datagrouptenanttag` (`data_id`, `group_id`, `tenant_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_info_tag';

CREATE TABLE IF NOT EXISTS `config_tags_relation` (
  `id`        bigint(20)   NOT NULL COMMENT 'id',
  `tag_name`  varchar(128) NOT NULL COMMENT 'tag_name',
  `tag_type`  varchar(64)  DEFAULT NULL COMMENT 'tag_type',
  `data_id`   varchar(255) NOT NULL COMMENT 'data_id',
  `group_id`  varchar(128) NOT NULL COMMENT 'group_id',
  `tenant_id` varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `nid`       bigint(20)   NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`nid`),
  UNIQUE KEY `uk_configtagrelation_configidtag` (`id`, `tag_name`, `tag_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='config_tag_relation';

CREATE TABLE IF NOT EXISTS `group_capacity` (
  `id`                bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id`          varchar(128) NOT NULL DEFAULT '' COMMENT 'Group ID',
  `quota`             int(10) unsigned NOT NULL DEFAULT '0',
  `usage`             int(10) unsigned NOT NULL DEFAULT '0',
  `max_size`          int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_count`    int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_size`     int(10) unsigned NOT NULL DEFAULT '0',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0',
  `gmt_create`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集群、各Group容量信息表';

CREATE TABLE IF NOT EXISTS `his_config_info` (
  `id`                 bigint(20) unsigned NOT NULL,
  `nid`                bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `data_id`            varchar(255) NOT NULL,
  `group_id`           varchar(128) NOT NULL,
  `app_name`           varchar(128) DEFAULT NULL,
  `content`            longtext     NOT NULL,
  `md5`                varchar(32)  DEFAULT NULL,
  `gmt_create`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user`           text,
  `src_ip`             varchar(50)  DEFAULT NULL,
  `op_type`            char(10)     DEFAULT NULL,
  `tenant_id`          varchar(128) DEFAULT '' COMMENT '租户字段',
  `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '',
  `publish_type`       varchar(50)  DEFAULT 'formal',
  `ext_info`           longtext     DEFAULT NULL,
  PRIMARY KEY (`nid`),
  KEY `idx_gmt_create` (`gmt_create`),
  KEY `idx_gmt_modified` (`gmt_modified`),
  KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多租户改造';

CREATE TABLE IF NOT EXISTS `tenant_capacity` (
  `id`                bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id`         varchar(128) NOT NULL DEFAULT '' COMMENT 'Tenant ID',
  `quota`             int(10) unsigned NOT NULL DEFAULT '0',
  `usage`             int(10) unsigned NOT NULL DEFAULT '0',
  `max_size`          int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_count`    int(10) unsigned NOT NULL DEFAULT '0',
  `max_aggr_size`     int(10) unsigned NOT NULL DEFAULT '0',
  `max_history_count` int(10) unsigned NOT NULL DEFAULT '0',
  `gmt_create`        datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户容量信息表';

CREATE TABLE IF NOT EXISTS `tenant_info` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',
  `kp`            varchar(128) NOT NULL COMMENT 'kp',
  `tenant_id`     varchar(128) DEFAULT '' COMMENT 'tenant_id',
  `tenant_name`   varchar(128) DEFAULT '' COMMENT 'tenant_name',
  `tenant_desc`   varchar(256) DEFAULT NULL COMMENT 'tenant_desc',
  `create_source` varchar(32)  DEFAULT NULL COMMENT 'create_source',
  `gmt_create`    bigint(20)   NOT NULL COMMENT '创建时间',
  `gmt_modified`  bigint(20)   NOT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`, `tenant_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='tenant_info';

CREATE TABLE IF NOT EXISTS `users` (
  `username` varchar(50)  NOT NULL PRIMARY KEY,
  `password` varchar(500) NOT NULL,
  `enabled`  boolean      NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `roles` (
  `username` varchar(50) NOT NULL,
  `role`     varchar(50) NOT NULL,
  UNIQUE INDEX `idx_user_role` (`username`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `permissions` (
  `role`     varchar(50)  NOT NULL,
  `resource` varchar(128) NOT NULL,
  `action`   varchar(8)   NOT NULL,
  UNIQUE INDEX `uk_role_permission` (`role`, `resource`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认 nacos 管理员账号（密码：nacos）
INSERT IGNORE INTO users (username, password, enabled)
  VALUES ('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', TRUE);
INSERT IGNORE INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
