# 心尚植发

Python3（FastAPI）+ SQLite 后端，Android（Kotlin）店员端。

## 功能

- 管理员登录（JWT）
- 客户信息录入 / 搜索 / 编辑
- 植发项目管理（含停用）
- 开单：选择客户 + 项目 → 生成订单
- 订单列表与状态更新

## 目录

```
hair-clinic/
  server/     # FastAPI + SQLite API
  android/    # Android Studio 工程
```

## 启动后端

```bash
cd /Users/txc/hair-clinic/server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API 文档：http://127.0.0.1:8000/docs
- APK 下载：http://127.0.0.1:8000/download/hairclinic.apk

默认账号：

- 用户名：`admin`
- 密码：`admin123`

SQLite 数据库文件：`server/data/hair_clinic.db`

## Android 联调

1. 用 Android Studio 打开 `android/` 目录
2. 模拟器默认请求 `http://10.0.2.2:8000/`（已写在 `BuildConfig.BASE_URL`）
3. 真机请把 `android/app/build.gradle` 里的 `BASE_URL` 改成电脑局域网 IP，例如：
   `buildConfigField "String", "BASE_URL", '"http://192.168.1.8:8000/"'`
4. 确保电脑防火墙放行 8000，后端用 `--host 0.0.0.0` 启动

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| GET/POST/PUT/DELETE | `/api/customers` | 客户 |
| GET/POST/PUT/DELETE | `/api/projects` | 项目（DELETE 为停用） |
| GET/POST | `/api/orders` | 订单列表 / 开单 |
| PATCH | `/api/orders/{id}/status` | 改订单状态 |
