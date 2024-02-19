import * as React from 'react';
import { useCallback } from 'react';

import {
  type EmitterSubscription,
  StyleSheet,
  Text,
  TouchableHighlight,
  View,
} from 'react-native';
import {
  cancelConnect,
  connect,
  createGroup,
  type Device,
  type GroupInfo,
  initialize,
  receiveFile,
  removeGroup,
  sendFileTo,
  startDiscoveringPeers,
  subscribeOnConnectionInfoUpdates,
  subscribeOnPeersUpdates,
  subscribeOnThisDeviceChanged,
  type WifiP2pInfo,
} from 'p2p-file-transfer';
import DocumentPicker, { types } from 'react-native-document-picker';

export default function App() {
  let groupInfoSubscription: EmitterSubscription;
  let peersSubscription: EmitterSubscription;
  let connectionSubscription: EmitterSubscription;

  const [selectedDevice, setSelectedDevice] = React.useState<Device>();
  const [devices, setDevices] = React.useState<Array<Device>>([]);

  const [groupInfo, setGroupInfo] = React.useState<GroupInfo>();
  const [connectionInfo, setConnectionInfo] = React.useState<WifiP2pInfo>();

  const handleStop = async () => {
    const result = await Promise.allSettled([
      cancelConnect(),
      startDiscoveringPeers(),
      removeGroup(),
    ]);

    result.forEach((status, value, reason) => {
      console.log('Stop', status, value, reason);
    });

    peersSubscription?.remove();
    groupInfoSubscription?.remove();
    connectionSubscription?.remove();

    setSelectedDevice(undefined);
    setConnectionInfo(undefined);
  };

  const handleStart = async () => {
    try {
      // await handleStop();
    } catch (e) {
      console.error(e);
    }

    console.log('stopped previous server');

    try {
      await initialize();
    } catch (e) {
      console.warn(e);
    }

    console.log('initialized server');

    try {
      await startDiscoveringPeers();
      console.log('discovering peers');

      peersSubscription = subscribeOnPeersUpdates((value) => {
        console.log('Peers:', value);
        setDevices(value.devices);
      });
      connectionSubscription = subscribeOnConnectionInfoUpdates((value) => {
        console.log('Connection updates: ', value);
        setConnectionInfo(value);
      });
      groupInfoSubscription = subscribeOnThisDeviceChanged((value) => {
        console.log('Group info:', value);
        setGroupInfo(value);
      });
    } catch (e) {
      console.error(e);
    }
  };

  const handleConnect = async () => {
    if (!selectedDevice) return;

    try {
      await connect(selectedDevice.deviceAddress);
    } catch (error) {
      console.error(error);
    }
  };

  const handleCreateGroup = async () => {
    try {
      await createGroup();
    } catch (e) {
      console.error(e);
    }
  };

  const handleFileSend = useCallback(async () => {
    try {
      const response = await DocumentPicker.pickSingle({
        type: [types.video],
      });
      const clients = groupInfo?.clients;
      const file = response?.uri;

      const address =
        clients?.[0]?.deviceAddress ??
        connectionInfo?.groupOwnerAddress?.hostAddress;

      console.log('Sending file', clients, connectionInfo, file, address);

      if (file && address) {
        await sendFileTo(file, address);
      }
    } catch (err) {
      console.error(err);
    }
  }, [groupInfo, connectionInfo]);

  const handleFileReceive = async () => {
    try {
      await receiveFile();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.info}>
        <Text>Peers: </Text>
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

        <Text>Group Info:</Text>
        {groupInfo && (
          <TouchableHighlight style={[styles.device, styles.groupInfo]}>
            <Text>{JSON.stringify(groupInfo)}</Text>
          </TouchableHighlight>
        )}
      </View>

      <View style={styles.row}>
        <TouchableHighlight onPress={handleStart} style={styles.button}>
          <Text style={styles.text}>Start</Text>
        </TouchableHighlight>

        <TouchableHighlight onPress={handleStop} style={styles.button}>
          <Text style={styles.text}>Stop</Text>
        </TouchableHighlight>
      </View>

      {selectedDevice && (
        <View style={styles.row}>
          <TouchableHighlight onPress={handleConnect} style={styles.button}>
            <Text style={styles.text}>Connect</Text>
          </TouchableHighlight>

          <TouchableHighlight onPress={handleCreateGroup} style={styles.button}>
            <Text style={styles.text}>Create Group</Text>
          </TouchableHighlight>
        </View>
      )}

      <View style={styles.row}>
        <TouchableHighlight onPress={handleFileSend} style={styles.button}>
          <Text style={styles.text}>Send File</Text>
        </TouchableHighlight>

        <TouchableHighlight onPress={handleFileReceive} style={styles.button}>
          <Text style={styles.text}>Receive File</Text>
        </TouchableHighlight>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
    marginVertical: 20,
  },
  info: {
    flex: 1,
    width: '90%',
  },
  text: {
    fontSize: 16,
    color: 'white',
  },
  button: {
    backgroundColor: 'blue',
    padding: 10,
    borderRadius: 5,
    flex: 1,
  },
  device: {
    backgroundColor: 'green',
    padding: 5,
    borderRadius: 5,
    marginVertical: 5,
  },
  groupInfo: {
    backgroundColor: 'red',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    width: '90%',
  },
});
