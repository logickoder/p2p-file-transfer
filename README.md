# p2p-file-transfer

A React Native package for peer-to-peer file transfers over Wi-Fi Direct.

## Installation

```bash
npm install p2p-file-transfer
```

## Linking

- **iOS:** Run `pod install` in the `ios` directory.
- **Android:** Automatic linking for React Native 0.60+. For earlier versions, follow manual linking instructions.

## Usage

1. Import the package:

```javascript
import * as P2pFileTransfer from 'p2p-file-transfer';
```

2. Check permissions and enable features:

```javascript
// Check permissions
const hasPermissions = await P2pFileTransfer.checkPermissions();

// Request permissions and if denied, open app settings
if (!hasPermissions) {
  try {
    await P2pFileTransfer.requestPermissions();
  } catch (error) {
    await P2pFileTransfer.openAppSettings();
  }
}

// Enable features like location or Wi-Fi
Promise.all([P2pFileTransfer.shouldEnableLocation(), P2pFileTransfer.isLocationEnabled()]).then(
  ([shouldEnable, enabled]) => {
    if (shouldEnable) {
      // Ideally you should prompt the user to enable location
      // either through a dialog or a button, clicking on the
      // positive action should call
      P2pFileTransfer.openLocationSettings();
    }
  },
).catchError(showError);

P2pFileTransfer.isWifiEnabled().then(enabled => {
  // Ideally you should prompt the user to enable Wi-Fi
  // either through a dialog or a button, clicking on the
  // positive action should call
  P2PFileTransfer.openWifiSettings();
}).catchError(showError);

P2pFileTransfer.isWifiHotspotEnabled().then(enabled => {
  // Ideally you should prompt the user to disable Wi-Fi hotspot
  // either through a dialog or a button, clicking on the
  // positive action should call
  P2PFileTransfer.openWifiHotspotSettings();
}).catchError(showError);

```

3. Stop any previous initialization of the module:

```javascript
try {
  // in case the module was used before, it will
  // be beneficial to stop any previous usage
  // before continuing
  peerSubscription?.remove();
  await Promise.allSettled([
    P2PFileTransfer.cancelConnect(),
    P2PFileTransfer.stopDiscoveringPeers(),
    P2PFileTransfer.removeGroup(),
  ]);
} catch (error) {
  showError(error);
}
```

4. Initialize and start discovering peers:

```javascript
let peersSubscription: EmmiterSubscription;

try {
  await P2PFileTransfer.initialize();
} catch (error) {
  showError(error);
}

try {
  await P2PFileTransfer.startDiscoveringPeers();
  peersSubscription = subscribeOnPeersUpdates(value => {
    // show the list of peers to the user
  });
} catch (error) {
  showError(error);
}
```

5. Connect to a peer:

```javascript
const deviceAddress = '...'; // Address of the peer to connect to
try {
  await P2pFileTransfer.connect(deviceAddress);
} catch (error) {
  showError(error);
}
```

6. Send a file:

```javascript
const filePath = '...'; // Path to the file to send
P2pFileTransfer.sendFile(filePath)
  .then((file) => {
    console.log('File sent:', file);
  })
  .catch((error) => {
    console.error('File sending error:', error);
  });
```

7. Receive a file:

```javascript
const destinationDirectory = '...'; // Destination for the received file
P2pFileTransfer.receiveFile(destinationDirectory)
  .then((file) => {
    console.log('File received:', file);
  })
  .catch((error) => {
    console.error('File receiving error:', error);
  });
```

## Additional Features

- **Group management:** Create, join, and manage Wi-Fi Direct groups.
- **Connection information:** Retrieve details about the current connection.
- **Progress events:** Subscribe to events for file sending and receiving progress.
- **Device information:** Get details about connected devices.
- **Error handling:** Handle potential errors with provided error codes.
