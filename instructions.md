# OneRoll Anroid

OneRoll is a lightweight camera app that guides guests through taking a limited number of photos and uploads them to your WebDAV storage.

## How it works
- On first launch the app prompts users to scan a configuration QR code.
- The QR content must be a JSON payload that defines the event (occasion) and WebDAV credentials.
- Photos are saved locally and uploaded to `baseURL + path + /<device-id>/filename.jpg`, keeping each device in its own subfolder.

## Configuration QR code
Generate a QR code that encodes JSON with these keys:

```json
{
  "occasionName": "Spring Gala 2024",
  "maxPhotos": 36,
  "webdav": {
    "baseURL": "https://u12345.your-storagebox.de",
    "path": "/one-roll/spring-gala",
    "username": "webdav-user",
    "password": "supersecret"
  }
}
```

Scan the QR with the in-app scanner to onboard the device. Use a QR generator that preserves the JSON exactly (UTF-8, no extra whitespace is required but is fine).

### Separating different occasions
- Give each occasion its own `path` (e.g. `/one-roll/wedding-anna`, `/one-roll/wedding-bob`).
- Ensure that `path` already exists on your WebDAV server; the app can only create per-device folders inside that path, not the root path itself.
- The app automatically nests a per-device folder under that `path`, so devices sharing the same occasion stay grouped while still separated by device ID.
- To start a new event, issue a new QR code with a different `path` value and (optionally) a new `occasionName`.