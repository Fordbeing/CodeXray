#!/bin/bash
set -e

# ============================================================
#  CodeXray 一键部署脚本
#  用法: bash deploy.sh
# ============================================================

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   CodeXray - AI 代码分析平台 一键部署${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""

# ---- 检查 Docker ----
if ! command -v docker &> /dev/null; then
    echo -e "${RED}[错误] 未检测到 Docker，请先安装 Docker${NC}"
    echo "  下载地址: https://www.docker.com/products/docker-desktop/"
    exit 1
fi

if ! docker info &> /dev/null 2>&1; then
    echo -e "${RED}[错误] Docker 未启动，请先启动 Docker Desktop${NC}"
    exit 1
fi

# ---- 检查 Docker Compose ----
if docker compose version &> /dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
    COMPOSE_CMD="docker-compose"
else
    echo -e "${RED}[错误] 未检测到 Docker Compose${NC}"
    exit 1
fi

echo -e "${GREEN}[✓]${NC} Docker 已就绪"

# ---- 停止旧容器 ----
echo ""
echo -e "${YELLOW}[*] 停止旧容器（如有）...${NC}"
$COMPOSE_CMD down --remove-orphans 2>/dev/null || true

# ---- 构建并启动 ----
echo ""
echo -e "${YELLOW}[*] 构建并启动所有服务（首次可能需要几分钟下载依赖）...${NC}"
echo ""
$COMPOSE_CMD up -d --build

# ---- 等待服务就绪 ----
echo ""
echo -e "${YELLOW}[*] 等待服务启动...${NC}"

for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost/api/settings 2>/dev/null | grep -q "200\|401"; then
        break
    fi
    sleep 2
    printf "."
done
echo ""

# ---- 检查结果 ----
echo ""
RUNNING=$($COMPOSE_CMD ps --status running -q 2>/dev/null | wc -l | tr -d ' ')
if [ "$RUNNING" -ge 4 ]; then
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  部署成功！${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo "  访问地址:  ${GREEN}http://localhost${NC}"
    echo ""
    echo "  服务状态:"
    $COMPOSE_CMD ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | head -10
    echo ""
    echo "  首次使用请在「系统设置」页面配置 AI 模型"
    echo ""
    echo "  常用命令:"
    echo "    查看日志:  $COMPOSE_CMD logs -f"
    echo "    停止服务:  $COMPOSE_CMD down"
    echo "    重启服务:  $COMPOSE_CMD restart"
    echo ""
else
    echo -e "${YELLOW}============================================${NC}"
    echo -e "${YELLOW}  部署完成，但部分服务可能未就绪${NC}"
    echo -e "${YELLOW}============================================${NC}"
    echo ""
    echo "  请检查日志: $COMPOSE_CMD logs"
    echo ""
fi
