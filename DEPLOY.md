# 🚀 HƯỚNG DẪN DEPLOY RMS LÊN RENDER

> Tài liệu đi kèm các blocker đã được sửa trong code (DB env, base URL, DataSeeder gate, cookie SameSite).
> Kiến trúc: 3 services trên Render — Postgres + Backend (Docker) + Frontend (Docker).

```
[Render Postgres] ◀── Internal URL ── [BE: Docker runtime] ◀── NEXT_PUBLIC_API_URL ── [FE: Docker runtime]
```

---

## Bước 0 — Chuẩn bị

1. Push 2 repo lên GitHub:
   - Repo BE: nội dung `D:\RMS\RMS_BE` (Dockerfile nằm trong thư mục con `RMS_BE/`)
   - Repo FE: nội dung `D:\RMS\RMS_FE` (Dockerfile nằm trong thư mục con `RMS_FE/`)
2. Đã build thử local thành công:
   - BE: `mvnw.cmd -q compile` ✅
   - FE: `npm run build` ✅ (37 routes)

## Bước 1 — Tạo Render Postgres

1. Render Dashboard → **New → Postgres** (free) → đặt tên `rms-db`
2. Chờ sẵn sàng, copy **Internal Database URL**, dạng:
   `postgres://user:password@dpg-xxxx:5432/rms_db_xxxx`
3. Chuyển sang format Spring Boot (thêm vào list env của BE sau):
   ```
   jdbc:postgresql://dpg-xxxx:5432/rms_db_xxxx
   ```
   (chỉ thay `postgres://` → `jdbc:postgresql://`, bỏ qua user:pass trong URL — để riêng username/password)

## Bước 2 — Tạo Backend service

**New → Web Service** → chọn repo BE:

| Setting | Giá trị |
|---|---|
| Runtime | **Docker** |
| Root Directory | `RMS_BE` (thư mục con chứa Dockerfile) |
| Instance Type | Free |
| Health Check Path | `/actuator/health` |

**Environment variables (BE):**

| Key | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://dpg-xxxx:5432/rms_db_xxxx` (từ Bước 1) |
| `DB_USERNAME` | user trong Internal URL |
| `DB_PASSWORD` | password trong Internal URL |
| `GOOGLE_OAUTH_CLIENT_ID` | client id (giống local) |
| `GOOGLE_OAUTH_CLIENT_SECRET` | client secret (giống local) |
| `GEMINI_API_KEY` | key `AIza...` từ AI Studio |
| `CORS_ALLOWED_ORIGINS` | `https://<tên-fe>.onrender.com` |
| `FRONTEND_URL` | `https://<tên-fe>.onrender.com` |
| `APP_BASE_URL` | `https://<tên-be>.onrender.com` |
| `SESSION_COOKIE_SAMESITE` | `None` |
| `SESSION_COOKIE_SECURE` | `true` |
| `SPRING_MAIL_USERNAME` | email Gmail (nếu muốn email chạy) |
| `SPRING_MAIL_PASSWORD` | App Password 16 ký tự |
| `PAYOS_CLIENT_ID` / `PAYOS_API_KEY` / `PAYOS_CHECKSUM_KEY` | từ dashboard PayOS |
| `UPLOAD_DIR` | `/var/data/uploads` (chỉ khi mua Render Disk) |

⚠️ **KHÔNG set `SEED_DATA`** trên production — nếu không seeder sẽ ghi đè dữ liệu thật.

## Bước 3 — Tạo Frontend service

**New → Web Service** → chọn repo FE:

| Setting | Giá trị |
|---|---|
| Runtime | **Docker** |
| Root Directory | `RMS_FE` (thư mục con chứa Dockerfile) |

**Environment variables (FE):**

| Key | Value |
|---|---|
| `NEXT_PUBLIC_API_URL` | `https://<tên-be>.onrender.com` |

⚠️ `NEXT_PUBLIC_*` được in-line **lúc build** — Render sẽ tự build lại khi đổi env var.

## Bước 4 — Cấu hình Google OAuth2

Google Cloud Console → **APIs & Services → Credentials → OAuth client (Web application)**:

1. **Authorized redirect URIs** — thêm:
   ```
   https://<tên-be>.onrender.com/login/oauth2/code/google
   ```
   (giữ lại URI localhost để dev local vẫn chạy)
2. **Authorized JavaScript origins** — thêm:
   ```
   https://<tên-fe>.onrender.com
   ```

## Bước 5 — Kiểm tra sau deploy

| Kiểm tra | Cách |
|---|---|
| BE sống | Mở `https://<be>.onrender.com/actuator/health` → `{"status":"UP"}` |
| DB kết nối | Mở Swagger `https://<be>.onrender.com/swagger-ui.html` → GET `/api/public/branches` trả về data |
| FE gọi BE | Mở FE → trang chủ load danh sách nhà hàng |
| Login thường | Login `admin@rms.com` (hoặc tài khoản có thật) |
| OAuth Google | Bấm "Đăng nhập bằng Google" → quay về `/oauth2/callback` có credentials |
| Chatbot AI | Mở widget chat → hỏi "gợi ý món" (key sai thì thấy câu trả lời rule-based) |
| Upload ảnh | Đổi avatar → ảnh hiển thị (URL phải là domain BE Render) |
| PayOS | Tạo booking có cọc → QR mở trang PayOS → **quay về đúng domain FE** |

---

## ⚠️ Hạn chế của Render free tier cần biết

| Hạn chế | Ảnh hưởng với RMS | Giải pháp |
|---|---|---|
| **Spin down sau 15 phút idle** | Request đầu sau idle mất 30–60s; WebSocket KDS đứt | UptimeRobot ping mỗi 10 phút, hoặc nâng Standard |
| **Filesystem ephemeral** | Ảnh upload mất khi service restart/redeploy | Render Disk ($1/mo) + `UPLOAD_DIR=/var/data/uploads`, hoặc AWS S3 |
| **1 instance duy nhất** | OK — WebSocket `sessions` đang là static Map, không scale ngang được | Khi cần scale: Redis Pub/Sub + JWT |
| **Không có vùng dữ liệu riêng** | Postgres free chỉ tồn tại 30 ngày đầu (hết hạn) | Backup định kỳ, hoặc nâng cấp DB |

## 🔁 Chạy local sau khi sửa code (đã đổi cách seed)

- `run-local.ps1` tự nạp `.env.local` — file này giờ có thêm `SEED_DATA=true` để DataSeeder + DefaultPosLayoutResetter vẫn chạy local như cũ.
- Production **không set** `SEED_DATA` → 2 seeder tự động bị skip (`@ConditionalOnProperty`).
- File mẫu đầy đủ biến env: `RMS_BE/RMS_BE/.env.example` và `RMS_FE/RMS_FE/.env.example`.

## 📌 Việc còn lại sau này (không chặn deploy)

1. Xóa mock login bypass (`AuthContext.tsx` — `admin@rms.com/123456`)
2. Bỏ `console.log` headers trong `api.ts:102`
3. 2FA TOTP đang là mock (`AuthService.verifyCode` luôn true)
4. Thêm `@EnableScheduling` nếu cần cron cảnh báo PO trễ
5. Rotate password DB local (password cũ đã nằm trong git history)
