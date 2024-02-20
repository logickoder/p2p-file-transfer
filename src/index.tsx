import { DeviceEventEmitter, NativeModules, Platform } from 'react-native';
import { getError } from './reasonCode';

const LINKING_ERROR =
  `The package 'p2p-file-transfer' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const P2pFileTransfer = NativeModules.P2pFileTransfer
  ? NativeModules.P2pFileTransfer
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const MODULE_NAME = 'P2pFileTransfer';
// ACTIONS
const PEERS_UPDATED_ACTION = 'PEERS_UPDATED';
const CONNECTION_INFO_UPDATED_ACTION = 'CONNECTION_INFO_UPDATED';
const THIS_DEVICE_CHANGED_ACTION = 'THIS_DEVICE_CHANGED_ACTION';
const CLIENTS_UPDATED = 'CLIENTS_UPDATED';

const subscribeOnEvent = (event: string, callback: (value: any) => void) => {
  return DeviceEventEmitter.addListener(`${MODULE_NAME}:${event}`, callback);
};

export const initialize = () => P2pFileTransfer.init();

export const subscribeOnThisDeviceChanged = (
  callback: (data: GroupInfo) => void
) => subscribeOnEvent(THIS_DEVICE_CHANGED_ACTION, callback);

export const subscribeOnClientUpdated = (
  callback: (data: ClientsUpdated) => void
) => subscribeOnEvent(CLIENTS_UPDATED, callback);

export const startDiscoveringPeers = (): Promise<string> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.discoverPeers((reasonCode?: number, message?: string) => {
      reasonCode === undefined
        ? resolve('success')
        : reject(getError(reasonCode, message));
    });
  });

export const subscribeOnPeersUpdates = (
  callback: (data: { devices: Device[] }) => void
) => subscribeOnEvent(PEERS_UPDATED_ACTION, callback);

export const getAvailablePeers = (): Promise<{ devices: Device[] }> =>
  P2pFileTransfer.getAvailablePeersList();

export const stopDiscoveringPeers = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.stopPeerDiscovery((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(reasonCode)
        : reject(getError(reasonCode));
    });
  });

export const connect = (deviceAddress: string) =>
  connectWithConfig({ deviceAddress });

export const connectWithConfig = (config: ConnectionArgs): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.connectWithConfig(config, (status: number) => {
      status === undefined ? resolve(undefined) : reject(getError(status));
    });
  });

export const subscribeOnConnectionInfoUpdates = (
  callback: (value: WifiP2pInfo) => void
) => subscribeOnEvent(CONNECTION_INFO_UPDATED_ACTION, callback);

export const getConnectionInfo = (): Promise<WifiP2pInfo> =>
  P2pFileTransfer.getConnectionInfo();

export const cancelConnect = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.cancelConnect((status: number) => {
      status === undefined ? resolve(undefined) : reject(getError(status));
    });
  });

export const createGroup = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.createGroup((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(undefined)
        : reject(getError(reasonCode));
    });
  });

export const getGroupInfo = (): Promise<GroupInfo> =>
  P2pFileTransfer.getGroupInfo();

export const removeGroup = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.removeGroup((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(undefined)
        : reject(getError(reasonCode));
    });
  });

export const sendFile = (pathToFile: string): Promise<File> =>
  P2pFileTransfer.sendFile(pathToFile);

export const sendFileTo = (
  pathToFile: string,
  address: string
): Promise<File> => P2pFileTransfer.sendFileTo(pathToFile, address);

export const receiveFile = (forceToScanGallery = false): Promise<string> =>
  new Promise((resolve, _) => {
    P2pFileTransfer.receiveFile(forceToScanGallery, (pathToFile: string) => {
      resolve(pathToFile);
    });
  });

export interface Device {
  deviceAddress: string;
  deviceName: string;
  isGroupOwner: boolean;
  primaryDeviceType: string | null;
  secondaryDeviceType: string | null;
  status: number;
}

export interface ConnectionArgs {
  deviceAddress: string;
  groupOwnerIntent?: number;
}

export interface GroupInfo {
  interface: string;
  networkName: string;
  passphrase: string;
  owner: Device;
  clients: Array<Device>;
}

export interface WifiP2pInfo {
  groupOwnerAddress: {
    hostAddress: string;
    isLoopbackAddress: boolean;
  } | null;
  groupFormed: boolean;
  isGroupOwner: boolean;
}

export interface File {
  time: number;
  file: string;
}

export interface ClientsUpdated {
  clients: Array<string>;
}
