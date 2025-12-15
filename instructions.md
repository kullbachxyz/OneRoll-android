# OneRoll Android

OneRoll guides guests through taking a limited number of photos and uploads them through the OneRoll Broker backend (no WebDAV credentials are ever shared with the app).

## How it works
- On first launch the app prompts users to scan a configuration QR code.
- The QR content must be a JSON payload that defines the occasion and points the app at a OneRoll Broker instance.
- The app enrolls with the broker to mint a short-lived upload token, then uploads photos with a bearer `Authorization` header.

## Configuration QR code
Generate a QR code that encodes JSON with these keys:

```json
{
  "occasionId": "spring-gala-2026",
  "occasionName": "Spring Gala 2026",
  "maxPhotos": 36,
  "brokerURL": "https://oneroll.example.com",
  "inviteToken": "..."
}
```

Scan the QR with the in-app scanner to onboard the device. Use a QR generator that preserves the JSON exactly (UTF-8; whitespace is fine).

### Separating different occasions
- Use a unique `occasionId` per event; issue a new invite/QR for every new occasion.
- The broker enforces per-device isolation; device IDs and uploads are scoped to the invite and the returned upload token.
- To start a new event, mint a new invite from the broker (which yields a new `inviteToken`) and distribute the updated QR.
