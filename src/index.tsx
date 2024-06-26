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
const P2P_STATE_CHANGED = 'P2P_STATE_CHANGED';
const PEERS_UPDATED_ACTION = 'PEERS_UPDATED';
const CONNECTION_INFO_UPDATED_ACTION = 'CONNECTION_INFO_UPDATED';
const THIS_DEVICE_CHANGED_ACTION = 'THIS_DEVICE_CHANGED_ACTION';
const CLIENTS_UPDATED = 'CLIENTS_UPDATED';
const RECEIVE_FILE = 'PROGRESS_FILE_RECEIVE';
const SEND_FILE = 'PROGRESS_FILE_SEND';

const subscribeOnEvent = (event: string, callback: (value: any) => void) => {
  return DeviceEventEmitter.addListener(`${MODULE_NAME}:${event}`, callback);
};

/**
 * Check if permissions have been granted
 */
export const checkPermissions = (): Promise<boolean> =>
  P2pFileTransfer.checkPermissions();

/**
 * Should location be enabled
 */
export const shouldEnableLocation = (): Promise<boolean> =>
  P2pFileTransfer.shouldEnableLocation();

/**
 * Checks if location is enabled
 */
export const isLocationEnabled = (): Promise<boolean> =>
  P2pFileTransfer.isLocationEnabled();

/**
 * Checks if Wi-Fi is enabled
 */
export const isWifiEnabled = (): Promise<boolean> =>
  P2pFileTransfer.isWifiEnabled();

/**
 * Checks if Wi-Fi hotspot is enabled
 */
export const isWifiHotspotEnabled = (): Promise<boolean> =>
  P2pFileTransfer.isWifiApEnabled();

/**
 * Request app permissions
 */
export const requestPermissions = (): Promise<boolean> =>
  P2pFileTransfer.requestPermissions();

/**
 * Open app settings
 */
export const openAppSettings = (): Promise<void> =>
  P2pFileTransfer.openAppSettings();

/**
 * Open Wi-Fi settings
 */
export const openWifiSettings = (): Promise<void> =>
  P2pFileTransfer.openWifiSettings();

/**
 * Open Wi-Fi hotspot settings
 */
export const openWifiHotspotSettings = (): Promise<void> =>
  P2pFileTransfer.openWifiApSettings();

/**
 * Open location settings
 */
export const openLocationSettings = (): Promise<void> =>
  P2pFileTransfer.openLocationSettings();

/**
 * Initialize the module
 */
export const initialize = () => P2pFileTransfer.init();

/**
 * Subscribe to P2P state changes
 *
 * @param callback the callback to be called when the p2p state changes
 */
export const subscribeOnP2pStateChanged = (
  callback: (data: P2pState) => void
) => subscribeOnEvent(P2P_STATE_CHANGED, callback);

/**
 * Subscribe to this device changes
 *
 * @param callback the callback to be called when the device changes
 */
export const subscribeOnThisDeviceChanged = (
  callback: (data: GroupInfo) => void
) => subscribeOnEvent(THIS_DEVICE_CHANGED_ACTION, callback);

/**
 * Subscribe to clients updates
 *
 * @param callback the callback to be called when the clients are updated
 */
export const subscribeOnClientUpdated = (
  callback: (data: ClientsUpdated) => void
) => subscribeOnEvent(CLIENTS_UPDATED, callback);

/**
 * Start discovering peers
 */
export const startDiscoveringPeers = (): Promise<string> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.discoverPeers((reasonCode?: number, message?: string) => {
      reasonCode === undefined
        ? resolve('success')
        : reject(getError(reasonCode, message));
    });
  });

/**
 * Subscribe to peers updates
 *
 * @param callback the callback to be called when the peers are updated
 */
export const subscribeOnPeersUpdates = (
  callback: (data: { devices: Device[] }) => void
) => subscribeOnEvent(PEERS_UPDATED_ACTION, callback);

/**
 * Get the list of available peers
 */
export const getAvailablePeers = (): Promise<{ devices: Device[] }> =>
  P2pFileTransfer.getAvailablePeersList();

/**
 * Stop discovering peers
 */
export const stopDiscoveringPeers = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.stopPeerDiscovery((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(reasonCode)
        : reject(getError(reasonCode));
    });
  });

/**
 * Connect to a device with the given address
 *
 * @param deviceAddress the address of the device
 */
export const connect = (deviceAddress: string) =>
  connectWithConfig({ deviceAddress });

/**
 * Connect to a device with the given config
 *
 * @param config the connection config
 */
export const connectWithConfig = (config: ConnectionArgs): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.connectWithConfig(config, (status: number) => {
      status === undefined ? resolve(undefined) : reject(getError(status));
    });
  });

/**
 * Subscribe to connection info updates
 * @param callback the callback to be called when the connection info is updated
 */
export const subscribeOnConnectionInfoUpdates = (
  callback: (value: WifiP2pInfo) => void
) => subscribeOnEvent(CONNECTION_INFO_UPDATED_ACTION, callback);

/**
 * Retrieve the connection info
 */
export const getConnectionInfo = (): Promise<WifiP2pInfo> =>
  P2pFileTransfer.getConnectionInfo();

/**
 * Cancel any ongoing connection
 */
export const cancelConnect = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.cancelConnect((status: number) => {
      status === undefined ? resolve(undefined) : reject(getError(status));
    });
  });

/**
 * Create a group
 */
export const createGroup = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.createGroup((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(undefined)
        : reject(getError(reasonCode));
    });
  });

/**
 * Get the group info
 */
export const getGroupInfo = (): Promise<GroupInfo> =>
  P2pFileTransfer.getGroupInfo();

/**
 * Exit the current group
 */
export const removeGroup = (): Promise<void> =>
  new Promise((resolve, reject) => {
    P2pFileTransfer.removeGroup((reasonCode?: number) => {
      reasonCode === undefined
        ? resolve(undefined)
        : reject(getError(reasonCode));
    });
  });

/**
 * Send a file to the group owner
 *
 * @param pathToFile the path to the file
 */
export const sendFile = (pathToFile: string): Promise<File> =>
  P2pFileTransfer.sendFile(pathToFile);

/**
 * Send a file to a specific device
 *
 * @param pathToFile the path to the file
 * @param address the address of the device
 */
export const sendFileTo = (
  pathToFile: string,
  address: string
): Promise<File> => P2pFileTransfer.sendFileTo(pathToFile, address);

/**
 * Subscribe to file send progress events
 *
 * @param callback the callback to be called when the file is being sent
 */
export const subscribeOnFileSend = (callback: (data: File) => void) =>
  subscribeOnEvent(SEND_FILE, callback);

/**
 *
 * @param destination The destination directory
 * @param forceToScanGallery If true, the file will be scanned by the media scanner
 */
export const receiveFile = (
  destination: string,
  forceToScanGallery = false
): Promise<File> =>
  P2pFileTransfer.receiveFile(destination, forceToScanGallery);

/**
 * Subscribe to file receive progress events
 *
 * @param callback the callback to be called when the file is being received
 */
export const subscribeOnFileReceive = (callback: (data: File) => void) =>
  subscribeOnEvent(RECEIVE_FILE, callback);

export interface P2pState {
  enabled: boolean;
}

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
  file?: string;
  progress: number;
}

export interface ClientsUpdated {
  clients: Array<string>;
}
