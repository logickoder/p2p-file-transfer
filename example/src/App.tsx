import * as React from 'react';
import { useCallback } from 'react';

import {
  StyleSheet,
  View,
  Text,
  TouchableHighlight,
  type EmitterSubscription,
} from 'react-native';
import {
  cancelConnect,
  connect,
  createGroup,
  type Device,
  getGroupInfo,
  initialize,
  receiveFile,
  removeGroup,
  sendFileTo,
  startDiscoveringPeers,
  stopDiscoveringPeers,
  subscribeOnConnectionInfoUpdates,
  subscribeOnPeersUpdates,
  subscribeOnThisDeviceChanged,
  type WifiP2pInfo,
} from 'p2p-file-transfer';
import DocumentPicker, { types } from 'react-native-document-picker';

export default function App() {
  let deviceSubscription: EmitterSubscription;
  let peersSubscription: EmitterSubscription;
  let connectionSubscription: EmitterSubscription;

  const [selectedDevice, setSelectedDevice] = React.useState<Device>();
  const [devices, setDevices] = React.useState<Array<Device>>([]);
  const [clients, setClients] = React.useState<Array<Device>>([]);

  const [connectionInfo, setConnectionInfo] = React.useState<WifiP2pInfo>();

  const handleInitialize = async () => {
    try {
      await initialize();
    } catch (error) {
      console.error(error);
    }
  };

  const handleStartDiscoveringPeers = async () => {
    try {
      await startDiscoveringPeers();

      peersSubscription?.remove();
      peersSubscription = subscribeOnPeersUpdates((value) => {
        console.log('Peers:', value);
        setDevices(value.devices);
      });
    } catch (error) {
      console.error(error);
    }
  };

  const handleStopDiscoveringPeers = async () => {
    try {
      peersSubscription?.remove();
      await stopDiscoveringPeers();
    } catch (error) {
      console.error(error);
    }
  };

  const handleCreateGroup = async () => {
    try {
      await createGroup();

      deviceSubscription?.remove();
      deviceSubscription = subscribeOnThisDeviceChanged((value) => {
        console.log('Group:', value);
      });
    } catch (error) {
      console.error(error);
    }
  };

  const handleRemoveGroup = async () => {
    try {
      await removeGroup();
      deviceSubscription?.remove();
    } catch (error) {
      console.error(error);
    }
  };

  const handleConnect = async () => {
    if (!selectedDevice) return;

    try {
      await connect(selectedDevice.deviceAddress);

      connectionSubscription?.remove();
      connectionSubscription = subscribeOnConnectionInfoUpdates((value) => {
        console.log('Connection updates: ', value);
        setConnectionInfo(value);
      });
    } catch (error) {
      console.error(error);
    }
  };

  const handleCancelConnect = async () => {
    if (!selectedDevice) return;

    try {
      await cancelConnect();
      connectionSubscription?.remove();

      setSelectedDevice(undefined);
      setConnectionInfo(undefined);
    } catch (error) {
      console.error(error);
    }
  };

  const handleGetGroupInfo = async () => {
    try {
      const info = await getGroupInfo();
      console.log(info);
      setClients(info.clients);
    } catch (e) {
      console.error(e);
    }
  };

  const handleFileSend = useCallback(async () => {
    try {
      const response = await DocumentPicker.pickSingle({
        type: [types.video],
      });
      const file = response?.uri;
      console.log('Sending file', clients, connectionInfo, file);

      const address =
        clients[0]?.deviceAddress ??
        connectionInfo?.groupOwnerAddress?.hostAddress;

      if (file && address) {
        await sendFileTo(file, address);
      }
    } catch (err) {
      console.error(err);
    }
  }, [connectionInfo, clients]);

  const handleFileReceive = async () => {
    try {
      await receiveFile();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <View style={styles.container}>
      {devices.map((device) => {
        return (
          <TouchableHighlight
            key={device.deviceAddress}
            style={styles.device}
            onPress={() => setSelectedDevice(device)}
          >
            <Text>
              {`${device === selectedDevice ? 'Selected:' : ''} ${JSON.stringify(device)}`}
            </Text>
          </TouchableHighlight>
        );
      })}

      <TouchableHighlight onPress={handleInitialize} style={styles.button}>
        <Text style={styles.text}>Initialize Wi-Fi Direct</Text>
      </TouchableHighlight>

      <TouchableHighlight
        onPress={handleStartDiscoveringPeers}
        style={styles.button}
      >
        <Text style={styles.text}>Start peer discovery</Text>
      </TouchableHighlight>

      <TouchableHighlight
        onPress={handleStopDiscoveringPeers}
        style={styles.button}
      >
        <Text style={styles.text}>Stop peer discovery</Text>
      </TouchableHighlight>

      <TouchableHighlight onPress={handleCreateGroup} style={styles.button}>
        <Text style={styles.text}>Create Group</Text>
      </TouchableHighlight>

      <TouchableHighlight onPress={handleRemoveGroup} style={styles.button}>
        <Text style={styles.text}>Remove Group</Text>
      </TouchableHighlight>

      <TouchableHighlight onPress={handleGetGroupInfo} style={styles.button}>
        <Text style={styles.text}>{'Get Group Info'}</Text>
      </TouchableHighlight>

      {selectedDevice && (
        <TouchableHighlight onPress={handleConnect} style={styles.button}>
          <Text style={styles.text}>Connect</Text>
        </TouchableHighlight>
      )}

      {selectedDevice && (
        <TouchableHighlight onPress={handleCancelConnect} style={styles.button}>
          <Text style={styles.text}>Cancel Connect</Text>
        </TouchableHighlight>
      )}

      <TouchableHighlight onPress={handleFileSend} style={styles.button}>
        <Text style={styles.text}>Send File</Text>
      </TouchableHighlight>

      <TouchableHighlight onPress={handleFileReceive} style={styles.button}>
        <Text style={styles.text}>Receive File</Text>
      </TouchableHighlight>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
  },
  text: {
    fontSize: 16,
  },
  button: {
    backgroundColor: 'blue',
    padding: 10,
    borderRadius: 5,
  },
  device: {
    backgroundColor: 'green',
    padding: 5,
    borderRadius: 5,
    marginBottom: 5,
  },
});
