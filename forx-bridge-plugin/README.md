# FORX Bridge Minecraft Plugin

FORX Bridge is a lightweight, secure, and robust command delivery bridge designed for **FORX STORE** to instantly deliver products to players when an administrator accepts their order.

## Features

- **Real-Time Integration:** Instantly sends console commands over HTTP POST requests when an order is accepted on the store.
- **Embedded Web Server:** Bypasses extra library dependencies (uses JDK's built-in `HttpServer`) preventing any plugin classpath clashes.
- **Strict Authentication:** Secures endpoints with custom Configurable API Key validation.
- **IP Allowlists:** Restricts execution to designated store servers or web servers.
- **Duplicate Prevention:** Safely identifies and screens out duplicate order execution requests.
- **Status Endpoint:** Exposes a clean metadata checker on `GET /status`.

---

## 🛠 Compilation / Building

This project is built using Gradle and targets **Java 21** and the **Paper API 1.21.4**.

To compile the plugin into a production-ready `.jar` file:

```bash
# Navigate to the plugin folder
cd forx-bridge-plugin

# Run the Gradle build task
./gradlew build
```

Once completed successfully, the compiled `.jar` plugin will be generated at:
`build/libs/ForxBridge-1.0.0.jar`

---

## 📦 Installation & Setup

1. Copy the compiled `ForxBridge-1.0.0.jar` into your Minecraft server's `plugins/` directory.
2. Start (or restart) the server to generate default configuration files.
3. Open `plugins/ForxBridge/config.yml` and configure:
   - `port`: The port you want the secure bridge web server to listen on.
   - `api-key`: Enter a long, highly secure random string for authentication.
   - `allowed-ips`: List of authorized IP addresses (or leave empty to allow any IP while still validating API key credentials).
4. Reload the configuration from console or in-game:
   `/forxbridge reload`

---

## 🔌 Webhook Integration Spec

### 1. Execute Commands (`POST /execute`)

**Request Payload:**
```json
{
  "apiKey": "YOUR_SECRET_API_KEY",
  "orderId": "ORDER123",
  "player": "NOT_DarkWarrior",
  "command": "lp user NOT_DarkWarrior parent add vip"
}
```

**Successful Response (HTTP 200):**
```json
{
  "success": true,
  "message": "Executed command successfully"
}
```

**Failure Response (HTTP 401 / 403 / 500):**
```json
{
  "success": false,
  "message": "Invalid API Key"
}
```

### 2. Status check (`GET /status`)

**Successful Response (HTTP 200):**
```json
{
  "success": true,
  "plugin": "FORX Bridge",
  "version": "1.0.0",
  "serverVersion": "Paper 1.21.4...",
  "onlinePlayers": 0,
  "status": "ONLINE"
}
```
