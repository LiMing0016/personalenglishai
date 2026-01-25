容器化与部署
1. 容器化概述

本项目使用 Docker 将应用（包括后端、前端、数据库等）容器化，以确保开发、测试、生产环境的一致性。容器化能够提升应用的可移植性、可扩展性和维护性。

1.1 使用 Docker 容器化的原因

一致性：在本地、测试和生产环境中运行相同的容器镜像，确保环境一致。

便捷性：容器化服务更容易部署，管理和扩展。

资源隔离：容器能够提供相互隔离的运行环境，不会互相影响。

可扩展性：容器化支持自动扩展和分布式架构，适合大规模应用。

2. 后端服务的 Docker 化
   2.1 Dockerfile 编写

为了将 Spring Boot 后端应用容器化，我们在后端项目根目录创建了 Dockerfile，用于描述如何构建应用的 Docker 镜像。

示例 Dockerfile（Spring Boot）
# 使用 Java 17 基础镜像
FROM eclipse-temurin:17-jre

# 设置工作目录
WORKDIR /app

# 复制构建好的 JAR 文件到容器
COPY target/*.jar app.jar

# 暴露容器内部端口 8080
EXPOSE 8080

# 启动 Spring Boot 应用
ENTRYPOINT ["java", "-jar", "app.jar"]


FROM：使用 eclipse-temurin:17-jre 作为基础镜像。

WORKDIR：设置工作目录 /app。

COPY：将构建好的 .jar 文件从宿主机复制到容器中。

EXPOSE：声明容器的端口，容器启动时会监听 8080 端口。

ENTRYPOINT：容器启动时执行 java -jar 来启动 Spring Boot 应用。

2.2 构建后端镜像

在后端项目的根目录下，执行以下命令构建 Docker 镜像：

docker build -t personal_english_ai/backend .


personal_english_ai/backend 是你后端镜像的名称。

. 代表 Dockerfile 所在的当前目录。

2.3 为镜像打标签

为了将构建的后端镜像推送到阿里云 ACR，需要给镜像打上标签：

docker tag personal_english_ai/backend:latest crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/backend:latest

2.4 推送镜像到阿里云 ACR

一旦镜像标签完成，使用以下命令将镜像推送到阿里云 ACR：

docker push crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/backend:latest

3. 前端服务的 Docker 化
   3.1 Dockerfile 编写

前端服务使用 Vue.js 和 Nginx，Dockerfile 用来容器化前端服务。

示例 Dockerfile（前端）
# 使用 Node.js 基础镜像来构建前端应用
FROM node:14 AS build

# 设置工作目录
WORKDIR /app

# 复制项目的 package.json 文件并安装依赖
COPY package*.json ./
RUN npm install

# 复制所有源代码
COPY . .

# 打包前端应用
RUN npm run build

# 使用 Nginx 镜像托管前端应用
FROM nginx:alpine

# 将前端构建文件复制到 Nginx 的静态文件目录
COPY --from=build /app/dist /usr/share/nginx/html

# 暴露 Nginx 默认的端口
EXPOSE 80


FROM node:14：使用 Node.js 镜像构建前端应用。

RUN npm install：安装前端依赖。

RUN npm run build：打包前端应用。

FROM nginx:alpine：使用 Nginx 镜像托管构建好的前端文件。

COPY --from=build /app/dist /usr/share/nginx/html：将前端打包文件复制到 Nginx 中。

EXPOSE 80：暴露 Nginx 的默认端口 80。

3.2 构建前端镜像

在前端项目的根目录下，执行以下命令构建前端 Docker 镜像：

docker build -t personal_english_ai/frontend .

3.3 为前端镜像打标签并推送

同样的，推送前端镜像到阿里云 ACR：

docker tag personal_english_ai/frontend:latest crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/frontend:latest
docker push crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/frontend:latest

4. MySQL 镜像
   4.1 推送 MySQL 镜像

MySQL 镜像不需要构建，我们直接使用官方的 MySQL 镜像，并将其推送到阿里云 ACR。

docker pull mysql:8.0
docker tag mysql:8.0 crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/mysql:8.0
docker push crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/mysql:8.0

5. 部署与运行
   5.1 使用 Docker Compose

你可以通过 Docker Compose 来启动所有容器，使得后端、前端和数据库服务一起启动。

以下是一个简单的 docker-compose.yml 文件示例：

version: '3'
services:
mysql:
image: crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/mysql:8.0
environment:
MYSQL_ROOT_PASSWORD: root
ports:
- "3306:3306"

backend:
image: crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/backend:latest
ports:
- "8080:8080"
depends_on:
- mysql

frontend:
image: crpi-fqie53ual3pljglk.cn-wulanchabu.personal.cr.aliyuncs.com/personal_english_ai/frontend:latest
ports:
- "80:80"

5.2 启动服务

运行以下命令启动所有服务：

docker-compose up -d

5.3 验证服务是否启动成功

前端：访问 http://localhost，应该看到前端页面。

后端：访问 http://localhost:8080，确保后端 API 正常。

数据库：检查数据库是否运行正常，确保后端能够连接数据库。

6. 总结

项目中的后端、前端和数据库服务已全部容器化，使用 Docker 构建镜像并推送到阿里云 ACR。

通过 Docker Compose 管理多个服务，确保环境一致性。

使用 GitHub 作为代码源，支持自动化构建和镜像推送。