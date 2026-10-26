CREATE DATABASE IF NOT EXISTS careerai
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON careerai.* TO 'careerapp'@'%';

USE careerai;

CREATE TABLE IF NOT EXISTS conversation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(30) NOT NULL,
  content LONGTEXT NOT NULL,
  CONSTRAINT fk_message_conversation
    FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);
CREATE INDEX idx_message_conversation_id_id
  ON message(conversation_id, id);
