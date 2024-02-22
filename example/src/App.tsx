import * as React from 'react';

import {
  type EmitterSubscription,
  ScrollView,
  StyleSheet,
  Text,
  ToastAndroid,
  TouchableHighlight,
  View,
} from 'react-native';
import {
  cancelConnect,
  connect,
  type Device,
  getConnectionInfo,
  type GroupInfo,
  initialize,
  receiveFile,
  removeGroup,
  sendFileTo,
  startDiscoveringPeers,
  stopDiscoveringPeers,
  subscribeOnClientUpdated,
  subscribeOnConnectionInfoUpdates,
  subscribeOnPeersUpdates,
  subscribeOnThisDeviceChanged,
  type WifiP2pInfo,
} from 'p2p-file-transfer';
import DocumentPicker, { types } from 'react-native-document-picker';
import * as RNFS from 'react-native-fs';

export default function App() {
  let groupInfoSubscription: EmitterSubscription;
  let peersSubscription: EmitterSubscription;
  let connectionSubscription: EmitterSubscription;
  let clientsSubscription: EmitterSubscription;

  const [selectedPeer, setSelectedPeer] = React.useState<Device>();
  const [peers, setPeers] = React.useState<Array<Device>>([]);
  const [clients, setClients] = React.useState<Array<string>>([]);
  const [groupInfo, setGroupInfo] = React.useState<GroupInfo>();
  const [connectionInfo, setConnectionInfo] = React.useState<WifiP2pInfo>();

  const showError = (error: any) => {
    console.log(error);
    ToastAndroid.show(JSON.stringify(error), ToastAndroid.SHORT);
  };

  const handleStop = async () => {
    peersSubscription?.remove();
    groupInfoSubscription?.remove();
    connectionSubscription?.remove();
    clientsSubscription?.remove();

    setPeers([]);
    setClients([]);
    setSelectedPeer(undefined);
    setGroupInfo(undefined);
    setConnectionInfo(undefined);

    await Promise.allSettled([
      cancelConnect(),
      stopDiscoveringPeers(),
      removeGroup(),
    ]);
  };

  const handleStart = async () => {
    try {
      await initialize();
    } catch (e) {
      showError(e);
    }

    try {
      console.log('Starting connection');

      await startDiscoveringPeers();

      peersSubscription = subscribeOnPeersUpdates((value) => {
        console.log('Peers:', value);
        setPeers(value.devices);
      });
      connectionSubscription = subscribeOnConnectionInfoUpdates((value) => {
        console.log('Connection updates: ', value);
        setConnectionInfo(value);
      });
      groupInfoSubscription = subscribeOnThisDeviceChanged((value) => {
        console.log('Group info:', value);
        setGroupInfo(value);
      });
      clientsSubscription = subscribeOnClientUpdated((value) => {
        console.log('Clients updated:', value);
        setClients(value.clients);
      });
    } catch (e) {
      showError(e);
    }
  };

  const handleConnect = async () => {
    if (!selectedPeer) return;

    try {
      await connect(selectedPeer.deviceAddress);
    } catch (error) {
      showError(error);
    }
  };

  const handleFileSend = async () => {
    try {
      const response = await DocumentPicker.pickSingle({
        type: [types.video],
      });

      const connectionInfo = await getConnectionInfo();
      const address = connectionInfo.isGroupOwner
        ? clients[0]
        : connectionInfo?.groupOwnerAddress?.hostAddress;

      const file = response?.uri;

      console.log('Sending file', clients, connectionInfo, file, address);

      if (file && address) {
        await sendFileTo(file, address);
      }
    } catch (err) {
      showError(err);
    }
  };

  const handleFileReceive = async () => {
    try {
      const folder = `${RNFS.ExternalDirectoryPath}/.spredHiddenFolder`;
      await receiveFile(folder);
    } catch (err) {
      showError(err);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView style={styles.info}>
        <Text>Peers: </Text>
        {peers.map((peer) => {
          return (
            <TouchableHighlight
              key={peer.deviceAddress}
              style={styles.device}
              onPress={() => setSelectedPeer(peer)}
            >
              <Text style={styles.text}>
                {`${peer === selectedPeer ? 'Selected:' : ''} ${JSON.stringify(peer)}`}
              </Text>
            </TouchableHighlight>
          );
        })}

        <Text>Group Info:</Text>
        {groupInfo && (
          <TouchableHighlight style={[styles.device, styles.groupInfo]}>
            <Text style={styles.text}>{JSON.stringify(groupInfo)}</Text>
          </TouchableHighlight>
        )}

        <Text>Connection Info:</Text>
        {connectionInfo && (
          <TouchableHighlight style={[styles.device, styles.connectionInfo]}>
            <Text style={styles.text}>{JSON.stringify(connectionInfo)}</Text>
          </TouchableHighlight>
        )}
      </ScrollView>

      <View style={styles.row}>
        <TouchableHighlight onPress={handleStart} style={styles.button}>
          <Text style={styles.text}>Start</Text>
        </TouchableHighlight>

        <TouchableHighlight onPress={handleStop} style={styles.button}>
          <Text style={styles.text}>Stop</Text>
        </TouchableHighlight>
      </View>

      {selectedPeer && (
        <View style={styles.row}>
          <TouchableHighlight onPress={handleConnect} style={styles.button}>
            <Text style={styles.text}>Connect</Text>
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
  connectionInfo: {
    backgroundColor: 'black',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    width: '90%',
  },
});
