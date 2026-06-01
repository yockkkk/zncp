CREATE DATABASE IF NOT EXISTS food_recommend DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE food_recommend;

CREATE TABLE IF NOT EXISTS dish (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜品ID',
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    category VARCHAR(50) COMMENT '菜品分类',
    price DECIMAL(10,2) COMMENT '价格',
    calories INT COMMENT '热量，单位kcal',
    protein DECIMAL(10,2) COMMENT '蛋白质，单位g',
    fat DECIMAL(10,2) COMMENT '脂肪，单位g',
    carbohydrate DECIMAL(10,2) COMMENT '碳水，单位g',
    taste VARCHAR(100) COMMENT '口味，例如清淡、麻辣、酸甜',
    suitable_people VARCHAR(255) COMMENT '适合人群',
    scene VARCHAR(255) COMMENT '推荐场景',
    tags VARCHAR(255) COMMENT '菜品标签',
    image_url VARCHAR(500) COMMENT '菜品图片',
    description TEXT COMMENT '菜品描述',
    sales INT DEFAULT 0 COMMENT '销量',
    stock INT DEFAULT 999 COMMENT '库存',
    status TINYINT DEFAULT 1 COMMENT '状态：1上架，0下架',
    vector_status TINYINT DEFAULT 0 COMMENT '向量状态：0未生成，1已生成',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='菜品表';

CREATE TABLE IF NOT EXISTS recommendation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '推荐记录ID',
    user_id BIGINT COMMENT '用户ID，可为空',
    image_url VARCHAR(500) COMMENT '用户上传图片地址',
    video_url VARCHAR(500) COMMENT '用户上传视频地址',
    user_profile_json TEXT COMMENT 'AI分析出的用户画像JSON',
    query_text TEXT COMMENT '生成的推荐查询文本',
    recommended_dish_ids VARCHAR(500) COMMENT '推荐菜品ID列表',
    result_json TEXT COMMENT '最终推荐结果JSON',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='推荐记录表';

CREATE TABLE IF NOT EXISTS prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL COMMENT '提示词编码',
    name VARCHAR(100) COMMENT '提示词名称',
    content TEXT NOT NULL COMMENT '提示词内容',
    type VARCHAR(50) COMMENT '类型：vision/recommend/rerank',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用，0禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='系统提示词表';

CREATE TABLE IF NOT EXISTS customer_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT COMMENT '推荐记录ID',
    age_range VARCHAR(50) COMMENT '年龄段',
    people_count INT COMMENT '用餐人数',
    consumption_level VARCHAR(50) COMMENT '消费能力等级',
    dining_scene VARCHAR(100) COMMENT '用餐场景',
    preference_tags VARCHAR(255) COMMENT '偏好标签',
    health_goal VARCHAR(100) COMMENT '健康目标',
    raw_result_json TEXT COMMENT 'AI原始分析结果',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT='顾客画像表';
